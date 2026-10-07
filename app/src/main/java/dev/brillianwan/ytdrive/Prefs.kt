package dev.brillianwan.ytdrive

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ytdrive_prefs")
private val KEY_FOLDER_URI = stringPreferencesKey("folder_uri")

/** Penyimpanan URI folder tujuan (SAF, persistable permission). Tanpa database. */
object Prefs {
    suspend fun getFolderUri(ctx: Context): String? =
        ctx.dataStore.data.map { it[KEY_FOLDER_URI] }.first()

    suspend fun setFolderUri(ctx: Context, uri: String) {
        ctx.dataStore.edit { it[KEY_FOLDER_URI] = uri }
    }

    suspend fun clearFolderUri(ctx: Context) {
        ctx.dataStore.edit { it.remove(KEY_FOLDER_URI) }
    }
}
