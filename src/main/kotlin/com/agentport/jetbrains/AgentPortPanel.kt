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
import javax.swing.text.html.HTMLEditorKit

class AgentPortPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    // ── Chat model ──────────────────────────────────────────────────────────────
    private sealed class Entry {
        data class User(val text: String) : Entry()
        data class Agent(val markdown: String) : Entry()
        data class Meta(val text: String) : Entry()
    }

    private val entries = mutableListOf<Entry>()
    private val streamBuf = StringBuilder()
    private var renderJob: Job? = null

    // ── Output pane ─────────────────────────────────────────────────────────────
    private val outputPane = JEditorPane().apply {
        isEditable = false
        contentType = "text/html"
        background = JBColor.background()
        border = BorderFactory.createEmptyBorder(0, 0, 0, 0)
        editorKit = HTMLEditorKit()
    }

    // ── Input area ──────────────────────────────────────────────────────────────
    private val inputField = JTextArea(3, 0).apply {
        lineWrap = true
        wrapStyleWord = true
        font = Font(Font.MONOSPACED, Font.PLAIN, 13)
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) { e.consume(); sendPrompt() }
            }
        })
    }

    private val sendButton = JButton("Send").apply { addActionListener { sendPrompt() } }
    private val clearButton = JButton("Clear").apply { addActionListener { clearChat() } }

    // ── Agent selector — shows all known agents, greyed out if not installed ────
    private val installedIds: Set<String> = AgentRegistry.detectAvailable().map { it.id }.toSet()

    private val agentSelector = JComboBox<Agent>().apply {
        AgentRegistry.knownAgents.forEach { addItem(it) }
        // Pre-select first installed, or first item
        val first = AgentRegistry.knownAgents.firstOrNull { it.id in installedIds }
        if (first != null) selectedItem = first

        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?, value: Any?, index: Int, isSelected: Boolean, cellHasFocus: Boolean
            ): Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val agent = value as? Agent ?: return c
                val installed = agent.id in installedIds
                text = if (installed) agent.displayName else "${agent.displayName} (not installed)"
                foreground = if (!installed && !isSelected) JBColor.GRAY else foreground
                return c
            }
        }
    }

    private val statusLabel = JLabel("").apply {
        font = Font(Font.SANS_SERIF, Font.ITALIC, 12); foreground = JBColor.GRAY
        border = BorderFactory.createEmptyBorder(2, 6, 2, 0)
    }
    private val hintLabel = JLabel("Enter to send  ·  Shift+Enter for newline").apply {
        font = Font(Font.SANS_SERIF, Font.PLAIN, 11); foreground = JBColor.GRAY
        border = BorderFactory.createEmptyBorder(2, 0, 2, 6)
    }

    private val uiScope = CoroutineScope(Dispatchers.Swing + SupervisorJob())
    private var client: AcpClient? = null

    init {
        setContent(buildLayout())
        applyDefaultAgent()
        agentSelector.addActionListener { reconnect() }
        reconnect()
    }

    // ── Layout ───────────────────────────────────────────────────────────────────
    private fun buildLayout(): JPanel = JPanel(BorderLayout()).apply {
        add(JBScrollPane(outputPane), BorderLayout.CENTER)
        add(JPanel(BorderLayout()).apply {
            add(JPanel(BorderLayout()).apply {
                add(statusLabel, BorderLayout.WEST); add(hintLabel, BorderLayout.EAST)
            }, BorderLayout.NORTH)
            add(buildInputBar(), BorderLayout.CENTER)
        }, BorderLayout.SOUTH)
    }

    private fun buildInputBar(): JPanel = JPanel(BorderLayout()).apply {
        add(JPanel(FlowLayout(FlowLayout.LEFT, 4, 2)).apply {
            add(agentSelector); add(clearButton)
        }, BorderLayout.NORTH)
        add(JBScrollPane(inputField).apply { preferredSize = Dimension(0, 80) }, BorderLayout.CENTER)
        add(sendButton, BorderLayout.EAST)
    }

    // ── Agent wiring ─────────────────────────────────────────────────────────────
    private fun applyDefaultAgent() {
        val defaultId = service<PluginSettings>().defaultAgentId
        if (defaultId.isNotEmpty()) {
            AgentRegistry.knownAgents.indexOfFirst { it.id == defaultId }
                .takeIf { it >= 0 }?.let { agentSelector.selectedIndex = it }
        }
    }

    private fun reconnect() {
        val agent = agentSelector.selectedItem as? Agent ?: return
        if (agent.id !in installedIds) {
            addMeta("${agent.displayName} is not installed — cannot connect.")
            renderChat(); return
        }
        val cwd = project.basePath ?: System.getProperty("user.home")
        client?.disconnect(); streamBuf.clear()

        client = AcpClient(
            onPermissionRequest = { tc, perms -> PermissionHandler(project).request(tc, perms) },
            onFileWrite = { path, content -> DiffHandler(project).showAndApply(path, content) },
        )
        uiScope.launch {
            try {
                client!!.connect(agent, cwd!!)
                addMeta("Connected to ${agent.displayName}")
                renderChat(); sendButton.isEnabled = true
            } catch (e: Exception) {
                addMeta("Failed to connect: ${e.message}"); renderChat()
            }
        }
    }

    // ── Chat actions ──────────────────────────────────────────────────────────────
    private fun clearChat() { entries.clear(); streamBuf.clear(); renderChat() }

    private fun sendPrompt() {
        val text = inputField.text.trim()
        if (text.isBlank()) return
        val c = client ?: run { addMeta("Not connected — select an installed agent first"); renderChat(); return }
        inputField.text = ""; sendButton.isEnabled = false; statusLabel.text = "Agent is thinking…"

        val openFile = FileEditorManager.getInstance(project).selectedEditor?.file?.path
        val prompt = if (openFile != null) "Context: currently open file is $openFile\n\n$text" else text

        entries.add(Entry.User(text)); streamBuf.clear(); renderChat()

        uiScope.launch {
            try {
                c.prompt(prompt).collect { event ->
                    when (event) {
                        is AcpEvent.TextChunk -> {
                            statusLabel.text = ""; streamBuf.append(event.text)
                            scheduleRender()
                        }
                        is AcpEvent.ToolCallStarted -> if (event.title.isNotBlank()) {
                            streamBuf.append("\n*${event.title}*\n"); scheduleRender()
                        }
                        is AcpEvent.Done -> finaliseStream()
                        is AcpEvent.AgentError -> { finaliseStream(); addMeta("Error: ${event.message}"); renderChat() }
                    }
                }
            } catch (e: Exception) {
                finaliseStream(); addMeta("Error: ${e.message ?: e.javaClass.simpleName}"); renderChat()
            } finally {
                statusLabel.text = ""; sendButton.isEnabled = true
            }
        }
    }

    private fun scheduleRender() {
        renderJob?.cancel()
        renderJob = uiScope.launch { delay(40); renderChat() }
    }

    private fun finaliseStream() {
        renderJob?.cancel()
        if (streamBuf.isNotEmpty()) { entries.add(Entry.Agent(streamBuf.toString())); streamBuf.clear() }
        renderChat()
    }

    private fun addMeta(text: String) { entries.add(Entry.Meta(text)) }

    // ── HTML renderer ─────────────────────────────────────────────────────────────
    private fun renderChat() {
        val dark = !JBColor.isBright()
        val bg       = if (dark) "#1e1e1e" else "#ffffff"
        val fg       = if (dark) "#d4d4d4" else "#1e1e1e"
        val codeBg   = if (dark) "#2d2d2d" else "#f5f5f5"
        val userFg   = if (dark) "#589DF6" else "#2470B3"
        val divider  = if (dark) "#3c3c3c" else "#e8e8e8"
        val metaFg   = "#888888"

        val html = buildString {
            append("""<html><head><style>
body{font-family:monospace;font-size:13px;background:$bg;color:$fg;margin:0;padding:8px}
.msg{padding:8px 0;border-bottom:1px solid $divider;margin-bottom:4px}
.lbl{font-size:11px;font-weight:bold;letter-spacing:0.5px;margin-bottom:4px}
.user-lbl{color:$userFg}.agent-lbl{color:$metaFg}
.meta{color:$metaFg;font-style:italic;font-size:12px;padding:2px 0}
pre{background:$codeBg;color:$fg;padding:10px;margin:6px 0;white-space:pre-wrap}
code{font-family:monospace;background:$codeBg;padding:1px 4px}
pre code{background:transparent;padding:0}
</style></head><body>""")

            for (e in entries) when (e) {
                is Entry.User -> append("""<div class='msg'><div class='lbl user-lbl'>YOU</div><div>${esc(e.text).replace("\n","<br>")}</div></div>""")
                is Entry.Agent -> append("""<div class='msg'><div class='lbl agent-lbl'>AGENT</div><div>${md(e.markdown)}</div></div>""")
                is Entry.Meta -> append("<div class='meta'>&#9472; ${esc(e.text)}</div>")
            }

            if (streamBuf.isNotEmpty())
                append("""<div class='msg'><div class='lbl agent-lbl'>AGENT</div><div>${esc(streamBuf.toString()).replace("\n","<br>")}</div></div>""")

            append("</body></html>")
        }

        outputPane.text = html
        outputPane.caretPosition = outputPane.document.length
    }

    // ── Markdown → HTML ────────────────────────────────────────────────────────
    private fun md(text: String): String {
        val parts = text.split(Regex("```(?:\\w*)\\n?"))
        return buildString {
            parts.forEachIndexed { i, part ->
                if (i % 2 == 0) append(inlineMd(part))
                else append("<pre><code>${esc(part.trimEnd())}</code></pre>")
            }
        }
    }

    private fun inlineMd(s: String): String = esc(s)
        .replace(Regex("`([^`\n]+)`"))           { "<code>${it.groupValues[1]}</code>" }
        .replace(Regex("\\*\\*(.+?)\\*\\*"))     { "<b>${it.groupValues[1]}</b>" }
        .replace(Regex("\\*(.+?)\\*"))           { "<i>${it.groupValues[1]}</i>" }
        .replace(Regex("(?m)^#{1,3} (.+)$"))    { "<b>${it.groupValues[1]}</b>" }
        .replace("\n", "<br>")

    private fun esc(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
}
