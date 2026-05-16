package com.luthiworks.luthimark.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.coil3.Coil3ImageTransformerImpl
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.m3.Markdown
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.findChildOfType
import org.intellij.markdown.ast.getTextInNode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileScreen(
    fileName: String,
    content: String,
    editing: Boolean,
    isDirty: Boolean,
    loading: Boolean,
    saving: Boolean,
    resolveImageUri: (String) -> Uri?,
    onContentChange: (String) -> Unit,
    onToggleEdit: (Boolean) -> Unit,
    onSave: () -> Unit,
    onBack: () -> Unit,
) {
    var showDiscardDialog by remember { mutableStateOf(false) }
    var searchActive by remember(fileName) { mutableStateOf(false) }
    var searchQuery by remember(fileName) { mutableStateOf("") }
    var currentMatchIndex by remember(fileName) { mutableIntStateOf(0) }
    var pendingSelection by remember { mutableStateOf<TextRange?>(null) }
    val viewerScrollState = rememberScrollState()

    val activateSearch: () -> Unit = { searchActive = true }
    val closeSearch: () -> Unit = {
        searchActive = false
        searchQuery = ""
    }

    val matches = remember(content, searchQuery) { findMatches(content, searchQuery) }

    LaunchedEffect(searchQuery) {
        currentMatchIndex = 0
    }

    LaunchedEffect(matches, currentMatchIndex, editing) {
        if (matches.isEmpty() || currentMatchIndex !in matches.indices) return@LaunchedEffect
        val match = matches[currentMatchIndex]
        if (editing) {
            pendingSelection = TextRange(match.first, match.last + 1)
        } else if (content.isNotEmpty() && viewerScrollState.maxValue > 0) {
            val fraction = match.first.toFloat() / content.length.toFloat()
            val target = (viewerScrollState.maxValue * fraction).toInt()
                .coerceIn(0, viewerScrollState.maxValue)
            viewerScrollState.animateScrollTo(target)
        }
    }

    val handleBack: () -> Unit = {
        when {
            searchActive -> closeSearch()
            isDirty -> showDiscardDialog = true
            else -> onBack()
        }
    }

    BackHandler(enabled = isDirty || searchActive) {
        when {
            searchActive -> closeSearch()
            isDirty -> showDiscardDialog = true
        }
    }

    Scaffold(
        topBar = {
            if (searchActive) {
                SearchTopAppBar(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    matchCount = matches.size,
                    currentIndex = if (matches.isEmpty()) -1 else currentMatchIndex,
                    onPrev = {
                        if (matches.isNotEmpty()) {
                            currentMatchIndex = (currentMatchIndex - 1 + matches.size) % matches.size
                        }
                    },
                    onNext = {
                        if (matches.isNotEmpty()) {
                            currentMatchIndex = (currentMatchIndex + 1) % matches.size
                        }
                    },
                    onClose = closeSearch,
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            if (isDirty) "$fileName *" else fileName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = handleBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = activateSearch) {
                            Icon(Icons.Filled.Search, contentDescription = "Search in file")
                        }
                        if (editing) {
                            IconButton(
                                onClick = onSave,
                                enabled = isDirty && !saving,
                            ) {
                                Icon(Icons.Filled.Save, contentDescription = "Save")
                            }
                            IconButton(onClick = { onToggleEdit(false) }) {
                                Icon(Icons.Filled.Visibility, contentDescription = "Preview")
                            }
                        } else {
                            IconButton(onClick = { onToggleEdit(true) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "Edit")
                            }
                        }
                    },
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
                editing -> EditorView(
                    content = content,
                    selectionRequest = pendingSelection,
                    onSelectionConsumed = { pendingSelection = null },
                    onContentChange = onContentChange,
                )
                else -> ViewerView(
                    content = content,
                    resolveImageUri = resolveImageUri,
                    scrollState = viewerScrollState,
                )
            }
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved edits. Going back will lose them.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchTopAppBar(
    query: String,
    onQueryChange: (String) -> Unit,
    matchCount: Int,
    currentIndex: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close search")
            }
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester),
                placeholder = { Text("Find in file") },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            )
            val countLabel = when {
                query.isEmpty() -> ""
                matchCount == 0 -> "0"
                else -> "${currentIndex + 1}/$matchCount"
            }
            if (countLabel.isNotEmpty()) {
                Text(
                    text = countLabel,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            IconButton(onClick = onPrev, enabled = matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Previous match")
            }
            IconButton(onClick = onNext, enabled = matchCount > 0) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Next match")
            }
        }
    }
}

