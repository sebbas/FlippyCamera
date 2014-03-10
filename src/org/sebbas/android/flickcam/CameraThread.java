package org.sebbas.android.flickcam;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.sebbas.android.helper.DeviceInfo;
import org.sebbas.android.listener.CameraThreadListener;
import org.sebbas.android.views.CameraPreviewNew;

import android.annotation.SuppressLint;
import android.content.Context;
import android.hardware.Camera;
import android.hardware.Camera.ErrorCallback;
import android.hardware.Camera.Parameters;
import android.hardware.Camera.PictureCallback;
import android.hardware.Camera.ShutterCallback;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.Surface;
import android.view.WindowManager;

public class CameraThread extends Thread {

    // Private constants
    private static final String TAG = "camera_thread";
    public static final int CAMERA_ID_BACK = Camera.CameraInfo.CAMERA_FACING_BACK;
    public static final int CAMERA_ID_FRONT = Camera.CameraInfo.CAMERA_FACING_FRONT;
    protected static final String COULD_NOT_INITIALIZE_CAMERA = "Could not initialize camera";
    protected static final String NO_CAMERAS_FOUND = "No cameras found on device";
    protected static final String CANNOT_CONNECT_TO_CAMERA = "Cannot connect to camera";
    protected static final String NO_STORAGE_AVAILABLE = "No storage available on this device";
    protected static final String FAILED_TO_SAVE_PICTURE = "Failed to save picture";
    protected static final String IS_SAVING_PICTURE = "Saving your picture ...";
    protected static final String SAVED_PICTURE_SUCCESSFULLY = "Saved picture successfully!";
    private static final String ALBUM_NAME = "FlickCam";
    
    // Private instance variables
    private Context mContext;
    private Handler mHandler;
    private Camera mCamera;
    private CameraThreadListener mCameraThreadListener;
    protected int mNumberOfCamerasSupported;
    protected int mCurrentCameraID;
    private boolean mVideoStabilizationSupported;
    private boolean mSmoothZoomSupported;
    protected int mZoomMax;
    private int mZoomValue;
    protected boolean mFlashEnabled;
    private MediaRecorder mMediaRecorder;
    
    // Callbacks
    private ErrorCallback mErrorCallback;
    private ShutterCallback mShutterCallback;
    private PictureCallback mRawCallback;
    private PictureCallback mPostViewCallback;
    private PictureCallback mJpegCallback;
    protected byte[] mPictureData;

    public CameraThread(CameraFragmentUI cameraFragment, Context context) {
        mContext = context;
        mCameraThreadListener = (CameraThreadListener) cameraFragment;
        mNumberOfCamerasSupported = Camera.getNumberOfCameras();
    }
    
    @Override
    public void run() {
        super.run();
        try {
            Looper.prepare();
            mHandler = new Handler();
            Looper.loop();
        } catch (Throwable t) {
            Log.e(TAG, "Camera thread halted due to an error", t);
        }
    }
    
