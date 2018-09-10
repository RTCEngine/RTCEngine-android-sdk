package cc.dot.rtc.utils;

import android.util.Log;

import org.webrtc.SessionDescription;

/**
 * Created by xiang on 10/09/2018.
 */

public interface SdpObserver extends org.webrtc.SdpObserver {


    default public void onCreateFailure(String s) {

        Log.e("SDPObserver", s);
    }


    default public void onSetFailure(String s) {

        Log.e("SDPObserver", s);
    }
}
