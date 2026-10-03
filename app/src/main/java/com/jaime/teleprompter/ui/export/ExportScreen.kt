package com.jaime.teleprompter.ui.export

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaime.teleprompter.R
import com.jaime.teleprompter.domain.ExportStage
import com.jaime.teleprompter.domain.ExportState

@Composable
fun ExportScreen(viewModel: ExportViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val leave = {
        viewModel.reset()
        onFinished()
    }
    BackHandler(enabled = state is ExportState.Running) { /* cancelar es explícito */ }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.windowInsetsPadding(WindowInsets.safeDrawing).padding(24.dp).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            when (val s = state) {
                ExportState.Idle -> Button(onClick = leave) { Text(stringResource(R.string.export_back)) }

                is ExportState.Running -> {
                    Text(stringResource(R.string.export_title), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        stringResource(
                            if (s.stage == ExportStage.VERTICAL) R.string.export_stage_vertical
                            else R.string.export_stage_horizontal,
                        ),
                    )
                    val progress = s.progress
                    if (progress != null) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    OutlinedButton(onClick = viewModel::cancel) { Text(stringResource(R.string.export_cancel)) }
                }

                is ExportState.Done -> {
                    Text(stringResource(R.string.export_done_title), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.export_done_body))
                    Text(stringResource(R.string.export_horizontal_desc))
                    Text(stringResource(R.string.export_vertical_desc))
                    Button(onClick = { share(context, listOf(s.horizontal, s.vertical)) }) {
                        Text(stringResource(R.string.export_share))
                    }
                    OutlinedButton(onClick = leave) { Text(stringResource(R.string.export_new)) }
                }

                is ExportState.Failed -> {
                    Text(stringResource(R.string.export_failed, s.message))
                    Button(onClick = leave) { Text(stringResource(R.string.export_back)) }
                }
            }
        }
    }
}

private fun share(context: Context, uris: List<Uri>) {
    val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
        type = "video/mp4"
        putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.export_share_chooser)))
}
