package cc.dot.rtc;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by xiang on 06/09/2018.
 */

public enum  RTCVideoProfile {

    RTCEngine_VideoProfile_120P(0, 160, 120, 15, 80),
    RTCEngine_VideoProfile_120P_2(1, 120, 160, 15, 80),
    RTCEngine_VideoProfile_120P_3(2, 120, 120, 15, 60),
    RTCEngine_VideoProfile_180P(10, 320, 180, 15, 160),
    RTCEngine_VideoProfile_180P_2(11, 180, 320, 15, 160),
    RTCEngine_VideoProfile_180P_3(12, 180, 180, 15, 120),
    RTCEngine_VideoProfile_240P(20, 320, 240, 15, 200),
    RTCEngine_VideoProfile_240P_2(21, 240, 320, 15, 200),
    RTCEngine_VideoProfile_240P_3(22, 240, 240, 15, 160),
    RTCEngine_VideoProfile_360P(30, 640, 360, 15, 400),
    RTCEngine_VideoProfile_360P_2(31, 360, 640, 15, 400),
    RTCEngine_VideoProfile_360P_3(32, 360, 360, 15, 300),
    RTCEngine_VideoProfile_480P(40, 640, 480, 15, 500),
    RTCEngine_VideoProfile_480P_2(41, 480, 640, 15, 500),
    RTCEngine_VideoProfile_480P_3(42, 480, 480, 15, 400),
    RTCEngine_VideoProfile_480P_4(43, 640, 480, 30, 750),
    RTCEngine_VideoProfile_480P_5(44, 480, 640, 30, 750),
    RTCEngine_VideoProfile_480P_6(45, 480, 480, 30, 680),
    RTCEngine_VideoProfile_480P_7(46, 640, 480, 15, 1000),
    RTCEngine_VideoProfile_720P(50, 1280, 720, 15, 1000),
    RTCEngine_VideoProfile_720P_2(51, 720, 1280, 15, 1000),
    RTCEngine_VideoProfile_720P_3(52, 1280, 720, 30, 1200),
    RTCEngine_VideoProfile_720P_4(53, 720, 1280, 30, 1500),
    ;


    public static Map<Integer, RTCVideoProfile> values = new HashMap<>();
    private final int type;
    private int width, height, fps, bits;


    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getFps() {
        return fps;
    }

    public void setFps(int fps) {
        this.fps = fps;
    }

    public int getBits() {
        return bits;
    }

    public void setBits(int bits) {
        this.bits = bits;
    }

    public int getType() {
        return type;
    }

    RTCVideoProfile(int type, int width, int height, int fps, int bits) {

        this.type = type;
        this.width = width;
        this.height = height;
        this.fps = fps;
        this.bits = bits;
    }

    static {
        for (RTCVideoProfile t : RTCVideoProfile.values()) {
            values.put(t.getType(), t);
        }

    }

    public static RTCVideoProfile valueOf(int type) {
        return values.get(type);
    }

}
