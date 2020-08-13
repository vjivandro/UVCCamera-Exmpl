/*
 * RealWear Development Software, Source Code and Object Code.
 * (c) RealWear, Inc. All rights reserved.
 *
 * Contact info@realwear.com for further information about the use of this code.
 */

package com.realwear.uvccameraexample

import android.hardware.usb.UsbDevice
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.Surface
import com.serenegiant.usb.Size
import com.serenegiant.usb.USBMonitor
import com.serenegiant.usb.UVCCamera
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

/**
 * Main activity for the application
 *
 * This activity extends the UVCCamera library's USBMonitor.OnDeviceConnectListener so that
 * the application can respond to the USB camera's lifecycle events.
 */
class MainActivity : AppCompatActivity(), USBMonitor.OnDeviceConnectListener {
    private var mUSBMonitor: USBMonitor? = null
    private var currentCamera: UVCCamera? = null
    private val cameraMutex = Mutex()
    private var surface: Surface? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mUSBMonitor = USBMonitor(this, this)
    }

    override fun onStart() {
        super.onStart()
        mUSBMonitor?.register()
    }

    override fun onStop() {
        mUSBMonitor?.unregister()
        releaseCameraAsync()
        super.onStop()
    }

    /**
     * Called when the camera is attached to the Android device
     */
    override fun onAttach(device: UsbDevice?) {
        Log.i(TAG, "Device has been attached")
        releaseCameraAsync()

        // When the camera is attached, we need to ask the user for permission to access it.
        mUSBMonitor?.requestPermission(device)
    }

    /**
     * Called when the camera connects
     *
     * Initialize camera properties for the preview stream
     */
    override fun onConnect(
        device: UsbDevice?,
        controlBlock: USBMonitor.UsbControlBlock?,
        createNew: Boolean
    ) {
        Log.i(TAG, "Device has been connected")

        // Try to open the camera that was connected
        val camera = UVCCamera()
        try {
            camera.open(controlBlock)
        } catch (e: UnsupportedOperationException) {
            Log.e(TAG, "Failed to open camera", e)
            return
        }

        // Specify a surface to display camera feed
        surface = Surface(textureView.surfaceTexture)
        camera.setPreviewDisplay(surface)

        // Specify camera preview size and format
        val size: Size = getResolution(camera)
        if (size == EMPTY_SIZE) {
            Log.e(TAG, "Failed to find a resolution from the camera")
            return
        }
        camera.setPreviewSize(size.width, size.height, UVCCamera.FRAME_FORMAT_UYVY)

        camera.startPreview()

        // Store camera for later so it can be properly released
        storeCameraAsync(camera)
    }

    /**
     * Called when the camera connection is cancelled
     */
    override fun onCancel(device: UsbDevice?) {
        Log.i(TAG, "Device connection has been cancelled")
    }

    /**
     * Called when the camera disconnects
     */
    override fun onDisconnect(device: UsbDevice?, controlBlock: USBMonitor.UsbControlBlock?) {
        Log.i(TAG, "Device has disconnected")
        releaseCameraAsync()
    }

    /**
     * Called when the camera is detached
     */
    override fun onDettach(device: UsbDevice?) {
        Log.i(TAG, "Device has been detached")
    }

    /**
     * Save the currently connected [camera].
     */
    private fun storeCameraAsync(camera: UVCCamera) = GlobalScope.async {
        cameraMutex.withLock {
            currentCamera = camera
        }
    }

    /**
     * Disconnect from the current camera.
     */
    private fun releaseCameraAsync() = GlobalScope.async {
        cameraMutex.withLock {
            currentCamera?.stopPreview()
            currentCamera?.setStatusCallback(null)
            currentCamera?.setButtonCallback(null)
            currentCamera?.close()
            currentCamera = null

            surface?.release()
            surface = null
        }
    }

    /**
     * Find a valid resolution that the [camera] supports.
     */
    private fun getResolution(camera: UVCCamera): Size {
        val possibleSizes: List<Size> = camera.supportedSizeList

        if (possibleSizes.isEmpty()) return EMPTY_SIZE
        return possibleSizes.first()
    }

    companion object {
        const val TAG = "UvcCameraExample"
        private val EMPTY_SIZE = Size(0, 0, 0, 0, 0)
    }
}