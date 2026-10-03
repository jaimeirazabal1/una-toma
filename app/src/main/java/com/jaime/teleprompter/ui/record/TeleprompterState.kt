package com.jaime.teleprompter.ui.record

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jaime.teleprompter.domain.TeleprompterMath

/** Estado observable del scroll. La matemática vive en [TeleprompterMath] (testeada en JVM). */
@Stable
class TeleprompterState {
    var offsetPx by mutableFloatStateOf(0f)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var viewportPx by mutableFloatStateOf(0f)
    var contentPx by mutableFloatStateOf(0f)

    private val maxOffsetPx: Float get() = TeleprompterMath.maxOffset(contentPx, viewportPx)

    fun advance(dtNanos: Long, speedPxPerSec: Float) {
        offsetPx = TeleprompterMath.advance(offsetPx, speedPxPerSec, dtNanos, maxOffsetPx)
    }

    /** Movimiento manual con el dedo; el scroll automático sigue funcionando por encima. */
    fun dragBy(deltaPx: Float) {
        offsetPx = TeleprompterMath.clamp(offsetPx + deltaPx, maxOffsetPx)
    }

    fun play() { isPlaying = true }
    fun pause() { isPlaying = false }
    fun toggle() { isPlaying = !isPlaying }
    fun restart() { offsetPx = 0f }
}
