package jp.ken1ma.sim.applet

import javax.smartcardio.CommandAPDU

import utest._

object SimpleJavaCardSigTests extends AppletTestSuite {
  val appletConf = conf("SimpleJavaCardSigApplet")
  AppletTestSuite.installApplet(appletConf.appletAID, classOf[SimpleJavaCardSigApplet]) // needed for the jcardsim

  val client = new SimpleJavaCardSigClient

  val tests = Tests {
    test("selectApplet") {
      selectApplet(appletConf.appletAID)
    }

    test("getPubKey") {
      val pubKey = client.getPubKey(channel)
    }

    test("hashAndSign") {
      val input = Array[Byte](0x01)
      val output = client.hashAndSign(channel, input)
    }
  }
}
