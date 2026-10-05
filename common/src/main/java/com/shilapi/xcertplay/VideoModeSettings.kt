package com.shilapi.xcertplay

import android.content.Context
import android.view.KeyEvent
import com.shilapi.xcertplay.hud.BydOutputSettings

/** Video availability on receivers that have no vehicle gear source. */
internal object VideoModeSettings {
    private const val VEHICLE_GEAR_OBSERVED = "vehicle_gear_observed"
    private fun prefs(context: Context) = context.getSharedPreferences("diplay_video_mode", Context.MODE_PRIVATE)

    fun manual(context: Context): Boolean = prefs(context).getBoolean("manual", false)
    fun setManual(context: Context, enabled: Boolean) = prefs(context).edit().putBoolean("manual", enabled).apply()
    fun allowed(context: Context): Boolean = prefs(context).getBoolean("allowed", true)
    fun setAllowed(context: Context, allowed: Boolean) = prefs(context).edit().putBoolean("allowed", allowed).apply()
    fun offered(context: Context): Boolean = manual(context) || BydOutputSettings.videoWhileParkedActive(context)

    fun playbackAllowed(context: Context, readVehicleParked: () -> Boolean?): Boolean? {
        // Manual availability cannot override an actual vehicle reading. Remember observed gear
        // across reconnects/restarts so losing that source never turns a known vehicle into a phone.
        val vehicleParked = readVehicleParked()
        val settings = prefs(context)
        if (vehicleParked != null && !settings.getBoolean(VEHICLE_GEAR_OBSERVED, false)) {
            // This runs on the existing blocking gear-poll worker; persist the first observation
            // synchronously so a following process restart cannot outrun an asynchronous write.
            settings.edit().putBoolean(VEHICLE_GEAR_OBSERVED, true).commit()
        }
        if (!manual(context)) return vehicleParked
        if (!allowed(context) || vehicleParked == false) return false
        return vehicleParked == true || !settings.getBoolean(VEHICLE_GEAR_OBSERVED, false)
    }

    enum class Action(val defaultKey: Int) {
        TOGGLE(KeyEvent.KEYCODE_F9), ON(KeyEvent.KEYCODE_F10), OFF(KeyEvent.KEYCODE_F11)
    }

    data class Shortcut(val keyCode: Int, val modifiers: Int = 0) {
        fun matches(event: KeyEvent): Boolean = keyCode != KeyEvent.KEYCODE_UNKNOWN &&
            event.keyCode == keyCode && modifiers == modifiers(event)

        fun label(): String = if (keyCode == KeyEvent.KEYCODE_UNKNOWN) "" else buildString {
            if (modifiers and KeyEvent.META_CTRL_ON != 0) append("Ctrl+")
            if (modifiers and KeyEvent.META_ALT_ON != 0) append("Alt+")
            if (modifiers and KeyEvent.META_SHIFT_ON != 0) append("Shift+")
            if (modifiers and KeyEvent.META_META_ON != 0) append("Meta+")
            append(KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_"))
        }
    }

    fun shortcut(context: Context, action: Action): Shortcut {
        val prefs = prefs(context)
        return Shortcut(prefs.getInt("${action.name}_key", action.defaultKey), prefs.getInt("${action.name}_modifiers", 0))
    }

    /** A chord has one owner, so explicit ON/OFF can never accidentally act as a toggle. */
    fun setShortcut(context: Context, action: Action, shortcut: Shortcut) {
        val edit = prefs(context).edit()
        if (shortcut.keyCode != KeyEvent.KEYCODE_UNKNOWN) {
            Action.entries.filter { it != action && shortcut(context, it) == shortcut }.forEach {
                edit.putInt("${it.name}_key", KeyEvent.KEYCODE_UNKNOWN).putInt("${it.name}_modifiers", 0)
            }
        }
        edit.putInt("${action.name}_key", shortcut.keyCode).putInt("${action.name}_modifiers", shortcut.modifiers).apply()
    }

    fun modifiers(event: KeyEvent): Int = KeyEvent.normalizeMetaState(event.metaState) and
        (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON or KeyEvent.META_SHIFT_ON or KeyEvent.META_META_ON)

    fun apply(context: Context, action: Action) = setAllowed(context, when (action) {
        Action.TOGGLE -> !allowed(context)
        Action.ON -> true
        Action.OFF -> false
    })
}

/** One action per press; consumes repeats and releases before they can reach CarPlay navigation. */
internal class VideoModeKeys {
    private val pressed = mutableSetOf<Pair<Int, Int>>()

    fun dispatch(context: Context, event: KeyEvent): Boolean {
        val key = event.deviceId to event.keyCode
        if (event.action == KeyEvent.ACTION_UP) return pressed.remove(key)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (key in pressed) return true
        if (!VideoModeSettings.manual(context)) return false
        val action = VideoModeSettings.Action.entries.firstOrNull {
            VideoModeSettings.shortcut(context, it).matches(event)
        } ?: return false
        pressed += key
        if (event.repeatCount == 0) VideoModeSettings.apply(context, action)
        return true
    }

    fun clear() = pressed.clear()
}
