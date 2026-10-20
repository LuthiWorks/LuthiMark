package com.luthiworks.luthimark.data

/**
 * Returns [name] if no entry in [taken] matches it, otherwise the first free
 * "base (n).ext" variant — e.g. "notes.md" becomes "notes (1).md".
 *
 * Matching ignores case, because shared storage on Android usually does too:
 * "Notes.md" and "notes.md" would collide on disk.
 */
internal fun uniqueFileName(name: String, taken: Set<String>): String {
    val takenLower = taken.mapTo(HashSet()) { it.lowercase() }
    if (name.lowercase() !in takenLower) return name
    val dot = name.lastIndexOf('.')
    // A leading dot (".env") is part of the name, not an extension.
    val base = if (dot > 0) name.substring(0, dot) else name
    val ext = if (dot > 0) name.substring(dot) else ""
    var n = 1
    while (true) {
        val candidate = "$base ($n)$ext"
        if (candidate.lowercase() !in takenLower) return candidate
        n++
    }
}
