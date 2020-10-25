package jp.ken1ma.sim.applet;

import javacard.framework.*;
import javacard.security.KeyPair;
import javacard.security.KeyBuilder;
import javacard.security.ECPrivateKey;
import javacard.security.ECPublicKey;
import javacard.security.Signature;

public class SimpleJavaCardSigApplet extends Applet {
    public static void install(byte[] installParamsBuf, short installParamsOffset, byte installParamsLen) {
        new SimpleJavaCardSigApplet().register();
    }

    private ECPrivateKey privKey;
    private ECPublicKey pubKey;
    private Signature sig;

    private SimpleJavaCardSigApplet() {
        sig = Signature.getInstance(Signature.ALG_ECDSA_SHA_256, false);
        genKeyPairs();
    }

    public void process(APDU apdu) {
        if (selectingApplet())
            return;

        byte[] buf = apdu.getBuffer();

        // ignoring CLA, P1 and P2

        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x01:
                JCSystem.beginTransaction();
                try {
                    genKeyPairs();
                    JCSystem.commitTransaction();
                } catch (Throwable th) {
                    JCSystem.abortTransaction();
                    throw th;
                }
                getPubKey(apdu, buf);
                break;

            case 0x02:
                getPubKey(apdu, buf);
                break;

            case 0x03:
                signHash(apdu, buf);
                break;

            default:
                ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    /**
     * Generate the private and public key.
     * The existing keys are discarded.
     */
    private void genKeyPairs() {
        KeyPair keyPair = new KeyPair(KeyPair.ALG_EC_FP, KeyBuilder.LENGTH_EC_FP_128);
        keyPair.genKeyPair();
        privKey = (ECPrivateKey)keyPair.getPrivate();
        pubKey = (ECPublicKey)keyPair.getPublic();

        sig.init(privKey, Signature.MODE_SIGN); // sig.sign will reset
    }

    /**
     * Get the public key.
     * The output consists of W, Field (P), A, B, G, R, and K,
     * each of which is byte array length of short then byte array,
     * except the last K is short.
     */
    private void getPubKey(APDU apdu, byte[] buf) {
        // point of the curve
        // compressed or uncompressed forms in ANSI X9.62 4.3.6 Point-to-Octet-String Conversion
        short len = pubKey.getW(buf, (short)2);
        buf[0] = (byte)(len >> 8);
        buf[1] = (byte)len;
        short offset = (short)(2 + len);
        //print("w = "); println(buf, 2, offset);

        // prime p for TYPE_EC_FP_PUBLIC
        len = pubKey.getField(buf, (short)(offset + 2));
        buf[offset             ] = (byte)(len >> 8);
        buf[(short)(offset + 1)] = (byte)len;
        offset += (short)(2 + len);
        //print("p = "); println(buf, offset - len, offset);

        // first coefficient of the curve of the key
        len = pubKey.getA(buf, (short)(offset + 2));
        buf[offset             ] = (byte)(len >> 8);
        buf[(short)(offset + 1)] = (byte)len;
        offset += (short)(2 + len);
        //print("a = "); println(buf, offset - len, offset);

        // second coefficient of the curve of the key
        len = pubKey.getB(buf, (short)(offset + 2));
        buf[offset             ] = (byte)(len >> 8);
        buf[(short)(offset + 1)] = (byte)len;
        offset += (short)(2 + len);
        //print("b = "); println(buf, offset - len, offset);

        // fixed point of the curve
        // compressed or uncompressed forms in ANSI X9.62 4.3.6 Point-to-Octet-String Conversion
        len = pubKey.getG(buf, (short)(offset + 2));
        buf[offset             ] = (byte)(len >> 8);
        buf[(short)(offset + 1)] = (byte)len;
        offset += (short)(2 + len);
        //print("g = "); println(buf, offset - len, offset);

        // order of the fixed point G of the curve
        len = pubKey.getR(buf, (short)(offset + 2));
        buf[offset             ] = (byte)(len >> 8);
        buf[(short)(offset + 1)] = (byte)len;
        offset += (short)(2 + len);
        //print("r = "); println(buf, offset - len, offset);

        // cofactor of the order of the fixed point G of the curve
        len = pubKey.getK(); // this is not len but k
        buf[offset             ] = (byte)(len >> 8);
        buf[(short)(offset + 1)] = (byte)len;
        //print("k = " + len);

        apdu.setOutgoingAndSend((short)0, (short)(offset + 2));
    }

    /** Generate a signature (DER: SEQUENCE of two INTEGERs) */
    private void signHash(APDU apdu, byte[] buf) {
        short cdataLen = (short)(buf[ISO7816.OFFSET_LC] & 0xff);
        if (apdu.setIncomingAndReceive() != cdataLen)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);
        //print("sig input  = "); println(buf, ISO7816.OFFSET_LC, ISO7816.OFFSET_LC + cdataLen);

        short sigLen = sig.signPreComputedHash(buf, ISO7816.OFFSET_CDATA, cdataLen, buf, (short)0); // The input and output buffer data may overlap
        //print("sig output = "); println(buf, 0, sigLen);
        apdu.setOutgoingAndSend((short)0, sigLen);
    }

/*
    // For debugging in the simulator
    private void print(String text) {
        System.out.print(text);
    }
    private void println(byte[] data, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            byte high = (byte)((data[i] >> 4) & 0x0f);
            if (high >= 0x0a)
                System.out.print((char)('A' + high - 0x0a));
            else
                System.out.print((char)('0' + high));

            byte low = (byte)(data[i] & 0xf);
            if (low >= 0x0a)
                System.out.print((char)('A' + low - 0x0a));
            else
                System.out.print((char)('0' + low));
        }
        System.out.println();
    }
*/
}
