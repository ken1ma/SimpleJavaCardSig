## Development environment

1. Latest macOS / Linux / Windows
1. [Java SE](https://www.oracle.com/technetwork/java/javase/downloads/) 11.0.10 LTS
1. [mill](http://www.lihaoyi.com/mill/) 0.9.4
	1. For Linux/macOS, the bootstrap script has been commited as below

			curl -L https://github.com/lihaoyi/mill/releases/download/0.9.4/0.9.4 > mill && chmod +x mill

	2. For Windows, the wrapper script has been commited as below

        curl -O https://raw.githubusercontent.com/lefou/millw/0.3.4/millw.bat

		1. Replace `./mill` below with `millw`
1. Java Card Development Kit and GlobalPlatformPro reside in `tool` directory


# Development procedures

## Build and run

1. Build and test with jcardsim

		./mill SimpleJavaCardSig.jcardsim.test
		./mill samples.JavaCardSystemInfo.jcardsim.test
		./mill samples.ByteArrayBuilder.jcardsim.test

1. Build, generate the Java Card CAP file, then install it.

		./mill SimpleJavaCardSig.applet.install
		./mill samples.JavaCardSystemInfo.applet.install
		./mill samples.ByteArrayBuilder.applet.install

    1. A Java Card 3.0 compatible card and a card reader is needed

1. Test the installed applet with PC/SC

		./mill SimpleJavaCardSig.pcsc.test
		./mill samples.JavaCardSystemInfo.pcsc.test
		./mill samples.ByteArrayBuilder.pcsc.test

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
