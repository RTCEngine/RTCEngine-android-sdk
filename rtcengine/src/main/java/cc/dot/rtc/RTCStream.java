package cc.dot.rtc;

import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.DataChannel;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.NV21Buffer;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.TextureBufferImpl;
import org.webrtc.VideoCapturer;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoFrame;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;
import org.webrtc.audio.AudioDeviceModule;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import cc.dot.rtc.capturer.RTCVideoFrame;
import cc.dot.rtc.filter.FilterManager;

import static android.util.Log.d;

/**
 * Created by xiang on 05/09/2018.
 */

public class RTCStream implements PeerConnection.Observer {

    private static String TAG = RTCStream.class.getSimpleName();


    public static Builder builder(Context context, RTCEngine engine) {
        return new Builder(context, engine);
    }


    public static class Builder {

        private Context context;
        private boolean local;
        private boolean audio;
        private boolean video;
        private String mediaStreamId;
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

    private RTCVideoProfile videoProfile;


    private RTCEngine engine;

    protected AudioTrack mAudioTrack;
    protected VideoTrack mVideoTrack;

    private VideoSource mVideoSource;
    private AudioSource mAudioSource;

    private SurfaceTextureHelper textureHelper;
    private VideoCapturer mVideoCapturer;

    private boolean usingFrontCamera = true;
    private boolean usingExternalVideo = false;

    private boolean closed;
    private FilterManager filterManager;
    private CapturerObserver capturerObserver;

    private PeerConnectionFactory factory;
    private AudioDeviceModule audioDeviceModule;

    protected String mediaStreamId;
    protected boolean local;
    protected boolean audio;
    protected boolean video;

    protected String publisherId;

    protected RtpSender videoSender;
    protected RtpSender audioSender;

    protected RtpTransceiver mVideoTransceiver;
    protected RtpTransceiver mAudioTransceiver;

    protected PeerConnection peerConnection;


    protected RTCView mView;
    protected RTCStreamListener mListener;
    protected JSONObject attributes = new JSONObject();


    private Handler mHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };


    RTCStream(Builder builder) {

        this.context = builder.context;
        this.local = builder.local;
        this.audio = builder.audio;
        this.video = builder.video;
        this.videoProfile = builder.videoProfile;
        this.attributes = builder.attributes;
        this.mediaStreamId = builder.mediaStreamId;


        this.engine = builder.engine;

        this.factory = engine.factory;

        this.audioDeviceModule = engine.audioDeviceModule;

        EglBase.Context eglContext = engine.rootEglBase.getEglBaseContext();

        this.textureHelper =  SurfaceTextureHelper.create("VideoCapturerThread", eglContext);
        filterManager = new FilterManager();

        mView = new RTCView(this.context, eglContext);

    }


    // for internal use
    protected RTCStream(Context context, RTCEngine engine) {

        this.context = context;
        this.engine = engine;
    }


    public boolean isLocal() { return  this.local; }


    public boolean hasAduio() {
        return this.audio;
    }


    public boolean hasVideo() { return  this.video; }


    public String getStreamId() {
        return this.mediaStreamId;
    }


    public RTCView getView() {
        return mView;
    }


    public boolean switchCamara() {


        if (mVideoCapturer instanceof CameraVideoCapturer) {
            ((CameraVideoCapturer)mVideoCapturer).switchCamera(new CameraVideoCapturer.CameraSwitchHandler(){

                @Override
                public void onCameraSwitchDone(boolean b) {

                }

                @Override
                public void onCameraSwitchError(String s) {

                }
            });
        }
        return true;
    }


