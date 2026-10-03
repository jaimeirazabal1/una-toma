package com.jaime.teleprompter.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.jaime.teleprompter.R
import com.jaime.teleprompter.domain.TeleprompterSettings
import kotlin.math.roundToInt

@Composable
private fun LabeledSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}

/** Velocidad, tamaño de letra y opacidad: se reutiliza en la pantalla de guion y durante la grabación. */
@Composable
fun TeleprompterSettingsControls(
    settings: TeleprompterSettings,
    onChange: ((TeleprompterSettings) -> TeleprompterSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        LabeledSlider(
            label = stringResource(R.string.speed_label, settings.speedDpPerSec.roundToInt()),
            value = settings.speedDpPerSec,
            range = TeleprompterSettings.SPEED_RANGE,
            onChange = { v -> onChange { it.copy(speedDpPerSec = v) } },
        )
        LabeledSlider(
            label = stringResource(R.string.font_label, settings.fontSizeSp.roundToInt()),
            value = settings.fontSizeSp,
            range = TeleprompterSettings.FONT_RANGE,
            onChange = { v -> onChange { it.copy(fontSizeSp = v) } },
        )
        LabeledSlider(
            label = stringResource(R.string.opacity_label, (settings.backgroundOpacity * 100).roundToInt()),
            value = settings.backgroundOpacity,
            range = 0f..1f,
            onChange = { v -> onChange { it.copy(backgroundOpacity = v) } },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.mirror_label), modifier = Modifier.weight(1f))
            Switch(checked = settings.mirror, onCheckedChange = { v -> onChange { it.copy(mirror = v) } })
        }
    }
}
