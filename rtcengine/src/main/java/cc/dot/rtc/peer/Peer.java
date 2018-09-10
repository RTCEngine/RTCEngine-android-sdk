package cc.dot.rtc.peer;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by xiang on 07/09/2018.
 */

public class Peer {

    private String id;

    private List<JSONObject> streams = new ArrayList<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<JSONObject> getStreams() {
        return streams;
    }

    Peer(JSONObject object) {

        try {
            JSONArray streams = object.getJSONArray("streams");
            this.id = object.getString("id");

            for (int i = 0; i < streams.length(); i++) {
                JSONObject stream = streams.getJSONObject(i);
                this.streams.add(stream);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

    }


    public JSONObject getStream(String streamId) {

        JSONObject findStream = null;
        try {
            for (int i = 0; i < streams.size(); i++) {
                JSONObject stream = streams.get(i);
                String _streamId = stream.getString("id");
                if (_streamId.equalsIgnoreCase(streamId)) {
                    findStream = stream;
                    break;
                }
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return findStream;
    }
}
