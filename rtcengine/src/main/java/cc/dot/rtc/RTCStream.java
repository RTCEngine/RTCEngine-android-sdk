package cc.dot.rtc;

import android.content.Context;
import android.content.Intent;

import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.CapturerObserver;
import org.webrtc.EglBase;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.UUID;

import cc.dot.rtc.capturer.RTCVideoCapturer;
import cc.dot.rtc.capturer.RTCVideoFrame;
import cc.dot.rtc.filter.FilterManager;

/**
 * Created by xiang on 05/09/2018.
 */

public class RTCStream {

    private static String TAG = RTCStream.class.getSimpleName();


    public static Builder builder(Context context, RTCEngine engine) {
        return new Builder(context, engine);
    }

    public static class Builder {

        private Context context;
        private boolean local;
        private boolean audio;
        private boolean video;
        private Intent screenCaptureIntent;
        private String mediaStreamId;
        private VideoCapturer videoCapturer;
        private RTCEngine engine;

        private RTCVideoProfile videoProfile = RTCVideoProfile.RTCEngine_VideoProfile_240P_2;

        public Builder(Context context, RTCEngine engine) {
            this.context = context;
            this.engine = engine;
        }

        public Builder setAudio(boolean audio) {
            this.audio = audio;
            return this;
        }

        public Builder setVideo(boolean video) {
            this.video = video;
            return this;
        }

        public Builder setScreenCaptureIntent(Intent intent) {
            this.screenCaptureIntent = intent;
            this.video = true;
            return this;
        }

        public Builder setCapturer(RTCVideoCapturer capturer) {
            this.videoCapturer = capturer;
            return this;
        }

        public Builder setVideoProfile(RTCVideoProfile profile) {
            this.videoProfile = profile;
            return this;
        }

        public RTCStream build() {
            this.local = true;
            this.mediaStreamId = UUID.randomUUID().toString();
            return null;
        }

    }


    public interface RTCStreamListener {


        void onCameraError(RTCStream stream, String error);

        void onVideoMuted(RTCStream stream, boolean muted);

        void onAudioMuted(RTCStream stream, boolean muted);

        void onAudioLevel(RTCStream stream, int audioLevel);

    }


    private Context context;
    private boolean local;
    private boolean audio;
    private boolean video;
    private Intent screenCaptureIntent;
    private String mediaStreamId;
    private MediaStream mediaStream;
    private String peerId;
    private VideoCapturer videoCapturer;
    private RTCEngine engine;

    private AudioTrack mAudioTrack;
    private VideoTrack mVideoTrack;
    private VideoSource mVideoSource;
    private AudioSource mAudioSource;

    private RTCView mView;
    private boolean closed;

    protected  RTCStreamListener mListener;
    private FilterManager filterManager;
    private CapturerObserver capturerObserver;
    private JSONObject attributes = new JSONObject();


    RTCStream(Builder builder) {

        this.context = builder.context;
        this.local = builder.local;
        this.audio = builder.audio;
        this.video = builder.video;
        this.screenCaptureIntent = builder.screenCaptureIntent;
        this.mediaStreamId = builder.mediaStreamId;
        this.videoCapturer = builder.videoCapturer;
        this.engine = builder.engine;

        EglBase.Context eglContext = engine.rootEglBase.getEglBaseContext();


        this.engine.addTask(() -> {
            this.setupLocalMedia();
        });
    }


    public void setupLocalMedia() {

        if (!local) {
            throw new RuntimeException("have to be local to setup local media");
        }

        if (mediaStream != null) {
            return;
        }

        PeerConnectionFactory factory = this.engine.factory;

        mediaStream = factory.createLocalMediaStream(mediaStreamId);

        // video enabled
        if (video || screenCaptureIntent != null || videoCapturer != null) {

            // todo
        }


        if (audio) {
            // todo 
        }

        if (mView != null) {
            mView.setStream(mediaStream);
        }
    }

}









