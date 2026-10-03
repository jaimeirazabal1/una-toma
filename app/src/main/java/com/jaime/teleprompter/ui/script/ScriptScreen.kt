package com.jaime.teleprompter.ui.script

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaime.teleprompter.R
import com.jaime.teleprompter.domain.Lens
import com.jaime.teleprompter.domain.RecordingQuality
import com.jaime.teleprompter.ui.components.TeleprompterSettingsControls

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScriptScreen(viewModel: ScriptViewModel, onRecord: () -> Unit) {
    val script by viewModel.script.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.script_title), style = MaterialTheme.typography.headlineMedium)

            OutlinedTextField(
                value = script.orEmpty(),
                onValueChange = viewModel::onScriptChange,
                placeholder = { Text(stringResource(R.string.script_hint)) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp),
            )

            Text(stringResource(R.string.section_teleprompter), style = MaterialTheme.typography.titleMedium)
            TeleprompterSettingsControls(
                settings = settings.teleprompter,
                onChange = viewModel::updateTeleprompter,
            )

            Text(stringResource(R.string.section_recording), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.quality_label))
            val qualities = listOf(
                RecordingQuality.UHD_4K to R.string.quality_4k,
                RecordingQuality.FHD to R.string.quality_fhd,
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                qualities.forEachIndexed { i, (q, label) ->
                    SegmentedButton(
                        selected = settings.recording.quality == q,
                        onClick = { viewModel.updateRecording { it.copy(quality = q) } },
                        shape = SegmentedButtonDefaults.itemShape(i, qualities.size),
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Text(stringResource(R.string.lens_label))
            val lenses = listOf(Lens.FRONT to R.string.lens_front, Lens.BACK to R.string.lens_back)
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                lenses.forEachIndexed { i, (lens, label) ->
                    SegmentedButton(
                        selected = settings.recording.lens == lens,
                        onClick = { viewModel.updateRecording { it.copy(lens = lens) } },
                        shape = SegmentedButtonDefaults.itemShape(i, lenses.size),
                        label = { Text(stringResource(label)) },
                    )
                }
            }
            Text(stringResource(R.string.countdown_label, settings.recording.countdownSeconds))
            Slider(
                value = settings.recording.countdownSeconds.toFloat(),
                onValueChange = { v -> viewModel.updateRecording { it.copy(countdownSeconds = v.toInt()) } },
                valueRange = 0f..10f,
                steps = 9,
            )

            Button(
                onClick = { viewModel.flush(onRecord) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.go_record)) }

            Text(
                stringResource(R.string.script_footer),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
    }
}
