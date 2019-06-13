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
import cc.dot.rtc.utils.MediaConstraintUtil;
import cc.dot.rtc.utils.SdpObserver;
import io.socket.client.Ack;
import io.socket.client.IO;
import io.socket.client.Socket;


import io.socket.emitter.Emitter;
import io.socket.parseqs.ParseQS;
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
        New,
        Connecting,
        Connected,
        DisConnected
    }

    public static interface RTCEngineListener {


        public void onJoined();


        public void onOccurError(int errorCode);


        public void onAddLocalStream(RTCStream stream);


        public void onRemoveLocalStream(RTCStream stream);


        public void onAddRemoteStream(RTCStream stream);


        public void onRemoveRemoteStream(RTCStream stream);


        public void onStateChange(RTCEngineStatus status);


        public void onReceiveMessage(JSONObject message);


        public void onStreamAdded(RTCStream stream);


        public void onStreamRemoved(RTCStream stream);

    }




    public static class Builder {

        private Context context;

        private RTCEngineListener listener;

        private RTCConfig config;

        public RTCEngine.Builder setContext(Context context) {
            this.context = context;
            return this;
        }

        public RTCEngine.Builder setDotEngineListener(RTCEngineListener listener) {
            this.listener = listener;
            return this;
        }

        public RTCEngine.Builder setConfig(RTCConfig config) {
            this.config = config;
            return this;
        }


        public RTCEngine build() {
            if (context == null) {
                throw new BuilderException("context should not be null");
            }
            if (listener == null) {
                throw new BuilderException("listener should not be null ");
            }
            if (config == null) {
                throw new BuilderException("config should not be null");
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

    private RTCConfig config;

    protected PeerConnectionFactory factory;

    private boolean videoCodecHwAcceleration = true;

    private boolean videoFlexfecEnabled = false;


    private List<PeerConnection.IceServer> iceServers = new ArrayList();

    private RTCStream localStream = null;

    private Map<String, RTCStream> localStreams = new ConcurrentHashMap<>();
    private Map<String, RTCStream> remoteStreams = new ConcurrentHashMap<>();


    private Map<String, JSONObject>  msAttributes = new HashMap<>();

    private boolean closed = false;

    private String mRoom;


    private Handler mHandler = new Handler(Looper.getMainLooper()) {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
        }
    };



    private RTCEngine(Context context, RTCEngineListener listener) {



        mContext = context;
        mEngineListener = listener;

        rootEglBase = EglBase.create();


        PeerConnectionFactory.InitializationOptions options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
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

//        if (mPeerConnection == null) {
//            return;
//        }
//
//        if (stream.audioSender != null) {
//            mPeerConnection.removeTrack(stream.audioSender);
//        }
//
//        if(stream.videoSender != null) {
//            mPeerConnection.removeTrack(stream.videoSender);
//        }

        executor.execute(() -> {
            this.removeStreamInternal(stream);
        });


        mHandler.post(() -> {
            mEngineListener.onRemoveLocalStream(stream);
        });
    }


    public boolean joinRoom(final String room) {

        if (TextUtils.isEmpty(room)){
            return false;
        }


        if (mStatus == RTCEngineStatus.Connected) {
            return false;
        }

        mRoom = room;

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

        this.close();

        return true;
    }


    public void publish(RTCStream stream) {

        if (mStatus != RTCEngineStatus.Connected) {
            return;
        }

        localStream = stream;


    }


    private void createPeerConnectionFactory() {


        if (factory != null) {
            return;
        }

        final AudioDeviceModule adm = JavaAudioDeviceModule.builder(mContext).createAudioDeviceModule();

        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;

        if (videoCodecHwAcceleration) {
            encoderFactory = new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(),true,false);
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


    private PeerConnection createPeerConnection(PeerConnection.Observer observer) {


        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(iceServers);

        configuration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        configuration.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        final PeerConnection pc = factory.createPeerConnection(configuration, observer);

        return pc;

    }

    private void setupSignlingClient(){

        String socketUrl = config.getSignallingServer();
        IO.Options options = new IO.Options();
        options.reconnection = true;
        options.reconnectionAttempts = 5;
        options.reconnectionDelay = 1000;
        options.reconnectionDelayMax = 5000;
        Map<String,String> queryMap = new HashMap<>();
        queryMap.put("room", mRoom);
        options.query = ParseQS.encode(queryMap);

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


        mSocket.on("streamadded", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });

        mSocket.on("streamremoved", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

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

//        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(authToken.getIceServers());
//        configuration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
//        configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
//        configuration.iceTransportsType = PeerConnection.IceTransportsType.ALL;
//        configuration.sdpSemantics = PeerConnection.SdpSemantics.PLAN_B;  // PLAN_B for now
//
//        if (authToken.getIceTransportPolicy() == "relay") {
//            configuration.iceTransportsType = PeerConnection.IceTransportsType.RELAY;
//        }
//
//
//        final PeerConnection pc = factory.createPeerConnection(configuration, peerConnectionObserver);

//        MediaConstraints offerConstraints =  MediaConstraintUtil.offerConstraints();
//
//        pc.createOffer(new SdpObserver() {
//            @Override
//            public void onCreateSuccess(SessionDescription sessionDescription) {
//
//                mPeerConnection.setLocalDescription(this,sessionDescription);
//
//                JSONObject object = new JSONObject();
//
//                try {
//                    object.put("appkey","appkey");
//                    object.put("room", authToken.getRoom());
//                    object.put("user", authToken.getUser());
//                    object.put("token",authToken.getToken());
//                    object.put("planb", true);
//                    object.put("sdp", sessionDescription.description);
//                } catch (JSONException e) {
//                    e.printStackTrace();
//                }
//
//                mSocket.emit("join", object);
//            }
//
//            @Override
//            public void onSetSuccess() {
//
//            }
//        }, offerConstraints);
//
//
//        mPeerConnection = pc;


        JSONObject data = new JSONObject();

        try {
            data.put("room",mRoom);
        } catch (JSONException e) {
            e.printStackTrace();
        }


        mSocket.emit("join",new Object[]{data}, new Ack() {
            @Override
            public void call(Object... args) {
                JSONObject obj = (JSONObject)args[0];
                handleJoined(obj);
            }
        });
    }


    private void sendLeave() {

        JSONObject object = new JSONObject();
        this.mSocket.emit("leave", object);
    }

    protected void sendConfigure(JSONObject data) {

        this.mSocket.emit("configure", data);
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

        setStatus(RTCEngineStatus.DisConnected);

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

        MediaConstraints offerConstraints =  MediaConstraintUtil.offerConstraints();

        // the SdpObserver is a little ugly, but it works
        mPeerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

                mPeerConnection.setLocalDescription(new SdpObserver(){

                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {
                    }

                    @Override
                    public void onSetSuccess() {

                        mPeerConnection.setRemoteDescription(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {
                            }

                            @Override
                            public void onSetSuccess() {
                            }

                        }, mPeerConnection.getRemoteDescription());
                    }

                }, sessionDescription);


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

        mPeerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

                mPeerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                    @Override
                    public void onSetSuccess() {

                        mPeerConnection.setRemoteDescription(new SdpObserver() {
                            @Override
                            public void onCreateSuccess(SessionDescription sessionDescription) {

                            }

                            @Override
                            public void onSetSuccess() {

                            }
                        }, mPeerConnection.getRemoteDescription());


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
        JSONArray streams = room.optJSONArray("streams");


        setStatus(RTCEngineStatus.Connected);

        mHandler.post(() -> {
            mEngineListener.onJoined();
        });


        for (int i = 0; i < streams.length(); i++) {
            String publisherId;
            try {
                JSONObject streamObj = streams.getJSONObject(i);
                Log.d(TAG, streamObj.toString());
                publisherId = streamObj.getString("publisherId");
            } catch (JSONException e) {
                e.printStackTrace();
                continue;
            }

            RTCStream stream = createRemoteStream(publisherId);

            mHandler.post(() -> {
                mEngineListener.onStreamAdded(stream);
            });

        }

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


    private void handlePeerRemoved(JSONObject data) {

        JSONObject peer  = data.optJSONObject("peer");
        String peerId = peer.optString("id");

        if (TextUtils.isEmpty(peerId)) {
            return;
        }

        mHandler.post(() -> {
            mEngineListener.onLeave(peerId);
        });
    }

    private void handlePeerConnected(JSONObject data) {

        JSONObject peer  = data.optJSONObject("peer");
        String peerId = peer.optString("id");

        if (TextUtils.isEmpty(peerId)) {
            return;
        }

        mHandler.post(() -> {
            mEngineListener.onJoined(peerId);
        });

    }


    private void handleStreamAdded(JSONObject data) {

        String streamId  = data.optString("msid");

        if (TextUtils.isEmpty(streamId)) {
            return;
        }

        RTCStream localStream = localStreams.get(streamId);

        mHandler.post(() -> {

            mEngineListener.onAddLocalStream(localStream);
        });

    }


    private void handleConfigure(JSONObject data) {

        String streamId  = data.optString("msid");

        if (TextUtils.isEmpty(streamId)) {
            return;
        }

        RTCStream remoteStream = remoteStreams.get(streamId);

        if (remoteStream == null) {
            return;
        }


        if (data.has("video")) {
            boolean muting = data.optBoolean("muting");
            mHandler.post(() -> {
                remoteStream.onMuteVideo(muting);
            });
        }

        if (data.has("audio")) {
            boolean muting = data.optBoolean("muting");
            mHandler.post(() -> {
                remoteStream.onMuteAudio(muting);
            });
        }

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


    private RTCStream createRemoteStream(String publisherId) {


        RTCStream stream = new RTCStream(RTCEngine.this.mContext, RTCEngine.this);
        stream.audio = false;
        stream.video = false;
        stream.local = false;

        stream.publisherId = publisherId;

        return stream;
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
