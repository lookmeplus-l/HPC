package com.example.screen_matcher

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.annotation.NonNull
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    companion object {
        const val CHANNEL = "com.example.screen_matcher/native"
        const val REQUEST_SCREENSHOT = 1001
        const val REQUEST_OVERLAY = 1002
    }

    private var pendingResult: MethodChannel.Result? = null
    private var mediaProjectionManager: MediaProjectionManager? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "startFloatingWindow" -> {
                        startFloatingWindow()
                        result.success(true)
                    }
                    "stopFloatingWindow" -> {
                        stopFloatingWindow()
                        result.success(true)
                    }
                    "requestScreenshot" -> {
                        pendingResult = result
                        requestScreenshot()
                    }
                    "checkOverlayPermission" -> {
                        result.success(hasOverlayPermission())
                    }
                    "requestOverlayPermission" -> {
                        requestOverlayPermission()
                        result.success(null)
                    }
                    "showFloatingWindowImage" -> {
                        val path = call.argument<String>("imagePath")
                        val matched = call.argument<Boolean>("matched") ?: false
                        if (matched && path != null) {
                            FloatingWindowService.showImage(path)
                        } else {
                            FloatingWindowService.hideImage()
                        }
                        result.success(true)
                    }
                    "setFloatingWindowStatus" -> {
                        val status = call.argument<String>("status") ?: "idle"
                        FloatingWindowService.setStatus(status)
                        result.success(true)
                    }
                    "matchTemplate" -> {
                        val screenshotPath = call.argument<String>("screenshotPath")
                        val templatePath = call.argument<String>("templatePath")
                        if (screenshotPath == null || templatePath == null) {
                            result.error("INVALID_ARGS", "Missing paths", null)
                            return@setMethodCallHandler
                        }
                        Thread {
                            val m = TemplateMatcher.match(screenshotPath, templatePath)
                            runOnUiThread {
                                result.success(mapOf(
                                    "matched" to m.matched,
                                    "nccScore" to m.nccScore,
                                    "bestX" to m.bestX,
                                    "bestY" to m.bestY,
                                    "bestScale" to m.bestScale,
                                    "templateWidth" to m.templateWidth,
                                    "templateHeight" to m.templateHeight
                                ))
                            }
                        }.start()
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun startFloatingWindow() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopFloatingWindow() {
        stopService(Intent(this, FloatingWindowService::class.java))
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else {
            true
        }
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
        }
    }

    private fun requestScreenshot() {
        val intent = mediaProjectionManager?.createScreenCaptureIntent()
        if (intent != null) {
            startActivityForResult(intent, REQUEST_SCREENSHOT)
        } else {
            pendingResult?.error("ERROR", "无法创建截屏请求", null)
            pendingResult = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_SCREENSHOT) return

        if (resultCode == Activity.RESULT_OK && data != null) {
            ScreenshotService.start(
                context = this,
                resultCode = resultCode,
                data = data,
                projectionManager = mediaProjectionManager!!,
                callback = object : ScreenshotService.ScreenshotCallback {
                    override fun onSuccess(imagePath: String) {
                        runOnUiThread {
                            pendingResult?.success(imagePath)
                            pendingResult = null
                        }
                    }

                    override fun onError(message: String) {
                        runOnUiThread {
                            pendingResult?.error("SCREENSHOT_ERROR", message, null)
                            pendingResult = null
                        }
                    }
                }
            )
        } else {
            pendingResult?.error("PERMISSION_DENIED", "用户取消了截屏权限", null)
            pendingResult = null
        }
    }

    override fun onDestroy() {
        stopFloatingWindow()
        super.onDestroy()
    }
}