    public void muteAudio(boolean muting) {

        if (mAudioTrack != null) {

            if (mAudioTrack.enabled() == !muting) {
                return;
            }

            mAudioTrack.setEnabled(!muting);


            JSONObject data = new JSONObject();
            try {
                data.put("audio", true);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            engine.sendConfigure(data);

        }
    }


    public void muteVideo(boolean muting) {

        if (mVideoTrack != null) {

            if (mVideoTrack.enabled() == !muting) {
                return;
            }

            mVideoTrack.setEnabled(!muting);

            JSONObject data = new JSONObject();
            try {
                data.put("video", true);
            } catch (JSONException e) {
                e.printStackTrace();
            }

            engine.sendConfigure(data);

        }
    }


    public void setStreamListener(RTCStreamListener listener) {
        mListener = listener;
    }


    public void destroy() {

        // todo
    }


    public void setupLocalMedia() {

        if (!local) {
            throw new RuntimeException("have to be local to setup local media");
        }


        if (video) {

            mVideoSource = factory.createVideoSource(false);
            mVideoTrack = factory.createVideoTrack(UUID.randomUUID().toString(), mVideoSource);

            int width = this.videoProfile.getWidth();
            int height = this.videoProfile.getHeight();
            int fps = this.videoProfile.getFps();

            if (!usingExternalVideo) {
                mVideoCapturer = createLocalVideoCapturer();

                mVideoCapturer.initialize(textureHelper,context,this.capturerConsumer);
                mVideoCapturer.startCapture(width, height, fps);

                //mVideoSource.adaptOutputFormat(width, height, fps);
            }

            mView.setVideoTrack(mVideoTrack);
        }

        if (audio) {
            mAudioSource = factory.createAudioSource(new MediaConstraints());
            mAudioTrack = factory.createAudioTrack(UUID.randomUUID().toString(), mAudioSource);
        }
    }


    public void shutdownLocalMedia() {

        if (video) {
            if (!usingExternalVideo) {
                try {
                    mVideoCapturer.stopCapture();
                } catch (InterruptedException e) {

                }

            }
        }
    }


    public void useExternalVideoSource(boolean external) {
        usingExternalVideo = external;
    }



    public void sendVideoFrame(final RTCVideoFrame frame, final int rotation) {

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

        capturerConsumer.onFrameCaptured(videoFrame);
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


    protected JSONObject dumps() {

        JSONObject object = new JSONObject();

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

        }
    }


    protected void setView(RTCView view) {
        mView = view;
    }


    private VideoCapturer createLocalVideoCapturer() {

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
        public void onFrameCaptured(final VideoFrame videoFrame) {


            final boolean isTextureFrame = videoFrame.getBuffer() instanceof VideoFrame.TextureBuffer;

            if (!isTextureFrame) {
                capturerObserver.onFrameCaptured(videoFrame);
                return;
            }

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


    private String stringForConnectionState(PeerConnection.PeerConnectionState newState){

        String statestr;
        switch (newState){
            case NEW:
                statestr = "NEW";
                break;
            case CONNECTING:
                statestr = "connecting";
                break;
            case CONNECTED:
                statestr = "connected";
                break;
            case CLOSED:
                statestr = "closed";
                break;
            case DISCONNECTED:
                statestr = "disconnected";
                break;
            default:
                statestr = "";
                break;
        }

        return statestr;
    }


    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {

    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

    }

    @Override
    public void onConnectionChange(PeerConnection.PeerConnectionState newState) {

        String state = stringForConnectionState(newState);
        Log.d(TAG, "PeerConnectionState:" + state);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {

    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {

        Log.d(TAG, "IceCandidate:" + iceCandidate.sdp);
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

    }

    @Override
    public void onAddStream(MediaStream mediaStream) {

    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {

    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {

    }

    @Override
    public void onRenegotiationNeeded() {

    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        if (rtpReceiver.track().kind().equalsIgnoreCase("video")) {


            mHandler.post(() -> {
                mView.setVideoTrack((VideoTrack)rtpReceiver.track());
            });
        }


        if (rtpReceiver.track().kind().equalsIgnoreCase("audio")) {
            Log.d(TAG, "RtpReceiver " + rtpReceiver.id());
        }
    }

    @Override
    public void onTrack(RtpTransceiver transceiver) {

        Log.d(TAG, "onTrack: " + transceiver.getMid() + transceiver.toString());
    }
}









