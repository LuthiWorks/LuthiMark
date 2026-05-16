package com.luthiworks.luthimark

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.AnimatedPane
import androidx.compose.material3.adaptive.layout.ListDetailPaneScaffoldRole
import androidx.compose.material3.adaptive.navigation.NavigableListDetailPaneScaffold
import androidx.compose.material3.adaptive.navigation.rememberListDetailPaneScaffoldNavigator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.luthiworks.luthimark.ui.FileScreen
import com.luthiworks.luthimark.ui.HomeScreen
import com.luthiworks.luthimark.ui.LuthiMarkViewModel
import com.luthiworks.luthimark.ui.WorkspaceDrawerContent
import com.luthiworks.luthimark.ui.theme.LuthiMarkTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: LuthiMarkViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LuthiMarkTheme {
                LuthiMarkApp(viewModel)
            }
        }
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
