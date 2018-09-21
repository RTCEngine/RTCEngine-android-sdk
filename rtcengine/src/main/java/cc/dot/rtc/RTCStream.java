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
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
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
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.UUID;

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
        private RTCExternalCapturer externalCapturer;
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

        public Builder setCapturer(RTCExternalCapturer capturer) {
            this.externalCapturer = capturer;
            this.videoCapturer = capturer.internalCapturer;
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
            RTCStream stream = new RTCStream(this);
            return stream;
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

    private SurfaceTextureHelper textureHelper;
    private VideoCapturer mVideoCapturer;

    protected RtpSender videoSender;
    protected RtpSender audioSender;



    private boolean closed;
    private FilterManager filterManager;
    private CapturerObserver capturerObserver;

    protected RTCView mView;
    protected RTCStreamListener mListener;
    protected JSONObject attributes = new JSONObject();


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

        this.textureHelper =  SurfaceTextureHelper.create("VideoCapturerThread", eglContext);
        filterManager = new FilterManager();

        mView = new RTCView(this.context, eglContext);

    }


    // for internal use
    protected RTCStream(Context context,String peerId, String streamId, boolean audio, boolean video, MediaStream mediaStream, RTCEngine engine) {

        this.context = context;
        this.mediaStream = mediaStream;
        this.peerId = peerId;
        this.mediaStreamId = streamId;
        this.local = false;
        this.engine = engine;
        this.audio = audio;
        this.video = video;
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


            videoCapturer.initialize(textureHelper,context,this.capturerConsumer);
            videoCapturer.startCapture(width, height, fps);

            //mVideoSource.adaptOutputFormat(width, height, fps);
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

        CameraEnumerator enumerator = Camera2Enumerator.isSupported(this.context)
                ? new Camera2Enumerator(context)
                : new Camera1Enumerator();


        final String[] deviceNames = enumerator.getDeviceNames();

        Log.d(TAG, deviceNames.toString());

        CameraVideoCapturer cameraCapturer = null;
        for (String device : deviceNames) {
            if (enumerator.isFrontFacing(device)) {
                cameraCapturer = enumerator.createCapturer(device, cameraEventsHandler);
            }
        }

        if (cameraCapturer != null) {
            return cameraCapturer;
        }

        for (String device : deviceNames) {
            cameraCapturer = enumerator.createCapturer(device, cameraEventsHandler);
            if (cameraCapturer != null) {
                break;
            }
        }

        return cameraCapturer;
    }



    private CapturerObserver capturerConsumer = new CapturerObserver() {
        @Override
        public void onCapturerStarted(boolean b) {
            capturerObserver.onCapturerStarted(b);
        }

        @Override
        public void onCapturerStopped() {
            capturerObserver.onCapturerStopped();
        }

        @Override
        public void onFrameCaptured(VideoFrame videoFrame) {

            // here we process videoframe
            Log.d(TAG, "onFrameCaptured");
            
            capturerObserver.onFrameCaptured(videoFrame);
        }
    };


    private CameraVideoCapturer.CameraEventsHandler cameraEventsHandler = new CameraVideoCapturer.CameraEventsHandler() {

        @Override
        public void onCameraError(String s) {

            Log.d(TAG, "onCameraError " + s);
        }

        @Override
        public void onCameraDisconnected() {

            Log.d(TAG, "onCameraDisconnected");
        }

        @Override
        public void onCameraFreezed(String s) {

            Log.d(TAG, "onCameraFreezed " + s);
        }

        @Override
        public void onCameraOpening(String s) {

            Log.d(TAG, "onCameraOpening " + s);
        }

        @Override
        public void onFirstFrameAvailable() {

            Log.d(TAG, "onFirstFrameAvailable");
        }

        @Override
        public void onCameraClosed() {

            Log.d(TAG, "onCameraClosed");
        }
    };


}









