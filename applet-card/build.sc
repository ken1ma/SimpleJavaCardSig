import mill._, scalalib._, scalajslib._
import $ivy.`com.github.pureconfig::pureconfig:0.14.0`, pureconfig._, pureconfig.generic.auto._, pureconfig.generic.ProductHint

object JavaCard {
  val jcHome = os.pwd / "tool" / "jc31b108_kit" // targetVersion can be 3.0.4, 3.0.5, or 3.1.0
  val targetVersion = "3.0.4"

  case class AppletConf(
    packageAIDHex: String,
    appletAIDHex:  String,
    packageName: String,
    appletClass: String,
    packageVersion: String,
  )

  def convert(destDir: os.Path, classesDir: os.Path, conf: AppletConf, log: mill.api.Logger): Unit = {
    def decorateHex(compact: String) = compact.grouped(2).map(hex => s"0x$hex").mkString(":") // 0102 to 0x01:0x02
    val args = Array(
      "-d", destDir.toString,
      "-target", targetVersion,
      "-classdir", classesDir.toString,
      "-exportpath", (jcHome / s"api_export_files_$targetVersion").toString,
      "-applet", decorateHex(conf.appletAIDHex), conf.appletClass,
      conf.packageName,
      decorateHex(conf.packageAIDHex),
      conf.packageVersion,
    )
    val mainClassName = "com.sun.javacard.converter.Main"

    // fork to run the converter
    // to avoid "ERROR: Second call to constructor of static parser.  You must either use ReInit() or set the JavaCC option STATIC to false during parser generation."
    val toolsJar = os.list(jcHome / "lib").filter(_.last == "tools.jar").head
    log.info(s"exec: $mainClassName ${args.mkString(" ")}")
    JavaFork.call(Seq("-cp", toolsJar.toString, mainClassName) ++ args: _*)

/*
    // load the converter class in a new ClassLoader
    val classLoader = new java.net.URLClassLoader(toolsJar.toNIO.toUri.toURL)
    val mainClass = Class.forName(mainClassName, true, classLoader)
    val mainMethod = mainClass.getMethod("main", classOf[Array[String]])
    SystemExitInterceptor.run { mainMethod.invoke(null, args) }
*/
  }
}

trait InheritUnmanagedClasspath extends JavaModule {
  def unmanagedClasspath = T { T.traverse(moduleDeps)(_.unmanagedClasspath)().fold(Agg.empty[PathRef])(_ ++ _) }
}

trait CommonJavaModule extends JavaModule with InheritUnmanagedClasspath {
  def javacOptions = Seq(
    "-target", "7", // java_card_tools-win-bin-b_71-02_sep_2020 says "unsupported class file format of version 55.0" (Java 11), and "52.0" (Java 8)
    "-source", "7", // required by -target
    "-g", // generate `LocalVariableTable` https://docs.oracle.com/en/java/javacard/3.1/guide/setting-java-compiler-options.html
    "-Xlint:all",
    "-deprecation",
    "-encoding", "UTF-8",
  )
}

trait CommonScalaModule extends ScalaModule with InheritUnmanagedClasspath {
  def scalaVersion = "2.13.4"
  def scalacOptions = Seq(
  // the default settings from https://scastie.scala-lang.org
    "-encoding", "UTF-8",
    "-deprecation",
    "-feature",
    "-unchecked",
  )

  trait Tests extends super.Tests with InheritUnmanagedClasspath
}

trait AppletRootModule extends CommonJavaModule { rootModule =>
  object applet extends CommonJavaModule {
    def moduleDeps = Seq(common.shared)

    // Read applet.conf
    def confFile = T.source { millSourcePath / os.up / "applet.conf" }
    import upickle.default.{ReadWriter => RW, macroRW}
    implicit val confRw: RW[JavaCard.AppletConf] = macroRW
    def conf = T {
      val confPath = confFile().path
      T.ctx.log.info("reading $confPath")

      implicit def hint[A] = ProductHint[A](ConfigFieldMapping(CamelCase, CamelCase))
      ConfigSource.file(confPath.toNIO).loadOrThrow[Map[String, JavaCard.AppletConf]]
    }

    /** Generates Java Card CAP file */
    def cap = T { // This could be run as a part of compile() but then System.out.println would be unusable even in the jcardsim
      val destDir = T.dest / "javacard"
      conf().toSeq.map { entry =>
        val (appletName, appletConf) = entry
        JavaCard.convert(destDir, compile().classes.path, appletConf, T.ctx.log)
        val capDir = os.RelPath(appletConf.packageName.replace('.', '/'))
        PathRef(destDir / capDir / "javacard" / (capDir.last + ".cap"))
      }
    }

    /** Install the CAP file */
    def install() = T.command {
      for (capFile <- cap().map(_.path)) {
        T.ctx.log.info(s"exec: gp -f -install $capFile")
        SystemExitInterceptor.run { pro.javacard.gp.GPTool.main(Array("-f", "-install", capFile.toString)) }
      }
    }

