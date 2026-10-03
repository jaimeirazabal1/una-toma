package com.jaime.teleprompter.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jaime.teleprompter.domain.Lens
import com.jaime.teleprompter.domain.PreviewMode
import com.jaime.teleprompter.domain.RecordingQuality
import com.jaime.teleprompter.domain.RecordingSettings
import com.jaime.teleprompter.domain.TeleprompterSettings
import com.jaime.teleprompter.domain.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "teleprompter")

class SettingsRepository(private val context: Context) {

    val script: Flow<String> = context.dataStore.data.map { it[Keys.SCRIPT] ?: DEFAULT_SCRIPT }

    val settings: Flow<UserSettings> = context.dataStore.data.map { it.toUserSettings() }

    suspend fun saveScript(text: String) {
        context.dataStore.edit { it[Keys.SCRIPT] = text }
    }

    suspend fun updateTeleprompter(transform: (TeleprompterSettings) -> TeleprompterSettings) =
        update { current -> current.copy(teleprompter = transform(current.teleprompter)) }

    suspend fun updateRecording(transform: (RecordingSettings) -> RecordingSettings) =
        update { current -> current.copy(recording = transform(current.recording)) }

    private suspend fun update(transform: (UserSettings) -> UserSettings) {
        context.dataStore.edit { prefs ->
            val new = transform(prefs.toUserSettings())
            prefs[Keys.SPEED] = new.teleprompter.speedDpPerSec
            prefs[Keys.FONT] = new.teleprompter.fontSizeSp
            prefs[Keys.OPACITY] = new.teleprompter.backgroundOpacity
            prefs[Keys.QUALITY] = new.recording.quality.name
            prefs[Keys.LENS] = new.recording.lens.name
            prefs[Keys.COUNTDOWN] = new.recording.countdownSeconds
            prefs[Keys.PREVIEW_MODE] = new.recording.previewMode.name
        }
    }

    private fun Preferences.toUserSettings() = UserSettings(
        teleprompter = TeleprompterSettings(
            speedDpPerSec = this[Keys.SPEED] ?: TeleprompterSettings.DEFAULT_SPEED,
            fontSizeSp = this[Keys.FONT] ?: TeleprompterSettings.DEFAULT_FONT_SIZE,
            backgroundOpacity = this[Keys.OPACITY] ?: TeleprompterSettings.DEFAULT_OPACITY,
        ),
        recording = RecordingSettings(
            quality = enumOrDefault(this[Keys.QUALITY], RecordingQuality.UHD_4K),
            lens = enumOrDefault(this[Keys.LENS], Lens.FRONT),
            countdownSeconds = this[Keys.COUNTDOWN] ?: 3,
            previewMode = enumOrDefault(this[Keys.PREVIEW_MODE], PreviewMode.BOTH),
        ),
    )

    private inline fun <reified E : Enum<E>> enumOrDefault(name: String?, default: E): E =
        enumValues<E>().firstOrNull { it.name == name } ?: default

    private object Keys {
        val SCRIPT = stringPreferencesKey("script")
        val SPEED = floatPreferencesKey("speed_dp_per_sec")
        val FONT = floatPreferencesKey("font_size_sp")
        val OPACITY = floatPreferencesKey("background_opacity")
        val QUALITY = stringPreferencesKey("quality")
        val LENS = stringPreferencesKey("lens")
        val COUNTDOWN = intPreferencesKey("countdown")
        val PREVIEW_MODE = stringPreferencesKey("preview_mode")
    }

    private companion object {
        const val DEFAULT_SCRIPT =
            "Pega aquí tu guion.\n\nEl texto avanza solo mientras grabas. " +
                "Toca el teleprompter para pausarlo o reanudarlo, y arrástralo con el dedo " +
                "para adelantar o retroceder sin detenerlo.\n\n" +
                "Ajusta la velocidad y el tamaño de letra desde el panel de ajustes."
    }
}
