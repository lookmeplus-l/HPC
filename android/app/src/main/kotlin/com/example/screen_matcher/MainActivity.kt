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
    private val CHANNEL = "com.example.screen_matcher/native"
    private val SCREENSHOT_REQUEST_CODE = 1001
    private val OVERLAY_PERMISSION_REQUEST_CODE = 1002

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
                        startFloatingWindowService()
                        result.success(true)
                    }
                    "stopFloatingWindow" -> {
                        stopFloatingWindowService()
                        result.success(true)
                    }
                    "requestScreenshot" -> {
                        pendingResult = result
                        requestScreenshotPermission()
                    }
                    "checkOverlayPermission" -> {
                        result.success(checkOverlayPermission())
                    }
                    "requestOverlayPermission" -> {
                        requestOverlayPermission()
                        result.success(null)
                    }
                    "updateFloatingWindowImage" -> {
                        val imagePath = call.argument<String>("imagePath")
                        val matched = call.argument<Boolean>("matched") ?: false
                        FloatingWindowService.updateImage(imagePath, matched)
                        result.success(true)
                    }
                    "updateFloatingWindowStatus" -> {
                        val status = call.argument<String>("status") ?: "idle"
                        FloatingWindowService.updateStatus(status)
                        result.success(true)
                    }
                    "matchTemplate" -> {
                        val screenshotPath = call.argument<String>("screenshotPath")
                        val templatePath = call.argument<String>("templatePath")
                        if (screenshotPath == null || templatePath == null) {
                            result.error("INVALID_ARGS", "Missing screenshotPath or templatePath", null)
                            return@setMethodCallHandler
                        }
                        Thread {
                            val matchResult = TemplateMatcher.match(screenshotPath, templatePath)
                            runOnUiThread {
                                result.success(mapOf(
                                    "matched" to matchResult.matched,
                                    "nccScore" to matchResult.nccScore,
                                    "bestX" to matchResult.bestX,
                                    "bestY" to matchResult.bestY,
                                    "bestScale" to matchResult.bestScale,
                                    "templateWidth" to matchResult.templateWidth,
                                    "templateHeight" to matchResult.templateHeight
                                ))
                            }
                        }.start()
                    }
                    else -> result.notImplemented()
                }
            }
    }

    private fun checkOverlayPermission(): Boolean {
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
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE)
        }
    }

    private fun startFloatingWindowService() {
        val intent = Intent(this, FloatingWindowService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun stopFloatingWindowService() {
        stopService(Intent(this, FloatingWindowService::class.java))
    }

    private fun requestScreenshotPermission() {
        val intent = mediaProjectionManager?.createScreenCaptureIntent()
        if (intent != null) {
            startActivityForResult(intent, SCREENSHOT_REQUEST_CODE)
        } else {
            pendingResult?.error("ERROR", "无法创建截屏请求", null)
            pendingResult = null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != SCREENSHOT_REQUEST_CODE) return

        if (resultCode == Activity.RESULT_OK && data != null) {
            ScreenshotService.startScreenshot(
                context = this,
                resultCode = resultCode,
                data = data,
                projectionManager = mediaProjectionManager!!,
                callback = object : ScreenshotService.ScreenshotCallback {
                    override fun onScreenshotTaken(imagePath: String) {
                        runOnUiThread {
                            pendingResult?.success(imagePath)
                            pendingResult = null
                        }
                    }

                    override fun onError(error: String) {
                        runOnUiThread {
                            pendingResult?.error("SCREENSHOT_ERROR", error, null)
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
        stopFloatingWindowService()
        super.onDestroy()
    }
}
