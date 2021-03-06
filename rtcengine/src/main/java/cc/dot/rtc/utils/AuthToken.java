package cc.dot.rtc.utils;

import android.text.TextUtils;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.PeerConnection;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by xiang on 06/09/2018.
 */

public class AuthToken {

    private String wsUrl;
    private String user;
    private String room;
    private String token;
    private String app_key;
    private String iceTransportPolicy;


    private List<PeerConnection.IceServer> iceServers = new ArrayList();

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getRoom() {
        return room;
    }

    public void setRoom(String room) {
        this.room = room;
    }

    public String getWsUrl() {
        return wsUrl;
    }

    public void setWsUrl(String wsUrl) {
        this.wsUrl = wsUrl;
    }

    public  String getToken() { return token;}

    public void setToken(String token) {this.token = token;}

    public  String getAppKey() { return app_key; }

    public  void  setAppKey(String  app_key) { this.app_key = app_key; }

    public List<PeerConnection.IceServer> getIceServers() {
        return iceServers;
    }

    public String getIceTransportPolicy() { return iceTransportPolicy; }


    public static  AuthToken parseToken(String token) {

        String[] arrays = token.split("[.]");
        if (arrays.length != 3) {
            return null;
        }
        String item = arrays[1];
        String json;
        try {
            byte[] base64 = Base64.decode(item, Base64.URL_SAFE);
            json = new String(base64, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return null;
        }
        if (TextUtils.isEmpty(json)) {
            return null;
        }

        AuthToken auth = new AuthToken();
        auth.iceServers = new ArrayList<>();

        try {
            JSONObject jsonObject = new JSONObject(json);
            auth.setRoom(jsonObject.optString("room"));
            auth.setWsUrl(jsonObject.optString("wsUrl"));
            auth.setUser(jsonObject.optString("user"));
            auth.setAppKey(jsonObject.optString("appkey"));
            auth.iceTransportPolicy = jsonObject.optString("iceTransportPolicy","");
            auth.setToken(token);

            JSONArray iceServerArray = jsonObject.optJSONArray("iceServers");

            if (iceServerArray != null) {
                auth.iceServers = parseIceServer(iceServerArray);
            }

        } catch (JSONException e) {
            e.printStackTrace();
            return null;
        }

        return auth;
    }


    public static ArrayList<PeerConnection.IceServer> parseIceServer(JSONArray data){

        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<PeerConnection.IceServer>();

        try {
            JSONArray iceServerArray = data;

            for (int i = 0; i < iceServerArray.length(); i++) {

                JSONObject iceObject = iceServerArray.getJSONObject(i);
                JSONArray urls = iceObject.optJSONArray("urls");
                ArrayList<String> urlsArray = new ArrayList<>();
                for (int j = 0; j < urls.length(); j++) {
                    urlsArray.add(urls.optString(j,""));
                }
                String username = iceObject.optString("username","");
                String password = iceObject.optString("credential","");

                PeerConnection.IceServer.Builder builder =  PeerConnection.IceServer.builder(urlsArray)
                            .setUsername(username)
                            .setPassword(password);

                PeerConnection.IceServer server = builder.createIceServer();
                iceServers.add(server);

            }
        } catch (JSONException e){
            e.printStackTrace();
        }

        return iceServers;
    }

}
