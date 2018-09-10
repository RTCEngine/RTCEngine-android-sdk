package cc.dot.rtc;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Capturer;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.EglBase;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpSender;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.UUID;

import cc.dot.rtc.capturer.RTCVideoCapturer;
import cc.dot.rtc.filter.FilterManager;

import static android.util.Log.d;

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
        private JSONObject attributes;
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

        public Builder setAttributes(JSONObject attributes) {
            this.attributes = attributes;
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
    private RTCVideoProfile videoProfile;
    private String mediaStreamId;
    protected MediaStream mediaStream;
    private String peerId;

    private RTCEngine engine;

    protected AudioTrack mAudioTrack;
    protected VideoTrack mVideoTrack;

    private VideoSource mVideoSource;
    private AudioSource mAudioSource;
    private VideoCapturer mVideoCapturer;

    protected RtpSender videoSender;
    protected RtpSender audioSender;


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
        this.videoProfile = builder.videoProfile;
        this.attributes = builder.attributes;
        this.screenCaptureIntent = builder.screenCaptureIntent;
        this.mediaStreamId = builder.mediaStreamId;
        this.mVideoCapturer = builder.videoCapturer;
        this.engine = builder.engine;

        EglBase.Context eglContext = engine.rootEglBase.getEglBaseContext();


    }


    // for internal use
    protected RTCStream(String peerId, String streamId, MediaStream mediaStream, JSONObject attributes) {

        this.mediaStream = mediaStream;
        this.peerId = peerId;
        this.mediaStreamId = streamId;
        this.local = false;
        this.attributes = attributes;

    }


    public boolean isLocal() { return  this.local; }


    public boolean isHasAduio() {
        return this.audio;
    }


    public void  enableFaceBeauty(boolean enable){

        if (filterManager != null){
            filterManager.setUseFilter(enable);
        }
    }


    public void  setBeautyLevel(float level){
        if (level > 1.0f || level < 0.0f){
            return;
        }
        if (filterManager != null){
            filterManager.setBeautyLevel(level);
        }
    }


    public void setBrightLevel(float level){
        if (level > 1.0f || level < 0.0f){
            return;
        }
        if (filterManager != null){
            filterManager.setBrightLevel(level);
        }
    }


    public String getStreamId() {
        return this.mediaStreamId;
    }

    public String getPeerId() {
        return peerId;
    }

    public RTCView getView() {
        return mView;
    }


    public boolean switchCamara() {

        // todo
        return true;
    }


    public void muteAudio(boolean muted) {
        // todo
    }


    public void muteVideo(boolean muted) {
        // todo
    }


    public void setStreamListener(RTCStreamListener listener) {
        mListener = listener;
    }


    public void destroy() {

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
        if (video || screenCaptureIntent != null || mVideoCapturer != null) {

            VideoCapturer videoCapturer = createLocalVideoCapturer();
            boolean isScreencast = (screenCaptureIntent != null);
            VideoSource videoSource = factory.createVideoSource(isScreencast);

            capturerObserver = videoSource.getCapturerObserver();

            int width = this.videoProfile.getWidth();
            int height = this.videoProfile.getHeight();
            int fps = this.videoProfile.getFps();

            VideoTrack videoTrack = factory.createVideoTrack(UUID.randomUUID().toString(), videoSource);

            mediaStream.addTrack(videoTrack);

            mVideoTrack = videoTrack;
            mVideoSource = videoSource;
            mVideoCapturer = videoCapturer;

            videoCapturer.startCapture(width, height, fps);

            mVideoSource.adaptOutputFormat(width, height, fps);
        }


        if (audio) {
            AudioSource audioSource = factory.createAudioSource(new MediaConstraints());
            AudioTrack audioTrack = factory.createAudioTrack(UUID.randomUUID().toString(), audioSource);

            mediaStream.addTrack(audioTrack);

            mAudioSource = audioSource;
            mAudioTrack = audioTrack;
        }


        if (mView != null) {
            mView.setStream(mediaStream);
        }
    }


    protected void onMuteAudio(boolean muted){

        if (mListener != null){
            mListener.onAudioMuted(this,muted);
        }
    }

    protected void onMuteVideo(boolean muted){

        if (mListener != null){
            mListener.onVideoMuted(this,muted);
        }
    }

    protected void onAudioLevel(int audioLevel){

        if (mListener != null){
            mListener.onAudioLevel(this,audioLevel);
        }
    }

    protected void setPeerId(String peerId) {
        this.peerId = peerId;
    }


    protected JSONObject dumps() {

        JSONObject object = new JSONObject();

        try {
            object.put("id", peerId);
            object.put("msid", mediaStreamId);
            object.put("local", local);
            object.put("bitrate", videoProfile.getBits());
            object.put("attributes", attributes);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        return object;
    }

    protected void close() {

        if (closed) {
            return;
        }

        this.closed = true;

        if (local) {

            if (mVideoCapturer != null) {

                try {
                    mVideoCapturer.stopCapture();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                mVideoCapturer.dispose();
                mVideoCapturer = null;
            }


            if (mVideoSource != null){
                mVideoSource.dispose();
                mVideoSource = null;
            }

            if (mAudioSource != null){
                mAudioSource.dispose();
                mAudioSource = null;
            }

            if (mediaStream != null) {
                mediaStream.dispose();
                mediaStream = null;
            }

        }

    }


    public MediaStream getMediaStream() {
        return mediaStream;
    }

    protected void setMediaStream(MediaStream mediaStream) {
        this.mediaStream = mediaStream;
    }


    protected void setVideoCapturer(VideoCapturer videoCapturer) {
        mVideoCapturer = videoCapturer;
    }

    protected void setView(RTCView view) {
        mView = view;
    }





    private VideoCapturer createLocalVideoCapturer() {

        if (mVideoCapturer != null) {
            return mVideoCapturer;
        } else if (screenCaptureIntent != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                return new ScreenCapturerAndroid(screenCaptureIntent, new MediaProjection.Callback() {
                    @Override
                    public void onStop() {
                        d(TAG, "MediaProjection cameras.");
                    }
                });
            } else {
                throw new RuntimeException("Only android 5.0+ support this");
            }
        }

        // by default, we capturer to texture
        Camera1Enumerator enumerator = new Camera1Enumerator();
        final String[] deviceNames = enumerator.getDeviceNames();

        for (String device : deviceNames) {
            if (enumerator.isFrontFacing(device)) {
                Camera1Capturer camera1Capturer = (Camera1Capturer)enumerator.createCapturer(device, cameraEventsHandler);
                if (camera1Capturer != null) {
                    return camera1Capturer;
                }
            }
        }

        for (String device : deviceNames) {
            Camera1Capturer camera1Capturer = (Camera1Capturer)enumerator.createCapturer(device, cameraEventsHandler);
            if (camera1Capturer != null) {
                return camera1Capturer;
            }
        }

        return null;
    }


    private CameraVideoCapturer.CameraEventsHandler cameraEventsHandler = new CameraVideoCapturer.CameraEventsHandler() {

        @Override
        public void onCameraError(String s) {

        }

        @Override
        public void onCameraDisconnected() {

        }

        @Override
        public void onCameraFreezed(String s) {

        }

        @Override
        public void onCameraOpening(String s) {

        }

        @Override
        public void onFirstFrameAvailable() {

        }

        @Override
        public void onCameraClosed() {

        }
    };


}