    def uninstall() = T.command {
      for (capFile <- cap().map(_.path)) {
        T.ctx.log.info(s"exec: gp -uninstall $capFile")
        SystemExitInterceptor.run { pro.javacard.gp.GPTool.main(Array("-uninstall", capFile.toString)) }
      }
    }
  }

  object client extends CommonScalaModule {
    def moduleDeps = Seq(common.client)

    object test extends CommonScalaModule {
      def moduleDeps = Seq(client, common.client.test, applet)
    }
  }

  object pcsc extends CommonScalaModule {
    object test extends Tests {
      def moduleDeps = Seq(client.test, common.pcsc.test)
      def testFrameworks = Seq("jp.ken1ma.sim.applet.PcscTestFramework")
      def forkEnv = Map("modulePath" -> rootModule.millSourcePath.toString)

      def sources = client.test.sources // TODO: the test cases in moduleDeps don't seem to be found
    }
  }

  object jcardsim extends CommonScalaModule {
    object test extends Tests {
      def moduleDeps = Seq(client.test, common.jcardsim.test)
      def testFrameworks = Seq("jp.ken1ma.sim.applet.JCardSimTestFramework")
      def forkEnv = Map("modulePath" -> rootModule.millSourcePath.toString)

      def sources = client.test.sources // TODO: the test cases in moduleDeps don't seem to be found

      // Avoid "Invalid APDU loaded. You may have JC API in your classpath before JCardSim"
      def runClasspath = T { super.runClasspath().filter(_.path != common.jcApiJar) }
    }
  }
}

object common extends Module {
  val jcApiJar = JavaCard.jcHome / "lib" / s"api_classic-${JavaCard.targetVersion}.jar"

  object shared extends CommonJavaModule {
    /** Java Card Development Kit */
    def unmanagedClasspath = T {
      Agg.from(Seq(PathRef(jcApiJar)))
    }
  }

  object client extends CommonScalaModule {
    def moduleDeps = Seq(shared)

    object test extends CommonScalaModule {
      def moduleDeps = Seq(client)
      def ivyDeps = Agg(
        ivy"com.github.pureconfig::pureconfig:0.14.0",
        ivy"com.lihaoyi::utest:0.7.6",
      )
    }
  }

  object pcsc extends Module {
    object test extends CommonScalaModule {
      def moduleDeps = Seq(client.test)
      def unmanagedClasspath = T { super.unmanagedClasspath() ++
        Agg.from(Seq(PathRef(os.pwd / "tool" / "apdu4j.jar"))) }
    }
  }

  object jcardsim extends Module {
    object test extends CommonScalaModule {
      def moduleDeps = Seq(client.test)
      def ivyDeps = Agg(
        ivy"com.klinec:jcardsim:3.0.5.11", // https://mvnrepository.com/artifact/com.klinec/jcardsim
          //ivy"com.licel:jcardsim:2.2.2", // https://github.com/licel/jcardsim is missing com.licel.jcardsim.smartcardio.CardSimulator
      )
    }
  }
}

object SimpleJavaCardSig extends AppletRootModule
object samples extends Module {
  object JavaCardSystemInfo extends AppletRootModule
  object ByteArrayBuilder   extends AppletRootModule
}

import $cp.tool.gp // gp.jar
object gp extends Module {
  def tool(args: String*) = T.command {
    SystemExitInterceptor.run { pro.javacard.gp.GPTool.main(args.toArray) }
  }
}

/** Intercept System.exit */
object SystemExitInterceptor {
  /** @return Some(status) when System.exit is called */
  def runAndReturnExitCode(f: => Unit): Option[Int] = {
    val orig = System.getSecurityManager
    System.setSecurityManager(new SystemExitInterceptor(Option(orig)))
    try {
      f
      None
    } catch {
      case SystemExitInterceptException(status) => Some(status)
    } finally {
      System.setSecurityManager(orig)
    }
  }

  def run(f: => Unit): Unit = {
    for (exitCode <- runAndReturnExitCode(f))
      if (exitCode != 0)
        throw new Exception(s"exitCode is non-zero: $exitCode")
  }

  case class SystemExitInterceptException(status: Int) extends SecurityException
}

class SystemExitInterceptor(delegate: Option[SecurityManager]) extends SecurityManager {
  import SystemExitInterceptor.SystemExitInterceptException

  // https://github.com/sbt/sbt/blob/develop/run/src/main/scala/sbt/TrapExit.scala could be consulted
  override def checkExit(status: Int): Unit = throw new SystemExitInterceptException(status)

  import java.security.Permission
  override def checkPermission(perm: Permission)                  = delegate.foreach(_.checkPermission(perm))
  override def checkPermission(perm: Permission, context: Object) = delegate.foreach(_.checkPermission(perm, context))
}

object JavaFork {
  import scala.util.Properties.javaHome
  val javaCommand = os.Path(javaHome) / "bin" / "java" // TODO: should be "java.exe" for Windows?
  def call(args: String*) = { 
    os.proc(javaCommand.toString +: args)
        .call(stdin = os.Inherit, stdout = os.Inherit, stderr = os.Inherit)
  }
}
