package com.example.infinitedraw

import com.example.infinitedraw.storage.ProjectStorage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import kotlin.io.createTempDir

/**
 * Step 23: the pure naming helpers behind .uzw import (only these two
 * functions of ProjectStorage are JVM-safe; the zip/SAF code needs a device).
 */
class UzwImportTest {

    @Test
    fun sanitizeStripsExtensionAndUnsafeChars() {
        assertEquals("sketch", ProjectStorage.sanitizeProjectName("sketch.uzw"))
        assertEquals("my sketch", ProjectStorage.sanitizeProjectName("my sketch.zip"))
        assertEquals("photo", ProjectStorage.sanitizeProjectName("photo"))
        assertEquals("a_b_c", ProjectStorage.sanitizeProjectName("a/b\\c"))
        assertEquals("Imported project", ProjectStorage.sanitizeProjectName("..."))
        assertEquals("Imported project", ProjectStorage.sanitizeProjectName(".."))
        assertEquals("Imported project", ProjectStorage.sanitizeProjectName("   "))
    }

    @Test
    fun dedupAppendsCounter() {
        val tmp = createTempDir(prefix = "uzwtest")
        try {
            assertEquals("sketch", ProjectStorage.dedupFolderName(tmp, "sketch"))
            File(tmp, "sketch").mkdirs()
            assertEquals("sketch (2)", ProjectStorage.dedupFolderName(tmp, "sketch"))
            File(tmp, "sketch (2)").mkdirs()
            File(tmp, "sketch (3)").mkdirs()
            assertEquals("sketch (4)", ProjectStorage.dedupFolderName(tmp, "sketch"))
        } finally {
            tmp.deleteRecursively()
        }
    }
}
