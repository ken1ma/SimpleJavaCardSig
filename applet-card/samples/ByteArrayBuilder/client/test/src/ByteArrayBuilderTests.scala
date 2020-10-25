package jp.ken1ma.sim.applet
package samples

import javax.smartcardio.{CommandAPDU, ResponseAPDU}

import utest._

object ByteArrayBuilderTests extends AppletTestSuite {
  val appletConf = conf("ByteArrayBuilderApplet")
  AppletTestSuite.installApplet(appletConf.appletAID, classOf[ByteArrayBuilderApplet]) // needed for the jcardsim

  def sendCommand(cla: Int, ins: Int)(p1: Int = 0, p2: Int = 0, data: Array[Byte] = Array())(maxExpectedRespDataSize: Int): ResponseAPDU =
      channel.transmit(new CommandAPDU(cla, ins, p1, p2, data, maxExpectedRespDataSize))

  val tests = Tests {
    test("selectApplet") {
      selectApplet(appletConf.appletAID)
    }

    test("initially empty") {
      val resp = sendCommand(0x00, 0x01)()(0)
      resp.getSW ==> 0x9000
      resp.getData ==> Array()
    }

    test("append") {
      val resp = sendCommand(0x00, 0x02)(0, 0, Array(0x02, 0x03, 0x05))(0)
      resp.getSW ==> 0x9000
      resp.getData ==> Array(0x02, 0x03, 0x05)
    }

    test("get after append") {
      val resp = sendCommand(0x00, 0x01)()(3)
      resp.getSW ==> 0x9000
      resp.getData ==> Array(0x02, 0x03, 0x05)
    }

//    test("the other is still empty") {
//      val resp = sendCommand(0x00, 0x01)(1)(0)
//      resp.getSW ==> 0x9000
//      resp.getData ==> Array()
//    }

    test("second append") {
      val resp = sendCommand(0x00, 0x02)(0, 0, Array(0x07))(0)
      resp.getSW ==> 0x9000
      resp.getData ==> Array(0x02, 0x03, 0x05, 0x07)
    }

    test("prepend") {
      val resp = sendCommand(0x00, 0x02)(0, 2, Array(0, 0, 0, 0, 0x01))(0)
      resp.getSW ==> 0x9000
      resp.getData ==> Array(0x01, 0x02, 0x03, 0x05, 0x07)
    }

    test("replace middle") {
      val resp = sendCommand(0x00, 0x02)(0, 2, Array(0, 1, 0, 4, 0x12, 0x13, 0x15))(0)
      resp.getSW ==> 0x9000
      resp.getData ==> Array(0x01, 0x12, 0x13, 0x15, 0x07)
    }

    test("replace left") {
      val resp = sendCommand(0x00, 0x02)(0, 2, Array(0, 0, 0, 2, 0x21, 0x22))(0)
      resp.getSW ==> 0x9000
      resp.getData ==> Array(0x21, 0x22, 0x13, 0x15, 0x07)
    }

    test("replace right") {
      val resp = sendCommand(0x00, 0x02)(0, 1, Array(0, 3, 0x35, 0x37))(0)
      resp.getSW ==> 0x9000
      resp.getData ==> Array(0x21, 0x22, 0x13, 0x35, 0x37)
    }

    test("clear") {
      val resp = sendCommand(0x00, 0x02)(0, 1, Array(0, 0))(0)
      resp.getSW ==> 0x9000
      resp.getData ==> Array()
    }

    test("get after clear") {
      val resp = sendCommand(0x01, 0x01)()(0)
      resp.getSW ==> 0x9000
      resp.getData ==> Array()
    }
  }
}
