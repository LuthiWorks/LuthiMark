package com.luthiworks.luthimark.ui

import android.app.Application
import android.content.Intent
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
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class Workspace(
    val uri: Uri,
    val name: String,
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

    val isDirty: Boolean get() = editing && content != originalContent

    val currentPathSegments: List<String> get() = pathStack.map { it.name }

    val canGoUp: Boolean get() = pathStack.isNotEmpty()

    init {
        viewModelScope.launch {
            val state = prefs.state.firstOrNull() ?: return@launch
            workspaces = state.roots
                .filter { hasPermission(it) }
                .map { Workspace(it, repo.rootName(it) ?: "(unnamed)") }
            recents = prefs.recents.firstOrNull().orEmpty()
            starred = prefs.starred.firstOrNull().orEmpty()
            val initial = state.currentRoot?.takeIf { uri -> workspaces.any { it.uri == uri } }
                ?: workspaces.firstOrNull()?.uri
            if (initial != null) activateRoot(initial)
            cleanRecents()
        }
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

    fun isStarred(uri: Uri): Boolean = starred.any { it.fileUri == uri }

    private fun hasPermission(uri: Uri): Boolean {
        val resolver = getApplication<Application>().contentResolver
        return resolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }
    }

    fun addWorkspace(uri: Uri) {
        viewModelScope.launch {
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
        rootUri = null
        rootName = null
        pathStack = emptyList()
        folders = emptyList()
        files = emptyList()
    }

    fun refreshContents() {
        val root = rootUri ?: return
        viewModelScope.launch {
            loadingFiles = true
            val contents = repo.listContents(root, currentPathSegments)
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

    fun openFile(file: MarkdownFile) {
        selectedFile = file
        selectedFilePath = currentPathSegments
        editing = false
        val workspace = rootUri
        viewModelScope.launch {
            loadingContent = true
            val text = repo.read(file.uri)
            loadingContent = false
            if (text == null) {
                selectedFile = null
                content = ""
                originalContent = ""
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
                    subPath = currentPathSegments,
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
        val root = rootUri ?: return null
        return repo.resolveRelativeImage(root, selectedFilePath, relativePath)
    }

    fun closeFile() {
        selectedFile = null
        content = ""
        originalContent = ""
        editing = false
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
        viewModelScope.launch {
            saving = true
            val ok = repo.write(file.uri, content)
            saving = false
            if (ok) {
                originalContent = content
                editing = false
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
