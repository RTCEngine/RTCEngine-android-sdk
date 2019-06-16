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

import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;

import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpTransceiver;
import org.webrtc.RtpTransceiver.RtpTransceiverInit;
import org.webrtc.RtpTransceiver.RtpTransceiverDirection;
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
import java.util.Collections;
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


        public void onStateChange(RTCEngineStatus status);


        public void onReceiveMessage(JSONObject message);


        public void onStreamAdded(RTCStream stream);


        public void onStreamRemoved(RTCStream stream);


        public void onLocalStreamPublished(RTCStream stream);


        public void onLocalStreamUnpublished(RTCStream stream);


        public void onStreamSubscribed(RTCStream stream);


        public void onStreamUnsubscribed(RTCStream stream);
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

    protected AudioDeviceModule audioDeviceModule;

    private boolean videoCodecHwAcceleration = true;



    private List<PeerConnection.IceServer> iceServers = new ArrayList();

    private RTCStream localStream = null;

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

        PeerConnection peerConnection = createPeerConnection(stream);

        localStream.peerConnection = peerConnection;
        ArrayList<String> streamIds = new ArrayList<String>();
        streamIds.add(stream.getStreamId());
        RtpTransceiverInit transceiverInit = new RtpTransceiverInit(RtpTransceiverDirection.SEND_ONLY, streamIds);

        if (stream.mAudioTrack != null) {
            stream.mAudioTransceiver = peerConnection.addTransceiver(stream.mAudioTrack, transceiverInit);
        } else {
            stream.mAudioTransceiver = peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO, transceiverInit);
        }

        if (stream.mVideoTrack != null) {
            stream.mVideoTransceiver = peerConnection.addTransceiver(stream.mVideoTrack,transceiverInit);
        } else {
            stream.mVideoTransceiver = peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, transceiverInit);
        }

        publishInternal(stream);
    }



    public void unpublish(RTCStream stream) {

        if (stream.mediaStreamId.equalsIgnoreCase(localStream.mediaStreamId)) {

            if (stream.mVideoTransceiver != null) {
                stream.peerConnection.removeTrack(stream.mVideoTransceiver.getSender());
            }

            if (stream.mAudioTransceiver != null) {
                stream.peerConnection.removeTrack(stream.mAudioTransceiver.getSender());
            }

            unpublishInternal(stream);
        }
    }


    public void subscribe(RTCStream stream) {

        if (remoteStreams.get(stream.publisherId) != null) {
            return;
        }

        remoteStreams.put(stream.publisherId, stream);

        PeerConnection peerConnection = createPeerConnection(stream);

        stream.peerConnection = peerConnection;

        RtpTransceiverInit transceiverInit = new RtpTransceiverInit(RtpTransceiverDirection.RECV_ONLY);

        peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,transceiverInit);
        peerConnection.addTransceiver(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO, transceiverInit);


        subscribeInternal(stream);
    }


    public void unsubscribe(RTCStream stream) {

        if (remoteStreams.get(stream.publisherId) == null){
            Log.e(TAG, "unsubscribe empty stream");
            return;
        }

        unsubscribeInternal(stream);

        remoteStreams.remove(stream.publisherId);

        stream.close();
    }


    private void createPeerConnectionFactory() {


        if (factory != null) {
            return;
        }

        audioDeviceModule = JavaAudioDeviceModule.builder(mContext).createAudioDeviceModule();

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
                .setAudioDeviceModule(audioDeviceModule)
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory();

    }


    private PeerConnection createPeerConnection(PeerConnection.Observer observer) {


        PeerConnection.RTCConfiguration configuration = new PeerConnection.RTCConfiguration(iceServers);

        configuration.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        configuration.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        configuration.iceTransportsType = PeerConnection.IceTransportsType.ALL;
        configuration.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;

        final PeerConnection peerConnection = factory.createPeerConnection(configuration, observer);

        return peerConnection;
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

                JSONObject obj = (JSONObject)args[0];
                handleStreamAdded(obj);
            }
        });

        mSocket.on("streamremoved", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

                JSONObject obj = (JSONObject)args[0];
                handleStreamRemoved(obj);
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

        if (localStream != null) {
            localStream.close();
        }

        for (RTCStream stream: remoteStreams.values()){
            stream.close();
        }

        localStream = null;
        remoteStreams.clear();

        factory.dispose();
        factory = null;

        audioDeviceModule.release();
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


    private void  handlePublishStream(RTCStream stream, JSONObject data) {

        String sdp = data.optString("sdp");

        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);

        stream.peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

            }

            @Override
            public void onSetSuccess() {
                mHandler.post(() -> {
                    mEngineListener.onLocalStreamPublished(stream);
                });
            }

        }, answer);

    }


    private void handleSubscribeStream(RTCStream stream, JSONObject data) {

        JSONObject streamobj = data.optJSONObject("stream");
        String sdp = data.optString("sdp");

        SessionDescription answer = new SessionDescription(SessionDescription.Type.ANSWER, sdp);

        stream.peerConnection.setRemoteDescription(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

            }

            @Override
            public void onSetSuccess() {

                mHandler.post(() -> {
                   mEngineListener.onStreamSubscribed(stream);
                });
            }
        }, answer);
    }


    private void handleStreamAdded(JSONObject data) {

        JSONObject streamobj = data.optJSONObject("stream");

        String publisherId = streamobj.optString("publisherid");

        RTCStream stream = createRemoteStream(publisherId);

        mHandler.post(() -> {
            mEngineListener.onStreamAdded(stream);
        });
    }

    private void handleStreamRemoved(JSONObject data) {

        JSONObject streamobj = data.optJSONObject("stream");

        String publisherId = streamobj.optString("publisherid");

        RTCStream remoteStream = remoteStreams.get(publisherId);

        if (remoteStream == null) {
            return;
        }

        remoteStreams.remove(publisherId);

        mHandler.post(() -> {
            mEngineListener.onStreamRemoved(remoteStream);
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



    private void publishInternal(RTCStream stream) {

        MediaConstraints offerConstraints =  MediaConstraintUtil.offerConstraints();

        stream.peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

                stream.peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {}

                    @Override
                    public void onSetSuccess() {

                        JSONObject data = new JSONObject();
                        JSONObject streamobj = new JSONObject();


                        try {
                            streamobj.put("publisherId", stream.mediaStreamId);
                            data.put("sdp", sessionDescription.description);
                            data.put("stream", streamobj);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        mSocket.emit("publish", new Object[]{data}, new Ack() {
                            @Override
                            public void call(Object... args) {
                                JSONObject obj = (JSONObject)args[0];
                                handlePublishStream(stream,obj);
                            }
                        });

                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {

            }
        }, offerConstraints);

    }



    private void unpublishInternal(RTCStream stream) {

        JSONObject dataobj = new JSONObject();
        JSONObject streamobj = new JSONObject();


        try {
            streamobj.put("publisherId", stream.mediaStreamId);
            dataobj.put("stream", streamobj);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mSocket.emit("unpublish", new Object[]{dataobj}, new Ack() {
            @Override
            public void call(Object... args) {

                mHandler.post(() -> {
                   mEngineListener.onLocalStreamUnpublished(stream);
                });
            }
        });
    }


    private void subscribeInternal(RTCStream stream) {

        MediaConstraints offerConstraints =  MediaConstraintUtil.offerConstraints();

        stream.peerConnection.createOffer(new SdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {

                stream.peerConnection.setLocalDescription(new SdpObserver() {
                    @Override
                    public void onCreateSuccess(SessionDescription sessionDescription) {

                    }

                    @Override
                    public void onSetSuccess() {

                        JSONObject data = new JSONObject();
                        JSONObject streamobj = new JSONObject();

                        try {
                            streamobj.put("publisherId", stream.publisherId);
                            data.put("stream", streamobj);
                            data.put("sdp", sessionDescription.description);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }

                        mSocket.emit("subscribe", new Object[]{data}, new Ack() {
                            @Override
                            public void call(Object... args) {

                                JSONObject obj = (JSONObject)args[0];
                                handleSubscribeStream(stream, obj);
                            }
                        });

                    }
                }, sessionDescription);
            }

            @Override
            public void onSetSuccess() {

            }
        }, offerConstraints);

    }


    private void unsubscribeInternal(RTCStream stream) {

        JSONObject data = new JSONObject();
        JSONObject streamobj = new JSONObject();

        try {
            streamobj.put("publisherId", stream.publisherId);
            streamobj.put("subscriberId", stream.mediaStreamId);
            data.put("stream", streamobj);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        mSocket.emit("unsubscribe", new Object[]{data}, new Ack() {
            @Override
            public void call(Object... args) {
                mHandler.post(() -> {
                    mEngineListener.onStreamUnsubscribed(stream);
                });
            }
        });
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



}
