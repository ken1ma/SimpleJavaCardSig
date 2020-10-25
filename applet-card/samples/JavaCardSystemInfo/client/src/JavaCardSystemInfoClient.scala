package jp.ken1ma.sim.applet
package samples

import javax.smartcardio.{CardChannel, CommandAPDU, ResponseAPDU}

object JavaCardSystemInfoClient {
  /*
    ivy"com.klinec:jcardsim:3.0.5.11" 3.0
    TAISYS 3.0
  */
  case class Info(major: Byte, minor: Byte)
  def getInfo(channel: CardChannel): Info = {
    val resp = channel.transmit(new CommandAPDU(0x00, 0x00, 0, 0, 2))
    if (resp.getSW != 0x9000)
      throw JavaCardException(s"sw = ${resp.getSW}")

    val data = resp.getData
    if (data.length != 2)
      throw JavaCardException(s"data.length = ${data.length}")
    Info(data(0), data(1))
  }
}
