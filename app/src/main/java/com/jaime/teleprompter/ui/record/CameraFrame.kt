package com.jaime.teleprompter.ui.record

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.jaime.teleprompter.R
import com.jaime.teleprompter.domain.HorizontalFrame
import com.jaime.teleprompter.domain.PreviewMode

private const val PORTRAIT = 9f / 16f
private const val LANDSCAPE = 16f / 9f

/** Fracción de la altura del cuadro vertical que ocupa el teleprompter (zona superior, junto a la lente). */
private const val TELEPROMPTER_HEIGHT_FRACTION = 0.34f

/**
 * La cámara siempre es 9:16 (vertical). Según el modo se muestra:
 *  - VERTICAL: cuadro completo (lo que verás en Reels/TikTok).
 *  - HORIZONTAL: solo la franja 16:9 elegida (lo que verá YouTube).
 *  - BOTH: cuadro completo con la franja 16:9 resaltada, que se arrastra arriba/abajo para reencuadrar.
 *
 * La misma instancia de PreviewView sobrevive al cambio de modo (solo cambian tamaños).
 * [teleprompter] se coloca en la parte superior.
 */
@Composable
fun CameraFrame(
    mode: PreviewMode,
    bandCenter: Float,
    onBandCenter: (Float) -> Unit,
    onPreviewView: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
    teleprompter: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val horizontal = mode == PreviewMode.HORIZONTAL

        // Cuadro vertical que cabe en el espacio disponible
        val camH = minOf(maxHeight, maxWidth * LANDSCAPE)
        val camW = camH * PORTRAIT
        val bandH = camW * PORTRAIT

        val windowW = if (horizontal) maxWidth else camW
        val windowH = if (horizontal) maxWidth * PORTRAIT else camH
        val viewW = if (horizontal) maxWidth else camW
        val viewH = if (horizontal) maxWidth * LANDSCAPE else camH
        val teleprompterH = if (horizontal) windowH * 0.7f else camH * TELEPROMPTER_HEIGHT_FRACTION

        val center = HorizontalFrame.clampCenter(bandCenter, HorizontalFrame.PORTRAIT_BAND_FRACTION)

        Box(Modifier.size(windowW, windowH).clipToBounds(), contentAlignment = Alignment.Center) {
            // En modo horizontal desplazamos la cámara para que la franja elegida quede en la ventana.
            val cameraShift = if (horizontal) viewH * (0.5f - center) else 0.dp
            Box(Modifier.requiredSize(viewW, viewH).offset(y = cameraShift)) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        PreviewView(context).apply {
                            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                            scaleType = PreviewView.ScaleType.FILL_CENTER
                            onPreviewView(this)
                        }
                    },
                )
            }

            if (mode == PreviewMode.BOTH) {
                val bandTop = camH * center - bandH / 2
                Box(Modifier.fillMaxWidth().height(bandTop).align(Alignment.TopCenter).background(Color.Black.copy(alpha = 0.5f)))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(bandH)
                        .align(Alignment.TopCenter)
                        .offset(y = bandTop)
                        .border(2.dp, Color.White.copy(alpha = 0.85f)),
                )
                Box(
                    Modifier
                        .fillMaxWidth()
                        .fillMaxSize()
                        .align(Alignment.TopCenter)
                        .offset(y = bandTop + bandH)
                        .background(Color.Black.copy(alpha = 0.5f)),
                )
            }

            Box(Modifier.fillMaxWidth().height(teleprompterH).align(Alignment.TopCenter)) {
                teleprompter()
            }

            // El asa va encima del teleprompter para poder arrastrarla aunque se solapen.
            if (mode == PreviewMode.BOTH) {
                val density = LocalDensity.current
                val camHPx = with(density) { camH.toPx() }
                val currentCenter by rememberUpdatedState(center)
                val onChange by rememberUpdatedState(onBandCenter)
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(y = camH * center - 18.dp)
                        .padding(end = 8.dp)
                        .height(36.dp)
                        .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                        .pointerInput(camHPx) {
                            detectVerticalDragGestures { change, dragAmount ->
                                change.consume()
                                onChange(currentCenter + dragAmount / camHPx)
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.band_handle), color = Color.White, fontSize = 13.sp)
                }
            }
        }
    }
}
