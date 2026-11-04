package com.luthiworks.luthimark

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.layout.PaneAdaptedValue
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luthiworks.luthimark.ui.FileScreen
import com.luthiworks.luthimark.ui.HomeScreen
import com.luthiworks.luthimark.ui.LuthiMarkViewModel
import com.luthiworks.luthimark.ui.SaveConflict
import com.luthiworks.luthimark.ui.Workspace
import com.luthiworks.luthimark.ui.WorkspaceDrawerContent
import com.luthiworks.luthimark.ui.theme.LuthiMarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: LuthiMarkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleViewIntent(intent)
        setContent {
            LuthiMarkTheme {
                LuthiMarkApp(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleViewIntent(intent)
    }

    private fun handleViewIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        viewModel.openIntentUri(uri)
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun LuthiMarkApp(viewModel: LuthiMarkViewModel) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val navigator = rememberListDetailPaneScaffoldNavigator<Uri>()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val status = viewModel.statusMessage
    LaunchedEffect(status) {
        if (status != null) {
            snackbarHostState.showSnackbar(status)
            viewModel.clearStatus()
        }
    }

    val pendingDetail = viewModel.pendingDetailNavigation
    LaunchedEffect(pendingDetail) {
        if (pendingDetail != null) {
            navigator.navigateTo(ListDetailPaneScaffoldRole.Detail, pendingDetail)
            viewModel.consumePendingDetailNavigation()
        }
    }

    // On a phone the open file covers the list; on a wide screen both show at once.
    val listVisible =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.List] == PaneAdaptedValue.Expanded
    val detailVisible =
        navigator.scaffoldValue[ListDetailPaneScaffoldRole.Detail] == PaneAdaptedValue.Expanded

    // Back (gesture or button) leaves a file through the scaffold, which never told
    // the view model, so the file stayed "open" and the next back exited the app
    // instead of going up a folder. Close the file whenever its pane goes away.
    var detailWasVisible by remember { mutableStateOf(detailVisible) }
    LaunchedEffect(detailVisible) {
        if (detailWasVisible && !detailVisible) viewModel.closeFile()
        detailWasVisible = detailVisible
    }

    // Edits live only in memory until saved, so anything that would replace the
    // open file asks first. (On a wide screen the list stays tappable mid-edit.)
    var pendingDiscard by remember { mutableStateOf<(() -> Unit)?>(null) }
    val unlessUnsaved: (() -> Unit) -> Unit = { action ->
        if (viewModel.isDirty) {
            pendingDiscard = action
        } else {
            action()
        }
    }

    var showWorkspacePicker by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            showWorkspacePicker = false
            viewModel.saveExternalToWorkspace(uri)
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) viewModel.openIntentUri(uri)
    }

    // Up a folder whenever the list is on screen. While a file covers the list,
    // the scaffold's own back handling returns to the list first.
    BackHandler(enabled = viewModel.pathStack.isNotEmpty() && listVisible) {
        viewModel.goUp()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
      Box(modifier = Modifier.fillMaxSize().safeDrawingPadding()) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                WorkspaceDrawerContent(
                    workspaces = viewModel.workspaces,
                    currentRoot = viewModel.rootUri,
                    onSwitch = { uri -> unlessUnsaved { viewModel.switchWorkspace(uri) } },
                    onAdd = { uri -> unlessUnsaved { viewModel.addWorkspace(uri) } },
                    onRemove = { uri ->
                        // Removing the active workspace closes the open file.
                        if (uri == viewModel.rootUri) {
                            unlessUnsaved { viewModel.removeWorkspace(uri) }
                        } else {
                            viewModel.removeWorkspace(uri)
                        }
                    },
                    onCloseDrawer = { scope.launch { drawerState.close() } },
                )
            },
        ) {
        NavigableListDetailPaneScaffold(
            navigator = navigator,
            listPane = {
                AnimatedPane {
                    HomeScreen(
                        rootUri = viewModel.rootUri,
                        rootName = viewModel.rootName,
                        pathStack = viewModel.pathStack,
                        folders = viewModel.folders,
                        files = viewModel.files,
                        recents = viewModel.recents,
                        starred = viewModel.starred,
                        isStarred = viewModel::isStarred,
                        canGoUp = viewModel.canGoUp,
                        loading = viewModel.loadingFiles,
                        hasAnyWorkspace = viewModel.workspaces.isNotEmpty(),
                        onAddWorkspace = { uri -> unlessUnsaved { viewModel.addWorkspace(uri) } },
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onRefresh = viewModel::refreshContents,
                        onEnterFolder = viewModel::enterFolder,
                        onGoUp = viewModel::goUp,
                        onBreadcrumbClick = viewModel::goToBreadcrumb,
                        onFileClick = { file ->
                            unlessUnsaved {
                                viewModel.openFile(file)
                                scope.launch {
                                    navigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail,
                                        file.uri,
                                    )
                                }
                            }
                        },
                        onEntryClick = { entry ->
                            unlessUnsaved {
                                viewModel.openRecent(entry)
                                scope.launch {
                                    navigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail,
                                        entry.fileUri,
                                    )
                                }
                            }
                        },
                        onToggleStarFile = viewModel::toggleStarFile,
                        onToggleStarEntry = viewModel::toggleStarFromEntry,
                        onCreateFile = { name ->
                            unlessUnsaved {
                                viewModel.createFile(name) { newFile ->
                                    viewModel.openFile(newFile)
                                    scope.launch {
                                        navigator.navigateTo(
                                            ListDetailPaneScaffoldRole.Detail,
                                            newFile.uri,
                                        )
                                    }
                                }
                            }
                        },
                        onOpenFile = { unlessUnsaved { documentPicker.launch(arrayOf("*/*")) } },
                    )
                }
            },
            detailPane = {
                AnimatedPane {
                    val selected = viewModel.selectedFile
                    if (selected != null) {
                        FileScreen(
                            fileName = selected.name,
                            content = viewModel.content,
                            editing = viewModel.editing,
                            isDirty = viewModel.isDirty,
                            loading = viewModel.loadingContent,
                            saving = viewModel.saving,
                            resolveImageUri = viewModel::resolveImageUri,
                            onContentChange = viewModel::updateContent,
                            onToggleEdit = viewModel::toggleEditing,
                            onSave = viewModel::save,
                            onBack = {
                                viewModel.closeFile()
                                scope.launch { navigator.navigateBack() }
                            },
                            onSaveToWorkspace = if (viewModel.isExternalFile) {
                                { showWorkspacePicker = true }
                            } else null,
                        )
                    } else {
                        DetailPlaceholder()
                    }
                }
            },
        )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
      }
    }

    if (showWorkspacePicker) {
        WorkspacePickerDialog(
            workspaces = viewModel.workspaces,
            onPick = { uri ->
                showWorkspacePicker = false
                viewModel.saveExternalToWorkspace(uri)
            },
            onChooseFolder = { folderPicker.launch(null) },
            onDismiss = { showWorkspacePicker = false },
        )
    }

    viewModel.saveConflict?.let { conflict ->
        SaveConflictDialog(
            conflict = conflict,
            onKeepBoth = { viewModel.resolveSaveConflict(replace = false) },
            onReplace = { viewModel.resolveSaveConflict(replace = true) },
            onCancel = viewModel::cancelSaveConflict,
        )
    }

    pendingDiscard?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingDiscard = null },
            title = { Text("Discard changes?") },
            text = {
                Text(
                    "You have unsaved edits to ${viewModel.selectedFile?.name ?: "this file"}. " +
                        "Continuing will lose them."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDiscard = null
                    action()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDiscard = null }) { Text("Keep editing") }
            },
        )
    }
}

