package cc.dot.rtc.utils;

import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public interface SDPSetObserver extends SdpObserver {

    public  default  void onCreateSuccess(SessionDescription var1){}

    public default  void onCreateFailure(String var1) { }

}
