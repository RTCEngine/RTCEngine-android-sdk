package cc.dot.rtc.filter;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;

import org.webrtc.GlShader;
import org.webrtc.GlUtil;

import java.nio.FloatBuffer;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Created by xiang on 05/09/2018.
 */

public class HardSkinBlurFilter {

    private static class Shader {
        public final GlShader glShader;
        public final int texMatrixLocation;

        public Shader(String fragmentShader) {
            this.glShader = new GlShader(VERTEX_SHADER_STRING, fragmentShader);
            this.texMatrixLocation = glShader.getUniformLocation("texMatrix");
        }
    }
    // clang-format off
    // Simple vertex shader, used for both YUV and OES.
    private static final String VERTEX_SHADER_STRING =
            "varying vec2 interp_tc;\n"
                    + "attribute vec4 in_pos;\n"
                    + "attribute vec4 in_tc;\n"
                    + "\n"
                    + "uniform mat4 texMatrix;\n"
                    + "\n"
                    + "void main() {\n"
                    + "    gl_Position = in_pos;\n"
                    + "    interp_tc = (texMatrix * in_tc).xy;\n"
                    + "}\n";


    private static final String OES_FRAGMENT_SHADER_STRING =
            "#extension GL_OES_EGL_image_external : require\n"
                    + "precision mediump float;\n"
                    + "varying vec2 interp_tc;\n"
                    + "\n"
                    + "uniform samplerExternalOES oes_tex;\n"
                    + "\n"
                    + "void main() {\n"
                    + "  gl_FragColor = texture2D(oes_tex, interp_tc);\n"
                    + "}\n";


