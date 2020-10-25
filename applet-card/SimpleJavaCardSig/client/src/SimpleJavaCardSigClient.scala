package jp.ken1ma.sim.applet

import java.math.BigInteger
import java.security.{KeyFactory, Signature, MessageDigest}
import java.security.spec.{ECPublicKeySpec, ECPoint, ECParameterSpec, EllipticCurve, ECFieldFp}
import java.security.interfaces.ECPublicKey
import javax.smartcardio.{CardChannel, CommandAPDU, ResponseAPDU}

class SimpleJavaCardSigClient {
  import SimpleJavaCardSigClient._

  var pubKey: ECPublicKey = null
  val sig: Signature = Signature.getInstance("SHA256withECDSA")

  def getPubKey(channel: CardChannel): ECPublicKey = {
    val resp = channel.transmit(new CommandAPDU(0x00, 0x02, 0, 0, 256))
    if (resp.getSW != 0x9000)
      throw JavaCardException(s"sw = ${resp.getSW}")
    val data = resp.getData

    val wLen = data(0) << 8 | data(1)
    val wBytes = data.slice(2, 2 + wLen)
    val (wx, wy) = decodePoint(wBytes)
    //println(s"w = ${hex(wBytes)} ($wx, $wy)")

    val pOffset = 2 + wLen;
    val pLen = data(pOffset) << 8 | data(pOffset + 1)
    val pBytes = data.slice(pOffset + 2, pOffset + 2 + pLen)
    val p = new BigInteger(1, pBytes)
    //println(s"p = ${hex(pBytes)} ($p)")

    val aOffset = pOffset + 2 + pLen;
    val aLen = data(aOffset) << 8 | data(aOffset + 1)
    val aBytes = data.slice(aOffset + 2, aOffset + 2 + aLen)
    val a = new BigInteger(1, aBytes)
    //println(s"a = ${hex(aBytes)} ($a)")

    val bOffset = aOffset + 2 + aLen;
    val bLen = data(bOffset) << 8 | data(bOffset + 1)
    val bBytes = data.slice(bOffset + 2, bOffset + 2 + bLen)
    val b = new BigInteger(1, bBytes)
    //println(s"b = ${hex(bBytes)} ($b)")

    val gOffset = bOffset + 2 + bLen;
    val gLen = data(gOffset) << 8 | data(gOffset + 1)
    val gBytes = data.slice(gOffset + 2, gOffset + 2 + gLen)
    val (gx, gy) = decodePoint(gBytes)
    //println(s"g = ${hex(gBytes)} ($gx, $gy)")

    val rOffset = gOffset + 2 + gLen;
    val rLen = data(rOffset) << 8 | data(rOffset + 1)
    val rBytes = data.slice(rOffset + 2, rOffset + 2 + rLen)
    val r = new BigInteger(1, rBytes)
    //println(s"r = ${hex(rBytes)} ($r)")

    val kOffset = rOffset + 2 + rLen;
    val k = data(kOffset) << 8 | data(kOffset + 1)
    //println(s"k = $k")

    if (data.length != kOffset + 2)
      throw JavaCardException(s"data.length = ${data.length} (expected (${kOffset + 2})")

    val curve = new EllipticCurve(new ECFieldFp(p), a, b)
    val params = new ECParameterSpec(curve, new ECPoint(gx, gy), r, k)
    val keySpec = new ECPublicKeySpec(new ECPoint(wx, wy), params)
    pubKey = ecKeyFactory.generatePublic(keySpec).asInstanceOf[ECPublicKey]
    pubKey
  }

  def hashAndSign(channel: CardChannel, input: Array[Byte]): Array[Byte] = {
    val hash = sha256.digest(input)

    val resp = channel.transmit(new CommandAPDU(0x00, 0x03, 0, 0, hash, 256))
    if (resp.getSW != 0x9000)
      throw JavaCardException(s"sw = ${resp.getSW}")
    val output = resp.getData

    //import java.util.Base64
    //println(s"base64 output = ${Base64.getEncoder.encodeToString(output)}")

    // verify the signature
    // TODO: Java Card doesn't generate shortest integer bytes? https://stackoverflow.com/questions/28843390/verify-javacard-signature-alg-ecdsa-sha-on-bouncy-castle
    if (pubKey == null)
      getPubKey(channel)
    sig.initVerify(pubKey)
    sig.update(input)
    if (!sig.verify(output))
      throw JavaCardException(s"failed to verify signature: ${hex(output)}")

    output
  }
}

object SimpleJavaCardSigClient {
  val ecKeyFactory = KeyFactory.getInstance("EC")
  val sha256 = MessageDigest.getInstance("SHA-256");

  // ANSI X9.62 4.3.7 Octet-String-to-Point Conversion
  def decodePoint(data: Array[Byte]): (BigInteger, BigInteger) = {
    val pc = data(0)
    if (pc != 4)
      throw new JavaCardException(s"ANSI X9.62: not in the uncompressed form: ${hex(data)}")

    if ((data.length - 1) % 2 != 0)
      throw new JavaCardException(s"ANSI X9.62: unexpected length: ${hex(data)}")
    val l = (data.length - 1) / 2

    val x = data.slice(1, 1 + l)
    val y = data.slice(1 + l, data.length)
    (new BigInteger(1, x), new BigInteger(1, y))
  }

  def hex(data: Array[Byte]): String = data.map(bt => f"$bt%02X").mkString
}
