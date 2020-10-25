## Development environment

1. Latest macOS / Linux / Windows
1. [Java SE](https://www.oracle.com/technetwork/java/javase/downloads/) 11.0.8 LTS
1. [mill](http://www.lihaoyi.com/mill/) 0.8.0
	1. For Linux/macOS, the bootstrap script has been downloaded and commited as below

			curl -L https://github.com/lihaoyi/mill/releases/download/0.8.0/0.8.0-11-8cd135 > mill && chmod +x mill

        1. An unreleased version is used to [pass JVM args](https://github.com/lihaoyi/mill/pull/933) (merged in [1a410f](https://github.com/lihaoyi/mill/commit/5e164d3e9b9f66458da5dd58b571f1dd8d96928a))

	2. On Windows, download https://github.com/lihaoyi/mill/releases/download/0.8.0/0.8.0-11-8cd135-assembly and save it as `mill.bat`
		1. Replace `./mill` below with `mill`
1. Java Card Development Kit and GlobalPlatformPro reside in `tool` directory


# Development procedures

## Build and run

1. Build and test with jcardsim

		./mill SimpleJavaCardSig.jcardsim.test

1. Build, generate the Java Card CAP file, then install it.

		./mill SimpleJavaCardSig.applet.install

    1. A Java Card 3.0 compatible card and a card reader is needed

1. Test the installed applet with PC/SC

		./mill SimpleJavaCardSig.pcsc.test

## Helpful commands

1. `gp` commands

    1. List the applets

            ./mill gp.tool -list

        1. Or `java -jar tool/gp.jar -list`

    1. Show some basic information of the card

            ./mill gp.tool -info

    1. List the commands

            ./mill gp.tool -help

1. `sc` commands

    1. List the readers

            java -jar tool/apdu4j.jar -l
