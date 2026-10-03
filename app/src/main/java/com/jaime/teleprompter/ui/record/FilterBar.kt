package com.jaime.teleprompter.ui.record

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.view.View
import androidx.annotation.StringRes
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaime.teleprompter.R
import com.jaime.teleprompter.domain.FilterSelection
import com.jaime.teleprompter.domain.VideoFilter
import kotlin.math.roundToInt

@StringRes
private fun VideoFilter.labelRes(): Int = when (this) {
    VideoFilter.ORIGINAL -> R.string.filter_original
    VideoFilter.NATURAL -> R.string.filter_natural
    VideoFilter.GLOW -> R.string.filter_glow
    VideoFilter.ROSE -> R.string.filter_rose
    VideoFilter.WARM -> R.string.filter_warm
    VideoFilter.COOL -> R.string.filter_cool
    VideoFilter.VIVID -> R.string.filter_vivid
    VideoFilter.CINE -> R.string.filter_cine
    VideoFilter.VINTAGE -> R.string.filter_vintage
    VideoFilter.BLACK_AND_WHITE -> R.string.filter_bw
}

/** Tono de piel de referencia: la muestra de cada filtro enseña cómo cambiaría una cara. */
private val SKIN_TONE = floatArrayOf(0.88f, 0.68f, 0.55f)

private fun VideoFilter.swatch(): Color {
    val out = FilterSelection(this).affine().apply(SKIN_TONE[0], SKIN_TONE[1], SKIN_TONE[2])
    return Color(out[0].coerceIn(0f, 1f), out[1].coerceIn(0f, 1f), out[2].coerceIn(0f, 1f))
}

/** Tira de filtros a un toque + intensidad. Se puede usar antes y durante la grabación. */
@Composable
fun FilterBar(
    selection: FilterSelection,
    onSelect: (VideoFilter) -> Unit,
    onIntensity: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        // Siempre ocupa su espacio (atenuado sin filtro) para que el preview no se redimensione al elegir.
        val active = selection.filter != VideoFilter.ORIGINAL
        run {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).alpha(if (active) 1f else 0.35f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.filter_intensity, (selection.intensity * 100).roundToInt()),
                    color = Color.White,
                    fontSize = 12.sp,
                )
                Slider(
                    value = selection.intensity,
                    onValueChange = onIntensity,
                    enabled = active,
                    modifier = Modifier.weight(1f).padding(start = 12.dp),
                )
            }
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(VideoFilter.entries) { filter ->
                val selected = selection.filter == filter
                val swatch = remember(filter) { filter.swatch() }
                Column(
                    Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(filter) }),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (selected) 3.dp else 1.dp,
                                color = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.3f),
                                shape = CircleShape,
                            ),
                    )
                    Text(
                        stringResource(filter.labelRes()),
                        color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

/** Aplica el filtro al preview en vivo con la misma matriz que usará la exportación. */
fun PreviewView.applyFilter(selection: FilterSelection) {
    if (selection.isIdentity) {
        setLayerType(View.LAYER_TYPE_NONE, null)
    } else {
        val paint = Paint().apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix(selection.affine().toAndroidColorMatrix()))
        }
        setLayerType(View.LAYER_TYPE_HARDWARE, paint)
    }
}