    private static final String OES_FRAGMENT_BEAUTY_SHADER_STRING =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "\n" +
                    "precision mediump float;\n" +
                    "\n" +
                    "  uniform samplerExternalOES oes_tex;\n" +
                    "  uniform vec2 singleStepOffset;\n" +
                    "\n" +
                    "  uniform vec4 params;\n" +
                    "\n" +
                    "  uniform float brightness; \n" +
                    "\n" +
                    "  varying vec2 interp_tc;\n" +
                    "\n" +
                    "  const  vec3 W = vec3(0.299,0.587,0.114);\n" +
                    "\n" +
                    "  const mat3 saturateMatrix = mat3(\n" +
                    "        1.1102,-0.0598,-0.061,\n" +
                    "        -0.0774,1.0826,-0.1186,\n" +
                    "        -0.0228,-0.0228,1.1772);\n" +
                    "\n" +
                    "  vec2 blurCoordinates[24];\n" +
                    "\n" +
                    "  float hardLight(float color)\n" +
                    "  {\n" +
                    "    if(color <= 0.5)\n" +
                    "    {\n" +
                    "        color = color * color * 2.0;\n" +
                    "    }\n" +
                    "    else\n" +
                    "    {\n" +
                    "        color = 1.0 - ((1.0 - color)*(1.0 - color) * 2.0);\n" +
                    "    }\n" +
                    "    return color;\n" +
                    "  }\n" +
                    "\n" +
                    "  void main(){\n" +
                    "      vec3 centralColor = texture2D(oes_tex, interp_tc).rgb;\n" +
                    "\n" +
                    "    blurCoordinates[0] = interp_tc.xy + singleStepOffset * vec2(0.0, -10.0);\n" +
                    "    blurCoordinates[1] = interp_tc.xy + singleStepOffset * vec2(0.0, 10.0);\n" +
                    "    blurCoordinates[2] = interp_tc.xy + singleStepOffset * vec2(-10.0, 0.0);\n" +
                    "    blurCoordinates[3] = interp_tc.xy + singleStepOffset * vec2(10.0, 0.0);\n" +
                    "    blurCoordinates[4] = interp_tc.xy + singleStepOffset * vec2(5.0, -8.0);\n" +
                    "    blurCoordinates[5] = interp_tc.xy + singleStepOffset * vec2(5.0, 8.0);\n" +
                    "    blurCoordinates[6] = interp_tc.xy + singleStepOffset * vec2(-5.0, 8.0);\n" +
                    "    blurCoordinates[7] = interp_tc.xy + singleStepOffset * vec2(-5.0, -8.0);\n" +
                    "    blurCoordinates[8] = interp_tc.xy + singleStepOffset * vec2(8.0, -5.0);\n" +
                    "    blurCoordinates[9] = interp_tc.xy + singleStepOffset * vec2(8.0, 5.0);\n" +
                    "    blurCoordinates[10] = interp_tc.xy + singleStepOffset * vec2(-8.0, 5.0);\n" +
                    "    blurCoordinates[11] = interp_tc.xy + singleStepOffset * vec2(-8.0, -5.0);\n" +
                    "    blurCoordinates[12] = interp_tc.xy + singleStepOffset * vec2(0.0, -6.0);\n" +
                    "    blurCoordinates[13] = interp_tc.xy + singleStepOffset * vec2(0.0, 6.0);\n" +
                    "    blurCoordinates[14] = interp_tc.xy + singleStepOffset * vec2(6.0, 0.0);\n" +
                    "    blurCoordinates[15] = interp_tc.xy + singleStepOffset * vec2(-6.0, 0.0);\n" +
                    "    blurCoordinates[16] = interp_tc.xy + singleStepOffset * vec2(-4.0, -4.0);\n" +
                    "    blurCoordinates[17] = interp_tc.xy + singleStepOffset * vec2(-4.0, 4.0);\n" +
                    "    blurCoordinates[18] = interp_tc.xy + singleStepOffset * vec2(4.0, -4.0);\n" +
                    "    blurCoordinates[19] = interp_tc.xy + singleStepOffset * vec2(4.0, 4.0);\n" +
                    "    blurCoordinates[20] = interp_tc.xy + singleStepOffset * vec2(-2.0, -2.0);\n" +
                    "    blurCoordinates[21] = interp_tc.xy + singleStepOffset * vec2(-2.0, 2.0);\n" +
                    "    blurCoordinates[22] = interp_tc.xy + singleStepOffset * vec2(2.0, -2.0);\n" +
                    "    blurCoordinates[23] = interp_tc.xy + singleStepOffset * vec2(2.0, 2.0);\n" +
                    "\n" +
                    "    float sampleColor = centralColor.g * 22.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[0]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[1]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[2]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[3]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[4]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[5]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[6]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[7]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[8]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[9]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[10]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[11]).g;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[12]).g * 2.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[13]).g * 2.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[14]).g * 2.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[15]).g * 2.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[16]).g * 2.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[17]).g * 2.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[18]).g * 2.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[19]).g * 2.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[20]).g * 3.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[21]).g * 3.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[22]).g * 3.0;\n" +
                    "    sampleColor += texture2D(oes_tex, blurCoordinates[23]).g * 3.0;\n" +
                    "    sampleColor = sampleColor / 62.0;\n" +
                    "\n" +
                    "    float highPass = centralColor.g - sampleColor + 0.5;\n" +
                    "\n" +
                    "    for(int i = 0; i < 5;i++)\n" +
                    "    {\n" +
                    "        highPass = hardLight(highPass);\n" +
                    "    }\n" +
                    "    float luminance = dot(centralColor, W);\n" +
                    "    float alpha = pow(luminance, params.r);\n" +
                    "\n" +
                    "    vec3 smoothColor = centralColor + (centralColor-vec3(highPass))*alpha*0.1;\n" +
                    "\n" +
                    "    smoothColor.r = clamp(pow(smoothColor.r, params.g),0.0,1.0);\n" +
                    "    smoothColor.g = clamp(pow(smoothColor.g, params.g),0.0,1.0);\n" +
                    "    smoothColor.b = clamp(pow(smoothColor.b, params.g),0.0,1.0);\n" +
                    "\n" +
                    "    vec3 screen = vec3(1.0) - (vec3(1.0)-smoothColor) * (vec3(1.0)-centralColor);\n" +
                    "    vec3 lighten = max(smoothColor, centralColor);\n" +
                    "    vec3 softLight = 2.0 * centralColor*smoothColor + centralColor*centralColor\n" +
                    "                    - 2.0 * centralColor*centralColor * smoothColor;\n" +
                    "\n" +
                    "    gl_FragColor = vec4(mix(centralColor, screen, alpha), 1.0);\n" +
                    "    gl_FragColor.rgb = mix(gl_FragColor.rgb, lighten, alpha);\n" +
                    "    gl_FragColor.rgb = mix(gl_FragColor.rgb, softLight, params.b);\n" +
                    "\n" +
                    "    vec3 satColor = gl_FragColor.rgb * saturateMatrix;\n" +
                    "    gl_FragColor.rgb = mix(gl_FragColor.rgb, satColor, params.a);\n" +
                    "     gl_FragColor.rgb = vec3(gl_FragColor.rgb + vec3(brightness));\n " +
                    "\n" +
                    "  }\n" +
                    "\n";

