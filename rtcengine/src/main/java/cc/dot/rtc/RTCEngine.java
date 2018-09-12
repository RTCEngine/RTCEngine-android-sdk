package cc.dot.rtc;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SoftwareVideoDecoderFactory;
import org.webrtc.SoftwareVideoEncoderFactory;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.audio.AudioDeviceModule;
import org.webrtc.audio.JavaAudioDeviceModule;

import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cc.dot.rtc.exception.BuilderException;
import cc.dot.rtc.peer.Peer;
import cc.dot.rtc.peer.PeerManager;
import cc.dot.rtc.utils.AuthToken;
import cc.dot.rtc.utils.MediaConstraintUtil;
import cc.dot.rtcengine.BuildConfig;
import io.socket.client.IO;
import io.socket.client.Socket;


import io.socket.emitter.Emitter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Created by xiang on 05/09/2018.
 */



// todo,  make Observer for internal use
public class RTCEngine {


    public enum  RTCEngineStatus {
        Connecting,
        Connected,
        DisConnected
    }

    public static interface RTCEngineListener {

        public void onJoined(String userId);

        public void onLeave(String userId);


//        public void onOccurError(DotEngineErrorType errorCode);
//        public void onWarning(DotEngineWarnType warnCode);


        public void onAddLocalStream(RTCStream stream);


        public void onRemoveLocalStream(RTCStream stream);


        public void onAddRemoteStream(RTCStream stream);


        public void onRemoveRemoteStream(RTCStream stream);


        public void onStateChange(RTCEngineStatus status);


        public void onReceiveMessage(JSONObject message);

    }


    public interface TokenCallback {

        void onSuccess(String token);
        void onFailure();
    }


    public static class Builder {

        private Context context;

        private RTCEngineListener listener;


        public RTCEngine.Builder setContext(Context context) {
            this.context = context;
            return this;
        }

        public RTCEngine.Builder setDotEngineListener(RTCEngineListener listener) {
            this.listener = listener;
            return this;
        }

        public RTCEngine build() {
            if (context == null) {
                throw new BuilderException("Must be set Context");
            }
            if (listener == null) {
                throw new BuilderException("Must be set RTCEngineListener");
            }

            return new RTCEngine(context, listener);

        }

    }


    public static RTCEngine.Builder builder() {
        return new RTCEngine.Builder();
    }


    private static String TAG = RTCEngine.class.getSimpleName();

    private static RTCEngine rtcEngine;

    protected EglBase rootEglBase;

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private Context mContext;

    private RTCEngineStatus mStatus = RTCEngineStatus.DisConnected;

    private RTCEngineListener mEngineListener;

    private Socket mSocket;

    private AuthToken authToken;

    protected PeerConnectionFactory factory;

    private boolean videoCodecHwAcceleration = true;

    private boolean videoFlexfecEnabled = false;

    private PeerConnection mPeerConnection;

    private List<PeerConnection.IceServer> iceServers = new ArrayList();

    private Map<String, RTCStream> localStreams = new ConcurrentHashMap<>();
    private Map<String, RTCStream> remoteStreams = new ConcurrentHashMap<>();

    private Map<String, JSONObject>  msAttributes = new HashMap<>();

    private boolean closed = false;

    private PeerManager peerManager = new PeerManager();

    private enum SDPAction {
        Join,
        AddStream,
        RemoveStream
    }

    private SDPAction sdpAction;


    // todo  move this to a single place
    private static final String VIDEO_FLEXFEC_FIELDTRIAL =
            "WebRTC-FlexFEC-03-Advertised/Enabled/WebRTC-FlexFEC-03/Enabled/";


