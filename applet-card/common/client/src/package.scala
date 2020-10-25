package jp.ken1ma.sim

package object applet {
  // something different from javax.smartcardio.CardException
  case class JavaCardException(message: String, cause: Throwable = null) extends Exception(message, cause)
}
