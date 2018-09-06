package cc.dot.rtc.capturer;

import android.content.Context;
import android.os.SystemClock;

import org.webrtc.CapturerObserver;
import org.webrtc.JavaI420Buffer;
import org.webrtc.NV12Buffer;
import org.webrtc.NV21Buffer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoFrame;
import org.webrtc.VideoFrame.Buffer;

import java.util.concurrent.TimeUnit;

/**
 * Created by xiang on 05/09/2018.
 */

public abstract class RTCVideoCapturer implements VideoCapturer {

    private static final String TAG = RTCVideoCapturer.class.getSimpleName();

    private CapturerObserver capturerObserver;

    private int mWidth;
    private int mHeight;
    private int mFramerate;

    private byte[] mCacheBuffer;



    @Override
    public void initialize(SurfaceTextureHelper surfaceTextureHelper, Context context, CapturerObserver capturerObserver) {

        if (capturerObserver == null) {
            throw new RuntimeException("capturerObserver not set.");
        }
        this.capturerObserver = capturerObserver;
    }


    @Override
    public void startCapture(int width, int height, int framerate) {

        mWidth = width;
        mHeight = height;
        mFramerate = framerate;

        if (this.capturerObserver!=null){
            this.capturerObserver.onCapturerStarted(true);
        }

    }

    @Override
    public void stopCapture() throws InterruptedException {

        if (this.capturerObserver != null) {
            this.capturerObserver.onCapturerStopped();
        }
    }

    @Override
    public void changeCaptureFormat(int width, int height, int framerate) {

        if (this.capturerObserver != null) {

        }
    }

    @Override
    public void dispose() {

    }


    @Override
    public boolean isScreencast() {
        return false;
    }


    public void onVideoFrame(final RTCVideoFrame frame, final int rotation) {

        final long captureTimeNs =
                TimeUnit.MILLISECONDS.toNanos(SystemClock.elapsedRealtime());

        Buffer buffer;
        if (frame.type == RTCVideoFrame.VideoFrameType.NV21_TYPE) {
            buffer = new NV21Buffer(frame.bytesArray,frame.width,frame.height,null);
        } else {
            // todo other
            return;
        }


        VideoFrame videoFrame = new VideoFrame(buffer, rotation /* rotation */, captureTimeNs);

        this.capturerObserver.onFrameCaptured(videoFrame);
    }



    // todo make a texture frame
    public void onTextureFrame(){


    }

}
