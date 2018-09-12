package dot.cc.rtcengine;

import android.os.Build;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.Random;

import cc.dot.rtc.RTCEngine;
import cc.dot.rtc.RTCStream;
import cc.dot.rtc.RTCView;
import cc.dot.rtc.utils.PermissionUtils;


public class MainActivity extends AppCompatActivity {

    private static String TAG = MainActivity.class.getSimpleName();

    private static String tokenUrl = "http://192.168.15.18:3888/api/generateToken";
    private static String room = "test_room";
    private static String appSecret = "test_secret";


    private Button  joinButton;
    private PermissionUtils permissionUtils;
    private boolean havePermission;

    private RTCEngine rtcEngine;
    private RTCStream localStream;

    private FrameLayout videoLayout;

    private boolean connected = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                        | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                        | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);


        setContentView(R.layout.activity_main);

        joinButton = (Button)findViewById(R.id.join);
        videoLayout = (FrameLayout)findViewById(R.id.viewLayout);

        final String username = (Build.DEVICE + new Random().nextInt(1000));


        permissionUtils = new PermissionUtils();


        if (!permissionUtils.hasPermissions(MainActivity.this)) {

            permissionUtils.requestPermissions(MainActivity.this, new PermissionUtils.Callback() {
                @Override
                public void onSuccess() {
                    havePermission = true;
                }

                @Override
                public void onFailure(String msg) {

                }
            });
        } else {
            havePermission = true;
        }


        joinButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (!havePermission) {

                    Toast.makeText(getApplicationContext(), "can not get video/audio permission",
                            Toast.LENGTH_SHORT).show();
                    return;
                }


                if (!connected) {

                    RTCEngine.generateToken(tokenUrl, appSecret, room, username, new RTCEngine.TokenCallback() {
                        @Override
                        public void onFailure() {

                            Toast.makeText(getApplicationContext(), "can not get token", Toast.LENGTH_SHORT).show();
                        }

                        @Override
                        public void onSuccess(String token) {

                            rtcEngine.joinRoom(token);
                        }
                    });

                } else {
                    
                    rtcEngine.leaveRoom();
                }
            }
        });

        rtcEngine = RTCEngine.builder()
                .setContext(getApplicationContext())
                .setDotEngineListener(rtcEngineListener).build();

        localStream = RTCStream.builder(getApplicationContext(),rtcEngine)
                .setAudio(true)
                .setVideo(true)
                .build();

        localStream.setupLocalMedia();

        addVideo(username, localStream.getView());


    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionUtils.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }


    private void addVideo(String userId, View view) {

        //九宫格布局
        view.setTag(userId);
        for (int i = videoLayout.getChildCount() - 1; i >= 0; i--) {
            View childAt = videoLayout.getChildAt(i);
            if (("" + userId).equals(childAt.getTag() + "")) {
                ViewGroup.LayoutParams layoutParams = childAt.getLayoutParams();
                videoLayout.removeView(childAt);
                videoLayout.addView(view, layoutParams);
                return;
            }
        }

        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();
        int size = videoLayout.getChildCount();
        int pw = (int) (displayMetrics.widthPixels * 0.5f);
        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(pw, pw);

        layoutParams.leftMargin = (size % 2) * pw;
        layoutParams.topMargin = (size / 2) * pw;
        videoLayout.addView(view, layoutParams);

        view.requestLayout();
    }


    private void updateFrameLayout() {

        //九宫格布局
        DisplayMetrics displayMetrics = getResources().getDisplayMetrics();

        int size = videoLayout.getChildCount();
        int pw = (int) (displayMetrics.widthPixels / 2.0f);

        for (int i = 0; i < size; i++) {
            View view = videoLayout.getChildAt(i);

            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(pw, pw);

            layoutParams.leftMargin = (i % 2) * pw;
            layoutParams.topMargin = (i / 2) * pw;

            videoLayout.updateViewLayout(view, layoutParams);
        }

    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onResume() {
        super.onResume();

    }

    private RTCEngine.RTCEngineListener rtcEngineListener = new RTCEngine.RTCEngineListener() {
        @Override
        public void onJoined(String userId) {

            Log.d(TAG, "onJoined " + userId);

        }

        @Override
        public void onLeave(String userId) {

            Log.d(TAG, "onLeave " + userId);
        }

        @Override
        public void onAddLocalStream(RTCStream stream) {

            Log.d(TAG, "onAddLocalStream " + stream.getStreamId());

            Toast.makeText(getApplicationContext(),
                    "localstream added :" + stream.getStreamId(),
                    Toast.LENGTH_SHORT).show();


        }

        @Override
        public void onRemoveLocalStream(RTCStream stream) {

            Log.d(TAG, "onRemoveLocalStream " + stream.getStreamId());
        }

        @Override
        public void onAddRemoteStream(RTCStream stream) {

            Log.d(TAG, "onAddRemoteStream " + stream.getStreamId());

            Toast.makeText(getApplicationContext(), "onAddRemoteStream :" + stream.getStreamId(),
                    Toast.LENGTH_SHORT).show();

            addVideo(stream.getPeerId(), stream.getView());


        }

        @Override
        public void onRemoveRemoteStream(RTCStream stream) {

            Log.d(TAG, "onRemoveRemoteStream " + stream.getStreamId());


            RTCView view = stream.getView();

            videoLayout.removeView(view);

            updateFrameLayout();
        }

        @Override
        public void onStateChange(RTCEngine.RTCEngineStatus status) {

            Log.d(TAG, "onStateChange ");

            String  statusStr = (status == RTCEngine.RTCEngineStatus.Connected) ? "connected" : "leaved";

            Toast.makeText(getApplicationContext(),statusStr,Toast.LENGTH_SHORT).show();

            if (status == RTCEngine.RTCEngineStatus.Connected) {

                rtcEngine.addStream(localStream);

                joinButton.setText("leave");
            }

            if (status == RTCEngine.RTCEngineStatus.DisConnected) {

                joinButton.setText("join");
            }
        }

        @Override
        public void onReceiveMessage(JSONObject message) {

            Log.d(TAG, "onReceiveMessage " + message.toString());
        }
    };


}
