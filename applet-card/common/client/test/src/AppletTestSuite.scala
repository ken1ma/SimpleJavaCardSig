package jp.ken1ma.sim.applet

import java.nio.file.{Path, Paths}
import javax.smartcardio.{CardChannel, CommandAPDU}
import javacard.framework.Applet

import pureconfig._, pureconfig.generic.auto._, pureconfig.generic.ProductHint
import utest._

trait AppletTestSuite extends TestSuite {
  // copied from build.sc
  case class AppletConf(
    packageAIDHex: String,
    appletAIDHex:  String,
    packageName: String,
    appletClass: String,
    packageVersion: String,
  ) {
    val packageAID: Array[Byte] = hexToBytes(packageAIDHex)
    val appletAID : Array[Byte] = hexToBytes(appletAIDHex)
    def hexToBytes(hex: String) = hex.grouped(2).map(hex => Integer.parseInt(hex, 16).toByte).toArray
  }

  val modulePath: Path = Paths.get(scala.util.Properties.envOrNone("modulePath") // set by build.sc
      .getOrElse(throw new Exception("Environement variable is required: modulePath")))

  val conf: Map[String, AppletConf] = {
    val confPath = modulePath.resolve("applet.conf")
    println(s"confPath = ${confPath.toAbsolutePath}")

    implicit def hint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))
    ConfigSource.file(confPath).loadOrThrow[Map[String, AppletConf]]
  }

  def channel: CardChannel = AppletTestSuite.channel

  def selectApplet(appletAID: Array[Byte]): Unit = {
    val resp = channel.transmit(new CommandAPDU(0x00, 0xA4, 0x04, 0, appletAID))
    if (resp.getSW != 0x9000)
      throw new Exception(s"sw = ${resp.getSW}")
  }
}

object AppletTestSuite {
  var channel: CardChannel = null // to be set by PcscTestFramework / JCardSimTestFramework
  var installApplet: (Array[Byte], Class[_ <: Applet]) => Unit = null
}
