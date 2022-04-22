package org.easydarwin.util;

import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LifecycleRegistry;
import android.graphics.SurfaceTexture;
import android.support.annotation.NonNull;
import android.view.TextureView;

import java.lang.ref.WeakReference;

public class TextureLifecycler implements LifecycleOwner {
    private LifecycleRegistry mLifecycleRegistry = new LifecycleRegistry(this);

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mLifecycleRegistry;
    }

    WeakReference<TextureView> mRef;

    public TextureLifecycler(TextureView view) {
        mRef = new WeakReference<>(view);
        mLifecycleRegistry.markState(Lifecycle.State.INITIALIZED);
        if (view.isAvailable()) {
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
            mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
        }
        view.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE);
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START);
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE);
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP);
                mLifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surface) {

            }
        });
    }

}
