package cc.dot.rtc;

import android.content.Context;

import org.webrtc.EglBase;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Created by xiang on 05/09/2018.
 */

public class RTCEngine {

    private static String TAG = RTCEngine.class.getSimpleName();

    private static RTCEngine rtcEngine;

    protected EglBase rootEglBase;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private Context mContext;
}
