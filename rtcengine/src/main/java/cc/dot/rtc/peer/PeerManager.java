package cc.dot.rtc.peer;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by xiang on 07/09/2018.
 */

public class PeerManager {

    private HashMap<String,Peer> peers = new HashMap<>();


    public void updatePeer(JSONObject object) {

        Peer peer = new Peer(object);
        if (peer != null) {
            peers.put(peer.getId(),peer);
        }
    }

    public Peer getPeer(String peerId) {

        Peer peer = peers.get(peerId);
        return peer;
    }

    public void removePeer(String peerId) {

        peers.remove(peerId);
    }

    public void clearAll() {

        peers.clear();
    }

    public Peer peerForStream(String streamId) {

        Peer peer = null;
        for (Peer _peer: peers.values()) {
            List<JSONObject> streams = _peer.getStreams();
            for (int i = 0; i < streams.size(); i++) {
                JSONObject stream = streams.get(i);
                String _streamId = stream.optString("id","");
                if (streamId.equalsIgnoreCase(_streamId)) {
                    peer = _peer;
                    break;
                }
            }
        }
        return peer;
    }

}
