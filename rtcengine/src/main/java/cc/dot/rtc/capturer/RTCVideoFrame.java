package cc.dot.rtc.capturer;

/**
 * Created by xiang on 05/09/2018.
 */

public class RTCVideoFrame {

    public enum VideoFrameType { NV21_TYPE, I420_TYPE, ARGB_TYPE, RGBA_TYPE };

    public final byte[] bytesArray;
    public final int width;
    public final int height;
    public final VideoFrameType type;


    public RTCVideoFrame(byte[] bytesArray, int width, int height, VideoFrameType type) {

        this.bytesArray = bytesArray;
        this.width = width;
        this.height = height;
        this.type = type;
    }
}
