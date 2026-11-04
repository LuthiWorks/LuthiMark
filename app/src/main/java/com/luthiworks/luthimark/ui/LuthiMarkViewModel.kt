package com.luthiworks.luthimark.ui

import android.app.Application
import android.content.Intent
import android.content.UriPermission
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luthiworks.luthimark.data.AppPreferences
import com.luthiworks.luthimark.data.MAX_RECENTS
import com.luthiworks.luthimark.data.MarkdownFile
import com.luthiworks.luthimark.data.MarkdownFolder
import com.luthiworks.luthimark.data.MarkdownRepository
import com.luthiworks.luthimark.data.RecentEntry
import com.luthiworks.luthimark.data.uniqueFileName
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class Workspace(
    val uri: Uri,
    val name: String,
)

/** A save-to-workspace that would land on an existing file, waiting for the user to choose. */
data class SaveConflict(
    val workspaceUri: Uri,
    val workspaceName: String,
    val fileName: String,
    val alternateName: String,
)

class LuthiMarkViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = AppPreferences(app)
    private val repo = MarkdownRepository(app)

    var workspaces by mutableStateOf<List<Workspace>>(emptyList())
        private set
    var rootUri by mutableStateOf<Uri?>(null)
        private set
    var rootName by mutableStateOf<String?>(null)
        private set
    var pathStack by mutableStateOf<List<MarkdownFolder>>(emptyList())
        private set
    var folders by mutableStateOf<List<MarkdownFolder>>(emptyList())
        private set
    var files by mutableStateOf<List<MarkdownFile>>(emptyList())
        private set
    var loadingFiles by mutableStateOf(false)
        private set

    var selectedFile by mutableStateOf<MarkdownFile?>(null)
        private set
    var selectedFilePath by mutableStateOf<List<String>>(emptyList())
        private set
    var content by mutableStateOf("")
        private set
    var originalContent by mutableStateOf("")
        private set
    var editing by mutableStateOf(false)
        private set
    var loadingContent by mutableStateOf(false)
        private set
    var saving by mutableStateOf(false)
        private set
    var statusMessage by mutableStateOf<String?>(null)

    var recents by mutableStateOf<List<RecentEntry>>(emptyList())
        private set
    var starred by mutableStateOf<List<RecentEntry>>(emptyList())
        private set

    var externalFileUri by mutableStateOf<Uri?>(null)
        private set
    var pendingDetailNavigation by mutableStateOf<Uri?>(null)
        private set
    var saveConflict by mutableStateOf<SaveConflict?>(null)
        private set

    // The in-flight file read and folder listing. Starting a new one cancels
    // the previous, so a slow read can never land after the user has moved on
    // (which used to put one file's text under another file's name).
    private var loadJob: Job? = null
    private var refreshJob: Job? = null

    val isExternalFile: Boolean get() = externalFileUri != null

    val isDirty: Boolean get() = editing && content != originalContent

    val currentPathSegments: List<String> get() = pathStack.map { it.name }

    val canGoUp: Boolean get() = pathStack.isNotEmpty()

    private val initJob: Job = viewModelScope.launch {
        val state = prefs.state.firstOrNull() ?: return@launch
        releaseStrayGrants(keep = state.roots.toSet())
        workspaces = state.roots
            .filter { hasPermission(it) }
            .map { Workspace(it, repo.rootName(it) ?: "(unnamed)") }
        recents = prefs.recents.firstOrNull().orEmpty()
        starred = prefs.starred.firstOrNull().orEmpty()
        cleanRecents()
        cleanStarred()
    }

    private suspend fun cleanRecents() {
        val workspaceUris = workspaces.map { it.uri }.toSet()
        val current = recents
        val cleaned = current.mapNotNull { entry ->
            if (entry.workspaceUri !in workspaceUris) return@mapNotNull null
            repo.validateOrLocate(entry)
        }
        if (cleaned != current) {
            recents = cleaned
            prefs.replaceRecents(cleaned)
        }
    }

    private suspend fun cleanStarred() {
        val workspaceUris = workspaces.map { it.uri }.toSet()
        val current = starred
        val cleaned = current.mapNotNull { entry ->
            if (entry.workspaceUri !in workspaceUris) return@mapNotNull null
            repo.validateOrLocate(entry)
        }
        if (cleaned != current) {
            starred = cleaned
            prefs.replaceStarred(cleaned)
        }
    }

    fun isStarred(uri: Uri): Boolean = starred.any { it.fileUri == uri }

    private fun hasPermission(uri: Uri): Boolean {
        val resolver = getApplication<Application>().contentResolver
        return resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    /**
     * Workspace folders are the only persisted grants the app needs. Earlier
     * builds also persisted one for every file opened from outside the app.
     * Android caps how many an app may hold and drops the oldest first — which
     * would be the workspaces — so anything that isn't a workspace is let go.
     */
    private fun releaseStrayGrants(keep: Set<Uri>) {
        val resolver = getApplication<Application>().contentResolver
        resolver.persistedUriPermissions
            .filter { it.uri !in keep }
            .forEach { release(it) }
    }

    private fun releaseGrant(uri: Uri) {
        val resolver = getApplication<Application>().contentResolver
        resolver.persistedUriPermissions
            .filter { it.uri == uri }
            .forEach { release(it) }
    }

    private fun release(permission: UriPermission) {
        var flags = 0
        if (permission.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (permission.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags == 0) return
        runCatching {
            getApplication<Application>().contentResolver
                .releasePersistableUriPermission(permission.uri, flags)
        }
    }

    fun addWorkspace(uri: Uri) {
        viewModelScope.launch {
            // Startup releases grants that aren't workspaces; let it finish first so
            // it can't release the grant this is about to take.
            initJob.join()
            val resolver = getApplication<Application>().contentResolver
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { resolver.takePersistableUriPermission(uri, flags) }
            prefs.addRoot(uri)
            val name = repo.rootName(uri) ?: "(unnamed)"
            val workspace = Workspace(uri, name)
            workspaces = (workspaces.filter { it.uri != uri } + workspace)
            activateRoot(uri)
        }
    }

    fun switchWorkspace(uri: Uri) {
        if (uri == rootUri) return
        viewModelScope.launch {
            prefs.setCurrentRoot(uri)
            activateRoot(uri)
        }
    }

    fun removeWorkspace(uri: Uri) {
        viewModelScope.launch {
            prefs.removeRoot(uri)
            prefs.pruneRecents(uri)
            prefs.pruneStarred(uri)
            releaseGrant(uri)
            recents = recents.filter { it.workspaceUri != uri }
            starred = starred.filter { it.workspaceUri != uri }
            workspaces = workspaces.filter { it.uri != uri }
            if (uri == rootUri) {
                closeFile()
                val next = workspaces.firstOrNull()?.uri
                if (next != null) activateRoot(next) else clearActive()
            }
        }
    }

    private fun activateRoot(uri: Uri) {
        rootUri = uri
        rootName = workspaces.firstOrNull { it.uri == uri }?.name
        pathStack = emptyList()
        closeFile()
        refreshContents()
    }

    private fun clearActive() {
        refreshJob?.cancel()
        rootUri = null
        rootName = null
        pathStack = emptyList()
        folders = emptyList()
        files = emptyList()
        loadingFiles = false
    }

    fun refreshContents() {
        val root = rootUri ?: return
        val path = currentPathSegments
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            loadingFiles = true
            val contents = repo.listContents(root, path)
            // A listing for a folder the user has already left is stale.
            if (root != rootUri || path != currentPathSegments) return@launch
            folders = contents.folders
            files = contents.files
            loadingFiles = false
        }
    }

    fun enterFolder(folder: MarkdownFolder) {
        pathStack = pathStack + folder
        refreshContents()
    }

    fun goUp() {
        if (pathStack.isEmpty()) return
        pathStack = pathStack.dropLast(1)
        refreshContents()
    }

    fun goToBreadcrumb(index: Int) {
        if (index < -1 || index >= pathStack.size) return
        pathStack = if (index < 0) emptyList() else pathStack.take(index + 1)
        refreshContents()
    }

    fun openIntentUri(uri: Uri) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            initJob.join()
            // No persisted grant here: the grant that comes with the intent covers
            // this session, and a persisted one was never used again (see
            // releaseStrayGrants for why keeping them was harmful).
            loadingContent = true
            val text = repo.read(uri)
            loadingContent = false
            if (text == null) {
                statusMessage = "Couldn't open the file"
                return@launch
            }
            val displayName = repo.queryDisplayName(uri)
                ?: uri.lastPathSegment?.substringAfterLast('/')
                ?: "External file"
            externalFileUri = uri
            saveConflict = null
            selectedFile = MarkdownFile(
                uri = uri,
                name = displayName,
                lastModified = 0L,
                sizeBytes = text.length.toLong(),
            )
            selectedFilePath = emptyList()
            content = text
            originalContent = text
            editing = false
            pendingDetailNavigation = uri
        }
    }

    fun consumePendingDetailNavigation() {
        pendingDetailNavigation = null
    }

    fun saveExternalToWorkspace(workspaceUri: Uri) {
        if (externalFileUri == null) return
        val fileToSave = selectedFile ?: return
        viewModelScope.launch {
            initJob.join()
            val resolver = getApplication<Application>().contentResolver
            if (workspaces.none { it.uri == workspaceUri }) {
                val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                runCatching { resolver.takePersistableUriPermission(workspaceUri, flags) }
                prefs.addRoot(workspaceUri)
                val newName = repo.rootName(workspaceUri) ?: "(unnamed)"
                workspaces = (workspaces.filter { it.uri != workspaceUri } +
                    Workspace(workspaceUri, newName))
            }
            val name = fileToSave.name.ifBlank { "untitled.md" }
            val taken = repo.childNames(workspaceUri)
            if (taken.any { it.equals(name, ignoreCase = true) }) {
                // Never overwrite silently: ask, offering a free name to keep both.
                saveConflict = SaveConflict(
                    workspaceUri = workspaceUri,
                    workspaceName = workspaces.firstOrNull { it.uri == workspaceUri }?.name
                        ?: "this workspace",
                    fileName = name,
                    alternateName = uniqueFileName(name, taken),
                )
                return@launch
            }
            finishSaveToWorkspace(workspaceUri, name, overwrite = false)
        }
    }

    fun resolveSaveConflict(replace: Boolean) {
        val conflict = saveConflict ?: return
        saveConflict = null
        viewModelScope.launch {
            finishSaveToWorkspace(
                workspaceUri = conflict.workspaceUri,
                name = if (replace) conflict.fileName else conflict.alternateName,
                overwrite = replace,
            )
        }
    }

    fun cancelSaveConflict() {
        saveConflict = null
    }

    private suspend fun finishSaveToWorkspace(workspaceUri: Uri, name: String, overwrite: Boolean) {
        // The external file may have been closed while the user was deciding.
        if (externalFileUri == null) return
        val originalName = selectedFile?.name
        val saved = repo.saveContentToWorkspace(workspaceUri, name, content, overwrite) ?: run {
            statusMessage = "Couldn't save to workspace"
            return
        }
        externalFileUri = null
        rootUri = workspaceUri
        rootName = workspaces.firstOrNull { it.uri == workspaceUri }?.name
        pathStack = emptyList()
        prefs.setCurrentRoot(workspaceUri)
        refreshContents()
        openFile(saved)
        val where = rootName ?: "workspace"
        statusMessage = if (saved.name == originalName) {
            "Saved to $where"
        } else {
            "Saved to $where as ${saved.name}"
        }
    }

    fun openFile(file: MarkdownFile) {
        loadJob?.cancel()
        externalFileUri = null
        saveConflict = null
        selectedFile = file
        val subPath = currentPathSegments
        selectedFilePath = subPath
        editing = false
        content = ""
        originalContent = ""
        val workspace = rootUri
        loadJob = viewModelScope.launch {
            loadingContent = true
            val text = repo.read(file.uri)
            // Cancellation already stops superseded reads; this is the backstop.
            if (selectedFile?.uri != file.uri) return@launch
            loadingContent = false
            if (text == null) {
                selectedFile = null
                statusMessage = "Couldn't open this file. It may have been moved or deleted."
                val updatedRecents = recents.filter { it.fileUri != file.uri }
                if (updatedRecents != recents) {
                    recents = updatedRecents
                    prefs.replaceRecents(updatedRecents)
                }
                if (starred.any { it.fileUri == file.uri }) {
                    starred = starred.filter { it.fileUri != file.uri }
                    prefs.removeStarred(file.uri)
                }
                return@launch
            }
            originalContent = text
            content = text
            if (workspace != null) {
                val entry = RecentEntry(
                    fileUri = file.uri,
                    fileName = file.name,
                    workspaceUri = workspace,
                    subPath = subPath,
                    timestamp = System.currentTimeMillis(),
                )
                prefs.recordRecent(entry)
                recents = (listOf(entry) + recents.filter { it.fileUri != file.uri })
                    .take(MAX_RECENTS)
            }
        }
    }

    fun openRecent(entry: RecentEntry) {
        if (entry.workspaceUri != rootUri) {
            val workspace = workspaces.firstOrNull { it.uri == entry.workspaceUri } ?: run {
                statusMessage = "Workspace no longer available"
                return
            }
            switchWorkspaceThenOpen(entry, workspace)
        } else {
            navigateToPathThenOpen(entry)
        }
    }

    private fun switchWorkspaceThenOpen(entry: RecentEntry, workspace: Workspace) {
        viewModelScope.launch {
            prefs.setCurrentRoot(workspace.uri)
            rootUri = workspace.uri
            rootName = workspace.name
            pathStack = entry.subPath.map { MarkdownFolder(Uri.EMPTY, it) }
            closeFile()
            refreshContents()
            openSelectedFromEntry(entry)
        }
    }

    private fun navigateToPathThenOpen(entry: RecentEntry) {
        if (entry.subPath != currentPathSegments) {
            pathStack = entry.subPath.map { MarkdownFolder(Uri.EMPTY, it) }
            refreshContents()
        }
        openSelectedFromEntry(entry)
    }

    private fun openSelectedFromEntry(entry: RecentEntry) {
        val file = MarkdownFile(
            uri = entry.fileUri,
            name = entry.fileName,
            lastModified = entry.timestamp,
            sizeBytes = 0L,
        )
        openFile(file)
    }

    fun toggleStarFile(file: MarkdownFile) {
        val workspace = rootUri ?: return
        if (isStarred(file.uri)) {
            unstar(file.uri)
        } else {
            val entry = RecentEntry(
                fileUri = file.uri,
                fileName = file.name,
                workspaceUri = workspace,
                subPath = currentPathSegments,
                timestamp = System.currentTimeMillis(),
            )
            starred = listOf(entry) + starred.filter { it.fileUri != file.uri }
            viewModelScope.launch { prefs.addStarred(entry) }
        }
    }

    fun toggleStarFromEntry(entry: RecentEntry) {
        if (isStarred(entry.fileUri)) {
            unstar(entry.fileUri)
        } else {
            val starredEntry = entry.copy(timestamp = System.currentTimeMillis())
            starred = listOf(starredEntry) + starred.filter { it.fileUri != entry.fileUri }
            viewModelScope.launch { prefs.addStarred(starredEntry) }
        }
    }

    fun unstar(uri: Uri) {
        starred = starred.filter { it.fileUri != uri }
        viewModelScope.launch { prefs.removeStarred(uri) }
    }

    fun resolveImageUri(relativePath: String): Uri? {
        if (externalFileUri != null) return null
        val root = rootUri ?: return null
        return repo.resolveRelativeImage(root, selectedFilePath, relativePath)
    }

    fun closeFile() {
        loadJob?.cancel()
        loadJob = null
        selectedFile = null
        content = ""
        originalContent = ""
        editing = false
        loadingContent = false
        externalFileUri = null
        saveConflict = null
    }

    fun toggleEditing(value: Boolean) {
        editing = value
        if (!value) {
            content = originalContent
        }
    }

    fun updateContent(text: String) {
        content = text
    }

    fun save() {
        val file = selectedFile ?: return
        val text = content
        viewModelScope.launch {
            saving = true
            val ok = repo.write(file.uri, text)
            saving = false
            if (selectedFile?.uri != file.uri) {
                // The user moved to another file while this one was saving.
                statusMessage = if (ok) "Saved ${file.name}" else "Failed to save ${file.name}"
                return@launch
            }
            if (ok) {
                originalContent = text
                // Anything typed while the save ran is still unsaved: stay in the editor.
                if (content == text) editing = false
                statusMessage = "Saved"
                refreshContents()
            } else {
                statusMessage = "Failed to save"
            }
        }
    }

    fun createFile(name: String, onCreated: (MarkdownFile) -> Unit) {
        val root = rootUri ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val newUri = repo.createMarkdownFile(root, currentPathSegments, name.trim()) ?: run {
                statusMessage = "Could not create file"
                return@launch
            }
            refreshContents()
            val file = files.firstOrNull { it.uri == newUri }
                ?: MarkdownFile(newUri, name, System.currentTimeMillis(), 0L)
            onCreated(file)
        }
    }

    fun clearStatus() {
        statusMessage = null
    }
}
