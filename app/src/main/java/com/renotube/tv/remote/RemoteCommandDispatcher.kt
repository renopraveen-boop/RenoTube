package com.renotube.tv.remote

import android.view.KeyEvent
import com.renotube.tv.MainActivity
import java.lang.ref.WeakReference

object RemoteCommandDispatcher {
    private var activityRef: WeakReference<MainActivity>? = null

    fun registerActivity(activity: MainActivity) {
        activityRef = WeakReference(activity)
    }

    fun unregisterActivity(activity: MainActivity) {
        if (activityRef?.get() == activity) {
            activityRef = null
        }
    }

    fun dispatchKey(command: String): Boolean {
        val activity = activityRef?.get() ?: return false
        val cmd = command.uppercase()

        // Check if activity can handle as a direct playback/settings command
        if (activity.onRemoteAction(cmd)) {
            return true
        }

        val keyCode = when (cmd) {
            "UP", "DPAD_UP" -> KeyEvent.KEYCODE_DPAD_UP
            "DOWN", "DPAD_DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
            "LEFT", "DPAD_LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
            "RIGHT", "DPAD_RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "OK", "ENTER", "CENTER" -> KeyEvent.KEYCODE_DPAD_CENTER
            "MENU", "CONTEXT_MENU" -> KeyEvent.KEYCODE_MENU
            "LONG_OK", "LONG_CENTER" -> {
                activity.runOnUiThread {
                    val downTime = android.os.SystemClock.uptimeMillis()
                    activity.dispatchKeyEvent(KeyEvent(downTime, downTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val repeatTime = android.os.SystemClock.uptimeMillis()
                        activity.dispatchKeyEvent(KeyEvent(downTime, repeatTime, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DPAD_CENTER, 1))
                    }, 300)
                    android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                        val upTime = android.os.SystemClock.uptimeMillis()
                        activity.dispatchKeyEvent(KeyEvent(downTime, upTime, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_DPAD_CENTER, 0))
                    }, 600)
                }
                return true
            }
            "BACK" -> KeyEvent.KEYCODE_BACK
            "HOME" -> {
                activity.runOnUiThread {
                    activity.onRemoteHome()
                }
                return true
            }
            "PLAY_PAUSE", "PLAY", "PAUSE" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "REWIND" -> KeyEvent.KEYCODE_MEDIA_REWIND
            "FAST_FORWARD" -> KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
            "VOLUME_UP", "VOL_UP" -> KeyEvent.KEYCODE_VOLUME_UP
            "VOLUME_DOWN", "VOL_DOWN" -> KeyEvent.KEYCODE_VOLUME_DOWN
            "VOLUME_MUTE", "MUTE" -> KeyEvent.KEYCODE_VOLUME_MUTE
            "NEXT", "NEXT_VIDEO" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "PREVIOUS", "PREV_VIDEO" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            else -> return false
        }

        activity.runOnUiThread {
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
            activity.dispatchKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
        }
        return true
    }

    fun dispatchSearch(query: String) {
        val activity = activityRef?.get() ?: return
        activity.runOnUiThread {
            activity.onRemoteSearch(query)
        }
    }

    fun dispatchPlay(videoId: String) {
        val activity = activityRef?.get() ?: return
        activity.runOnUiThread {
            activity.onRemotePlay(videoId)
        }
    }

    fun getPlayerStatusJson(): String {
        return activityRef?.get()?.getPlayerStatusJson() ?: "{\"active\":false}"
    }

    fun getCurrentPositionMs(): Long {
        return activityRef?.get()?.getCurrentPositionMs() ?: 0L
    }

    fun seekToMs(ms: Long): Boolean {
        val activity = activityRef?.get() ?: return false
        activity.seekToMs(ms)
        return true
    }

    fun pausePlayback(): Boolean {
        val activity = activityRef?.get() ?: return false
        activity.pausePlayback()
        return true
    }

    fun resumePlayback(): Boolean {
        val activity = activityRef?.get() ?: return false
        activity.resumePlayback()
        return true
    }
}
