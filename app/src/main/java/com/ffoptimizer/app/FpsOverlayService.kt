package com.ffoptimizer.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Choreographer
import android.view.Gravity
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class FpsOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: android.view.View? = null
    private lateinit var tvFps: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var frameCount = 0
    private var lastTime = System.nanoTime()

    private val choreographer = Choreographer.getInstance()

    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            frameCount++
            val currentTime = System.nanoTime()
            val elapsed = currentTime - lastTime

            if (elapsed >= 1_000_000_000L) {
                val fps = (frameCount * 1_000_000_000L / elapsed).toInt()
                updateFpsDisplay(fps)
                frameCount = 0
                lastTime = currentTime
            }

            choreographer.postFrameCallback(this)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(1, buildNotification())
        createOverlay()
        choreographer.postFrameCallback(frameCallback)
    }

    private fun createOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val inflater = LayoutInflater.from(this)
        overlayView = inflater.inflate(R.layout.overlay_fps, null)
        tvFps = overlayView!!.findViewById(R.id.tv_fps_overlay)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 16
            y = 120
        }

        windowManager.addView(overlayView, params)

        // Drag support
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        overlayView!!.setOnTouchListener { _, event ->
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                android.view.MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun updateFpsDisplay(fps: Int) {
        handler.post {
            tvFps.text = "$fps FPS"
            tvFps.setTextColor(when {
                fps >= 90 -> Color.parseColor("#00FF88")
                fps >= 60 -> Color.parseColor("#FFD600")
                fps >= 30 -> Color.parseColor("#FF6B00")
                else -> Color.parseColor("#FF1744")
            })
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "fps_overlay",
            "FPS Monitor",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return NotificationCompat.Builder(this, "fps_overlay")
            .setContentTitle("FF Optimizer")
            .setContentText("Monitor de FPS ativo")
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        choreographer.removeFrameCallback(frameCallback)
        overlayView?.let {
            windowManager.removeView(it)
            overlayView = null
        }
    }
}
