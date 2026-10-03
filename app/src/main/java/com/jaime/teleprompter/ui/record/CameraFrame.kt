package com.jaime.teleprompter.ui.record

import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.jaime.teleprompter.domain.PreviewMode

private const val PORTRAIT = 9f / 16f
private const val LANDSCAPE = 16f / 9f

/** Fracción de la altura del cuadro vertical que ocupa el teleprompter (zona superior, junto a la lente). */
private const val TELEPROMPTER_HEIGHT_FRACTION = 0.34f

/**
 * La cámara siempre es 9:16 (vertical). Según el modo se muestra:
 *  - VERTICAL: cuadro completo (lo que verás en Reels/TikTok).
 *  - HORIZONTAL: solo la franja central 16:9 (lo que verá YouTube).
 *  - BOTH: cuadro completo con la franja 16:9 resaltada y el resto atenuado.
 *
 * La misma instancia de PreviewView sobrevive al cambio de modo (solo cambian tamaños).
 * [teleprompter] se coloca en la parte superior.
 */
@Composable
fun CameraFrame(
    mode: PreviewMode,
    onPreviewView: (PreviewView) -> Unit,
    modifier: Modifier = Modifier,
    teleprompter: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val horizontal = mode == PreviewMode.HORIZONTAL

        // Cuadro vertical que cabe en el espacio disponible
        val camH = minOf(maxHeight, maxWidth * LANDSCAPE)
        val camW = camH * PORTRAIT
        // Franja 16:9 centrada dentro del cuadro vertical
        val bandH = camW * PORTRAIT

        val windowW = if (horizontal) maxWidth else camW
        val windowH = if (horizontal) maxWidth * PORTRAIT else camH
        val viewW = if (horizontal) maxWidth else camW
        val viewH = if (horizontal) maxWidth * LANDSCAPE else camH
        val teleprompterH = if (horizontal) windowH * 0.7f else camH * TELEPROMPTER_HEIGHT_FRACTION

        Box(Modifier.size(windowW, windowH).clipToBounds(), contentAlignment = Alignment.Center) {
            Box(Modifier.requiredSize(viewW, viewH)) {
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
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)))
                    Box(Modifier.fillMaxWidth().height(bandH).border(2.dp, Color.White.copy(alpha = 0.85f)))
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color.Black.copy(alpha = 0.5f)))
                }
            }

            Box(Modifier.fillMaxWidth().height(teleprompterH).align(Alignment.TopCenter)) {
                teleprompter()
            }
        }
    }
}
