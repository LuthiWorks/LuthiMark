package com.luthiworks.luthimark.data

import org.junit.Assert.assertEquals
import org.junit.Test

class FileNamesTest {

    @Test
    fun freeNameIsKept() {
        assertEquals("notes.md", uniqueFileName("notes.md", setOf("other.md")))
    }

    @Test
    fun takenNameGetsCounterBeforeExtension() {
        assertEquals("notes (1).md", uniqueFileName("notes.md", setOf("notes.md")))
    }

    @Test
    fun counterSkipsTakenVariants() {
        val taken = setOf("notes.md", "notes (1).md", "notes (2).md")
        assertEquals("notes (3).md", uniqueFileName("notes.md", taken))
    }

    @Test
    fun matchingIgnoresCase() {
        assertEquals("Notes (1).md", uniqueFileName("Notes.md", setOf("notes.md")))
        assertEquals("notes (2).md", uniqueFileName("notes.md", setOf("NOTES.md", "Notes (1).MD")))
    }

    @Test
    fun nameWithoutExtension() {
        assertEquals("README (1)", uniqueFileName("README", setOf("README")))
    }

    @Test
    fun leadingDotIsNotAnExtension() {
        assertEquals(".env (1)", uniqueFileName(".env", setOf(".env")))
    }

    @Test
    fun onlyTheLastDotSplitsTheExtension() {
        assertEquals("v1.2 notes (1).md", uniqueFileName("v1.2 notes.md", setOf("v1.2 notes.md")))
    }
}
