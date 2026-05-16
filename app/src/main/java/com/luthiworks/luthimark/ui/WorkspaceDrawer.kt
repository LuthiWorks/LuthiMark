package com.luthiworks.luthimark.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun WorkspaceDrawerContent(
    workspaces: List<Workspace>,
    currentRoot: Uri?,
    onSwitch: (Uri) -> Unit,
    onAdd: (Uri) -> Unit,
    onRemove: (Uri) -> Unit,
    onCloseDrawer: () -> Unit,
) {
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            onAdd(uri)
            onCloseDrawer()
        }
    }

    var pendingRemoval by remember { mutableStateOf<Workspace?>(null) }

    ModalDrawerSheet {
        Text(
            "Workspaces",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        HorizontalDivider()
        if (workspaces.isEmpty()) {
            Text(
                "No workspaces yet. Add one below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                items(workspaces, key = { it.uri.toString() }) { workspace ->
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Filled.Folder, contentDescription = null) },
                        label = { Text(workspace.name) },
                        selected = workspace.uri == currentRoot,
                        onClick = {
                            onSwitch(workspace.uri)
                            onCloseDrawer()
                        },
                        badge = {
                            IconButton(onClick = { pendingRemoval = workspace }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove ${workspace.name}",
                                )
                            }
                        },
                        modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        NavigationDrawerItem(
            icon = { Icon(Icons.Filled.CreateNewFolder, contentDescription = null) },
            label = { Text("Add workspace") },
            selected = false,
            onClick = { folderPicker.launch(null) },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        Spacer(modifier = Modifier.height(8.dp))
    }

    val toRemove = pendingRemoval
    if (toRemove != null) {
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove workspace?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("'${toRemove.name}' will be removed from your workspaces.")
                    Text(
                        "Files in the folder are not deleted — only the LuthiMark shortcut is removed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onRemove(toRemove.uri)
                    pendingRemoval = null
                }) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") }
            },
        )
    }
}
