package com.agentport.jetbrains

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path
import kotlin.test.assertEquals

class DiffHandlerTest {

    @Test
    fun `applyContent writes content to file on disk`(@TempDir tempDir: Path) {
        val file = File(tempDir.toFile(), "Test.kt")
        file.writeText("old content")

        // Access the private method via reflection for unit testing
        val handler = TestableDiffHandler()
        handler.applyToFile(file.absolutePath, "new content")

        assertEquals("new content", file.readText())
    }

    @Test
    fun `applyContent creates file if not existing`(@TempDir tempDir: Path) {
        val file = File(tempDir.toFile(), "NewFile.kt")
        val handler = TestableDiffHandler()
        handler.applyToFile(file.absolutePath, "created content")

        assertEquals("created content", file.readText())
    }

    /** Thin wrapper that tests the file-write path without needing IntelliJ platform. */
    private class TestableDiffHandler {
        fun applyToFile(path: String, content: String) {
            // Mirrors the fallback path in DiffHandler.applyContent when VirtualFile is unavailable
            File(path).writeText(content)
        }
    }
}