    private Handler mHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };



    private RTCEngine(Context context, RTCEngineListener listener) {

        if (BuildConfig.DEBUG) {
            // todo add some debug
        }

        mContext = context;
        mEngineListener = listener;

        rootEglBase = EglBase.create();


        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .setFieldTrials(this.getFieldTrials())
                .createInitializationOptions();

        PeerConnectionFactory.initialize(options);

        createPeerConnectionFactory();


    }



    public void addStream(final RTCStream stream) {

        if (mStatus != RTCEngineStatus.Connected) {
            return;
        }

        if (localStreams.get(stream.getStreamId()) != null) {
            Log.e(TAG, "stream alread in");
            return;
        }

        this.executor.execute(() -> {
            this.addStreamInternal(stream);
        });

    }


    public void removeStream(final RTCStream stream) {

        if (mStatus != RTCEngineStatus.Connected) {
            return;
        }

        if (localStreams.get(stream.getStreamId()) == null) {
            return;
        }

        localStreams.remove(stream.getStreamId());

        if (mPeerConnection == null) {
            return;
        }

        if (stream.audioSender != null) {
            mPeerConnection.removeTrack(stream.audioSender);
        }

        if(stream.videoSender != null) {
            mPeerConnection.removeTrack(stream.videoSender);
        }

        executor.execute(() -> {
            this.removeStreamInternal(stream);
        });


        mHandler.post(() -> {

            mEngineListener.onRemoveLocalStream(stream);
        });
    }


    public boolean joinRoom(final String token) {

        if (TextUtils.isEmpty(token)){

            return false;
        }

        if (mStatus != RTCEngineStatus.DisConnected) {
            return false;
        }

        authToken = AuthToken.parseToken(token);

        if (authToken == null){

            // todo on error code
            return false;
        }

        iceServers = authToken.getIceServers();

        executor.execute(() -> {
            this.setupSignlingClient();
        });

        return true;
    }


    public boolean leaveRoom() {

        if (mStatus != RTCEngineStatus.Connected) {
            return false;
        }

        this.sendLeave();


        return true;
    }


    static public void generateToken(String tokenUrl, String appSecret, String room, String user, TokenCallback tokenCallback) {

        new AsyncTask<Void, Void, String>() {

            @Override
            protected String doInBackground(Void... voids) {

                OkHttpClient httpClient = new OkHttpClient();
                MediaType JSON=MediaType.parse("application/json; charset=utf-8");

                try {
                    JSONObject json = new JSONObject();
                    json.put("room",room);
                    json.put("user",user);

                    json.put("secret", appSecret);

                    RequestBody body = RequestBody.create(JSON, json.toString());

                    Request request = new Request.Builder()
                            .url(tokenUrl)
                            .post(body)
                            .build();
                    Response response = httpClient.newCall(request).execute();

                    JSONObject responseJSON = new JSONObject(response.body().string());
                    JSONObject dataObject = responseJSON.getJSONObject("d");
                    if (!dataObject.has("token")) {
                        return null;
                    }
                    return dataObject.getString("token");

                } catch (Exception e) {
                    e.printStackTrace();
                }
                return null;
            }

            @Override
            protected void onPostExecute(String s) {

                if (tokenCallback == null) {
                    return;
                }
                if (!TextUtils.isEmpty(s)) {
                    tokenCallback.onSuccess(s);
                } else {
                    tokenCallback.onFailure();
                }

            }
        }.execute();

    }


    private void createPeerConnectionFactory() {


        if (factory != null) {
            return;
        }

        final AudioDeviceModule adm = JavaAudioDeviceModule.builder(mContext).createAudioDeviceModule();

        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        if (videoCodecHwAcceleration) {
            encoderFactory = new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(),false,true);
            decoderFactory = new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext());
        } else {
            encoderFactory = new SoftwareVideoEncoderFactory();
            decoderFactory = new SoftwareVideoDecoderFactory();
        }

        factory = PeerConnectionFactory.builder()
                .setOptions(new PeerConnectionFactory.Options())
                .setAudioDeviceModule(adm)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();

        adm.release();

    }

    private void setupSignlingClient(){

        String socketUrl = authToken.getWsUrl();
        IO.Options options = new IO.Options();
        options.reconnection = true;
        options.reconnectionAttempts = 5;
        options.reconnectionDelay = 1000;
        options.reconnectionDelayMax = 5000;

        try {
            mSocket = IO.socket(socketUrl, options);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            // todo  error callback
            return;
        }


        mSocket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                join();
            }
        });

        mSocket.on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });

        mSocket.on(Socket.EVENT_ERROR, new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });


        mSocket.on(Socket.EVENT_RECONNECT, new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });

        mSocket.on("joined", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                handleJoined(data);
            }
        });

        mSocket.on("offer", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                handleOffer(data);
            }
        });

        mSocket.on("answer", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                handleAnswer(data);
            }
        });

        mSocket.on("peerRemoved", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                handlePeerRemoved(data);
            }
        });


        mSocket.on("peerConnected", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                handlePeerConnected(data);
            }
        });

        mSocket.on("streamAdded", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                handleStreamAdded(data);
            }
        });


        mSocket.on("configure", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];
                handleConfigure(data);
            }
        });


        mSocket.on("message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {
                JSONObject data = (JSONObject) args[0];

                mHandler.post(() -> {
                    mEngineListener.onReceiveMessage(data);
                });
            }
        });

        mSocket.connect();

    }


    private void join() {


        sdpAction = SDPAction.Join;

        // todo   use iceservers
        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(new ArrayList());
        configuration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        configuration.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        configuration.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;  // PLAN_B for now


        MediaConstraints constraints = MediaConstraintUtil.connectionConstraints();

        final PeerConnection pc = factory.createPeerConnection(configuration, peerConnectionObserver);

        MediaConstraints offerConstraints =  MediaConstraintUtil.offerConstraints();

        pc.createOffer(new cc.dot.rtc.utils.SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

                mPeerConnection.setLocalDescription(this,sessionDescription);

                JSONObject object = new JSONObject();

                try {
                    object.put("appkey","appkey");
                    object.put("room", authToken.getRoom());
                    object.put("user", authToken.getUser());
                    object.put("token",authToken.getToken());
                    object.put("planb", true);
                    object.put("sdp", sessionDescription.description);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                mSocket.emit("join", object);
            }

            @Override
            public void onSetSuccess() {

            }
        }, offerConstraints);


        mPeerConnection = pc;

    }


    private void sendLeave() {

        JSONObject object = new JSONObject();
        this.mSocket.emit("leave", object);
    }


    private void close() {

        if (closed) {
            return;
        }

        closed = true;

        if (mSocket != null) {
            mSocket.disconnect();
        }

        for (RTCStream stream: localStreams.values()) {
            if (stream.mediaStream != null) {
                mPeerConnection.removeStream(stream.mediaStream);
            }
            mHandler.post(() -> {
                mEngineListener.onRemoveLocalStream(stream);
            });
        }

        for (RTCStream stream: remoteStreams.values()) {
            mHandler.post(() -> {
                mEngineListener.onRemoveRemoteStream(stream);
            });

            stream.close();
        }

        localStreams.clear();
        remoteStreams.clear();

        peerManager.clearAll();

        if (mPeerConnection != null) {
            mPeerConnection.dispose();
        }
    }

    private void  addStreamInternal(RTCStream stream) {

        stream.setupLocalMedia();

        localStreams.put(stream.getStreamId(), stream);


        if (stream.mAudioTrack != null) {
            stream.audioSender = mPeerConnection.addTrack(stream.mAudioTrack,
                    Arrays.asList(stream.getStreamId()));
        }

        if (stream.mVideoTrack != null) {
            stream.videoSender = mPeerConnection.addTrack(stream.mVideoTrack,
                    Arrays.asList(stream.getStreamId()));
        }


        stream.setPeerId(authToken.getUser());

        // now we should handle sdp,  we just ignore the get/set sdp error
        MediaConstraints offerConstraints =  MediaConstraintUtil.offerConstraints();
        mPeerConnection.createOffer(new cc.dot.rtc.utils.SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

                mPeerConnection.setLocalDescription(this, sessionDescription);

                JSONObject object = new JSONObject();

                try {
                    object.put("stream", stream.dumps());
                    object.put("sdp", sessionDescription.description);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                mSocket.emit("addStream", object);
            }

            @Override
            public void onSetSuccess() {

            }

        }, offerConstraints);

    }


    private void removeStreamInternal(RTCStream stream) {

        MediaConstraints offerConstraints = MediaConstraintUtil.offerConstraints();

        mPeerConnection.createOffer(new cc.dot.rtc.utils.SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

                mPeerConnection.setLocalDescription(new cc.dot.rtc.utils.SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                    @Override
                    public void onSetSuccess() {

                        JSONObject data = new JSONObject();

                        try {
                            data.put("stream", stream.dumps());
                            data.put("sdp", sessionDescription.description);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        mSocket.emit("removeStream", data);
                    }

                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {

            }
        }, offerConstraints);
    }


    private void handleJoined(JSONObject data) {

        JSONObject room  = data.optJSONObject("room");
        JSONArray peers = room.optJSONArray("peers");

        if (peers == null) {
            Log.e(TAG, "message does not have peers");
            return;
        }

        // refactor this
        for (int i = 0; i < peers.length(); i++) {
            try {
                JSONObject object = peers.getJSONObject(i);
                peerManager.updatePeer(object);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        String answerStr = data.optString("sdp");

        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, answerStr);

        mPeerConnection.setRemoteDescription(new cc.dot.rtc.utils.SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

            }

            @Override
            public void onSetSuccess() {

            }
        }, answer);


        setStatus(RTCEngineStatus.Connected);

    }


    private void handleOffer(JSONObject data) {


        JSONObject room  = data.optJSONObject("room");
        JSONArray peers = room.optJSONArray("peers");

        if (peers == null) {
            Log.e(TAG, "message does not have peers");
            return;
        }

        // refactor this
        for (int i = 0; i < peers.length(); i++) {
            try {
                JSONObject object = peers.getJSONObject(i);
                peerManager.updatePeer(object);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }


        String offerStr = data.optString("sdp");
        SessionDescription offer = new SessionDescription(SessionDescription.Type.OFFER, offerStr);

        // This is ugly code, make this look better
        mPeerConnection.setRemoteDescription(new cc.dot.rtc.utils.SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {}

            @Override
            public void onSetSuccess() {

                MediaConstraints constrainst = MediaConstraintUtil.answerConstraints();

                mPeerConnection.createAnswer(new cc.dot.rtc.utils.SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                        mPeerConnection.setLocalDescription(new cc.dot.rtc.utils.SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {

                                Log.d(TAG, "onCreateSuccess");
                            }
                            @Override
                            public void onSetSuccess() {
                            }
                        }, sessionDescription);

                    }

                    @Override
                    public void onSetSuccess() {}
                }, constrainst);

            }

        }, offer);

    }


    private void handleAnswer(JSONObject data) {

        JSONObject room  = data.optJSONObject("room");
        JSONArray peers = room.optJSONArray("peers");

        if (peers == null) {
            Log.e(TAG, "message does not have peers");
            return;
        }

        // refactor this
        for (int i = 0; i < peers.length(); i++) {
            try {
                JSONObject object = peers.getJSONObject(i);
                peerManager.updatePeer(object);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }


        String answerStr = data.optString("sdp");
        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, answerStr);


        mPeerConnection.setRemoteDescription(new cc.dot.rtc.utils.SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

            }

            @Override
            public void onSetSuccess() {

                Log.d(TAG, "set remote sdp");
            }
        }, answer);
    }


    private void handlePeerRemoved(JSONObject data) {


    }

    private void handlePeerConnected(JSONObject data) {


    }


    private void handleStreamAdded(JSONObject data) {

    }


    private void handleConfigure(JSONObject data) {


    }




    private String getFieldTrials() {
        String fieldTrials = "";
        if (videoFlexfecEnabled) {
            fieldTrials += VIDEO_FLEXFEC_FIELDTRIAL;
            Log.d(TAG, "Enable FlexFEC field trial.");
        }
        // other fieldTrails
        return fieldTrials;
    }


    protected void addTask(Runnable task) {
        executor.execute(task);
    }



    private void setStatus(RTCEngineStatus status) {

        if (this.mStatus == status) {
            return;
        }

        mStatus = status;

        mHandler.post(() -> {

            mEngineListener.onStateChange(mStatus);
        });
    }



    private PeerConnection.Observer peerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            String state = stringForIceState(iceConnectionState);

            Log.d(TAG, "onIceConnectionChange: " + state);

        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            Log.d(TAG, "onIceGatheringChange");
        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {

            Log.d(TAG, "onIceCandidate " + iceCandidate.sdp);
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {

            String streamId = mediaStream.getId();

            Peer peer = peerManager.peerForStream(streamId);

            if (peer == null) {
                Log.e(TAG, "onAddStream but can not find peer");
                return;
            }

            boolean audio = mediaStream.audioTracks.size() > 0;
            boolean video = mediaStream.videoTracks.size() > 0;

            RTCStream stream = new RTCStream(RTCEngine.this.mContext, peer.getId(), streamId, audio, video, mediaStream, RTCEngine.this);

            for (JSONObject streamObj: peer.getStreams()) {
                String _streamId = streamObj.optString("id", "");
                if (_streamId.equalsIgnoreCase(streamId)) {
                    JSONObject attributes = streamObj.optJSONObject("attributes");
                    stream.attributes = attributes;
                }
            }

            remoteStreams.put(streamId, stream);

            // init view here
            mHandler.post(() -> {
                RTCView view = new RTCView(RTCEngine.this.mContext, RTCEngine.this.rootEglBase.getEglBaseContext());
                view.setStream(mediaStream);

                stream.mView = view;

                mEngineListener.onAddRemoteStream(stream);
            });

        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

            Log.d(TAG, "onRemoveStream: " + mediaStream.getId());

            String streamId = mediaStream.getId();

            RTCStream stream = remoteStreams.get(streamId);

            remoteStreams.remove(streamId);

            mHandler.post(() -> {

                mEngineListener.onRemoveRemoteStream(stream);
            });
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

            Log.d(TAG, "onAddTrack" + rtpReceiver.id());
        }
    };


    private String stringForIceState(PeerConnection.IceConnectionState newState){

        String statestr;
        switch (newState){
            case NEW:
                statestr = "NEW";
                break;
            case CHECKING:
                statestr = "checking";
                break;
            case COMPLETED:
                statestr = "completed";
                break;
            case CONNECTED:
                statestr = "connected";
                break;
            case CLOSED:
                statestr = "closed";
                break;
            case FAILED:
                statestr = "failed";
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

}