    // Vertex coordinates in Normalized Device Coordinates, i.e. (-1, -1) is bottom-left and (1, 1) is
    // top-right.
    private static final FloatBuffer FULL_RECTANGLE_BUF = GlUtil.createFloatBuffer(new float[] {
            -1.0f, -1.0f, // Bottom left.
            1.0f, -1.0f, // Bottom right.
            -1.0f, 1.0f, // Top left.
            1.0f, 1.0f, // Top right.
    });

    // Texture coordinates - (0, 0) is bottom-left and (1, 1) is top-right.
    private static final FloatBuffer FULL_RECTANGLE_TEX_BUF = GlUtil.createFloatBuffer(new float[] {
            0.0f, 0.0f, // Bottom left.
            1.0f, 0.0f, // Bottom right.
            0.0f, 1.0f, // Top left.
            1.0f, 1.0f // Top right.
    });


    private static final float offset_array[] = {
            2.0f/480, 2.0f/640,
    };

    private boolean _useFilter;

    private float _beautyLevel = 0.5f;
    private float _toneLevel = 0.5f;
    private float _brightLevel = 0.06f;
    private Shader shader;

    // todo addd do nothing filter
    private final Map<String, Shader> shaders = new IdentityHashMap<String,Shader>();

    public void setBeautyLevel(float beautyLevel){
        if (beautyLevel < 0.0f || beautyLevel > 1.0f){
            return;
        }
        _beautyLevel = beautyLevel;
    }

    public void setBrightLevel(float brightLevel){

        if (brightLevel < 0.0f || brightLevel > 1.0f){
            return;
        }

        _brightLevel = 0.6f * (-0.5f + brightLevel);
    }


    // do we need texMatrix here ?
    public void drawOffscreen(int oesTextureId, float[] texMatrix, int width, int height){
        prepareShader(OES_FRAGMENT_BEAUTY_SHADER_STRING, texMatrix);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTextureId);
        GLES20.glViewport(0, 0, width, height);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        GlUtil.checkNoGLES2Error("glDrawArrays ");
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
    }

    private void prepareShader(String fragmentShader, float[] texMatrix) {

        if (shader == null) {
            shader = new Shader(fragmentShader);
        }
        shader.glShader.useProgram();
        GLES20.glUniform1i(shader.glShader.getUniformLocation("oes_tex"), 0);
        GLES20.glUniform2fv(shader.glShader.getUniformLocation("singleStepOffset"), 1, offset_array, 0);
        GLES20.glUniform1f(shader.glShader.getUniformLocation("brightness"), _brightLevel);
        float[] arrayValue = new float[]{1.0f - 0.6f * _beautyLevel, 1.0f - 0.4f * _beautyLevel, 0.3f, 0.3f};
        //float[] arrayValue = new float[]{0.4f, 0.6f, 0.4f, 0.3f};
        GLES20.glUniform4fv(shader.glShader.getUniformLocation("params"), 1, FloatBuffer.wrap(arrayValue));

        GlUtil.checkNoGLES2Error("Initialize fragment shader uniform values.");

        shader.glShader.setVertexAttribArray("in_pos", 2, FULL_RECTANGLE_BUF);
        shader.glShader.setVertexAttribArray("in_tc", 2, FULL_RECTANGLE_TEX_BUF);

        // Copy the texture transformation matrix over.
        GLES20.glUniformMatrix4fv(shader.texMatrixLocation, 1, false, texMatrix, 0);

    }


    public void release() {

        shader.glShader.release();

    }

}
