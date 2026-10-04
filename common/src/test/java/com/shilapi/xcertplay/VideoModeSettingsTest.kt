package com.shilapi.xcertplay

import android.content.Context
import android.view.KeyEvent
import com.shilapi.xcertplay.hud.BydOutputSettings
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29], manifest = Config.NONE)
class VideoModeSettingsTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private val keys = VideoModeKeys()

    @Before fun reset() {
        context.getSharedPreferences("diplay_video_mode", Context.MODE_PRIVATE).edit().clear().commit()
        BydOutputSettings.setVideoWhileParked(context, false)
    }

    @Test fun manualModeOffersVideoWithoutVehicleSupportAndKeepsAvailabilitySeparate() {
        assertFalse(VideoModeSettings.offered(context))
        assertFalse(keys.dispatch(context, event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F9)))
        VideoModeSettings.setManual(context, true)
        assertTrue(VideoModeSettings.offered(context))
        assertTrue(VideoModeSettings.allowed(context))
        press(KeyEvent.KEYCODE_F11)
        assertFalse(VideoModeSettings.allowed(context))
        assertTrue(VideoModeSettings.offered(context)) // Remains negotiated while unavailable.
        VideoModeSettings.setManual(context, false)
        assertFalse(VideoModeSettings.offered(context))
        VideoModeSettings.setManual(context, true)
        assertFalse(VideoModeSettings.allowed(context)) // Saved OFF survives re-enabling.
    }

    @Test fun manualAvailabilityReplacesGearAndNeverReadsVehicleData() {
        val unavailableVehicle: () -> Boolean? = { error("Manual mode must not query ADB") }
        VideoModeSettings.setManual(context, true)
        assertEquals(true, VideoModeSettings.playbackAllowed(context, unavailableVehicle))
        VideoModeSettings.setAllowed(context, false)
        assertEquals(false, VideoModeSettings.playbackAllowed(context, unavailableVehicle))
        VideoModeSettings.setManual(context, false)
        assertNull(VideoModeSettings.playbackAllowed(context) { null })
        assertEquals(true, VideoModeSettings.playbackAllowed(context) { true })
        assertEquals(false, VideoModeSettings.playbackAllowed(context) { false })
    }

    @Test fun toggleIgnoresRepeatsAndExplicitOnOffAreIdempotent() {
        VideoModeSettings.setManual(context, true)
        assertTrue(keys.dispatch(context, event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F9)))
        assertFalse(VideoModeSettings.allowed(context))
        assertTrue(keys.dispatch(context, event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F9, repeat = 1)))
        assertFalse(VideoModeSettings.allowed(context))
        assertTrue(keys.dispatch(context, event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F9)))
        press(KeyEvent.KEYCODE_F9)
        assertTrue(VideoModeSettings.allowed(context))
        repeat(2) { press(KeyEvent.KEYCODE_F11) }
        assertFalse(VideoModeSettings.allowed(context))
        repeat(2) { press(KeyEvent.KEYCODE_F10) }
        assertTrue(VideoModeSettings.allowed(context))
    }

    @Test fun recordedChordNormalizesModifiersAndConsumesReleaseAfterCtrlIsReleased() {
        VideoModeSettings.setManual(context, true)
        val chord = VideoModeSettings.Shortcut(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON)
        VideoModeSettings.setShortcut(context, VideoModeSettings.Action.TOGGLE, chord)
        assertFalse(keys.dispatch(context, event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V)))
        assertTrue(keys.dispatch(context, event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_LEFT_ON)))
        assertFalse(VideoModeSettings.allowed(context))
        assertTrue(keys.dispatch(context, event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_V)))
        assertEquals("Ctrl+" + KeyEvent.keyCodeToString(KeyEvent.KEYCODE_V).removePrefix("KEYCODE_"),
            VideoModeSettings.shortcut(context, VideoModeSettings.Action.TOGGLE).label())
    }

    @Test fun reassigningChordClearsPreviousOwnerAndClearDisablesAction() {
        VideoModeSettings.setManual(context, true)
        val chord = VideoModeSettings.Shortcut(KeyEvent.KEYCODE_F9)
        VideoModeSettings.setShortcut(context, VideoModeSettings.Action.OFF, chord)
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, VideoModeSettings.shortcut(context, VideoModeSettings.Action.TOGGLE).keyCode)
        repeat(2) { press(KeyEvent.KEYCODE_F9) }
        assertFalse(VideoModeSettings.allowed(context))
        VideoModeSettings.setShortcut(context, VideoModeSettings.Action.OFF, VideoModeSettings.Shortcut(KeyEvent.KEYCODE_UNKNOWN))
        assertFalse(keys.dispatch(context, event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F9)))
    }

    @Test fun unrelatedKeysAndKeysFromOtherDevicesAreNotSwallowed() {
        VideoModeSettings.setManual(context, true)
        assertFalse(keys.dispatch(context, event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_UP)))
        assertTrue(keys.dispatch(context, event(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_F9)))
        val otherDeviceRelease = KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F9, 0, 0, 42, 0)
        assertFalse(keys.dispatch(context, otherDeviceRelease))
        assertTrue(keys.dispatch(context, event(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_F9)))
    }

    private fun press(key: Int) {
        assertTrue(keys.dispatch(context, event(KeyEvent.ACTION_DOWN, key)))
        assertTrue(keys.dispatch(context, event(KeyEvent.ACTION_UP, key)))
    }

    private fun event(action: Int, key: Int, modifiers: Int = 0, repeat: Int = 0) =
        KeyEvent(0, 0, action, key, repeat, modifiers)
}