    // Public methods
    public synchronized void stopCamera() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                Log.i(TAG, "Camera thread loop quitting by request");
                deinitializeCamera();
                Looper.myLooper().quit();
            }
            
        });
    }
    
    public synchronized void initializeCamera(final int cameraID) {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                try {
                    if (mNumberOfCamerasSupported == 0) {
                        Log.e(TAG, NO_CAMERAS_FOUND);
                        mCameraThreadListener.alertCameraThreadError(NO_CAMERAS_FOUND);
                    } else {
                        mCamera = getCameraInstance(cameraID);
                        if (mCamera == null) {
                            Log.e(TAG, CANNOT_CONNECT_TO_CAMERA);
                            mCameraThreadListener.alertCameraThreadError(CANNOT_CONNECT_TO_CAMERA);
                        } else {
                            mCurrentCameraID = cameraID;
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, COULD_NOT_INITIALIZE_CAMERA, t);
                    mCameraThreadListener.alertCameraThreadError(COULD_NOT_INITIALIZE_CAMERA);
                }
            }
            
        });
    }
    
    public synchronized void initializeCameraProperties() {
        mHandler.post(new Runnable() {

            @SuppressLint("NewApi")
            @Override
            public void run() {
                Parameters parameters = mCamera.getParameters();
                mZoomMax = parameters.getMaxZoom();
                mZoomValue = 0;
                mSmoothZoomSupported = parameters.isSmoothZoomSupported();
                if (DeviceInfo.supportsSDK(15)) {
                    mVideoStabilizationSupported = parameters.isVideoStabilizationSupported();
                }
            }
            
        });
    }
    
    public synchronized void setCameraParameters(final boolean flashEnabled, final int deviceRotation) {
        mHandler.post(new Runnable() {

            @SuppressLint("NewApi")
            @Override
            public void run() {
                Parameters parameters = mCamera.getParameters();
                // Adds continuous auto focus (only if API is high enough) to the parameters.
                if (DeviceInfo.supportsSDK(14)) {
                    parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                } else {
                    parameters.setFocusMode(Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                } 
                
                // Enable auto white balance. This makes preview look smoother
                parameters.setWhiteBalance(Parameters.WHITE_BALANCE_AUTO);
                
                if (flashEnabled) {
                    parameters.setFlashMode(Parameters.FLASH_MODE_ON);
                } else {
                    parameters.setFlashMode(Parameters.FLASH_MODE_OFF);
                }
                
                // This will only get called if video stabilization is supported and if the API is high enough. All handled in initCameraProperties.
                if (mVideoStabilizationSupported && DeviceInfo.supportsSDK(15)) {
                    parameters.setVideoStabilization(true);
                }
                
                // Set the current zoom value if we didn't use smooth zoom
                if (!mSmoothZoomSupported) {
                    parameters.setZoom(mZoomValue);
                }
                parameters.setRotation(deviceRotation);
                // Finally, add the parameters to the camera
                mCamera.setParameters(parameters);
                mFlashEnabled = flashEnabled; // Keep track of the flash settings
            }
            
        });
    }
    
    public synchronized void setCameraDisplayOrientation() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                setCameraDisplayOrientation(mContext, mCurrentCameraID, mCamera);
                mCameraThreadListener.cameraSetupComplete(mCamera, mCurrentCameraID);
            }
            
        });
    }
    
    public synchronized void performZoom(final float scaleFactor) {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                mZoomValue = (int) ((scaleFactor - 1) * (mZoomMax + 1));
                if (mZoomValue > 0 && mZoomValue < mZoomMax) {
                    if (mSmoothZoomSupported) {
                        mCamera.startSmoothZoom(mZoomValue);
                    } else {
                        setCameraParameters(mFlashEnabled, mCurrentCameraID); // Just update the camera parameters. This will also set the new zoom level
                    }
                }
            }
        });
    }
    
    public synchronized void startRecorder(final CameraPreviewNew... cameraPreview) {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                prepareMediaRecorder(cameraPreview[0]);
                startMediaRecorder();
                resetMediaRecorder();
            }
            
        });
    }
    
    public synchronized void switchCamera() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                deinitializeCamera();
                if (mCurrentCameraID == CAMERA_ID_BACK) {
                    mCurrentCameraID = CAMERA_ID_FRONT;
                } else if (mCurrentCameraID == CAMERA_ID_FRONT) {
                    mCurrentCameraID = CAMERA_ID_BACK;
                }
                initializeCamera(mCurrentCameraID);
                initializeCameraProperties();
                setCameraParameters(mFlashEnabled, mCurrentCameraID);
                setCameraDisplayOrientation();
            }
        });
    }
    
    public synchronized void takePicture() {
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                mCamera.takePicture(getShutterCallback(), getRawCallback(), getPostViewCallback(), getJpegCallback());
            }
            
        });
    }
    
    public synchronized void writePictureData() {
        if (pictureDataIsAvailable())
        mHandler.post(new Runnable() {

            @Override
            public void run() {
                String filename = getAlbumStorageDir() + "/" + getDefaultFilename();
                
                if (!DeviceInfo.isExternalStorageWritable()) {
                    mCameraThreadListener.alertCameraThreadError(NO_STORAGE_AVAILABLE);
                } else  if (mPictureData == null) {
                    mCameraThreadListener.alertCameraThreadError(FAILED_TO_SAVE_PICTURE);
                    Log.d(TAG, "Data Was Empty, Not Writing to File");
                } else {
                    mCameraThreadListener.alertCameraThreadError(IS_SAVING_PICTURE);
                    
                    try {
                        FileOutputStream output = new FileOutputStream(filename);
                        output.write(mPictureData);
                        output.close();
                        mCameraThreadListener.alertCameraThreadError(SAVED_PICTURE_SUCCESSFULLY);
                        Log.d(TAG, "Image Saved Successfully");
                    } catch (IOException e) {
                        mCameraThreadListener.alertCameraThreadError(FAILED_TO_SAVE_PICTURE);
                        Log.d(TAG, "Saving Image Failed!");
                    } finally {
                        // We have to refresh the grid view UI to make the new photo show up
                        mCameraThreadListener.newPictureAddedToGallery();
                    }
                }
            }
            
        });
    }
    
    
    
    // Private methods
    private Camera getCameraInstance(int cameraID) {
        Camera camera = null;
        try {
            camera = Camera.open(cameraID);
            camera.setErrorCallback(getErrorCallback());
            Log.d(TAG, "Camera Opened Successfully");
        } catch (RuntimeException e) {
            Log.d(TAG, "Failed to Open Camera - " + e.getMessage());
        }
        return camera; // returns null if camera is unavailable
    }
    
    public void deinitializeCamera() {
        stopPreview();
        releaseCamera();
        clearPictureData();
        
        Log.d(TAG, "Camera Initialization has Been Undone");
    }
    
    private void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
    }
    
    private void releaseCamera() {
        if (mCamera != null) {
            mCamera.release();
        }
    }
    
    private void clearPictureData() {
        mPictureData = null;
    }
    
    private boolean pictureDataIsAvailable() {
        return (mPictureData != null);
    }

    private void prepareMediaRecorder(CameraPreviewNew cameraPreview) {
        mMediaRecorder = new MediaRecorder();
        mCamera.unlock();
        mMediaRecorder.setCamera(mCamera);
        
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.CAMERA);
        
        mMediaRecorder.setProfile(CamcorderProfile.get(mCurrentCameraID, CamcorderProfile.QUALITY_HIGH));
        mMediaRecorder.setOutputFile(getPipeFD());
        
        // We have to set the preview display for devices that use a SurfaceView
        if(!DeviceInfo.supportsSDK(14)) {
            setPreviewDisplayForMediaRecorder(cameraPreview);
        }
        
        try {
            mMediaRecorder.prepare();
            Log.d(TAG, "Prepare media recorder successful");
        } catch (IllegalStateException e) {
            resetMediaRecorder();
            Log.d(TAG, "Failed to prepare media recorder- IllegalStateException");
        } catch (IOException e) {
            resetMediaRecorder();
            Log.d(TAG, "Failed to prepare media recorder. IOException");
        }
    }
    
    private void setPreviewDisplayForMediaRecorder(CameraPreviewNew cameraPreview) {
        mMediaRecorder.setPreviewDisplay(cameraPreview.getHolder().getSurface());
    }
    
    private void startMediaRecorder() {
        if (mMediaRecorder != null) {
            try {
                mMediaRecorder.start();
                Log.d(TAG, "Start media recorder successful");
            } catch (RuntimeException ie){
                Log.e(TAG, "Failed to start the media recorder");
            }
            
        }
    }
    
    private void resetMediaRecorder() {
        if (mMediaRecorder != null) {
            mMediaRecorder.reset();
        }
    }
    
    private FileDescriptor getPipeFD() {
        FileDescriptor outputPipe = null;
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            outputPipe = pipe[1].getFileDescriptor();
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        return outputPipe;
    }
    
    private String getDefaultFilename() {
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        return "IMG_" + timeStamp + ".jpeg";
    }
    
    private File getAlbumStorageDir() {
        // Get the directory for the user's public pictures directory.
        File file = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), ALBUM_NAME);
        if (!file.mkdirs()) {
            Log.e(TAG, "Directory not created");
        }
        return file;
    }
    
    // Callbacks
    private Camera.ErrorCallback getErrorCallback() {
        if (mErrorCallback == null) {
            mErrorCallback = new Camera.ErrorCallback() {
                
                @Override
                public void onError(int error, Camera camera) {
                    if (error == Camera.CAMERA_ERROR_SERVER_DIED) {
                        deinitializeCamera();
                        initializeCamera(mCurrentCameraID);
                        initializeCameraProperties();
                        setCameraParameters(mFlashEnabled, mCurrentCameraID);
                        setCameraDisplayOrientation();
                    }
                }
            };
        }
        return mErrorCallback;
    }
    
    private ShutterCallback getShutterCallback() {
        return mShutterCallback;
    }

    private PictureCallback getRawCallback() {
        return mRawCallback;
    }

    private PictureCallback getPostViewCallback() {
        return mPostViewCallback;
    }

    private PictureCallback getJpegCallback() {
        Log.d(TAG, "JPEG Callback");
        if (mJpegCallback == null) {
            mJpegCallback = new PictureCallback() {

                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    Log.d(TAG, "On picture taken");
                    mPictureData = data;
                }
            };
        }
        return mJpegCallback;
    }
    
    // Static methods
    private static void setCameraDisplayOrientation(Context context, int cameraId, android.hardware.Camera camera) {
        android.hardware.Camera.CameraInfo info = new android.hardware.Camera.CameraInfo();
        android.hardware.Camera.getCameraInfo(cameraId, info);
        int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
        int degrees = 0;
        switch (rotation) {
            case Surface.ROTATION_0: degrees = 0; break;
            case Surface.ROTATION_90: degrees = 90; break;
            case Surface.ROTATION_180: degrees = 180; break;
            case Surface.ROTATION_270: degrees = 270; break;
        }
        
        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;  // compensate the mirror
        } else {  // back-facing
            result = (info.orientation - degrees + 360) % 360;
        }
        camera.setDisplayOrientation(result);
    }

    

}