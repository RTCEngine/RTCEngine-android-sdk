package cc.dot.rtc;

import org.webrtc.PeerConnection;

import java.util.ArrayList;
import java.util.List;

public class RTCConfig {



    private String signallingServer;

    private String iceTransportPolicy;
    private List<PeerConnection.IceServer> iceServers = new ArrayList();


    public String getSignallingServer() {
        return signallingServer;
    }

    public void setSignallingServer(String signallingServer) {
        this.signallingServer = signallingServer;
    }

    public String getIceTransportPolicy() {
        return iceTransportPolicy;
    }

    public void setIceTransportPolicy(String iceTransportPolicy) {
        this.iceTransportPolicy = iceTransportPolicy;
    }

    public List<PeerConnection.IceServer> getIceServers() {
        return iceServers;
    }

    public void setIceServers(List<PeerConnection.IceServer> iceServers) {
        this.iceServers = iceServers;
    }

}
