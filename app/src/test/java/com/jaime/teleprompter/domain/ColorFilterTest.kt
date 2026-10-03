package com.jaime.teleprompter.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Test

class ColorFilterTest {

    private val skin = floatArrayOf(0.88f, 0.68f, 0.55f)

    @Test
    fun `identity leaves colors unchanged`() {
        assertArrayEquals(skin, ColorAffine.IDENTITY.apply(skin[0], skin[1], skin[2]), 1e-6f)
    }

    @Test
    fun `original filter and zero intensity are identity`() {
        assertTrue(FilterSelection(VideoFilter.ORIGINAL, 1f).isIdentity)
        assertTrue(FilterSelection(VideoFilter.VIVID, 0f).isIdentity)
        assertFalse(FilterSelection(VideoFilter.VIVID, 0.5f).isIdentity)
    }

    @Test
    fun `black and white produces equal channels`() {
        val out = FilterSelection(VideoFilter.BLACK_AND_WHITE).affine().apply(skin[0], skin[1], skin[2])
        assertEquals(out[0], out[1], 1e-5f)
        assertEquals(out[1], out[2], 1e-5f)
    }

    @Test
    fun `intensity blends linearly between original and filter`() {
        val full = FilterSelection(VideoFilter.WARM, 1f).affine().apply(skin[0], skin[1], skin[2])
        val half = FilterSelection(VideoFilter.WARM, 0.5f).affine().apply(skin[0], skin[1], skin[2])
        for (i in 0 until 3) assertEquals((skin[i] + full[i]) / 2f, half[i], 1e-5f)
    }

    @Test
    fun `then applies this first and next second`() {
        val gain = ColorAffine.gains(2f, 2f, 2f)
        val shift = ColorAffine.brightness(0.1f)
        assertEquals(0.5f * 2f + 0.1f, gain.then(shift).apply(0.5f, 0.5f, 0.5f)[0], 1e-6f)
        assertEquals((0.5f + 0.1f) * 2f, shift.then(gain).apply(0.5f, 0.5f, 0.5f)[0], 1e-6f)
    }

    @Test
    fun `media3 matrix carries offset in alpha column and matches apply`() {
        val affine = FilterSelection(VideoFilter.GLOW).affine()
        val m = affine.toMedia3Matrix()
        val input = floatArrayOf(skin[0], skin[1], skin[2], 1f)
        val expected = affine.apply(skin[0], skin[1], skin[2])
        for (row in 0 until 3) {
            var out = 0f
            for (col in 0 until 4) out += m[col * 4 + row] * input[col]
            assertEquals(expected[row], out, 1e-5f)
        }
    }

    @Test
    fun `android color matrix scales offsets to 0-255`() {
        val m = ColorAffine.brightness(0.1f).toAndroidColorMatrix()
        assertEquals(25.5f, m[4], 1e-4f)
        assertEquals(1f, m[0], 0f)
    }

    @Test
    fun `timeline returns the filter active at each time`() {
        val builder = FilterTimeline.Builder(FilterSelection(VideoFilter.ORIGINAL))
        builder.add(2_000, FilterSelection(VideoFilter.ROSE))
        builder.add(5_000, FilterSelection(VideoFilter.COOL, 0.6f))
        val timeline = builder.build()
        assertEquals(VideoFilter.ORIGINAL, timeline.selectionAt(0).filter)
        assertEquals(VideoFilter.ORIGINAL, timeline.selectionAt(1_999).filter)
        assertEquals(VideoFilter.ROSE, timeline.selectionAt(2_000).filter)
        assertEquals(VideoFilter.COOL, timeline.selectionAt(9_000).filter)
        assertFalse(timeline.isIdentity)
        assertTrue(FilterTimeline.NONE.isIdentity)
    }
}

class FilterTimelineTest {

    @Test
    fun `smoothing alone makes the selection non identity but keeps color identity`() {
        val s = FilterSelection(VideoFilter.ORIGINAL, 1f, smoothing = 0.5f)
        assertTrue(s.colorIsIdentity)
        assertFalse(s.isIdentity)
    }

    @Test
    fun `timeline reports color and smoothing separately`() {
        val onlySmooth = FilterTimeline.Builder(FilterSelection(smoothing = 0.4f)).build()
        assertFalse(onlySmooth.hasColor)
        assertTrue(onlySmooth.hasSmoothing)
        assertFalse(onlySmooth.isIdentity)
    }

    @Test
    fun `rapid changes are throttled and keep the latest value`() {
        val b = FilterTimeline.Builder(FilterSelection())
        b.add(1_000, FilterSelection(VideoFilter.ROSE, 0.1f))
        for (i in 1..50) b.add(1_000L + i * 10, FilterSelection(VideoFilter.ROSE, 0.1f + i * 0.01f))
        val timeline = b.build()
        // 51 cambios en 500 ms: como máximo uno cada 200 ms (más la entrada inicial).
        assertTrue(timeline.entries.size <= 5)
        assertEquals(0.6f, timeline.selectionAt(5_000).intensity, 1e-5f)
    }

    @Test
    fun `entries are capped`() {
        val b = FilterTimeline.Builder(FilterSelection())
        for (i in 1..1_000) b.add(i * 500L, FilterSelection(VideoFilter.VIVID, (i % 10) / 10f))
        assertTrue(b.build().entries.size <= FilterTimeline.MAX_ENTRIES)
    }

    @Test
    fun `pack and unpack round trip`() {
        val b = FilterTimeline.Builder(FilterSelection(VideoFilter.GLOW, 0.8f, 0.3f))
        b.add(2_000, FilterSelection(VideoFilter.BLACK_AND_WHITE, 1f, 0f))
        val original = b.build()
        val copy = FilterTimeline.unpack(original.pack())
        assertEquals(original.entries, copy.entries)
    }
}

class HorizontalFrameTest {

    @Test
    fun `portrait 9x16 frame gives the 81 over 256 band`() {
        assertEquals(HorizontalFrame.PORTRAIT_BAND_FRACTION, HorizontalFrame.bandFraction(2160, 3840), 1e-5f)
    }

    @Test
    fun `centered band is symmetric in ndc`() {
        val (bottom, top) = HorizontalFrame.cropNdc(0.5f, HorizontalFrame.PORTRAIT_BAND_FRACTION)
        assertEquals(-bottom, top, 1e-5f)
        assertEquals(HorizontalFrame.PORTRAIT_BAND_FRACTION * 2f, top - bottom, 1e-5f)
    }

    @Test
    fun `band is clamped inside the frame`() {
        val f = HorizontalFrame.PORTRAIT_BAND_FRACTION
        val (_, topAtTop) = HorizontalFrame.cropNdc(-5f, f)
        val (bottomAtBottom, _) = HorizontalFrame.cropNdc(5f, f)
        assertEquals(1f, topAtTop, 1e-5f)
        assertEquals(-1f, bottomAtBottom, 1e-5f)
    }

    @Test
    fun `moving the center down lowers the crop window`() {
        val f = HorizontalFrame.PORTRAIT_BAND_FRACTION
        val (_, topLow) = HorizontalFrame.cropNdc(0.7f, f)
        val (_, topMid) = HorizontalFrame.cropNdc(0.5f, f)
        assertTrue(topLow < topMid)
    }
}
