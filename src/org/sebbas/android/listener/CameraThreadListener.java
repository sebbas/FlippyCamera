package org.sebbas.android.listener;

import android.graphics.Rect;

public interface CameraThreadListener {

	public void alertCameraThread(String message);
	public void cameraSetupComplete(int cameraID);
	public void newPictureAddedToGallery();
	public void setTouchFocusView(Rect tFocusRect);
	public void makeFlashAnimation();
}
