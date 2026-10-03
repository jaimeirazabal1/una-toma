package com.jaime.teleprompter.ui.record

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.view.Surface
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jaime.teleprompter.R
import com.jaime.teleprompter.domain.PreviewMode
import com.jaime.teleprompter.ui.components.TeleprompterSettingsControls

private val PERMISSIONS = arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

@Composable
fun RecordScreen(
    viewModel: RecordViewModel,
    onClose: () -> Unit,
    onRecordingFinished: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current

    PortraitAndKeepAwake(context, view)

    var hasPermission by remember {
        mutableStateOf(PERMISSIONS.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED })
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        hasPermission = result.values.all { it }
    }
    LaunchedEffect(Unit) { if (!hasPermission) permissionLauncher.launch(PERMISSIONS) }

    val finishedCallback by rememberUpdatedState(onRecordingFinished)
    LaunchedEffect(viewModel) { viewModel.finished.collect { finishedCallback() } }

    if (!hasPermission) {
        PermissionRequest(onRequest = { permissionLauncher.launch(PERMISSIONS) }, onClose = onClose)
        return
    }

    val settings = viewModel.settings.collectAsStateWithLifecycle().value ?: return
    val script by viewModel.script.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()

    val teleprompter = remember { TeleprompterState() }
    LaunchedEffect(ui.phase) {
        when (ui.phase) {
            RecordPhase.COUNTDOWN -> { teleprompter.pause(); teleprompter.restart() }
            RecordPhase.RECORDING -> teleprompter.play()
            RecordPhase.IDLE -> teleprompter.pause()
        }
    }

    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(previewView, settings.recording.lens, settings.recording.quality) {
        val pv = previewView ?: return@LaunchedEffect
        viewModel.bindCamera(lifecycleOwner, pv.surfaceProvider, settings.recording)
    }

    LaunchedEffect(previewView, filter) { previewView?.applyFilter(filter) }

    val displayRotation = { view.display?.rotation ?: Surface.ROTATION_90 }
    val busy = ui.phase != RecordPhase.IDLE
    BackHandler(enabled = busy) { viewModel.onRecordClick(displayRotation()) }

    var showSettings by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            TopBar(
                busy = busy,
                showSettings = showSettings,
                previewMode = settings.recording.previewMode,
                onClose = onClose,
                onToggleSettings = { showSettings = !showSettings },
                onPreviewMode = viewModel::setPreviewMode,
            )
            Box(Modifier.weight(1f).fillMaxWidth()) {
                CameraFrame(
                    mode = settings.recording.previewMode,
                    onPreviewView = { previewView = it },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    TeleprompterOverlay(
                        script = script,
                        settings = settings.teleprompter,
                        state = teleprompter,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (ui.phase == RecordPhase.COUNTDOWN) {
                    Text(
                        text = ui.countdown.toString(),
                        color = Color.White,
                        fontSize = 120.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
                if (showSettings) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    ) {
                        Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp)) {
                            TeleprompterSettingsControls(
                                settings = settings.teleprompter,
                                onChange = viewModel::updateTeleprompter,
                            )
                        }
                    }
                }
                ui.errorRes?.let { res ->
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
                    ) {
                        Text(
                            text = ui.errorCode?.let { stringResource(res, it) } ?: stringResource(res),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
            FilterBar(
                selection = filter,
                onSelect = viewModel::selectFilter,
                onIntensity = viewModel::setFilterIntensity,
                modifier = Modifier.padding(top = 6.dp),
            )
            BottomBar(
                ui = ui,
                teleprompter = teleprompter,
                onRecordClick = { viewModel.onRecordClick(displayRotation()) },
            )
        }
    }
}

@Composable
private fun PortraitAndKeepAwake(context: Context, view: android.view.View) {
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TopBar(
    busy: Boolean,
    showSettings: Boolean,
    previewMode: PreviewMode,
    onClose: () -> Unit,
    onToggleSettings: () -> Unit,
    onPreviewMode: (PreviewMode) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = onClose, enabled = !busy) { Text(stringResource(R.string.record_close)) }
        TextButton(onClick = onToggleSettings) {
            Text(stringResource(if (showSettings) R.string.record_hide_settings else R.string.record_settings))
        }
        val modes = listOf(
            PreviewMode.VERTICAL to R.string.preview_vertical,
            PreviewMode.HORIZONTAL to R.string.preview_horizontal,
            PreviewMode.BOTH to R.string.preview_both,
        )
        SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
            modes.forEachIndexed { index, (mode, label) ->
                SegmentedButton(
                    selected = previewMode == mode,
                    onClick = { onPreviewMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                    label = { Text(stringResource(label), fontSize = 11.sp) },
                )
            }
        }
    }
}

@Composable
private fun BottomBar(
    ui: RecordUiState,
    teleprompter: TeleprompterState,
    onRecordClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        FilledTonalButton(onClick = teleprompter::restart) { Text(stringResource(R.string.record_restart_text)) }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RecordButton(recording = ui.phase != RecordPhase.IDLE, onClick = onRecordClick)
            if (ui.phase == RecordPhase.RECORDING) {
                Text(formatElapsed(ui.elapsedMs), color = Color.White, fontSize = 14.sp)
            }
        }
        FilledTonalButton(onClick = teleprompter::toggle) {
            Text(stringResource(if (teleprompter.isPlaying) R.string.record_pause_text else R.string.record_play_text))
        }
    }
}

@Composable
private fun RecordButton(recording: Boolean, onClick: () -> Unit) {
    val description = stringResource(R.string.record_button_cd)
    Box(
        Modifier
            .size(76.dp)
            .semantics { contentDescription = description }
            .border(4.dp, Color.White, CircleShape)
            .padding(8.dp)
            .clip(if (recording) RoundedCornerShape(10.dp) else CircleShape)
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
    )
}

@Composable
private fun PermissionRequest(onRequest: () -> Unit, onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(Color.Black).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.permission_needed), color = Color.White)
        Button(onClick = onRequest) { Text(stringResource(R.string.permission_grant)) }
        TextButton(onClick = onClose) { Text(stringResource(R.string.record_close)) }
    }
}

private fun formatElapsed(ms: Long): String {
    val totalSeconds = ms / 1000
    return "%02d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
