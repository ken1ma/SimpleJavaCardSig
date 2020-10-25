package jp.ken1ma.sim.applet

import com.licel.jcardsim.smartcardio.{CardSimulator, CardTerminalSimulator}
import com.licel.jcardsim.utils.AIDUtil

class JCardSimTestFramework extends utest.runner.Framework {
  import JCardSimTestFramework._

  override def setup() = {
    AppletTestSuite.channel = card.getBasicChannel
    AppletTestSuite.installApplet = { (aid, cls) =>
      simulator.installApplet(AIDUtil.create(aid), cls)
    }
  }
}

object JCardSimTestFramework {
  // https://jcardsim.org/docs/quick-start-guide-simulator-api
  val simulator = new CardSimulator
  val terminal = CardTerminalSimulator.terminal(simulator);
  val card = terminal.connect("T=0")
}
