package cc.dot.rtc;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.view.ViewGroup;

import org.webrtc.EglBase;
import org.webrtc.GlRectDrawer;
import org.webrtc.MediaStream;
import org.webrtc.RendererCommon;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoSink;
import org.webrtc.VideoTrack;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Created by xiang on 05/09/2018.
 */

public class RTCView extends ViewGroup{


    public enum ScaleMode {
        ScaleModeFit,
        ScaleModeFill
    }


    public interface RTCViewListener {

        void onFirstFrameRendered();
        void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation);

    }


    private static final RendererCommon.ScalingType DEFAULT_SCALING_TYPE = RendererCommon.ScalingType.SCALE_ASPECT_FILL;
    private static final Method IS_IN_LAYOUT;

    static {
        Method isInLayout = null;

        try {
            Method m = RTCView.class.getMethod("isInLayout");
            if (boolean.class.isAssignableFrom(m.getReturnType())) {
                isInLayout = m;
            }
        } catch (NoSuchMethodException e) {
            // Fall back to the behavior of ViewCompat#isInLayout(View).
        }
        IS_IN_LAYOUT = isInLayout;
    }


    private int frameHeight;

    /**
     * The rotation (degree) of the last video frame rendered by
     * {@link #surfaceViewRenderer}.
     */
    private int frameRotation;

    /**
     * The width of the last video frame rendered by
     * {@link #surfaceViewRenderer}.
     */
    private int frameWidth;


    /**
     * The {@code Object} which synchronizes the access to the layout-related
     * state of this instance such as {@link #frameHeight},
     * {@link #frameRotation}, {@link #frameWidth}, and {@link #scalingType}.
     */
    private final Object layoutSyncRoot = new Object();

    /**
     * The indicator which determines whether this {@code WebRTCView} is to
     * mirror the video represented by {@link #videoTrack} during its rendering.
     */
    private boolean mirror;



    /**
     * The {@code RendererEvents} which listens to rendering events reported by
     * {@link #surfaceViewRenderer}.
     */

    private final RendererCommon.RendererEvents rendererEvents = new RendererCommon.RendererEvents() {
        @Override
        public void onFirstFrameRendered() {

            if (viewListener != null){
                viewListener.onFirstFrameRendered();
            }
        }

        @Override
        public void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

            RTCView.this.onFrameResolutionChanged(videoWidth, videoHeight, rotation);

            if (viewListener != null) {
                viewListener.onFrameResolutionChanged(videoWidth,videoHeight,rotation);
            }

        }
    };


    private RendererCommon.ScalingType scalingType;


    private SurfaceViewRenderer surfaceViewRenderer;



    private VideoTrack videoTrack;


    private RTCViewListener viewListener;


    private final Runnable requestSurfaceViewRendererLayoutRunnable
            = new Runnable() {
        @Override
        public void run() {
            requestSurfaceViewRendererLayout();
        }
    };



    public RTCView(Context context, EglBase.Context glContext) {
        super(context);

        surfaceViewRenderer = new SurfaceViewRenderer(context);
        surfaceViewRenderer.init(glContext, rendererEvents);
        addView(surfaceViewRenderer);
        setMirror(true);
        setScalingType(DEFAULT_SCALING_TYPE);
        surfaceViewRenderer.setZOrderOnTop(true);
        setZOrder(0);
    }


    public RTCView(Context context, EglBase.Context glContext, GlRectDrawer drawer) {
        super(context);

        surfaceViewRenderer = new SurfaceViewRenderer(context);
        surfaceViewRenderer.init(glContext, rendererEvents,EglBase.CONFIG_PLAIN,drawer);
        addView(surfaceViewRenderer);

        setMirror(true);
        setScalingType(DEFAULT_SCALING_TYPE);
        surfaceViewRenderer.setZOrderOnTop(true);
        setZOrder(0);
    }



    private final SurfaceViewRenderer getSurfaceViewRenderer() {
        return surfaceViewRenderer;
    }


    private void onFrameResolutionChanged(int videoWidth, int videoHeight, int rotation) {

        boolean changed = false;

        synchronized (layoutSyncRoot) {
            if (this.frameHeight != videoHeight) {
                this.frameHeight = videoHeight;
                changed = true;
            }
            if (this.frameRotation != rotation) {
                this.frameRotation = rotation;
                changed = true;
            }
            if (this.frameWidth != videoWidth) {
                this.frameWidth = videoWidth;
                changed = true;
            }
        }
        if (changed) {
            // The onFrameResolutionChanged method call executes on the
            // surfaceViewRenderer's render Thread.
            post(requestSurfaceViewRendererLayoutRunnable);
        }
    }



    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int height = bottom - top;
        int width = right - left;

        if (height == 0 || width == 0) {
            left = top = right = bottom = 0;
        } else {
            int frameHeight;
            int frameRotation;
            int frameWidth;
            RendererCommon.ScalingType scalingType;

            synchronized (layoutSyncRoot) {
                frameHeight = this.frameHeight;
                frameRotation = this.frameRotation;
                frameWidth = this.frameWidth;
                scalingType = this.scalingType;
            }

            SurfaceViewRenderer surfaceViewRenderer = getSurfaceViewRenderer();

            switch (scalingType) {
                case SCALE_ASPECT_FILL:
                    // Fill this ViewGroup with surfaceViewRenderer and the latter
                    // will take care of filling itself with the video similarly to
                    // the cover value the CSS property object-fit.
                    right = width;
                    left = 0;
                    bottom = height;
                    top = 0;
                    break;
                case SCALE_ASPECT_FIT:
                    // Lay surfaceViewRenderer out inside this ViewGroup in accord
                    // with the contain value of the CSS property object-fit.
                    // SurfaceViewRenderer will fill itself with the video similarly
                    // to the cover or contain value of the CSS property object-fit
                    // (which will not matter, eventually).
                    if (frameHeight == 0 || frameWidth == 0) {
                        left = top = right = bottom = 0;
                    } else {
                        float frameAspectRatio
                                = (frameRotation % 180 == 0)
                                ? frameWidth / (float) frameHeight
                                : frameHeight / (float) frameWidth;
                        Point frameDisplaySize
                                = RendererCommon.getDisplaySize(
                                scalingType,
                                frameAspectRatio,
                                width, height);

                        left = (width - frameDisplaySize.x) / 2;
                        top = (height - frameDisplaySize.y) / 2;
                        right = left + frameDisplaySize.x;
                        bottom = top + frameDisplaySize.y;
                    }
                    break;
            }
        }
        surfaceViewRenderer.layout(left, top, right, bottom);
    }

    private void removeRendererFromVideoTrack() {
        if (surfaceViewRenderer != null) {
            videoTrack.removeSink(surfaceViewRenderer);

            videoTrack = null;

            getSurfaceViewRenderer().release();

            // Since this WebRTCView is no longer rendering anything, make sure
            // surfaceViewRenderer displays nothing as well.
            synchronized (layoutSyncRoot) {
                frameHeight = 0;
                frameRotation = 0;
                frameWidth = 0;
            }
            requestSurfaceViewRendererLayout();
        }
    }


    @SuppressLint("WrongCall")
    private void requestSurfaceViewRendererLayout() {
        // Google/WebRTC just call requestLayout() on surfaceViewRenderer when
        // they change the value of its mirror or surfaceType property.
        getSurfaceViewRenderer().requestLayout();
        // The above is not enough though when the video frame's dimensions or
        // rotation change. The following will suffice.
        if (!invokeIsInLayout()) {
            onLayout(
                    /* changed */ false,
                    getLeft(), getTop(), getRight(), getBottom());
        }
    }


    public void setMirror(boolean mirror) {
        if (this.mirror != mirror) {
            this.mirror = mirror;

            SurfaceViewRenderer surfaceViewRenderer = getSurfaceViewRenderer();

            surfaceViewRenderer.setMirror(mirror);
            // SurfaceViewRenderer takes the value of its mirror property into
            // account upon its layout.
            requestSurfaceViewRendererLayout();
        }
    }

    public void  setScaleMode(ScaleMode scaleMode){

        if (scaleMode == ScaleMode.ScaleModeFill){

            RendererCommon.ScalingType scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FILL;

            setScalingType(scalingType);

        } else if(scaleMode == ScaleMode.ScaleModeFit){

            RendererCommon.ScalingType scalingType = RendererCommon.ScalingType.SCALE_ASPECT_FIT;

            setScalingType(scalingType);

        }
    }


    private void setScalingType(RendererCommon.ScalingType scalingType) {
        SurfaceViewRenderer surfaceViewRenderer;

        synchronized (layoutSyncRoot) {
            if (this.scalingType == scalingType) {
                return;
            }

            this.scalingType = scalingType;

            surfaceViewRenderer = getSurfaceViewRenderer();
            surfaceViewRenderer.setScalingType(scalingType);
        }
        // Both this instance ant its SurfaceViewRenderer take the value of
        // their scalingType properties into account upon their layouts.
        requestSurfaceViewRendererLayout();
    }


    protected void setVideoTrack(VideoTrack videoTrack) {
        VideoTrack oldValue = this.videoTrack;

        if (oldValue != videoTrack) {
            if (oldValue != null) {
                removeRendererFromVideoTrack();
            }

            this.videoTrack = videoTrack;

            if (videoTrack != null) {

                videoTrack.addSink(surfaceViewRenderer);
            }
        }
    }


    public void setZOrder(int zOrder) {
        SurfaceViewRenderer surfaceViewRenderer = getSurfaceViewRenderer();

        switch (zOrder) {
            case 0:
                surfaceViewRenderer.setZOrderMediaOverlay(false);
                break;
            case 1:
                surfaceViewRenderer.setZOrderMediaOverlay(true);
                break;
            case 2:
                surfaceViewRenderer.setZOrderOnTop(true);
                break;
        }
    }


    public void setViewListener(RTCViewListener listener){

        viewListener = listener;
    }


    private boolean invokeIsInLayout() {
        Method m = IS_IN_LAYOUT;
        boolean b = false;

        if (m != null) {
            try {
                b = (boolean) m.invoke(this);
            } catch (Exception e) {
                // Fall back to the behavior of ViewCompat#isInLayout(View).
            }
        }
        return b;
    }

}
