package com.example.screen_matcher

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileOutputStream

class ScreenshotService : Service() {

    companion object {
        private const val CHANNEL_ID = "screen_matcher_screenshot"
        private const val NOTIFICATION_ID = 2002

        private var callback: ScreenshotCallback? = null

        fun start(
            context: Context,
            resultCode: Int,
            data: Intent,
            projectionManager: MediaProjectionManager,
            callback: ScreenshotCallback
        ) {
            this.callback = callback
            val intent = Intent(context, ScreenshotService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("data", data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        interface ScreenshotCallback {
            fun onSuccess(imagePath: String)
            fun onError(message: String)
        }
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode == -1 || data == null) {
            callback?.onError("Invalid intent data")
            stopSelf()
            return START_NOT_STICKY
        }

        val projectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        mediaProjection = projectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                cleanup()
                stopSelf()
            }
        }, Handler(Looper.getMainLooper()))

        captureScreen()
        return START_NOT_STICKY
    }

    private fun captureScreen() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenshotCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null
        )

        imageReader?.setOnImageAvailableListener({ reader ->
            val image = reader.acquireLatestImage()
            if (image != null) {
                try {
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * width

                    val bitmap = android.graphics.Bitmap.createBitmap(
                        width + rowPadding / pixelStride,
                        height,
                        android.graphics.Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)

                    val cropped = android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, height)
                    bitmap.recycle()

                    val file = File(cacheDir, "screenshot_${System.currentTimeMillis()}.png")
                    FileOutputStream(file).use { out ->
                        cropped.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
                    }
                    cropped.recycle()

                    callback?.onSuccess(file.absolutePath)
                } catch (e: Exception) {
                    callback?.onError("截图保存失败: ${e.message}")
                } finally {
                    image.close()
                }
            }
            cleanup()
            stopSelf()
        }, Handler(Looper.getMainLooper()))
    }

    private fun cleanup() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        mediaProjection?.stop()
        mediaProjection = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "截图服务",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("截图识别")
            .setContentText("正在截取屏幕...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        cleanup()
        super.onDestroy()
    }
}
