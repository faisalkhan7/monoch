package com.monotrader.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages user preferences for the overlay filter.
 */
class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("monotrader_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_OPACITY = "opacity"
        private const val KEY_POSITION = "position"
        private const val KEY_ANTI_FLASH_MS = "anti_flash_ms"
        private const val KEY_OVERLAY_ACTIVE = "overlay_active"
        private const val KEY_OVERLAY_COLOR = "overlay_color"
        private const val KEY_DAMPEN_STRENGTH = "dampen_strength"

        // Position constants
        const val POSITION_LEFT = 0
        const val POSITION_RIGHT = 1
        const val POSITION_FULL = 2

        // Defaults
        const val DEFAULT_OPACITY = 45          // 45% opacity — good balance
        const val DEFAULT_POSITION = POSITION_LEFT
        const val DEFAULT_ANTI_FLASH_MS = 200   // 200ms smoothing
        const val DEFAULT_COLOR = 0xFF808080.toInt() // Neutral gray
    }

    var opacity: Int
        get() = prefs.getInt(KEY_OPACITY, DEFAULT_OPACITY)
        set(value) = prefs.edit().putInt(KEY_OPACITY, value.coerceIn(0, 85)).apply()

    var position: Int
        get() = prefs.getInt(KEY_POSITION, DEFAULT_POSITION)
        set(value) = prefs.edit().putInt(KEY_POSITION, value).apply()

    var antiFlashMs: Int
        get() = prefs.getInt(KEY_ANTI_FLASH_MS, DEFAULT_ANTI_FLASH_MS)
        set(value) = prefs.edit().putInt(KEY_ANTI_FLASH_MS, value.coerceIn(0, 1000)).apply()

    var isOverlayActive: Boolean
        get() = prefs.getBoolean(KEY_OVERLAY_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_OVERLAY_ACTIVE, value).apply()

    var overlayColor: Int
        get() = prefs.getInt(KEY_OVERLAY_COLOR, DEFAULT_COLOR)
        set(value) = prefs.edit().putInt(KEY_OVERLAY_COLOR, value).apply()

    /** Dampener strength: 0 = off, 1 = light, 2 = medium (default), 3 = strong */
    var dampenStrength: Int
        get() = prefs.getInt(KEY_DAMPEN_STRENGTH, 2)
        set(value) = prefs.edit().putInt(KEY_DAMPEN_STRENGTH, value.coerceIn(0, 3)).apply()
}
