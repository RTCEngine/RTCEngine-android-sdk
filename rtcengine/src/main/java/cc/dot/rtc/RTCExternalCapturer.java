package cc.dot.rtc;

import android.os.SystemClock;

import org.webrtc.NV21Buffer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;

import java.util.concurrent.TimeUnit;

import cc.dot.rtc.capturer.RTCVideoFrame;

/**
 * Created by xiang on 12/09/2018.
 */

public class RTCExternalCapturer {

    protected RTCInternalCapturer internalCapturer = new RTCInternalCapturer();


    public void onVideoFrame(final RTCVideoFrame frame, final int rotation) {

        final long captureTimeNs =
                TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());

        VideoFrame.Buffer buffer;
        if (frame.type == RTCVideoFrame.VideoFrameType.NV21_TYPE) {
            buffer = new NV21Buffer(frame.bytesArray,frame.width,frame.height,null);
        } else {
            // todo other
            return;
        }


        VideoFrame videoFrame = new VideoFrame(buffer, rotation /* rotation */, captureTimeNs);

        if (internalCapturer != null && internalCapturer.capturerObserver != null) {

            internalCapturer.capturerObserver.onFrameCaptured(videoFrame);
        }

    }

}
