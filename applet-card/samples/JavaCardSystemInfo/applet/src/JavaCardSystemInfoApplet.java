package jp.ken1ma.sim.applet.samples;

import javacard.framework.*;

public class JavaCardSystemInfoApplet extends Applet {
    public static void install(byte[] installParamsBuf, short installParamsOffset, byte installParamsLen) {
        new JavaCardSystemInfoApplet().register();
    }

    private JavaCardSystemInfoApplet() {
    }

    public void process(APDU apdu) {
        if (selectingApplet())
            return;

        byte[] buf = apdu.getBuffer();
        Util.setShort(buf, (short)0, JCSystem.getVersion());
        apdu.setOutgoingAndSend((short)0, (short)2);
    }
}
