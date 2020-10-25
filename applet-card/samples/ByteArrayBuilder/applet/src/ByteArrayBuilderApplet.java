package jp.ken1ma.sim.applet.samples;

import javacard.framework.*;

/*
    This applet keeps an array of byte arrays, for which
    get/replace instructions are provided.

    replace INS: CDATA structure
        [startIndex [endIndex]] [byteArray]
        where startIndex is inclusive and endIndex is exclusive

    P1 specifies which byte array to operate on.
    When P2 is
        0   there are no startIndex nor endIndex
        1   there is only startIndex
        2   there are both startIndex and endIndex
    When startIndex or endIndex is omitted, they are assumed to be the end of the byte array.

    append/prepend/clear operations can be accomplished by
                P2  CDATA
        append  0   byteArrayToAppend
        prepend 2   0 0 byteArrayToPrepend
        clear   1   0 (noByteArray)

    This class can be used to see how the garbage collector works in the runtime,
    and how much memory an applet can access.
*/
public class ByteArrayBuilderApplet extends Applet {
    // Converter 3.1.0 says "unsupported multidimensional array"
    //public static final short NUM_BYTE_ARRAYS = 8;

    public static final short BYTE_ARRAY_INCREMENT = 1024;

    public static void install(byte[] installParamsBuf, short installParamsOffset, byte installParamsLen) {
        new ByteArrayBuilderApplet().register();
    }

    private byte[] byteArray;
    private short byteArrayLen;

    private ByteArrayBuilderApplet() {
        byteArray = new byte[BYTE_ARRAY_INCREMENT];
    }

    public void process(APDU apdu) {
        if (selectingApplet())
            return;

        byte[] buf = apdu.getBuffer();

        //byte p1 = buf[ISO7816.OFFSET_P1];
        //if (!(p1 >= 0 && p1 < NUM_BYTE_ARRAYS))
        //    ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);

        switch (buf[ISO7816.OFFSET_INS]) {
            case 0x01: get    (apdu, buf); break;
            case 0x02: replace(apdu, buf); break;
            default: ISOException.throwIt(ISO7816.SW_INS_NOT_SUPPORTED);
        }
    }

    private void get(APDU apdu, byte[] buf) {
        if (byteArrayLen > 0) {
            Util.arrayCopyNonAtomic(byteArray, (short)0, buf, (short)0, byteArrayLen);
            apdu.setOutgoingAndSend((short)0, byteArrayLen);

        } else
            apdu.setOutgoingAndSend((short)0, (short)0);
    }

    private void replace(APDU apdu, byte[] buf) {
        byte p2 = buf[ISO7816.OFFSET_P2];
        if (!(p2 >= 0 && p2 <= 2))
            ISOException.throwIt(ISO7816.SW_INCORRECT_P1P2);

        short cdataLen = (short)(buf[ISO7816.OFFSET_LC] & 0xff);
        if (apdu.setIncomingAndReceive() != cdataLen)
            ISOException.throwIt(ISO7816.SW_WRONG_LENGTH);

        byte[] current = byteArray;
        short currentLen = byteArrayLen;

        short startIndex;
        if (p2 >= 1)
            startIndex = Util.getShort(buf, ISO7816.OFFSET_CDATA);
        else
            startIndex = currentLen;

        short endIndex;
        if (p2 >= 2)
            endIndex = Util.getShort(buf, (short)(ISO7816.OFFSET_CDATA + 2));
        else
            endIndex = currentLen;

        if (!(startIndex <= endIndex && endIndex <= currentLen))
            ISOException.throwIt(ISO7816.SW_DATA_INVALID);

        short paramOffset = (short)(2 * p2);
        short paramLen = (short)(cdataLen - paramOffset);

        short nextLen = (short)(currentLen - (endIndex - startIndex) + paramLen);
        if (currentLen != nextLen)
            byteArrayLen = nextLen;

        byte[] next;
        JCSystem.beginTransaction();
        try {
            if (current == null || nextLen > current.length) {
                // without the casts, Converter 3.1.0 says
                // "unsupported int type of intermediate value, must cast intermediate value to type short or byte." and
                // "unsupported int type array index, must cast array index to type short or byte."
                next = new byte[(short)((short)(nextLen + BYTE_ARRAY_INCREMENT - 1) / BYTE_ARRAY_INCREMENT * BYTE_ARRAY_INCREMENT)];
                byteArray = next;

                // copy [0, startIndex)
                if (startIndex > 0)
                    Util.arrayCopy(current, (short)0, next, (short)0, startIndex);

                // copy the given bytes
                if (paramLen > 0)
                    Util.arrayCopy(buf, (short)(ISO7816.OFFSET_CDATA + paramOffset), next, startIndex, paramLen);

                // copy [endIndex, currentLen)
                if (endIndex < currentLen)
                    Util.arrayCopy(current, endIndex, next, (short)(startIndex + paramLen), (short)(currentLen - endIndex));

                if (current != null) {
                    try {
                        JCSystem.requestObjectDeletion();
                    } catch (SystemException ex) {
                        if (ex.getReason() != SystemException.ILLEGAL_USE) // the object deletion mechanism is not implemented
                            throw ex;
                    }
                }

            } else {
                next = current;

                // move [endIndex, currentLen)
                if (startIndex < currentLen)
                    Util.arrayCopy(next, endIndex, next, (short)(startIndex + paramLen), (short)(currentLen - endIndex));

                // copy the given bytes
                if (paramLen > 0)
                    Util.arrayCopy(buf, (short)(ISO7816.OFFSET_CDATA + paramOffset), next, startIndex, paramLen);
            }
            JCSystem.commitTransaction();

        } catch (Throwable th) {
            JCSystem.abortTransaction();
            throw th;
        }

        get(apdu, buf);
    }
}
