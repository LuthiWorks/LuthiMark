package com.luthiworks.luthimark.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MarkdownFile(
    val uri: Uri,
    val name: String,
    val lastModified: Long,
    val sizeBytes: Long,
)

data class MarkdownFolder(
    val uri: Uri,
    val name: String,
)

data class FolderContents(
    val folders: List<MarkdownFolder>,
    val files: List<MarkdownFile>,
) {
    val isEmpty: Boolean get() = folders.isEmpty() && files.isEmpty()

    companion object {
        val Empty = FolderContents(emptyList(), emptyList())
    }
}

class MarkdownRepository(private val context: Context) {

    suspend fun rootName(rootUri: Uri): String? = withContext(Dispatchers.IO) {
        DocumentFile.fromTreeUri(context, rootUri)?.name
    }

    suspend fun listContents(rootUri: Uri, subPath: List<String>): FolderContents =
        withContext(Dispatchers.IO) {
            val folder = walkTo(rootUri, subPath) ?: return@withContext FolderContents.Empty
            val children = folder.listFiles()
            val folders = children.asSequence()
                .filter { it.isDirectory }
                .map { MarkdownFolder(it.uri, it.name ?: "(unnamed)") }
                .sortedBy { it.name.lowercase() }
                .toList()
            val files = children.asSequence()
                .filter { it.isFile && it.name?.endsWithMarkdown() == true }
                .map {
                    MarkdownFile(
                        uri = it.uri,
                        name = it.name ?: "(unnamed)",
                        lastModified = it.lastModified(),
                        sizeBytes = it.length(),
                    )
                }
                .sortedByDescending { it.lastModified }
                .toList()
            FolderContents(folders, files)
        }

    suspend fun read(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
        }.getOrNull()
    }

    suspend fun validateOrLocate(entry: RecentEntry): RecentEntry? = withContext(Dispatchers.IO) {
        if (documentExists(entry.fileUri)) return@withContext entry
        val located = findByName(entry.workspaceUri, entry.fileName) ?: return@withContext null
        entry.copy(fileUri = located.uri, subPath = located.subPath)
    }

    private fun documentExists(uri: Uri): Boolean = runCatching {
        DocumentFile.fromSingleUri(context, uri)?.exists() == true
    }.getOrDefault(false)

    private data class LocatedFile(val uri: Uri, val subPath: List<String>)

    private fun findByName(workspaceUri: Uri, fileName: String): LocatedFile? {
        val root = DocumentFile.fromTreeUri(context, workspaceUri) ?: return null
        return searchByName(root, fileName, mutableListOf())
    }

    private fun searchByName(
        folder: DocumentFile,
        target: String,
        path: MutableList<String>,
    ): LocatedFile? {
        val children = runCatching { folder.listFiles() }.getOrDefault(emptyArray())
        for (child in children) {
            if (child.isFile && child.name == target) {
                return LocatedFile(child.uri, path.toList())
            }
        }
        for (child in children) {
            if (child.isDirectory) {
                val childName = child.name ?: continue
                path.add(childName)
                val found = searchByName(child, target, path)
                path.removeAt(path.lastIndex)
                if (found != null) return found
            }
        }
        return null
    }

    suspend fun write(uri: Uri, content: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.bufferedWriter().use { it.write(content) }
            }
            true
        }.getOrElse { false }
    }

    suspend fun createMarkdownFile(
        rootUri: Uri,
        subPath: List<String>,
        fileName: String,
    ): Uri? = withContext(Dispatchers.IO) {
        val folder = walkTo(rootUri, subPath) ?: return@withContext null
        val safeName = if (fileName.endsWithMarkdown()) fileName else "$fileName.md"
        val existing = folder.findFile(safeName)
        if (existing != null) return@withContext existing.uri
        folder.createFile("text/markdown", safeName)?.uri
    }

    private fun walkTo(rootUri: Uri, subPath: List<String>): DocumentFile? {
        var current: DocumentFile = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        for (segment in subPath) {
            current = current.findFile(segment) ?: return null
            if (!current.isDirectory) return null
        }
        return current
    }

    fun resolveRelativeImage(
        rootUri: Uri,
        containingPath: List<String>,
        relativePath: String,
    ): Uri? {
        val parts = relativePath.split('/').filter { it.isNotBlank() && it != "." }
        if (parts.isEmpty()) return null
        var folder: DocumentFile = DocumentFile.fromTreeUri(context, rootUri) ?: return null
        val resolvedPath = mutableListOf<String>()
        resolvedPath.addAll(containingPath)
        for (part in parts.dropLast(1)) {
            if (part == "..") {
                if (resolvedPath.isEmpty()) return null
                resolvedPath.removeAt(resolvedPath.lastIndex)
            } else {
                resolvedPath.add(part)
            }
        }
        for (segment in resolvedPath) {
            folder = folder.findFile(segment) ?: return null
            if (!folder.isDirectory) return null
        }
        val file = folder.findFile(parts.last()) ?: return null
        return if (file.isFile) file.uri else null
    }

    private fun String.endsWithMarkdown(): Boolean =
        endsWith(".md", ignoreCase = true) || endsWith(".markdown", ignoreCase = true)
}
