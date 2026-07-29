package com.example.screen_matcher

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import java.io.File

class MainActivity : AppCompatActivity() {

    private val REQUEST_OVERLAY = 1001
    private val REQUEST_SCREENSHOT = 1002

    private lateinit var presetManager: PresetImageManager
    private lateinit var rvImages: RecyclerView
    private lateinit var tvEmpty: TextView
    private lateinit var tvCount: TextView
    private lateinit var tvFloatingLabel: TextView
    private lateinit var tvScreenshotLabel: TextView

    private var mediaProjectionManager: MediaProjectionManager? = null
    private var floatingActive = false
    private var scanning = false

    private val imageAdapter = ImageAdapter()
    private var pendingScreenshotResult: ((String?) -> Unit)? = null

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            uri?.let { handlePickedImage(it) }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        presetManager = PresetImageManager(this)
        presetManager.init()

        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        rvImages = findViewById(R.id.rv_images)
        tvEmpty = findViewById(R.id.tv_empty)
        tvCount = findViewById(R.id.tv_image_count)
        tvFloatingLabel = findViewById(R.id.tv_floating_label)
        tvScreenshotLabel = findViewById(R.id.tv_screenshot_label)

        rvImages.layoutManager = LinearLayoutManager(this)
        rvImages.adapter = imageAdapter

        findViewById<View>(R.id.btn_floating).setOnClickListener { toggleFloating() }
        findViewById<View>(R.id.btn_screenshot).setOnClickListener { startRecognition() }
        findViewById<View>(R.id.btn_add).setOnClickListener { filePickerLauncher.launch("image/*") }

        updateUI()
    }

    private fun updateUI() {
        val names = presetManager.allNames
        tvCount.text = "预置图片 (${names.size})"
        tvEmpty.visibility = if (names.isEmpty()) View.VISIBLE else View.GONE
        rvImages.visibility = if (names.isEmpty()) View.GONE else View.VISIBLE
        imageAdapter.notifyDataSetChanged()
    }

    private fun toggleFloating() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "请先授予悬浮窗权限", Toast.LENGTH_SHORT).show()
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, REQUEST_OVERLAY)
            return
        }

        if (floatingActive) {
            stopService(Intent(this, FloatingWindowService::class.java))
            floatingActive = false
        } else {
            val intent = Intent(this, FloatingWindowService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
            floatingActive = true
        }

        tvFloatingLabel.text = if (floatingActive) "关闭悬浮窗" else "启动悬浮窗"
    }

    private fun startRecognition() {
        if (scanning || presetManager.allNames.isEmpty()) {
            if (presetManager.allNames.isEmpty()) {
                Toast.makeText(this, "请先添加图片", Toast.LENGTH_SHORT).show()
            }
            return
        }

        scanning = true
        tvScreenshotLabel.text = "识别中"

        FloatingWindowService.setStatus("scanning")

        val intent = mediaProjectionManager?.createScreenCaptureIntent()
        if (intent == null) {
            onScreenshotFailed("无法创建截屏请求")
            return
        }

        pendingScreenshotResult = { path ->
            if (path == null) {
                onScreenshotFailed("截屏失败")
                return@pendingScreenshotResult
            }

            Thread {
                var bestName: String? = null
                var bestScore = 0f
                var bestMatched = false

                for (name in presetManager.allNames) {
                    val templatePath = presetManager.cachePath(name) ?: continue
                    val result = TemplateMatcher.match(path, templatePath)
                    if (result.nccScore > bestScore) {
                        bestScore = result.nccScore
                        bestName = name
                        bestMatched = result.matched
                    }
                }

                val matched = bestMatched && bestName != null
                val templatePath = bestName?.let { presetManager.cachePath(it) }

                runOnUiThread {
                    if (matched && templatePath != null) {
                        FloatingWindowService.showImage(templatePath)
                        FloatingWindowService.setStatus("matched")
                    } else {
                        FloatingWindowService.hideImage()
                        FloatingWindowService.setStatus("no_match")
                    }

                    File(path).delete()
                    scanning = false
                    tvScreenshotLabel.text = "截屏识别"
                }
            }.start()
        }

        startActivityForResult(intent, REQUEST_SCREENSHOT)
    }

    private fun onScreenshotFailed(msg: String) {
        runOnUiThread {
            FloatingWindowService.setStatus("idle")
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            scanning = false
            tvScreenshotLabel.text = "截屏识别"
        }
    }

    private fun handlePickedImage(uri: Uri) {
        try {
            val input = contentResolver.openInputStream(uri) ?: return
            val tmpFile = File(cacheDir, "upload_${System.currentTimeMillis()}.tmp")
            tmpFile.outputStream().use { out -> input.copyTo(out) }
            input.close()

            val ok = presetManager.addImage(tmpFile)
            tmpFile.delete()

            Toast.makeText(this, if (ok) "已添加图片" else "添加失败", Toast.LENGTH_SHORT).show()
            updateUI()
        } catch (e: Exception) {
            Toast.makeText(this, "添加失败", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == REQUEST_SCREENSHOT) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                ScreenshotService.start(
                    context = this,
                    resultCode = resultCode,
                    data = data,
                    projectionManager = mediaProjectionManager!!,
                    callback = object : ScreenshotService.ScreenshotCallback {
                        override fun onSuccess(imagePath: String) {
                            pendingScreenshotResult?.invoke(imagePath)
                            pendingScreenshotResult = null
                        }

                        override fun onError(message: String) {
                            onScreenshotFailed(message)
                            pendingScreenshotResult = null
                        }
                    }
                )
            } else {
                onScreenshotFailed("用户取消了截屏权限")
                pendingScreenshotResult = null
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingActive) {
            stopService(Intent(this, FloatingWindowService::class.java))
        }
    }

    inner class ImageAdapter :
        RecyclerView.Adapter<ImageAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_image, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val name = presetManager.allNames[position]
            holder.tvName.text = name

            val thumbnail = presetManager.loadThumbnail(name)
            holder.ivThumb.setImageBitmap(thumbnail)

            val isDynamic = presetManager.isDynamic(name)
            holder.ivDelete.visibility = if (isDynamic) View.VISIBLE else View.GONE

            holder.ivDelete.setOnClickListener {
                presetManager.removeImage(name)
                updateUI()
            }
        }

        override fun getItemCount() = presetManager.allNames.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val ivThumb: ImageView = view.findViewById(R.id.iv_thumb)
            val tvName: TextView = view.findViewById(R.id.tv_name)
            val ivDelete: ImageView = view.findViewById(R.id.iv_delete)
        }
    }
}
