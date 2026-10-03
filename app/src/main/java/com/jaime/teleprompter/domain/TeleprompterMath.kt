package com.jaime.teleprompter.domain

/**
 * Lógica pura del scroll del teleprompter (sin dependencias de Android ni de Compose)
 * para poder testearla en la JVM.
 */
object TeleprompterMath {

    /** Posición donde debe estar la línea de lectura, como fracción de la altura visible. */
    const val READING_LINE_FRACTION = 0.3f

    fun maxOffset(contentHeightPx: Float, viewportHeightPx: Float): Float =
        (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)

    fun clamp(offsetPx: Float, maxOffsetPx: Float): Float = offsetPx.coerceIn(0f, maxOffsetPx)

    fun advance(offsetPx: Float, speedPxPerSec: Float, dtNanos: Long, maxOffsetPx: Float): Float {
        val delta = speedPxPerSec * (dtNanos / NANOS_PER_SECOND)
        return clamp(offsetPx + delta, maxOffsetPx)
    }

    fun isAtEnd(offsetPx: Float, maxOffsetPx: Float): Boolean =
        maxOffsetPx > 0f && offsetPx >= maxOffsetPx

    private const val NANOS_PER_SECOND = 1_000_000_000f
}
