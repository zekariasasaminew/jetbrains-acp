package com.agentport.jetbrains

import com.agentclientprotocol.client.*
import com.agentclientprotocol.common.*
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.StdioTransport
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.nio.file.Paths

typealias ProcessFactory = (command: List<String>, cwd: String) -> Process

class AcpClient(
    private val onPermissionRequest: suspend (
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
    ) -> RequestPermissionResponse,
    private val onFileWrite: suspend (path: String, newContent: String) -> Boolean,
    private val processFactory: ProcessFactory = defaultProcessFactory,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var process: Process? = null
    private var session: ClientSession? = null
    private var currentInfo: AgentSession? = null

    val activeSession: AgentSession? get() = currentInfo

    suspend fun connect(agent: Agent, cwd: String) {
        val cmd = listOf(agent.command) + agent.args
        val proc = processFactory(cmd, cwd)
        process = proc

        val transport = StdioTransport(
            parentScope = scope,
            ioDispatcher = Dispatchers.IO,
            input = proc.inputStream.asSource().buffered(),
            output = proc.outputStream.asSink().buffered(),
        )
        val protocol = Protocol(scope, transport)
        val client = Client(protocol)

        protocol.start()

        client.initialize(
            ClientInfo(
                capabilities = ClientCapabilities(
                    fs = FileSystemCapability(readTextFile = true, writeTextFile = true),
                    terminal = true,
                )
            )
        )

        val clientSession = client.newSession(
            SessionCreationParameters(cwd, emptyList())
        ) { _, _ -> SessionOps() }

        session = clientSession
        currentInfo = AgentSession(
            sessionId = clientSession.sessionId.value,
            cwd = cwd,
            agent = agent,
        )
    }

    fun prompt(text: String): Flow<AcpEvent> = flow {
        val s = checkNotNull(session) { "Not connected — call connect() first" }
        s.prompt(listOf(ContentBlock.Text(text))).collect { event ->
            when (event) {
                is Event.SessionUpdateEvent -> {
                    when (val update = event.update) {
                        is SessionUpdate.AgentMessageChunk -> {
                            val chunk = update.content
                            if (chunk is ContentBlock.Text) emit(AcpEvent.TextChunk(chunk.text))
                        }
                        is SessionUpdate.AgentThoughtChunk -> {
                            val chunk = update.content
                            if (chunk is ContentBlock.Text) emit(AcpEvent.ThoughtChunk(chunk.text))
                        }
                        is SessionUpdate.ToolCallUpdate -> {
                            val title = update.title ?: ""
                            val kind  = update.kind?.name?.lowercase() ?: "other"
                            if (title.isNotBlank() || kind != "other") emit(AcpEvent.ToolCall(title, kind))
                        }
                        else -> Unit
                    }
                }
                is Event.PromptResponseEvent -> emit(AcpEvent.Done(event.response.stopReason))
            }
        }
    }

    fun disconnect() {
        scope.cancel()
        process?.destroyForcibly()
        process = null
        session = null
        currentInfo = null
    }

    private inner class SessionOps : ClientSessionOperations, FileSystemOperations, TerminalOperations {

        override suspend fun requestPermissions(
            toolCall: SessionUpdate.ToolCallUpdate,
            permissions: List<PermissionOption>,
            _meta: JsonElement?,
        ): RequestPermissionResponse = onPermissionRequest(toolCall, permissions)

        override suspend fun notify(update: SessionUpdate, _meta: JsonElement?) = Unit

        override suspend fun fsReadTextFile(
            path: String, line: UInt?, limit: UInt?, _meta: JsonElement?,
        ): ReadTextFileResponse {
            val content = Paths.get(path).toFile().readText()
            return ReadTextFileResponse(content)
        }

        override suspend fun fsWriteTextFile(
            path: String, content: String, _meta: JsonElement?,
        ): WriteTextFileResponse {
            onFileWrite(path, content)
            return WriteTextFileResponse()
        }

        override suspend fun terminalCreate(
            command: String, args: List<String>, cwd: String?,
            env: List<EnvVariable>, outputByteLimit: ULong?, _meta: JsonElement?,
        ): CreateTerminalResponse {
            val proc = ProcessBuilder(listOf(command) + args).apply {
                cwd?.let { directory(java.io.File(it)) }
                env.forEach { environment()[it.name] = it.value }
            }.start()
            activeTerminals[proc.pid().toString()] = proc
            return CreateTerminalResponse(proc.pid().toString())
        }

        override suspend fun terminalOutput(
            terminalId: String, _meta: JsonElement?,
        ): TerminalOutputResponse {
            val proc = activeTerminals[terminalId] ?: return TerminalOutputResponse("", truncated = false)
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            return TerminalOutputResponse(if (err.isBlank()) out else "$out\n$err", truncated = false)
        }

        override suspend fun terminalWaitForExit(
            terminalId: String, _meta: JsonElement?,
        ): WaitForTerminalExitResponse {
            val code = activeTerminals[terminalId]?.waitFor() ?: 0
            return WaitForTerminalExitResponse(code.toUInt())
        }

        override suspend fun terminalKill(
            terminalId: String, _meta: JsonElement?,
        ): KillTerminalCommandResponse {
            activeTerminals.remove(terminalId)?.destroy()
            return KillTerminalCommandResponse()
        }

        override suspend fun terminalRelease(
            terminalId: String, _meta: JsonElement?,
        ): ReleaseTerminalResponse {
            activeTerminals.remove(terminalId)
            return ReleaseTerminalResponse()
        }
    }

    private val activeTerminals = mutableMapOf<String, Process>()

    companion object {
        private val isWindows = System.getProperty("os.name", "").startsWith("Windows")

        val defaultProcessFactory: ProcessFactory = { command, cwd ->
            // On Windows, bare command names (.cmd/.bat) require cmd /c to be resolved by the shell
            val actualCommand = if (isWindows && !command.first().contains(File.separatorChar)) {
                listOf("cmd", "/c") + command
            } else {
                command
            }
            ProcessBuilder(actualCommand)
                .directory(File(cwd))
                .redirectInput(ProcessBuilder.Redirect.PIPE)
                .redirectOutput(ProcessBuilder.Redirect.PIPE)
                .redirectError(ProcessBuilder.Redirect.PIPE)
                .start()
        }
    }
}
