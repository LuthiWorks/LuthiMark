package com.luthiworks.luthimark.ui

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.luthiworks.luthimark.data.MarkdownFile
import com.luthiworks.luthimark.data.MarkdownFolder
import com.luthiworks.luthimark.data.RecentEntry

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    rootUri: Uri?,
    rootName: String?,
    pathStack: List<MarkdownFolder>,
    folders: List<MarkdownFolder>,
    files: List<MarkdownFile>,
    recents: List<RecentEntry>,
    starred: List<RecentEntry>,
    isStarred: (Uri) -> Boolean,
    canGoUp: Boolean,
    loading: Boolean,
    hasAnyWorkspace: Boolean,
    onAddWorkspace: (Uri) -> Unit,
    onOpenDrawer: () -> Unit,
    onRefresh: () -> Unit,
    onEnterFolder: (MarkdownFolder) -> Unit,
    onGoUp: () -> Unit,
    onBreadcrumbClick: (Int) -> Unit,
    onFileClick: (MarkdownFile) -> Unit,
    onEntryClick: (RecentEntry) -> Unit,
    onToggleStarFile: (MarkdownFile) -> Unit,
    onToggleStarEntry: (RecentEntry) -> Unit,
    onCreateFile: (String) -> Unit,
    onOpenFile: () -> Unit,
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> if (uri != null) onAddWorkspace(uri) }

    var showCreateDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("LuthiMark") },
                navigationIcon = {
                    if (canGoUp) {
                        IconButton(onClick = onGoUp) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Up one folder",
                            )
                        }
                    } else {
                        IconButton(onClick = onOpenDrawer) {
                            Icon(Icons.Filled.Menu, contentDescription = "Workspaces")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onOpenFile) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open file",
                        )
                    }
                    if (rootUri != null) {
                        IconButton(onClick = onRefresh) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (rootUri != null) {
                FloatingActionButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "New file")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (rootUri != null) {
                Breadcrumb(
                    rootName = rootName ?: "Root",
                    pathStack = pathStack,
                    onBreadcrumbClick = onBreadcrumbClick,
                )
                HorizontalDivider()
            }
            Box(modifier = Modifier.fillMaxSize()) {
                val homeRecents = if (pathStack.isEmpty()) recents else emptyList()
                val homeStarred = if (pathStack.isEmpty()) starred else emptyList()
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                    rootUri == null && homeRecents.isEmpty() && homeStarred.isEmpty() ->
                        WelcomeState(
                            onOpenFile = onOpenFile,
                            onChooseFolder = { folderPicker.launch(null) },
                        )
                    rootUri != null && pathStack.isNotEmpty() &&
                        folders.isEmpty() && files.isEmpty() ->
                        EmptyState(
                            title = "Empty folder",
                            body = "No subfolders or markdown files here. Tap + to create a new file.",
                            actionLabel = null,
                            onAction = {},
                        )
                    else -> ContentList(
                        folders = folders,
                        files = files,
                        recents = homeRecents,
                        starred = homeStarred,
                        isStarred = isStarred,
                        onEnterFolder = onEnterFolder,
                        onFileClick = onFileClick,
                        onEntryClick = onEntryClick,
                        onToggleStarFile = onToggleStarFile,
                        onToggleStarEntry = onToggleStarEntry,
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New markdown file") },
            text = {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Filename (without extension)") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        onCreateFile(name)
                        showCreateDialog = false
                    },
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun Breadcrumb(
    rootName: String,
    pathStack: List<MarkdownFolder>,
    onBreadcrumbClick: (Int) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        LazyRow(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            item {
                BreadcrumbChip(
                    text = rootName,
                    isCurrent = pathStack.isEmpty(),
                    onClick = { onBreadcrumbClick(-1) },
                )
            }
            itemsIndexed(pathStack) { index, folder ->
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
                BreadcrumbChip(
                    text = folder.name,
                    isCurrent = index == pathStack.lastIndex,
                    onClick = { onBreadcrumbClick(index) },
                )
            }
        }
    }
}

@Composable
private fun BreadcrumbChip(
    text: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        enabled = !isCurrent,
    ) {
        Text(
            text = text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = if (isCurrent) {
                MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                )
            } else {
                MaterialTheme.typography.bodyMedium
            },
        )
    }
}

