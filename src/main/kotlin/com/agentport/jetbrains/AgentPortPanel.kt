package com.agentport.jetbrains

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import kotlinx.coroutines.*
import com.intellij.openapi.components.service
import kotlinx.coroutines.swing.Swing
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Font
import javax.swing.*

class AgentPortPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val outputArea = JBTextArea().apply {
        isEditable = false
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        background = JBColor.background()
    }

    private val inputField = JTextArea(3, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
    }

    private val sendButton = JButton("Send").apply {
        addActionListener { sendPrompt() }
    }

    private val agentSelector = JComboBox<Agent>().apply {
        renderer = object : DefaultListCellRenderer() {
            override fun getText() = (selectedItem as? Agent)?.displayName ?: "Select agent"
        }
    }

    private val statusLabel = JLabel("").apply {
        font = Font(Font.SANS_SERIF, Font.ITALIC, 12)
        foreground = JBColor.GRAY
    }

    private val uiScope = CoroutineScope(Dispatchers.Swing + SupervisorJob())
    private var client: AcpClient? = null

    init {
        setContent(buildLayout())
        refreshAgentList()
        // Add listener AFTER refreshAgentList so initial population doesn't trigger reconnect
        agentSelector.addActionListener { reconnect() }
        // Connect to whichever agent is pre-selected
        reconnect()
    }

    private fun buildLayout(): JPanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(outputArea), BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            add(statusLabel, BorderLayout.NORTH)
            add(buildInputBar(), BorderLayout.CENTER)
        }, BorderLayout.SOUTH)
    }

    private fun buildInputBar(): JPanel = JPanel(BorderLayout()).apply {
        add(agentSelector, BorderLayout.NORTH)
        add(JBScrollPane(inputField).apply {
            preferredSize = Dimension(0, 80)
        }, BorderLayout.CENTER)
        add(sendButton, BorderLayout.EAST)
    }

    private fun refreshAgentList() {
        val settings = service<PluginSettings>()
        val agents = AgentRegistry.detectAvailable()
        agentSelector.removeAllItems()
        agents.forEach { agentSelector.addItem(it) }
        if (agents.isEmpty()) {
            appendOutput("[No ACP agents found on PATH]")
            return
        }
        val defaultId = settings.defaultAgentId
        if (defaultId.isNotEmpty()) {
            agents.indexOfFirst { it.id == defaultId }.takeIf { it >= 0 }?.let { agentSelector.selectedIndex = it }
        }
    }

    private fun reconnect() {
        val agent = agentSelector.selectedItem as? Agent ?: return
        val cwd = project.basePath ?: System.getProperty("user.home")
        client?.disconnect()

        val diffHandler = DiffHandler(project)
        val permHandler = PermissionHandler(project)

        client = AcpClient(
            onPermissionRequest = { toolCall, permissions -> permHandler.request(toolCall, permissions) },
            onFileWrite = { path, newContent -> diffHandler.showAndApply(path, newContent) },
        )

        uiScope.launch {
            try {
                client!!.connect(agent, cwd!!)
                appendOutput("[Connected to ${agent.displayName}]")
                sendButton.isEnabled = true
            } catch (e: Exception) {
                appendOutput("[Failed to connect: ${e.message}]")
            }
        }
    }

    private fun sendPrompt() {
        val text = inputField.text.trim()
        if (text.isBlank()) return
        val c = client ?: run { appendOutput("[Not connected — select an agent first]\n"); return }
        inputField.text = ""
        sendButton.isEnabled = false
        statusLabel.text = "Agent is thinking…"
        appendOutput("\nYou: $text\n")
        var firstChunk = true

        uiScope.launch {
            try {
                c.prompt(text).collect { event ->
                    when (event) {
                        is AcpEvent.TextChunk -> {
                            if (firstChunk) { statusLabel.text = ""; firstChunk = false }
                            appendOutput(event.text)
                        }
                        is AcpEvent.ToolCallStarted -> appendOutput("\n[${event.title}]\n")
                        is AcpEvent.Done -> { statusLabel.text = ""; appendOutput("\n") }
                        is AcpEvent.AgentError -> { statusLabel.text = ""; appendOutput("\n[Error: ${event.message}]\n") }
                    }
                }
            } catch (e: Exception) {
                statusLabel.text = ""
                appendOutput("\n[Error: ${e.message ?: e.javaClass.simpleName}]\n")
            } finally {
                sendButton.isEnabled = true
            }
        }
    }

    private fun appendOutput(text: String) {
        outputArea.append(text)
        outputArea.caretPosition = outputArea.document.length
    }
}
