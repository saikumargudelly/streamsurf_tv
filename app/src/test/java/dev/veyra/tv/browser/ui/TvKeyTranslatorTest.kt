package dev.veyra.tv.browser.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvKeyTranslatorTest {

    @Test
    fun testIsActionBack() {
        assertTrue(TvKeyTranslator.isActionBack(KeyEvent.KEYCODE_BACK))
        assertTrue(TvKeyTranslator.isActionBack(KeyEvent.KEYCODE_BUTTON_B))
        assertTrue(TvKeyTranslator.isActionBack(KeyEvent.KEYCODE_ESCAPE))

        assertFalse(TvKeyTranslator.isActionBack(KeyEvent.KEYCODE_DPAD_CENTER))
        assertFalse(TvKeyTranslator.isActionBack(KeyEvent.KEYCODE_ENTER))
        assertFalse(TvKeyTranslator.isActionBack(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test
    fun testIsActionConfirm() {
        assertTrue(TvKeyTranslator.isActionConfirm(KeyEvent.KEYCODE_DPAD_CENTER))
        assertTrue(TvKeyTranslator.isActionConfirm(KeyEvent.KEYCODE_ENTER))
        assertTrue(TvKeyTranslator.isActionConfirm(KeyEvent.KEYCODE_NUMPAD_ENTER))
        assertTrue(TvKeyTranslator.isActionConfirm(KeyEvent.KEYCODE_BUTTON_A))
        assertTrue(TvKeyTranslator.isActionConfirm(KeyEvent.KEYCODE_BUTTON_C))

        assertFalse(TvKeyTranslator.isActionConfirm(KeyEvent.KEYCODE_BACK))
        assertFalse(TvKeyTranslator.isActionConfirm(KeyEvent.KEYCODE_BUTTON_B))
        assertFalse(TvKeyTranslator.isActionConfirm(KeyEvent.KEYCODE_DPAD_LEFT))
    }

    @Test
    fun testTranslateButtonBToBack() {
        assertEquals(KeyEvent.KEYCODE_BACK, TvKeyTranslator.translateKeyCode(KeyEvent.KEYCODE_BUTTON_B))
    }

    @Test
    fun testTranslateEscapeToBack() {
        assertEquals(KeyEvent.KEYCODE_BACK, TvKeyTranslator.translateKeyCode(KeyEvent.KEYCODE_ESCAPE))
    }

    @Test
    fun testTranslateButtonAToDpadCenter() {
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, TvKeyTranslator.translateKeyCode(KeyEvent.KEYCODE_BUTTON_A))
    }

    @Test
    fun testTranslateButtonCToDpadCenter() {
        assertEquals(KeyEvent.KEYCODE_DPAD_CENTER, TvKeyTranslator.translateKeyCode(KeyEvent.KEYCODE_BUTTON_C))
    }

    @Test
    fun testTranslatePassThroughUnchanged() {
        assertEquals(KeyEvent.KEYCODE_DPAD_UP, TvKeyTranslator.translateKeyCode(KeyEvent.KEYCODE_DPAD_UP))
        assertEquals(KeyEvent.KEYCODE_ENTER, TvKeyTranslator.translateKeyCode(KeyEvent.KEYCODE_ENTER))
        assertEquals(KeyEvent.KEYCODE_DPAD_DOWN, TvKeyTranslator.translateKeyCode(KeyEvent.KEYCODE_DPAD_DOWN))
    }
}

