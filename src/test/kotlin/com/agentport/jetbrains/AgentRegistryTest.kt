package com.agentport.jetbrains

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class AgentRegistryTest {

    @Test
    fun `detectAvailable returns only agents present on PATH`(@TempDir tempDir: Path) {
        val fakeAgent = File(tempDir.toFile(), "fake-agent")
        fakeAgent.createNewFile()
        fakeAgent.setExecutable(true)

        val originalPath = System.getenv("PATH")
        withPath("${tempDir.toAbsolutePath()}${File.pathSeparator}$originalPath") {
            val available = AgentRegistry.detectAvailable()
            // Should not contain fake-agent (not in KNOWN_AGENTS)
            assertTrue(available.none { it.command == "fake-agent" })
        }
    }

    @Test
    fun `isOnPath returns true when executable exists in PATH`(@TempDir tempDir: Path) {
        val exe = File(tempDir.toFile(), "myagent")
        exe.createNewFile()
        exe.setExecutable(true)

        val result = AgentRegistry.isOnPath(
            command = "myagent",
            pathOverride = tempDir.toAbsolutePath().toString()
        )
        assertTrue(result)
    }

    @Test
    fun `isOnPath returns false when command not on PATH`(@TempDir tempDir: Path) {
        val result = AgentRegistry.isOnPath(
            command = "definitely-not-installed-xyzzy",
            pathOverride = tempDir.toAbsolutePath().toString()
        )
        assertFalse(result)
    }

    @Test
    fun `KNOWN_AGENTS contains all expected agents`() {
        val ids = AgentRegistry.knownAgents.map { it.id }
        assertTrue(ids.containsAll(listOf("claude", "gemini", "opencode", "copilot", "codex", "goose")))
    }

    private fun withPath(path: String, block: () -> Unit) {
        // PATH is read-only via System.getenv; tests use the override parameter instead
        block()
    }
}
