package com.jaime.teleprompter.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaime.teleprompter.R
import com.jaime.teleprompter.domain.TeleprompterMath
import com.jaime.teleprompter.domain.TeleprompterSettings

/**
 * Teleprompter: scroll automático por frame (independiente de la tasa de refresco),
 * toque = pausar/reanudar, arrastre vertical = mover manualmente mientras avanza.
 */
@Composable
fun TeleprompterOverlay(
    script: String,
    settings: TeleprompterSettings,
    state: TeleprompterState,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val speedPxPerSec by rememberUpdatedState(settings.speedDpPerSec * density.density)

    LaunchedEffect(state) {
        var lastFrame = 0L
        while (true) {
            withFrameNanos { now ->
                if (lastFrame != 0L && state.isPlaying) state.advance(now - lastFrame, speedPxPerSec)
                lastFrame = now
            }
        }
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .background(Color.Black.copy(alpha = settings.backgroundOpacity))
            .onSizeChanged { state.viewportPx = it.height.toFloat() }
            .pointerInput(state) { detectTapGestures(onTap = { state.toggle() }) }
            .pointerInput(state) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    state.dragBy(-dragAmount)
                }
            },
    ) {
        val topPad = with(density) { (state.viewportPx * TeleprompterMath.READING_LINE_FRACTION).toDp() }
        val bottomPad = with(density) { (state.viewportPx * (1f - TeleprompterMath.READING_LINE_FRACTION)).toDp() }

        Text(
            text = script,
            color = Color.White,
            fontSize = settings.fontSizeSp.sp,
            lineHeight = (settings.fontSizeSp * 1.3f).sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer { translationY = -state.offsetPx }
                .onSizeChanged { state.contentPx = it.height.toFloat() }
                .padding(start = 14.dp, end = 14.dp, top = topPad, bottom = bottomPad),
        )

        // Línea de lectura
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(top = topPad)
                .fillMaxWidth()
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
        )

        if (!state.isPlaying) {
            Text(
                text = stringResource(R.string.paused_badge),
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
