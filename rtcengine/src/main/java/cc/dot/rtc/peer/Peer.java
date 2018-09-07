package cc.dot.rtc.peer;

/**
 * Created by xiang on 07/09/2018.
 */

public class Peer {

    private String id;

    private String[] msids;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setMsids(String[] msids) {
        this.msids = msids;
    }

    public String[] getMsids() {
        return msids;
    }

}
