package com.monotrader.app

import android.animation.ValueAnimator
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.animation.LinearInterpolator
import androidx.core.app.NotificationCompat

/**
 * Foreground service that manages the monochrome overlay with anti-flash dampening.
 *
 * Two layers work together:
 *
 * 1. BASE OVERLAY — a static semi-transparent gray that desaturates MT5's colors.
 *    Higher opacity = more monochrome. This alone significantly mutes flashing.
 *
 * 2. DAMPENER — a second overlay on top that continuously oscillates its opacity
 *    in a slow sine wave. This creates a "visual gate" that breaks up rapid
 *    stroboscopic patterns from price ticks. The cycle time is configurable:
 *    - 200ms  = subtle smoothing (recommended for scalping)
 *    - 500ms  = noticeable calming effect (recommended for swing)
 *    - 1000ms = very slow breathing, maximum comfort
 *    - 0ms    = dampener disabled, base overlay only
 *
 * Both layers are FLAG_NOT_TOUCHABLE so all taps pass through to MT5.
 */
class OverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "monotrader_overlay"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "com.monotrader.app.STOP_OVERLAY"
        const val ACTION_TOGGLE = "com.monotrader.app.TOGGLE_OVERLAY"
        const val ACTION_UPDATE = "com.monotrader.app.UPDATE_OVERLAY"

        // Dampener oscillates between base opacity and base + this percentage
        const val DAMPEN_AMPLITUDE_PERCENT = 8

        fun start(context: Context) {
            context.startForegroundService(Intent(context, OverlayService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).apply { action = ACTION_STOP }
            )
        }

        fun update(context: Context) {
            context.startService(
                Intent(context, OverlayService::class.java).apply { action = ACTION_UPDATE }
            )
        }
    }

    private lateinit var windowManager: WindowManager
    private lateinit var prefs: PrefsManager

    // Base gray overlay
    private var baseOverlay: View? = null
    // Dampener overlay (oscillating opacity for anti-flash)
    private var dampenerOverlay: View? = null
    private var dampenerAnimator: ValueAnimator? = null

    private var isVisible = true

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = PrefsManager(this)
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                removeAllOverlays()
                prefs.isOverlayActive = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                sendBroadcast(Intent("com.monotrader.app.OVERLAY_STATE_CHANGED"))
                return START_NOT_STICKY
            }
            ACTION_TOGGLE -> {
                toggleOverlay()
                return START_STICKY
            }
            ACTION_UPDATE -> {
                showOverlay()
                return START_STICKY
            }
            else -> {
                showOverlay()
                prefs.isOverlayActive = true
                sendBroadcast(Intent("com.monotrader.app.OVERLAY_STATE_CHANGED"))
                return START_STICKY
            }
        }
    }

    // ── Overlay Creation ───────────────────────────────────────

    private fun showOverlay() {
        removeAllOverlays()

        val (overlayWidth, overlayX) = calculateOverlayBounds()
        val screenHeight = getScreenHeight()

        val baseAlpha = (prefs.opacity * 255 / 100).coerceIn(0, 217) // max 85%
        val color = prefs.overlayColor
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF

        // ── Layer 1: Base overlay (static gray) ──
        baseOverlay = View(this).apply {
            setBackgroundColor(Color.argb(baseAlpha, r, g, b))
        }
        windowManager.addView(baseOverlay, makeOverlayParams(overlayWidth, screenHeight, overlayX))

        // ── Layer 2: Dampener overlay (oscillating anti-flash) ──
        val antiFlashMs = prefs.antiFlashMs
        if (antiFlashMs > 0) {
            val dampAmplitude = (DAMPEN_AMPLITUDE_PERCENT * 255 / 100)
            // Dampener starts transparent and oscillates up to dampAmplitude
            dampenerOverlay = View(this).apply {
                setBackgroundColor(Color.argb(0, r, g, b))
            }
            windowManager.addView(dampenerOverlay, makeOverlayParams(overlayWidth, screenHeight, overlayX))

            // Continuous sine-wave animation
            dampenerAnimator?.cancel()
            dampenerAnimator = ValueAnimator.ofInt(0, dampAmplitude, 0).apply {
                duration = antiFlashMs.toLong()
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.RESTART
                interpolator = LinearInterpolator()
                addUpdateListener { anim ->
                    val alpha = anim.animatedValue as Int
                    dampenerOverlay?.setBackgroundColor(Color.argb(alpha, r, g, b))
                }
                start()
            }
        }

        // Apply blur effect on Android 12+ for extra smoothing
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && prefs.antiFlashMs > 0) {
            try {
                val blurRadius = (prefs.antiFlashMs / 200f).coerceIn(0.1f, 4f)
                val blurEffect = android.graphics.RenderEffect.createBlurEffect(
                    blurRadius, blurRadius,
                    android.graphics.Shader.TileMode.CLAMP
                )
                dampenerOverlay?.setRenderEffect(blurEffect)
            } catch (_: Exception) {
                // RenderEffect not supported on some devices, skip
            }
        }

        isVisible = true
    }

    private fun makeOverlayParams(width: Int, height: Int, x: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = 0
        }
    }

    private fun calculateOverlayBounds(): Pair<Int, Int> {
        val screenWidth = getScreenWidth()
        return when (prefs.position) {
            PrefsManager.POSITION_LEFT -> Pair(screenWidth / 2, 0)
            PrefsManager.POSITION_RIGHT -> Pair(screenWidth / 2, screenWidth / 2)
            PrefsManager.POSITION_FULL -> Pair(screenWidth, 0)
            else -> Pair(screenWidth / 2, 0)
        }
    }

    private fun getScreenWidth(): Int {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.widthPixels
    }

    private fun getScreenHeight(): Int {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }

    // ── Toggle & Remove ────────────────────────────────────────

    private fun toggleOverlay() {
        val newVisibility = if (isVisible) View.GONE else View.VISIBLE
        baseOverlay?.visibility = newVisibility
        dampenerOverlay?.visibility = newVisibility

        if (isVisible) {
            dampenerAnimator?.pause()
        } else {
            dampenerAnimator?.resume()
        }

        isVisible = !isVisible
        updateNotification()
    }

    private fun removeAllOverlays() {
        dampenerAnimator?.cancel()
        dampenerAnimator = null

        listOf(dampenerOverlay, baseOverlay).forEach { view ->
            view?.let {
                try { windowManager.removeView(it) } catch (_: IllegalArgumentException) {}
            }
        }
        dampenerOverlay = null
        baseOverlay = null
    }

    // ── Notification ───────────────────────────────────────────

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MonoTrader Overlay",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Filtre monochrome actif"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, OverlayService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val toggleIntent = PendingIntent.getService(
            this, 2,
            Intent(this, OverlayService::class.java).apply { action = ACTION_TOGGLE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dampenText = if (prefs.antiFlashMs > 0) " | Anti-flash ${prefs.antiFlashMs}ms" else ""
        val statusText = if (isVisible)
            "Filtre ${prefs.opacity}%$dampenText"
        else
            "En pause"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("MonoTrader")
            .setContentText(statusText)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                if (isVisible) "Pause" else "Reprendre",
                toggleIntent
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Arrêter",
                stopIntent
            )
            .build()
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification())
    }

    override fun onDestroy() {
        removeAllOverlays()
        super.onDestroy()
    }
}
