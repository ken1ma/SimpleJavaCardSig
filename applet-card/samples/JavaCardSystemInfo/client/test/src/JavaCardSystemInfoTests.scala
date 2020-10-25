package jp.ken1ma.sim.applet
package samples

import javax.smartcardio.CommandAPDU

import utest._

object JavaCardSystemInfoTests extends AppletTestSuite {
  val appletConf = conf("JavaCardSystemInfoApplet")
  AppletTestSuite.installApplet(appletConf.appletAID, classOf[JavaCardSystemInfoApplet]) // needed for the jcardsim

  val tests = Tests {
    test("selectApplet") {
      selectApplet(appletConf.appletAID)
    }

    test("getInfo") {
      val info = JavaCardSystemInfoClient.getInfo(channel)
      println(s"info = $info")
    }
  }
}
