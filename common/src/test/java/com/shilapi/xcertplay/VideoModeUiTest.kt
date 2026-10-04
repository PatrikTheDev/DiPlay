package com.shilapi.xcertplay

import android.app.AlertDialog
import android.content.Context
import android.os.Looper
import org.robolectric.Shadows.shadowOf
import android.view.KeyEvent
import android.widget.LinearLayout
import android.widget.Switch
import com.shilapi.xcertplay.host.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], qualifiers = "en", manifest = Config.NONE)
class VideoModeUiTest {
    private lateinit var activity: DiPlayActivity

    @Before fun setup() {
        RuntimeEnvironment.getApplication().getSharedPreferences("diplay_video_mode", Context.MODE_PRIVATE)
            .edit().clear().commit()
        CarPlayBackgroundSession.clear()
        activity = Robolectric.buildActivity(DiPlayActivity::class.java).get()
        activity.setTheme(android.R.style.Theme_Material_NoActionBar)
    }

    @Test fun manualOptionIsVisibleWithoutBydOrAdbAndAvailabilityCanBeChanged() {
        val container = LinearLayout(activity)
        DiPlayActivity::class.java.getDeclaredMethod("videoModeSettings", LinearLayout::class.java)
            .apply { isAccessible = true }.invoke(activity, container)
        val card = container.getChildAt(0) as LinearLayout
        val manual = (card.getChildAt(1) as LinearLayout).getChildAt(1) as Switch
        assertFalse(manual.isChecked)
        manual.isChecked = true
        assertTrue(VideoModeSettings.manual(activity))
        assertNull(org.robolectric.Shadows.shadowOf(activity).nextStartedActivity)
        val updated = LinearLayout(activity)
        DiPlayActivity::class.java.getDeclaredMethod("videoModeSettings", LinearLayout::class.java)
            .apply { isAccessible = true }.invoke(activity, updated)
        val allowed = ((updated.getChildAt(0) as LinearLayout).getChildAt(2) as LinearLayout).getChildAt(1) as Switch
        assertTrue(allowed.isChecked)
        allowed.isChecked = false
        assertFalse(VideoModeSettings.allowed(activity))
    }

    @Test fun shortcutRecorderWaitsForReleaseAndDoesNotToggleAvailability() {
        VideoModeSettings.setManual(activity, true)
        record(VideoModeSettings.Action.TOGGLE)
        val dialog = ShadowAlertDialog.getLatestAlertDialog()
        dialog.dispatchKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_V, 0, KeyEvent.META_CTRL_LEFT_ON))
        assertEquals(KeyEvent.KEYCODE_F9, VideoModeSettings.shortcut(activity, VideoModeSettings.Action.TOGGLE).keyCode)
        dialog.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_V))
        assertFalse(dialog.isShowing)
        assertEquals(VideoModeSettings.Shortcut(KeyEvent.KEYCODE_V, KeyEvent.META_CTRL_ON),
            VideoModeSettings.shortcut(activity, VideoModeSettings.Action.TOGGLE))
        assertTrue(VideoModeSettings.allowed(activity))
    }

    @Test fun cancelPreservesShortcutAndClearRemovesIt() {
        record(VideoModeSettings.Action.ON)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(KeyEvent.KEYCODE_F10, VideoModeSettings.shortcut(activity, VideoModeSettings.Action.ON).keyCode)
        record(VideoModeSettings.Action.ON)
        ShadowAlertDialog.getLatestAlertDialog().getButton(AlertDialog.BUTTON_NEUTRAL).performClick()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(KeyEvent.KEYCODE_UNKNOWN, VideoModeSettings.shortcut(activity, VideoModeSettings.Action.ON).keyCode)
    }

    private fun record(action: VideoModeSettings.Action) {
        DiPlayActivity::class.java.getDeclaredMethod("recordVideoShortcut", VideoModeSettings.Action::class.java, String::class.java)
            .apply { isAccessible = true }.invoke(activity, action, activity.getString(R.string.video_shortcut_toggle))
    }
}
