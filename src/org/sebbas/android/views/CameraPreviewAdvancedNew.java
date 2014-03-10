package org.sebbas.android.views;
import java.io.IOException;

import org.sebbas.android.flickcam.CameraThread;
import org.sebbas.android.interfaces.CameraPreviewListener;
import org.sebbas.android.listener.ScaleListener;
import org.sebbas.android.listener.ScaleListenerNew;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.View;

@SuppressLint("NewApi")
public class CameraPreviewAdvancedNew extends TextureView implements
        SurfaceTextureListener {

    private static final String TAG = "camera_preview_advanced";
    private CameraThread mCameraThread;
    private Camera mCamera;
    private ScaleGestureDetector mScaleDetector;

    public CameraPreviewAdvancedNew(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
    
    public CameraPreviewAdvancedNew(Context context, CameraThread cameraThread, Camera camera) {
        super(context);
        mCamera = camera;
        mCameraThread = cameraThread;
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListenerNew(this, cameraThread));
        setSurfaceTextureListener(this);
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "ON SURFACE TEXTURE AVAILABLE");
        
        try {
            if (mCamera != null) {
                mCamera.setPreviewTexture(surface);
                mCameraThread.startRecorder();
            }
        } catch (IOException e) {
            // Something bad happened
            e.printStackTrace();
        }
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        Log.d(TAG, "ON SURFACE TEXTURE DESTROYED");
        return true;
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        Log.d(TAG, "ON SURFACE TEXTURE SIZE CHANGED");
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // Let the ScaleGestureDetector inspect all events.
        mScaleDetector.onTouchEvent(event);
        return true;
    }
}
