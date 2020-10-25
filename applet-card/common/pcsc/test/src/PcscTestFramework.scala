package jp.ken1ma.sim.applet

import scala.jdk.CollectionConverters._
import javax.smartcardio.{TerminalFactory, CardTerminals}

import apdu4j.TerminalManager
import apdu4j.terminals.LoggingCardTerminal
import utest._

class PcscTestFramework extends utest.runner.Framework {
  override def setup() = {
    TerminalManager.fixPlatformPaths
    val terminalFactory = TerminalFactory.getDefault
    val terminals = terminalFactory.terminals.list(CardTerminals.State.CARD_PRESENT).asScala
    if (terminals.isEmpty)
      throw new Exception("No card reader with a card present")
    if (terminals.size >= 2)
      throw new Exception(s"More than one card reader with a card present: ${terminals.map(_.getName).mkString(", ")}")
    val terminal = terminals.head
    println(s"CardTerminal: $terminal")

    val card = LoggingCardTerminal.getInstance(terminal).connect("T=0")

    AppletTestSuite.channel = card.getBasicChannel();
    AppletTestSuite.installApplet = (_, _) => () // the applet must have been already installed
  }
}
