package cc.dot.rtc.utils;

import android.util.Log;

public interface SDPCreateObserver extends org.webrtc.SdpObserver {


    default public void onSetFailure(String s) { }

    default public void onSetSuccess() {}
}
