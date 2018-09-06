package cc.dot.rtc;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONObject;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cc.dot.rtc.exception.BuilderException;
import cc.dot.rtc.utils.AuthToken;
import dot.cc.rtcengine.BuildConfig;
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

public class RTCEngine implements SdpObserver, PeerConnection.Observer{




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


        public void onReceiveMessage(String text);

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

    private boolean closed;


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


    }


    public void removeStream(final RTCStream stream) {


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


        });

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

            }
        });

        mSocket.on("offer", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });

        mSocket.on("answer", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });

        mSocket.on("peerRemoved", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });


        mSocket.on("peerConnected", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });

        mSocket.on("streamAdded", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });


        mSocket.on("configure", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });


        mSocket.on("message", new Emitter.Listener() {
            @Override
            public void call(Object... args) {

            }
        });

        mSocket.connect();

    }


    private void join() {


    }


    private void handleJoined(JSONObject data) {


    }


    private void handleOffer(JSONObject data) {


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


    // SdpObserver
    @Override
    public void onCreateSuccess(SessionDescription sessionDescription) {

    }

    @Override
    public void onSetSuccess() {

    }

    @Override
    public void onCreateFailure(String s) {

    }

    @Override
    public void onSetFailure(String s) {

    }



    // PeerConnection.Observer



    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {

    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {

    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {

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

    }


}
