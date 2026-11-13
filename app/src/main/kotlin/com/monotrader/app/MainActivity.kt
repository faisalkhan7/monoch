package com.monotrader.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat

/**
 * Main settings screen for MonoTrader.
 *
 * Provides controls for:
 * - Overlay toggle (on/off)
 * - Opacity level (0–85%)
 * - Anti-flash smoothing duration (0–1000ms)
 * - Overlay position (left half, right half, full screen)
 */
class MainActivity : AppCompatActivity() {

    private lateinit var prefs: PrefsManager

    private lateinit var btnToggle: Button
    private lateinit var seekOpacity: SeekBar
    private lateinit var tvOpacity: TextView
    private lateinit var seekAntiFlash: SeekBar
    private lateinit var tvAntiFlash: TextView
    private lateinit var radioGroup: RadioGroup

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            updateToggleButton()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = PrefsManager(this)

        // Bind views
        btnToggle = findViewById(R.id.btn_toggle)
        seekOpacity = findViewById(R.id.seek_opacity)
        tvOpacity = findViewById(R.id.tv_opacity_value)
        seekAntiFlash = findViewById(R.id.seek_anti_flash)
        tvAntiFlash = findViewById(R.id.tv_anti_flash_value)
        radioGroup = findViewById(R.id.radio_position)

        setupToggleButton()
        setupOpacitySlider()
        setupAntiFlashSlider()
        setupAntiFlashPresets()
        setupPositionSelector()
        setupDisplaySettings()

        // Listen for state changes from the service
        val filter = IntentFilter("com.monotrader.app.OVERLAY_STATE_CHANGED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(stateReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(stateReceiver, filter)
        }
    }

    override fun onDestroy() {
        unregisterReceiver(stateReceiver)
        super.onDestroy()
    }

    override fun onResume() {
        super.onResume()
        updateToggleButton()
    }

    // ── Toggle Button ──────────────────────────────────────────

    private fun setupToggleButton() {
        updateToggleButton()
        btnToggle.setOnClickListener {
            if (!checkOverlayPermission()) return@setOnClickListener
            if (!checkNotificationPermission()) return@setOnClickListener

            if (prefs.isOverlayActive) {
                OverlayService.stop(this)
            } else {
                OverlayService.start(this)
            }
        }
    }

    private fun updateToggleButton() {
        if (prefs.isOverlayActive) {
            btnToggle.text = "⏹  Désactiver le filtre"
            btnToggle.setBackgroundColor(0xFFD32F2F.toInt())
        } else {
            btnToggle.text = "▶  Activer le filtre"
            btnToggle.setBackgroundColor(0xFF388E3C.toInt())
        }
        btnToggle.setTextColor(0xFFFFFFFF.toInt())
    }

    // ── Opacity Slider ─────────────────────────────────────────

    private fun setupOpacitySlider() {
        seekOpacity.max = 85
        seekOpacity.progress = prefs.opacity
        tvOpacity.text = "${prefs.opacity}%"

        seekOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvOpacity.text = "$progress%"
                if (fromUser) {
                    prefs.opacity = progress
                    if (prefs.isOverlayActive) {
                        OverlayService.update(this@MainActivity)
                    }
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })
    }

    // ── Anti-Flash Slider ──────────────────────────────────────

    private fun setupAntiFlashSlider() {
        seekAntiFlash.max = 1000
        seekAntiFlash.progress = prefs.antiFlashMs
        tvAntiFlash.text = "${prefs.antiFlashMs}ms"

        seekAntiFlash.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                tvAntiFlash.text = "${progress}ms"
                if (fromUser) {
                    prefs.antiFlashMs = progress
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                // Apply on release for smoother UX
                if (prefs.isOverlayActive) {
                    OverlayService.update(this@MainActivity)
                }
            }
        })
    }

    // ── Anti-Flash Presets ─────────────────────────────────────

    private fun setupAntiFlashPresets() {
        findViewById<Button>(R.id.btn_preset_off).setOnClickListener {
            setAntiFlash(0)
        }
        findViewById<Button>(R.id.btn_preset_scalp).setOnClickListener {
            setAntiFlash(200)
        }
        findViewById<Button>(R.id.btn_preset_swing).setOnClickListener {
            setAntiFlash(500)
        }
        findViewById<Button>(R.id.btn_preset_zen).setOnClickListener {
            setAntiFlash(1000)
        }
    }

    private fun setAntiFlash(ms: Int) {
        prefs.antiFlashMs = ms
        seekAntiFlash.progress = ms
        tvAntiFlash.text = "${ms}ms"
        if (prefs.isOverlayActive) {
            OverlayService.update(this)
        }
    }

    // ── Display Settings (refresh rate) ───────────────────────

    private fun setupDisplaySettings() {
        findViewById<Button>(R.id.btn_open_display_settings).setOnClickListener {
            try {
                // Try Xiaomi-specific display settings first
                val intent = Intent(Settings.ACTION_DISPLAY_SETTINGS)
                startActivity(intent)
            } catch (_: Exception) {
                // Fallback to general settings
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
        }
    }

    // ── Position Selector ──────────────────────────────────────

    private fun setupPositionSelector() {
        when (prefs.position) {
            PrefsManager.POSITION_LEFT -> findViewById<RadioButton>(R.id.radio_left).isChecked = true
            PrefsManager.POSITION_RIGHT -> findViewById<RadioButton>(R.id.radio_right).isChecked = true
            PrefsManager.POSITION_FULL -> findViewById<RadioButton>(R.id.radio_full).isChecked = true
        }

        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            prefs.position = when (checkedId) {
                R.id.radio_left -> PrefsManager.POSITION_LEFT
                R.id.radio_right -> PrefsManager.POSITION_RIGHT
                R.id.radio_full -> PrefsManager.POSITION_FULL
                else -> PrefsManager.POSITION_LEFT
            }
            if (prefs.isOverlayActive) {
                OverlayService.update(this)
            }
        }
    }

    // ── Permissions ────────────────────────────────────────────

    private fun checkOverlayPermission(): Boolean {
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
            return false
        }
        return true
    }

    private fun checkNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                requestPermissions(
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
                return false
            }
        }
        return true
    }
}
