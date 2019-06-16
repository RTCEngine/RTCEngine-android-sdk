package cc.dot.rtc.utils;

import org.webrtc.MediaConstraints;

/**
 * Created by xiang on 07/09/2018.
 */

public class MediaConstraintUtil {


    public static MediaConstraints connectionConstraints() {
        MediaConstraints constraints = new MediaConstraints();
        constraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));

        return constraints;
    }


    public static MediaConstraints offerConstraints() {
        MediaConstraints constraints = new MediaConstraints();

        return constraints;
    }


    public static MediaConstraints answerConstraints() {
        MediaConstraints constraints = new MediaConstraints();
        return constraints;
    }

}
