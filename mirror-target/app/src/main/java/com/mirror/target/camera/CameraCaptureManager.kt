package com.mirror.target.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import timber.log.Timber
import java.nio.ByteBuffer
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraCaptureManager(private val context: Context) {

    companion object {
        // Fixed size for encoder consistency
        const val WIDTH = 640
        const val HEIGHT = 480
    }

    var onFrameAvailable: ((ByteArray) -> Unit)? = null

    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var imageReader: ImageReader? = null
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    private val cameraLock = Semaphore(1)
    private var isCapturing = false

    private val stateCallback = object : CameraDevice.StateCallback() {
        override fun onOpened(camera: CameraDevice) {
            cameraLock.release()
            cameraDevice = camera
            createCaptureSession()
        }
        override fun onDisconnected(camera: CameraDevice) {
            cameraLock.release()
            camera.close()
        }
        override fun onError(camera: CameraDevice, error: Int) {
            cameraLock.release()
            camera.close()
            Timber.e("Camera error: $error")
        }
    }

    fun startCapture() {
        if (isCapturing) return
        startBackgroundThread()
        openCamera()
        isCapturing = true
    }

    fun stopCapture() {
        if (!isCapturing) return
        isCapturing = false
        closeCamera()
        stopBackgroundThread()
    }

    private fun openCamera() {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        try {
            val cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            } ?: manager.cameraIdList[0]

            if (!cameraLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) return
            
            // Fixed 640x480 for encoder consistency
            imageReader = ImageReader.newInstance(WIDTH, HEIGHT, ImageFormat.YUV_420_888, 3)
                .apply { 
                    setOnImageAvailableListener({ reader ->
                        val image = reader.acquireLatestImage()
                        if (image != null) {
                            try {
                                val nv21 = yuv420ToNv21(image)
                                onFrameAvailable?.invoke(nv21)
                            } finally {
                                image.close()
                            }
                        }
                    }, backgroundHandler)
                }
            
            manager.openCamera(cameraId, stateCallback, backgroundHandler)
        } catch (e: Exception) {
            Timber.e(e, "Failed to open camera")
        }
    }

    private fun closeCamera() {
        try {
            cameraLock.acquire()
            captureSession?.close()
            cameraDevice?.close()
            imageReader?.close()
        } finally {
            cameraLock.release()
        }
    }

    private fun createCaptureSession() {
        val camera = cameraDevice ?: return
        val reader = imageReader ?: return
        
        camera.createCaptureSession(
            listOf(reader.surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    captureSession = session
                    val request = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(reader.surface)
                        // Set target FPS to 15 for encoder
                        set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, android.util.Range(15, 15))
                    }.build()
                    session.setRepeatingRequest(request, null, backgroundHandler)
                }
                override fun onConfigureFailed(session: CameraCaptureSession) {}
            },
            backgroundHandler
        )
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("CameraThread").apply {
            start()
            backgroundHandler = Handler(looper)
        }
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        backgroundThread?.join()
        backgroundThread = null
    }

    /**
     * Convert YUV_420_888 Image to NV21 byte array.
     * NV21 format: Y plane followed by interleaved VU planes.
     */
    private fun yuv420ToNv21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        
        val yPlane = image.planes[0]
        val uPlane = image.planes[1]
        val vPlane = image.planes[2]
        
        val yBuffer = yPlane.buffer
        val uBuffer = uPlane.buffer
        val vBuffer = vPlane.buffer
        
        // Calculate sizes
        val ySize = yBuffer.remaining()
        val vuSize = uBuffer.remaining() + vBuffer.remaining()
        
        val nv21 = ByteArray(ySize + vuSize)
        
        // Copy Y plane
        yBuffer.get(nv21, 0, ySize)
        
        // Copy V and U planes interleaved (VU order for NV21)
        val vuPos = ySize
        val uStride = uPlane.pixelStride
        val vStride = vPlane.pixelStride
        
        val uRowStride = uPlane.rowStride
        val vRowStride = vPlane.rowStride
        
        var pos = vuPos
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vIdx = row * vRowStride + col * vStride
                val uIdx = row * uRowStride + col * uStride
                
                if (vIdx < vBuffer.capacity() && uIdx < uBuffer.capacity()) {
                    nv21[pos++] = vBuffer.get(vIdx)
                    nv21[pos++] = uBuffer.get(uIdx)
                }
            }
        }
        
        return nv21
    }
}
