package com.mirror.target.camera

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Size
import timber.log.Timber
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

class CameraCaptureManager(private val context: Context) {

    companion object {
        private const val MAX_WIDTH = 1280
        private const val MAX_HEIGHT = 720
    }

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
            
            val characteristics = manager.getCameraCharacteristics(cameraId)
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val size = chooseOptimalSize(map?.getOutputSizes(ImageFormat.YUV_420_888) ?: emptyArray())
            
            imageReader = ImageReader.newInstance(size.width, size.height, ImageFormat.YUV_420_888, 3)
                .apply { setOnImageAvailableListener({ it.acquireLatestImage()?.close() }, backgroundHandler) }
            
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

    private fun chooseOptimalSize(choices: Array<Size>): Size {
        return choices.filter { it.width <= MAX_WIDTH && it.height <= MAX_HEIGHT }
            .maxByOrNull { it.width * it.height } ?: choices.firstOrNull() ?: Size(640, 480)
    }
}
