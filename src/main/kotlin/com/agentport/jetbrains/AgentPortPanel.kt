package com.agentport.jetbrains

import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.text.*

class AgentPortPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    // Output pane with styled document for visual message differentiation
    private val outputPane = JTextPane().apply {
        isEditable = false
        background = JBColor.background()
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }
    private val doc: StyledDocument = outputPane.styledDocument

    private val userLabelStyle: Style = doc.addStyle("userLabel", null).also {
        StyleConstants.setForeground(it, JBColor(Color(0x2470B3), Color(0x589DF6)))
        StyleConstants.setBold(it, true)
    }
    private val bodyStyle: Style = doc.addStyle("body", null).also {
        StyleConstants.setForeground(it, JBColor.foreground())
    }
    private val agentStyle: Style = doc.addStyle("agent", null).also {
        StyleConstants.setForeground(it, JBColor.foreground())
    }
    private val metaStyle: Style = doc.addStyle("meta", null).also {
        StyleConstants.setForeground(it, JBColor.GRAY)
        StyleConstants.setItalic(it, true)
    }

    // Enter = send, Shift+Enter = new line
    private val inputField = JTextArea(3, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) {
                    e.consume()
                    sendPrompt()
                }
            }
        })
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
        border = BorderFactory.createEmptyBorder(2, 6, 2, 0)
    }

    private val hintLabel = JLabel("Enter to send  ·  Shift+Enter for new line").apply {
        font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        foreground = JBColor.GRAY
        border = BorderFactory.createEmptyBorder(2, 6, 2, 0)
    }

    private val uiScope = CoroutineScope(Dispatchers.Swing + SupervisorJob())
    private var client: AcpClient? = null

    init {
        setContent(buildLayout())
        refreshAgentList()
        agentSelector.addActionListener { reconnect() }
        reconnect()
    }

    private fun buildLayout(): JPanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(outputPane), BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            add(JPanel(BorderLayout()).apply {
                add(statusLabel, BorderLayout.WEST)
                add(hintLabel, BorderLayout.EAST)
            }, BorderLayout.NORTH)
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
            appendMeta("[No ACP agents found on PATH]\n")
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
                appendMeta("[Connected to ${agent.displayName}]\n")
                sendButton.isEnabled = true
            } catch (e: Exception) {
                appendMeta("[Failed to connect: ${e.message}]\n")
            }
        }
    }

    private fun sendPrompt() {
        val text = inputField.text.trim()
        if (text.isBlank()) return
        val c = client ?: run { appendMeta("[Not connected — select an agent first]\n"); return }
        inputField.text = ""
        sendButton.isEnabled = false
        statusLabel.text = "Agent is thinking…"

        // Inject currently open file as silent context prefix
        val openFile = FileEditorManager.getInstance(project).selectedEditor?.file?.path
        val promptText = if (openFile != null) "Context: currently open file is $openFile\n\n$text" else text

        appendUserMessage(text)
        var firstChunk = true

        uiScope.launch {
            try {
                c.prompt(promptText).collect { event ->
                    when (event) {
                        is AcpEvent.TextChunk -> {
                            if (firstChunk) {
                                statusLabel.text = ""
                                appendText("\n", agentStyle)
                                firstChunk = false
                            }
                            appendText(event.text, agentStyle)
                        }
                        is AcpEvent.ToolCallStarted -> {
                            if (event.title.isNotBlank()) appendMeta("\n[${event.title}]\n")
                        }
                        is AcpEvent.Done -> {
                            statusLabel.text = ""
                            appendText("\n", agentStyle)
                        }
                        is AcpEvent.AgentError -> {
                            statusLabel.text = ""
                            appendMeta("\n[Error: ${event.message}]\n")
                        }
                    }
                }
            } catch (e: Exception) {
                statusLabel.text = ""
                appendMeta("\n[Error: ${e.message ?: e.javaClass.simpleName}]\n")
            } finally {
                sendButton.isEnabled = true
            }
        }
    }

    private fun appendUserMessage(text: String) {
        appendText("\nYou  ", userLabelStyle)
        appendText("$text\n", bodyStyle)
    }

    private fun appendMeta(text: String) = appendText(text, metaStyle)

    private fun appendText(text: String, style: Style) {
        try {
            doc.insertString(doc.length, text, style)
            outputPane.caretPosition = doc.length
        } catch (_: BadLocationException) {}
    }
}
