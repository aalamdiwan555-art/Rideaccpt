package com.drclicker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

/**
 * FloatingOverlayService
 *
 * Draws a compact, draggable status widget over all other apps using
 * TYPE_APPLICATION_OVERLAY.  The surrounding transparent space is fully
 * pass-through so the driver can still interact with the Rapido Captain map.
 */
class FloatingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private lateinit var prefs: SharedPreferences

    // ─── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()
        startForeground(NOTIF_ID, buildNotification())
        addOverlayView()

        // Signal the engine that it should start processing
        AutoAcceptEngineService.instance?.setEngineActive(true)
    }

    override fun onDestroy() {
        AutoAcceptEngineService.instance?.setEngineActive(false)
        removeOverlayView()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ─── Overlay View ─────────────────────────────────────────────────────────

    private fun addOverlayView() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_layout, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // FLAG_NOT_FOCUSABLE    → widget never steals keyboard/IME focus
            // FLAG_NOT_TOUCH_MODAL  → touches outside the widget pass through to underlying app
            // FLAG_LAYOUT_IN_SCREEN → widget can be placed over status bar region
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 16
            y = 200
        }

        // ── Drag to reposition ──────────────────────────────────────────────
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (initialTouchX - event.rawX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }
                else -> {
                    v.performClick()
                    false
                }
            }
        }

        windowManager.addView(view, params)
        overlayView = view

        // Sync initial accepted count
        updateOverlayStats()
    }

    private fun removeOverlayView() {
        overlayView?.let {
            if (it.isAttachedToWindow) {
                windowManager.removeView(it)
            }
            overlayView = null
        }
    }

    // ─── Public API (called from AutoAcceptEngineService) ─────────────────────

    /**
     * Update the overlay widget's accepted count and last log line.
     * Safe to call from any thread.
     */
    fun updateOverlayStats() {
        overlayView?.post {
            val count = prefs.getInt(MainActivity.KEY_STAT_ACCEPTED, 0)
            overlayView?.findViewById<TextView>(R.id.tvOverlayAccepted)?.text = "Accepted: $count"
        }
    }

    // ─── Notification (required for foreground service on API 26+) ───────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dr. Clicker Engine",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Dr. Clicker overlay and auto-accept engine status"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Dr. Clicker — Active")
            .setContentText("Monitoring Rapido Captain for ride requests")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    // ─── Constants ────────────────────────────────────────────────────────────

    companion object {
        private const val NOTIF_ID = 1001
        private const val CHANNEL_ID = "dr_clicker_overlay"
    }
}
