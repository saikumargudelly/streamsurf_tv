package dev.veyra.tv.browser.ui

import android.view.KeyEvent

/**
 * TV Remote & Gamepad Key Normalization Helper.
 * Adapts SmartTube's GlobalKeyTranslator pattern to eliminate hardware remote and controller quirks:
 * - BUTTON_B (gamepad / Shield remote B button) -> KEYCODE_BACK
 * - ESCAPE (USB air mice, mini-keyboards) -> KEYCODE_BACK
 * - BUTTON_A (gamepad A / select button) -> KEYCODE_DPAD_CENTER
 * - BUTTON_C (Switch Pro / generic controllers) -> KEYCODE_DPAD_CENTER
 */
object TvKeyTranslator {

    fun translateKeyCode(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_B,
            KeyEvent.KEYCODE_ESCAPE -> KeyEvent.KEYCODE_BACK

            KeyEvent.KEYCODE_BUTTON_A,
            KeyEvent.KEYCODE_BUTTON_C -> KeyEvent.KEYCODE_DPAD_CENTER

            else -> keyCode
        }
    }

    fun translate(event: KeyEvent): KeyEvent {
        val targetCode = translateKeyCode(event.keyCode)
        if (targetCode == event.keyCode) return event

        return KeyEvent(
            event.downTime,
            event.eventTime,
            event.action,
            targetCode,
            event.repeatCount,
            event.metaState,
            event.deviceId,
            event.scanCode,
            event.flags,
            event.source
        )
    }

    fun isActionBack(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_BACK ||
                keyCode == KeyEvent.KEYCODE_BUTTON_B ||
                keyCode == KeyEvent.KEYCODE_ESCAPE
    }

    fun isActionConfirm(keyCode: Int): Boolean {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER ||
                keyCode == KeyEvent.KEYCODE_ENTER ||
                keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                keyCode == KeyEvent.KEYCODE_BUTTON_A ||
                keyCode == KeyEvent.KEYCODE_BUTTON_C
    }
}
