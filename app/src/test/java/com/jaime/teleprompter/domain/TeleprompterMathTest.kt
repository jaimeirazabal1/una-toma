package com.jaime.teleprompter.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TeleprompterMathTest {

    @Test
    fun `advance moves proportionally to elapsed time`() {
        val result = TeleprompterMath.advance(0f, speedPxPerSec = 100f, dtNanos = 500_000_000L, maxOffsetPx = 1000f)
        assertEquals(50f, result, 0.001f)
    }

    @Test
    fun `advance never exceeds the max offset`() {
        val result = TeleprompterMath.advance(990f, 100f, 1_000_000_000L, 1000f)
        assertEquals(1000f, result, 0.001f)
    }

    @Test
    fun `clamp prevents scrolling above the start`() {
        assertEquals(0f, TeleprompterMath.clamp(-40f, 500f), 0f)
    }

    @Test
    fun `max offset is zero when content fits in viewport`() {
        assertEquals(0f, TeleprompterMath.maxOffset(300f, 800f), 0f)
    }

    @Test
    fun `isAtEnd is false for content that fits and true at the bottom`() {
        assertFalse(TeleprompterMath.isAtEnd(0f, 0f))
        assertTrue(TeleprompterMath.isAtEnd(500f, 500f))
        assertFalse(TeleprompterMath.isAtEnd(499f, 500f))
    }
}