@Composable
private fun SaveConflictDialog(
    conflict: SaveConflict,
    onKeepBoth: () -> Unit,
    onReplace: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("File already exists") },
        text = {
            Text(
                "\"${conflict.fileName}\" already exists in ${conflict.workspaceName}. " +
                    "Replace it, or keep both and save this copy as \"${conflict.alternateName}\"?"
            )
        },
        confirmButton = {
            TextButton(onClick = onKeepBoth) { Text("Keep both") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onCancel) { Text("Cancel") }
                TextButton(onClick = onReplace) {
                    Text("Replace", color = MaterialTheme.colorScheme.error)
                }
            }
        },
    )
}

@Composable
private fun WorkspacePickerDialog(
    workspaces: List<Workspace>,
    onPick: (Uri) -> Unit,
    onChooseFolder: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save to workspace") },
        text = {
            if (workspaces.isEmpty()) {
                Text("No workspaces yet. Choose a folder to save into.")
            } else {
                LazyColumn {
                    items(workspaces, key = { it.uri.toString() }) { workspace ->
                        ListItem(
                            headlineContent = { Text(workspace.name) },
                            modifier = Modifier
                                .clickable { onPick(workspace.uri) }
                                .padding(vertical = 4.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onChooseFolder) {
                Text(if (workspaces.isEmpty()) "Choose folder" else "Choose another folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun DetailPlaceholder() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                "Select a file",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
