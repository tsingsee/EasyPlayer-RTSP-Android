package org.easydarwin.player;

import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.support.annotation.IntDef;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;

import org.easydarwin.video.EasyPlayerClient;

/**
 * Created by apple on 2017/9/9.
 */

public class EasyPlayer {

    private static final java.lang.String LOG_TAG = "EasyPlayer";
    private final int mTransport;
    private final String mPath;
    private Surface surface;
    private EasyPlayerClient mRTSPClient;

    private static class ComponentListener implements SurfaceHolder.Callback, TextureView.SurfaceTextureListener {

        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {

        }

        @Override
        public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    }

    public static class EasyPlayerFactory {

        private Uri mURI;
        private int mTransportMode = TRANSPORT_MODE_TCP;

        public static final int TRANSPORT_MODE_TCP = 1;
        public static final int SETRANSPORT_MODE_UDP = 2;
        private boolean autoPlayWhenReady;

        // 定义Type类型
        @IntDef({TRANSPORT_MODE_TCP, SETRANSPORT_MODE_UDP})
        public @interface TRANSPORT_MODE {
        }

        public EasyPlayerFactory setUri(Uri uri) {
            String scheme = uri.getScheme();
            if (!"rtsp".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("only support rtsp stream.");
            }
            mURI = uri;
            return this;
        }

        public EasyPlayerFactory setPath(String path) {
            setUri(Uri.parse(path));
            return this;
        }

        public EasyPlayerFactory setAutoPlayWhenReady(boolean autoPlayWhenReady) {
            this.autoPlayWhenReady = autoPlayWhenReady;
            return this;
        }

        public EasyPlayerFactory setTransportMode(@TRANSPORT_MODE int transport) {
            mTransportMode = transport;
            return this;
        }

        public EasyPlayer build() {

            if (mURI == null) throw new NullPointerException("uri should not be null!");

            return new EasyPlayer(mURI.getPath(), mTransportMode);
        }


    }

    private EasyPlayer(String path, @EasyPlayerFactory.TRANSPORT_MODE int transport) {
        mPath = path;
        mTransport = transport;
    }


    public void create() {
    }


    public void destroy() {
        removeSurfaceCallbacks();
    }


    private TextureView textureView;
    private SurfaceHolder surfaceHolder;
    private final ComponentListener componentListener = new ComponentListener() {
        @Override
        public void surfaceCreated(SurfaceHolder surfaceHolder) {
            super.surfaceCreated(surfaceHolder);
            surface = surfaceHolder.getSurface();

        }

        @Override
        public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
            super.surfaceDestroyed(surfaceHolder);
            surface = null;
        }

        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            super.onSurfaceTextureAvailable(surfaceTexture, i, i1);
            surface = new Surface(surfaceTexture);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            surface = null;
            return true;
        }
    };

    private void removeSurfaceCallbacks() {
        if (textureView != null) {
            if (textureView.getSurfaceTextureListener() != componentListener) {
                Log.w(LOG_TAG, "SurfaceTextureListener already unset or replaced.");
            } else {
                textureView.setSurfaceTextureListener(null);
            }
            textureView = null;
        }
        if (surfaceHolder != null) {
            surfaceHolder.removeCallback(componentListener);
            surfaceHolder = null;
        }
    }


    private void setVideoSurfaceInternal(Surface surface) {
        this.surface = surface;
    }


    /**
     * Sets the {@link SurfaceHolder} that holds the {@link Surface} onto which video will be
     * rendered. The player will track the lifecycle of the surface automatically.
     *
     * @param surfaceHolder The surface holder.
     */
    public void setVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
        removeSurfaceCallbacks();
        this.surfaceHolder = surfaceHolder;
        if (surfaceHolder == null) {
            setVideoSurfaceInternal(null);
        } else {
            surfaceHolder.addCallback(componentListener);
            Surface surface = surfaceHolder.getSurface();
            setVideoSurfaceInternal(surface != null && surface.isValid() ? surface : null);
        }
    }

    /**
     * Clears the {@link SurfaceHolder} that holds the {@link Surface} onto which video is being
     * rendered if it matches the one passed. Else does nothing.
     *
     * @param surfaceHolder The surface holder to clear.
     */
    public void clearVideoSurfaceHolder(SurfaceHolder surfaceHolder) {
        if (surfaceHolder != null && surfaceHolder == this.surfaceHolder) {
            setVideoSurfaceHolder(null);
        }
    }

    /**
     * Sets the {@link SurfaceView} onto which video will be rendered. The player will track the
     * lifecycle of the surface automatically.
     *
     * @param surfaceView The surface view.
     */
    public void setVideoSurfaceView(SurfaceView surfaceView) {
        setVideoSurfaceHolder(surfaceView == null ? null : surfaceView.getHolder());
    }

    /**
     * Sets the {@link TextureView} onto which video will be rendered. The player will track the
     * lifecycle of the surface automatically.
     *
     * @param textureView The texture view.
     */
    public void setVideoTextureView(TextureView textureView) {
        removeSurfaceCallbacks();
        this.textureView = textureView;
        if (textureView == null) {
            setVideoSurfaceInternal(null);
        } else {
            if (textureView.getSurfaceTextureListener() != null) {
                Log.w(LOG_TAG, "Replacing existing SurfaceTextureListener.");
            }
            textureView.setSurfaceTextureListener(componentListener);
            SurfaceTexture surfaceTexture = textureView.isAvailable() ? textureView.getSurfaceTexture()
                    : null;
            setVideoSurfaceInternal(surfaceTexture == null ? null : new Surface(surfaceTexture));
        }
    }
}
