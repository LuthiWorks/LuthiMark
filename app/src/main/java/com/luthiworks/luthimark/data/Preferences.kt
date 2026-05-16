package com.luthiworks.luthimark.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "luthimark_prefs")

private val LEGACY_FOLDER_URI = stringPreferencesKey("folder_uri")
private val FOLDER_URIS = stringSetPreferencesKey("folder_uris")
private val CURRENT_ROOT = stringPreferencesKey("current_root")
private val RECENT_FILES = stringSetPreferencesKey("recent_files")
private val STARRED_FILES = stringSetPreferencesKey("starred_files")

internal const val MAX_RECENTS = 3
private const val FIELD_SEP = "\u001F"
private const val SUBPATH_SEP = "\u001E"

data class RecentEntry(
    val fileUri: Uri,
    val fileName: String,
    val workspaceUri: Uri,
    val subPath: List<String>,
    val timestamp: Long,
)

class AppPreferences(private val context: Context) {

    val state: Flow<PersistedRoots> = context.dataStore.data.map { prefs ->
        readRoots(prefs)
    }

    val recents: Flow<List<RecentEntry>> = context.dataStore.data.map { prefs ->
        prefs[RECENT_FILES].orEmpty()
            .mapNotNull { parseRecent(it) }
            .sortedByDescending { it.timestamp }
    }

    val starred: Flow<List<RecentEntry>> = context.dataStore.data.map { prefs ->
        prefs[STARRED_FILES].orEmpty()
            .mapNotNull { parseRecent(it) }
            .sortedByDescending { it.timestamp }
    }

    suspend fun addRoot(uri: Uri) {
        context.dataStore.edit { prefs ->
            val current = prefs[FOLDER_URIS].orEmpty().toMutableSet()
            current += uri.toString()
            prefs[FOLDER_URIS] = current
            prefs[CURRENT_ROOT] = uri.toString()
        }
    }

    suspend fun removeRoot(uri: Uri) {
        context.dataStore.edit { prefs ->
            val current = prefs[FOLDER_URIS].orEmpty().toMutableSet()
            current -= uri.toString()
            prefs[FOLDER_URIS] = current
            if (prefs[CURRENT_ROOT] == uri.toString()) {
                val next = current.firstOrNull()
                if (next == null) prefs.remove(CURRENT_ROOT) else prefs[CURRENT_ROOT] = next
            }
        }
    }

    suspend fun setCurrentRoot(uri: Uri) {
        context.dataStore.edit { prefs ->
            prefs[CURRENT_ROOT] = uri.toString()
        }
    }

    suspend fun recordRecent(entry: RecentEntry) {
        context.dataStore.edit { prefs ->
            val current = prefs[RECENT_FILES].orEmpty()
                .mapNotNull { parseRecent(it) }
                .filter { it.fileUri != entry.fileUri }
            val updated = (listOf(entry) + current)
                .sortedByDescending { it.timestamp }
                .take(MAX_RECENTS)
                .map { encodeRecent(it) }
                .toSet()
            prefs[RECENT_FILES] = updated
        }
    }

    suspend fun replaceRecents(entries: List<RecentEntry>) {
        context.dataStore.edit { prefs ->
            prefs[RECENT_FILES] = entries
                .sortedByDescending { it.timestamp }
                .take(MAX_RECENTS)
                .map { encodeRecent(it) }
                .toSet()
        }
    }

    suspend fun pruneRecents(workspaceUri: Uri) {
        context.dataStore.edit { prefs ->
            val current = prefs[RECENT_FILES].orEmpty()
                .mapNotNull { parseRecent(it) }
                .filter { it.workspaceUri != workspaceUri }
                .map { encodeRecent(it) }
                .toSet()
            prefs[RECENT_FILES] = current
        }
    }

    suspend fun addStarred(entry: RecentEntry) {
        context.dataStore.edit { prefs ->
            val current = prefs[STARRED_FILES].orEmpty()
                .mapNotNull { parseRecent(it) }
                .filter { it.fileUri != entry.fileUri }
            val updated = (listOf(entry) + current)
                .map { encodeRecent(it) }
                .toSet()
            prefs[STARRED_FILES] = updated
        }
    }

    suspend fun removeStarred(uri: Uri) {
        context.dataStore.edit { prefs ->
            val current = prefs[STARRED_FILES].orEmpty()
                .mapNotNull { parseRecent(it) }
                .filter { it.fileUri != uri }
                .map { encodeRecent(it) }
                .toSet()
            prefs[STARRED_FILES] = current
        }
    }

    suspend fun pruneStarred(workspaceUri: Uri) {
        context.dataStore.edit { prefs ->
            val current = prefs[STARRED_FILES].orEmpty()
                .mapNotNull { parseRecent(it) }
                .filter { it.workspaceUri != workspaceUri }
                .map { encodeRecent(it) }
                .toSet()
            prefs[STARRED_FILES] = current
        }
    }

    private fun readRoots(prefs: Preferences): PersistedRoots {
        val explicit = prefs[FOLDER_URIS].orEmpty()
        val legacy = prefs[LEGACY_FOLDER_URI]
        val merged = if (legacy != null) explicit + legacy else explicit
        val current = prefs[CURRENT_ROOT] ?: legacy
        return PersistedRoots(
            roots = merged.map(Uri::parse).toList(),
            currentRoot = current?.let(Uri::parse),
        )
    }

    private fun parseRecent(encoded: String): RecentEntry? {
        val parts = encoded.split(FIELD_SEP)
        if (parts.size != 5) return null
        return runCatching {
            val sub = parts[3]
            RecentEntry(
                fileUri = Uri.parse(parts[0]),
                fileName = parts[1],
                workspaceUri = Uri.parse(parts[2]),
                subPath = if (sub.isEmpty()) emptyList() else sub.split(SUBPATH_SEP),
                timestamp = parts[4].toLong(),
            )
        }.getOrNull()
    }

    private fun encodeRecent(entry: RecentEntry): String {
        val sub = entry.subPath.joinToString(SUBPATH_SEP)
        return "${entry.fileUri}$FIELD_SEP${entry.fileName}$FIELD_SEP${entry.workspaceUri}$FIELD_SEP$sub$FIELD_SEP${entry.timestamp}"
    }
}

data class PersistedRoots(
    val roots: List<Uri>,
    val currentRoot: Uri?,
)
