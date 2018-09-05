package cc.dot.rtc.filter;

import android.opengl.GLES20;
import android.util.Log;


import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/**
 * Created by xiang on 05/09/2018.
 */

public class FilterManager {

    private static final String TAG = "FilterManager";

    private int mOffscreenTexture;
    private int mFramebuffer;

    private HardSkinBlurFilter filter;

    private int textureWidth;
    private int textureHeight;

    private IntBuffer rgbaIntBuffer;
    private ByteBuffer rgbaBuffer;
    private ByteBuffer nv21Buffer;

    private boolean useFilter = false;


    public FilterManager(){
        filter = new HardSkinBlurFilter();
    }

    public boolean isUseFilter() {
        return useFilter;
    }

    public void setUseFilter(boolean useFilter) {
        this.useFilter = useFilter;
    }


    public void setBeautyLevel(float beautyLevel){

        filter.setBeautyLevel(beautyLevel);
    }

    public void setBrightLevel(float brightLevel){

        filter.setBrightLevel(brightLevel);
    }


    public static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            Log.e(TAG, msg);
            throw new RuntimeException(msg);
        }
    }

    public byte[] getNV21Data(){
        rgbaBuffer.asIntBuffer().put(rgbaIntBuffer.array());

        //todo yuv convert
        

        return nv21Buffer.array();
    }

    public int drawFrame(int texId, float[] texMatrix, int texWidth, int texHeight) {

        GLES20.glViewport(0, 0, texWidth, texHeight);

        if (mOffscreenTexture == 0 || texWidth != textureWidth || texHeight != textureHeight) {
            prepareFramebuffer(texWidth, texHeight);
            textureWidth = texWidth;
            textureHeight = texHeight;
        }

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer);
        // draw
        filter.drawOffscreen(texId,texMatrix,texWidth,texHeight);

        GLES20.glReadPixels(0, 0, texWidth, texHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, rgbaIntBuffer);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        rgbaIntBuffer.rewind();

        return mOffscreenTexture;

    }


    private void prepareFramebuffer(int width, int height) {

        checkGlError("start");
        int[] values = new int[1];

        // Create a texture object and bind it.  This will be the color buffer.
        GLES20.glGenTextures(1, values, 0);
        checkGlError("glGenTextures");
        mOffscreenTexture = values[0];   // expected > 0
        Log.i(TAG, "prepareFramebuffer mOffscreenTexture:" + mOffscreenTexture);

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOffscreenTexture);
        checkGlError("glBindTexture");


        rgbaIntBuffer = IntBuffer.allocate(width*height);
        rgbaBuffer = ByteBuffer.allocate(width * height*4);
        nv21Buffer = ByteBuffer.allocate(width*height*3/2);

        // Create texture storage.
        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, width, height, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);

        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        checkGlError("glTexParameter");
        // Create framebuffer object and bind it.
        GLES20.glGenFramebuffers(1, values, 0);
        checkGlError("glGenFramebuffers");
        mFramebuffer = values[0];    // expected > 0

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFramebuffer);
        checkGlError("glBindFramebuffer " + mFramebuffer);

        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mOffscreenTexture, 0);

        // See if GLES is happy with all this.
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer not complete, status=" + status);
        }

        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        // Switch back to the default framebuffer.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        checkGlError("glBindFramebuffer");
    }
}