@Composable
private fun ViewerView(
    content: String,
    resolveImageUri: (String) -> Uri?,
    scrollState: androidx.compose.foundation.ScrollState,
) {
    if (content.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("(empty file)", style = MaterialTheme.typography.bodyMedium)
        }
        return
    }
    val displayContent = remember(content) {
        rewriteRelativeImages(content, resolveImageUri)
    }
    val components = markdownComponents(
        codeFence = { model ->
            val node = model.node
            val src = model.content
            val language = node.findChildOfType(MarkdownTokenTypes.FENCE_LANG)
                ?.getTextInNode(src)?.toString()?.trim()
            val codeNodes = node.children.filter { it.type == MarkdownTokenTypes.CODE_FENCE_CONTENT }
            val code = if (codeNodes.isNotEmpty()) {
                val s = codeNodes.first().startOffset
                val e = codeNodes.last().endOffset
                src.substring(s, e)
            } else {
                src.substring(node.startOffset, node.endOffset)
            }
            HighlightedCode(code = code, language = language)
        },
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Markdown(
            content = displayContent,
            imageTransformer = Coil3ImageTransformerImpl,
            components = components,
        )
    }
}

private val IMAGE_LINK_REGEX = Regex("""!\[([^\]]*)\]\(([^)\s]+)(?:\s+"([^"]*)")?\)""")

private fun rewriteRelativeImages(
    source: String,
    resolveImageUri: (String) -> Uri?,
): String = IMAGE_LINK_REGEX.replace(source) { match ->
    val alt = match.groupValues[1]
    val src = match.groupValues[2]
    val title = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }
    if (
        src.startsWith("http://") ||
        src.startsWith("https://") ||
        src.startsWith("content://") ||
        src.startsWith("file://") ||
        src.startsWith("data:")
    ) {
        match.value
    } else {
        val resolved = resolveImageUri(src)?.toString()
        if (resolved == null) "*[image not found: $src]*"
        else if (title != null) "![$alt]($resolved \"$title\")"
        else "![$alt]($resolved)"
    }
}

private fun findMatches(content: String, query: String): List<IntRange> {
    if (query.isBlank() || content.isEmpty()) return emptyList()
    val results = mutableListOf<IntRange>()
    var idx = 0
    while (idx <= content.length - query.length) {
        val found = content.indexOf(query, idx, ignoreCase = true)
        if (found < 0) break
        results.add(found until found + query.length)
        idx = found + query.length.coerceAtLeast(1)
    }
    return results
}

@Composable
private fun EditorView(
    content: String,
    selectionRequest: TextRange?,
    onSelectionConsumed: () -> Unit,
    onContentChange: (String) -> Unit,
) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(content)) }

    LaunchedEffect(content) {
        if (fieldValue.text != content) {
            fieldValue = TextFieldValue(
                text = content,
                selection = fieldValue.selection.coerceIn(content.length),
            )
        }
    }

    LaunchedEffect(selectionRequest) {
        val req = selectionRequest ?: return@LaunchedEffect
        if (req != fieldValue.selection) {
            fieldValue = fieldValue.copy(selection = req)
        }
        onSelectionConsumed()
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = {
            fieldValue = it
            if (it.text != content) onContentChange(it.text)
        },
        modifier = Modifier.fillMaxSize().padding(8.dp),
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = { Text("Start writing markdown...") },
    )
}

private fun TextRange.coerceIn(maxLen: Int): TextRange {
    val s = start.coerceIn(0, maxLen)
    val e = end.coerceIn(0, maxLen)
    return TextRange(s, e)
}