@Composable
private fun ContentList(
    folders: List<MarkdownFolder>,
    files: List<MarkdownFile>,
    recents: List<RecentEntry>,
    starred: List<RecentEntry>,
    isStarred: (Uri) -> Boolean,
    onEnterFolder: (MarkdownFolder) -> Unit,
    onFileClick: (MarkdownFile) -> Unit,
    onEntryClick: (RecentEntry) -> Unit,
    onToggleStarFile: (MarkdownFile) -> Unit,
    onToggleStarEntry: (RecentEntry) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (starred.isNotEmpty()) {
            item(key = "starred-header") {
                SectionHeader("Starred")
            }
            items(starred, key = { "starred:${it.fileUri}" }) { entry ->
                EntryRow(
                    entry = entry,
                    leadingIcon = Icons.Filled.Star,
                    starred = true,
                    onClick = { onEntryClick(entry) },
                    onToggleStar = { onToggleStarEntry(entry) },
                )
                HorizontalDivider()
            }
        }
        if (recents.isNotEmpty()) {
            item(key = "recent-header") {
                SectionHeader("Recent")
            }
            items(recents, key = { "recent:${it.fileUri}" }) { entry ->
                EntryRow(
                    entry = entry,
                    leadingIcon = Icons.Filled.History,
                    starred = isStarred(entry.fileUri),
                    onClick = { onEntryClick(entry) },
                    onToggleStar = { onToggleStarEntry(entry) },
                )
                HorizontalDivider()
            }
        }
        val hasWorkspaceContent = folders.isNotEmpty() || files.isNotEmpty()
        if ((starred.isNotEmpty() || recents.isNotEmpty()) && hasWorkspaceContent) {
            item(key = "files-header") {
                SectionHeader("All files")
            }
        }
        items(folders, key = { "folder:${it.uri}" }) { folder ->
            ListItem(
                leadingContent = {
                    Icon(Icons.Filled.Folder, contentDescription = null)
                },
                headlineContent = {
                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onEnterFolder(folder) }
                    .padding(horizontal = 4.dp),
            )
            HorizontalDivider()
        }
        items(files, key = { "file:${it.uri}" }) { file ->
            ListItem(
                leadingContent = {
                    Icon(Icons.Filled.Description, contentDescription = null)
                },
                headlineContent = {
                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                supportingContent = {
                    Text(formatMeta(file), style = MaterialTheme.typography.bodySmall)
                },
                trailingContent = {
                    StarToggle(starred = isStarred(file.uri), onClick = { onToggleStarFile(file) })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onFileClick(file) }
                    .padding(horizontal = 4.dp),
            )
            HorizontalDivider()
        }
    }
}

@Composable
private fun EntryRow(
    entry: RecentEntry,
    leadingIcon: ImageVector,
    starred: Boolean,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
) {
    ListItem(
        leadingContent = {
            Icon(leadingIcon, contentDescription = null)
        },
        headlineContent = {
            Text(entry.fileName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(formatRecent(entry), style = MaterialTheme.typography.bodySmall)
        },
        trailingContent = {
            StarToggle(starred = starred, onClick = onToggleStar)
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
    )
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun StarToggle(starred: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        if (starred) {
            Icon(
                Icons.Filled.Star,
                contentDescription = "Unstar",
                tint = MaterialTheme.colorScheme.primary,
            )
        } else {
            Icon(Icons.Outlined.StarOutline, contentDescription = "Star")
        }
    }
}

private fun formatRecent(entry: RecentEntry): String {
    val rel = DateUtils.getRelativeTimeSpanString(
        entry.timestamp,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    ).toString()
    return if (entry.subPath.isEmpty()) rel
    else "$rel · ${entry.subPath.joinToString(" / ")}"
}

@Composable
private fun WelcomeState(
    onOpenFile: () -> Unit,
    onChooseFolder: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Welcome to LuthiMark", style = MaterialTheme.typography.headlineSmall)
        Text(
            "Open a single file to read or edit, or add a folder to organize files into a workspace.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        Button(onClick = onOpenFile) { Text("Open a file") }
        TextButton(
            onClick = onChooseFolder,
            modifier = Modifier.padding(top = 8.dp),
        ) { Text("Add a workspace folder") }
    }
}

@Composable
private fun EmptyState(
    title: String,
    body: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        if (actionLabel != null) {
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun formatMeta(file: MarkdownFile): String {
    val sizeKb = (file.sizeBytes / 1024.0)
    val sizeStr = if (sizeKb < 1.0) "${file.sizeBytes} B" else "%.1f KB".format(sizeKb)
    val rel = if (file.lastModified > 0) {
        DateUtils.getRelativeTimeSpanString(
            file.lastModified,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS,
        ).toString()
    } else "—"
    return "$rel · $sizeStr"
}
