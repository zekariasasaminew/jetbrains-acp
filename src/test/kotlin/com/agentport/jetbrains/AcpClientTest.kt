package com.agentport.jetbrains

import com.agentclientprotocol.model.*
import io.mockk.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class AcpClientTest {

    private val testAgent = Agent("test", "Test Agent", "test-agent", listOf("--acp"))

    @Test
    fun `activeSession is null before connect`() {
        val client = AcpClient(
            onPermissionRequest = { _, _ -> error("not expected") },
            onFileWrite = { _, _ -> true },
        )
        assertNull(client.activeSession)
    }

    @Test
    fun `activeSession reflects agent and cwd after connect`() = runTest {
        // TODO: Integration test — requires a real ACP agent subprocess.
        // OSS contributors: mock the StdioTransport to test the full connect() flow.
    }

    @Test
    fun `prompt throws if not connected`() = runTest {
        val client = AcpClient(
            onPermissionRequest = { _, _ -> error("not expected") },
            onFileWrite = { _, _ -> true },
        )
        val ex = assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking {
                client.prompt("hello").toList()
            }
        }
        assertTrue(ex.message!!.contains("connect()"))
    }

    @Test
    fun `disconnect clears activeSession`() = runTest {
        val client = AcpClient(
            onPermissionRequest = { _, _ -> error("not expected") },
            onFileWrite = { _, _ -> true },
        )
        // Even without connecting, disconnect() should be a no-op
        client.disconnect()
        assertNull(client.activeSession)
    }

    @Test
    fun `defaultProcessFactory builds command correctly`() {
        // Smoke test: verify the factory creates a process for a real command
        // (uses system 'java' which is guaranteed to exist in the test environment)
        val factory = AcpClient.defaultProcessFactory
        val proc = factory(listOf("java", "-version"), System.getProperty("user.dir"))
        assertNotNull(proc)
        proc.destroy()
    }
}
