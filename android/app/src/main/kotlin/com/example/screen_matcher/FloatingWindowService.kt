package com.example.screen_matcher

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat

class FloatingWindowService : Service() {

    companion object {
        private const val CHANNEL_ID = "screen_matcher_floating"
        private const val NOTIFICATION_ID = 2001

        private val mainHandler = Handler(Looper.getMainLooper())

        private var instance: FloatingWindowService? = null
        private var imageView: ImageView? = null
        private var statusText: TextView? = null
        private var imageContainer: LinearLayout? = null
        var onStartRecognition: (() -> Unit)? = null

        fun showImage(path: String) {
            mainHandler.post {
                val bitmap = BitmapFactory.decodeFile(path)
                imageView?.setImageBitmap(bitmap)
                imageContainer?.visibility = View.VISIBLE
            }
        }

        fun hideImage() {
            mainHandler.post {
                imageContainer?.visibility = View.GONE
                statusText?.text = "未找到匹配"
            }
        }

        fun setStatus(text: String) {
            mainHandler.post {
                statusText?.text = when (text) {
                    "scanning" -> "正在识别中..."
                    "matched"   -> "已匹配"
                    "no_match"  -> "未找到匹配"
                    "idle"      -> "点击开始识别"
                    else        -> text
                }
            }
        }
    }

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ForegroundServiceType")
    override fun onCreate() {
        super.onCreate()
        instance = this

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingWindow()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "截图识别服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "截图识别悬浮窗服务运行中"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("截图识别")
            .setContentText("悬浮窗服务运行中")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWindow() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_window, null)

        statusText = floatingView?.findViewById(R.id.status_text)
        imageContainer = floatingView?.findViewById(R.id.image_container)
        imageView = floatingView?.findViewById(R.id.matched_image)

        floatingView?.findViewById<Button>(R.id.start_button)?.setOnClickListener {
            onStartRecognition?.invoke()
        }

        floatingView?.findViewById<Button>(R.id.close_button)?.setOnClickListener {
            hideImage()
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        floatingView?.findViewById<View>(R.id.drag_handle)?.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    val lp = floatingView?.layoutParams as WindowManager.LayoutParams
                    initialX = lp.x
                    initialY = lp.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    val lp = floatingView?.layoutParams as WindowManager.LayoutParams
                    lp.x = initialX + (event.rawX - initialTouchX).toInt()
                    lp.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, lp)
                    true
                }
                else -> false
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 100
            y = 200
        }

        windowManager?.addView(floatingView, params)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        floatingView?.let { windowManager?.removeView(it) }
    }
}
