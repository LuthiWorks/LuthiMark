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
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.foundation.clickable
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

    BackHandler(enabled = viewModel.pathStack.isNotEmpty() && viewModel.selectedFile == null) {
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
                    onSwitch = viewModel::switchWorkspace,
                    onAdd = viewModel::addWorkspace,
                    onRemove = viewModel::removeWorkspace,
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
                        onAddWorkspace = viewModel::addWorkspace,
                        onOpenDrawer = { scope.launch { drawerState.open() } },
                        onRefresh = viewModel::refreshContents,
                        onEnterFolder = viewModel::enterFolder,
                        onGoUp = viewModel::goUp,
                        onBreadcrumbClick = viewModel::goToBreadcrumb,
                        onFileClick = { file ->
                            viewModel.openFile(file)
                            scope.launch {
                                navigator.navigateTo(
                                    ListDetailPaneScaffoldRole.Detail,
                                    file.uri,
                                )
                            }
                        },
                        onEntryClick = { entry ->
                            viewModel.openRecent(entry)
                            scope.launch {
                                navigator.navigateTo(
                                    ListDetailPaneScaffoldRole.Detail,
                                    entry.fileUri,
                                )
                            }
                        },
                        onToggleStarFile = viewModel::toggleStarFile,
                        onToggleStarEntry = viewModel::toggleStarFromEntry,
                        onCreateFile = { name ->
                            viewModel.createFile(name) { newFile ->
                                viewModel.openFile(newFile)
                                scope.launch {
                                    navigator.navigateTo(
                                        ListDetailPaneScaffoldRole.Detail,
                                        newFile.uri,
                                    )
                                }
                            }
                        },
                        onOpenFile = { documentPicker.launch(arrayOf("*/*")) },
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
