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
import javax.swing.ScrollPaneConstants.*
import javax.swing.text.html.HTMLEditorKit

// ── Avatar circle — painted like VS Code's 24px avatar ──────────────────────
private class AvatarCircle(private val letter: String, private val bg: Color) : JComponent() {
    init {
        preferredSize = Dimension(26, 26)
        minimumSize  = Dimension(26, 26)
        maximumSize  = Dimension(26, 26)
        isOpaque = false
    }
    override fun paintComponent(g: Graphics) {
        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g2.color = bg
        g2.fillOval(0, 0, width, height)
        g2.color = Color.WHITE
        g2.font = Font(Font.SANS_SERIF, Font.BOLD, 11)
        val fm = g2.fontMetrics
        g2.drawString(letter, (width - fm.stringWidth(letter)) / 2, (height + fm.ascent - fm.descent) / 2)
    }
}

// ── Content pane that wraps to container width (mirrors VS Code list rows) ───
private class ContentPane : JEditorPane() {
    init { contentType = "text/html"; isEditable = false; isOpaque = false; border = null }
    override fun getScrollableTracksViewportWidth() = true
    // Report a tiny preferred width so BoxLayout never forces the row wider than the viewport
    override fun getPreferredSize(): Dimension = Dimension(10, super.getPreferredSize().height)
}

class AgentPortPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    // ── Colour tokens (updated per theme) ───────────────────────────────────────
    private val userAvatarColor  get() = if (!JBColor.isBright()) Color(0x4A9EFF) else Color(0x0066CC)
    private val agentAvatarColor get() = if (!JBColor.isBright()) Color(0x7C67EE) else Color(0x6B57FF)
    private val userRowBg        get() = JBColor.background()
    private val agentRowBg       get() = JBColor(Color(0xF7F7F7), Color(0x2A2A2D))
    private val codeBg           get() = JBColor(Color(0xF0F0F0), Color(0x1E1E1E))
    private val fgColor          get() = JBColor.foreground()
    private val mutedColor       get() = JBColor.GRAY

    // ── Chat model ───────────────────────────────────────────────────────────────
    private sealed class Entry {
        data class User(val text: String) : Entry()
        data class Agent(val markdown: String) : Entry()
        data class Meta(val text: String) : Entry()
    }

    private val entries = mutableListOf<Entry>()
    private val streamBuf    = StringBuilder()
    private val thoughtBuf   = StringBuilder()
    private val toolItems    = mutableListOf<Pair<String, String>>() // (title, kind)
    private var streamingPane: ContentPane? = null
    private var renderJob: Job? = null

    // ── Message list panel (vertical, grows downward) ────────────────────────────
    private val messageList = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = JBColor.background()
    }
    private val scrollPane = JBScrollPane(messageList).apply {
        border = BorderFactory.createEmptyBorder()
        verticalScrollBar.unitIncrement = 16
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
    }

    // ── Input area ───────────────────────────────────────────────────────────────
    private val inputField = JTextArea(3, 0).apply {
        lineWrap = true; wrapStyleWord = true
        font = Font(Font.SANS_SERIF, Font.PLAIN, 13)
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(JBColor(Color(0xCCCCCC), Color(0x555555)), 1),
            BorderFactory.createEmptyBorder(6, 8, 6, 8)
        )
        addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_ENTER && !e.isShiftDown) { e.consume(); sendPrompt() }
            }
        })
    }

    private val sendButton = JButton("⬆").apply {
        font = Font(Font.SANS_SERIF, Font.BOLD, 16)
        preferredSize = Dimension(36, 36); isFocusPainted = false
        toolTipText = "Send (Enter)"
        addActionListener { sendPrompt() }
    }
    private val clearButton = JButton("⊘").apply {
        font = Font(Font.SANS_SERIF, Font.PLAIN, 14)
        preferredSize = Dimension(28, 28); isFocusPainted = false
        toolTipText = "Clear conversation"
        addActionListener { clearChat() }
    }

    private val installedIds = AgentRegistry.detectAvailable().map { it.id }.toSet()
    private val agentSelector = JComboBox<Agent>().apply {
        AgentRegistry.knownAgents.forEach { addItem(it) }
        selectedItem = AgentRegistry.knownAgents.firstOrNull { it.id in installedIds }
        renderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(l: JList<*>?, v: Any?, i: Int, sel: Boolean, f: Boolean): Component {
                super.getListCellRendererComponent(l, v, i, sel, f)
                val a = v as? Agent ?: return this
                val ok = a.id in installedIds
                text = if (ok) a.displayName else "${a.displayName} (not installed)"
                if (!ok && !sel) foreground = JBColor.GRAY
                return this
            }
        }
    }

    private val statusLabel = JLabel("").apply {
        font = Font(Font.SANS_SERIF, Font.ITALIC, 11); foreground = mutedColor
    }
    private val hintLabel = JLabel("Shift+Enter for newline").apply {
        font = Font(Font.SANS_SERIF, Font.PLAIN, 10); foreground = mutedColor
    }

    // Model selector — populated dynamically from ACP session after connect
    private val modelIds = mutableListOf<String>() // parallel to modelSelector items
    private val modelSelector = JComboBox<String>().apply {
        font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
        toolTipText = "Select model"
        isVisible = false
        addActionListener {
            val idx = selectedIndex.takeIf { it >= 0 } ?: return@addActionListener
            val id = modelIds.getOrNull(idx) ?: return@addActionListener
            uiScope.launch { client?.setModel(id) }
        }
    }
    private val modelRow = JPanel(BorderLayout(4, 0)).apply {
        background = JBColor.background()
        isVisible = false
        add(JLabel("Model:").apply { font = Font(Font.SANS_SERIF, Font.PLAIN, 11); foreground = mutedColor }, BorderLayout.WEST)
        add(modelSelector, BorderLayout.CENTER)
    }

    private val uiScope = CoroutineScope(Dispatchers.Swing + SupervisorJob())
    private var client: AcpClient? = null

    init {
        setContent(buildLayout())
        applyDefaultAgent()
        agentSelector.addActionListener { reconnect() }
        reconnect()
    }

    // ── Layout ────────────────────────────────────────────────────────────────────
    private fun buildLayout(): JPanel = JPanel(BorderLayout()).apply {
        add(scrollPane, BorderLayout.CENTER)
        add(buildInputPanel(), BorderLayout.SOUTH)
    }

    private fun buildInputPanel(): JPanel = JPanel(BorderLayout(0, 4)).apply {
        background = JBColor.background()
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, JBColor(Color(0xDDDDDD), Color(0x3C3C3C))),
            BorderFactory.createEmptyBorder(8, 12, 8, 12)
        )
        // Top row: agent selector + status + clear
        add(JPanel(BorderLayout(6, 0)).apply {
            background = JBColor.background()
            add(agentSelector, BorderLayout.WEST)
            add(statusLabel, BorderLayout.CENTER)
            add(clearButton, BorderLayout.EAST)
        }, BorderLayout.NORTH)
        // Middle row: model selector (hidden until agent supports it)
        add(modelRow, BorderLayout.CENTER)
        // Input row: text + send
        add(JPanel(BorderLayout(6, 0)).apply {
            background = JBColor.background()
            add(inputField, BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                background = JBColor.background()
                add(sendButton, BorderLayout.NORTH)
                add(hintLabel, BorderLayout.SOUTH)
            }, BorderLayout.EAST)
        }, BorderLayout.SOUTH)
    }

    private fun refreshModelSelector(c: AcpClient) {
        if (!c.isModelsSupported()) { modelRow.isVisible = false; return }
        val models = c.getAvailableModels()
        if (models.isEmpty()) { modelRow.isVisible = false; return }
        val current = c.getCurrentModelId()
        val listeners = modelSelector.actionListeners.toList()
        listeners.forEach { modelSelector.removeActionListener(it) }
        modelSelector.removeAllItems()
        modelIds.clear()
        models.forEach { m ->
            modelSelector.addItem(m.name.ifBlank { m.modelId.value })
            modelIds += m.modelId.value
        }
        current?.let { id -> modelIds.indexOf(id).takeIf { it >= 0 }?.let { modelSelector.selectedIndex = it } }
        listeners.forEach { modelSelector.addActionListener(it) }
        modelRow.isVisible = true
        modelSelector.isVisible = true
    }

    // ── Message row builder (mirrors .interactive-item-container) ────────────────
    private fun buildRow(entry: Entry): JPanel {
        val isUser = entry is Entry.User
        val isMeta = entry is Entry.Meta
        val rowBg  = if (isUser) userRowBg else if (isMeta) JBColor.background() else agentRowBg

        return JPanel(BorderLayout(12, 0)).apply {
            background = rowBg; alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            border = BorderFactory.createEmptyBorder(12, 16, 12, 16)

            if (!isMeta) {
                val avatarBg = if (isUser) userAvatarColor else agentAvatarColor
                val letter   = if (isUser) "Y" else "A"
                add(JPanel(BorderLayout()).apply {
                    background = rowBg; isOpaque = false
                    add(AvatarCircle(letter, avatarBg), BorderLayout.NORTH)
                }, BorderLayout.WEST)
            }

            add(JPanel(BorderLayout(0, 4)).apply {
                background = rowBg; isOpaque = false
                if (!isMeta) {
                    val nameText  = if (isUser) "You" else "Agent"
                    val nameColor = if (isUser) userAvatarColor else agentAvatarColor
                    add(JLabel(nameText).apply {
                        font = Font(Font.SANS_SERIF, Font.BOLD, 12); foreground = nameColor
                    }, BorderLayout.NORTH)
                }
                add(buildContentPane(entry, rowBg), BorderLayout.CENTER)
            }, BorderLayout.CENTER)
        }
    }

    private fun buildContentPane(entry: Entry, rowBg: Color): JComponent = ContentPane().apply {
        background = rowBg
        val kit = HTMLEditorKit()
        kit.styleSheet.apply {
            addRule("body{font-family:sans-serif;font-size:13px;margin:0;padding:0;color:${fgHex()}}")
            addRule("pre{background:${hex(codeBg)};padding:10px;margin:4px 0;white-space:pre-wrap;font-family:monospace}")
            addRule("code{font-family:monospace;background:${hex(codeBg)};padding:1px 3px}")
            addRule("pre code{background:transparent;padding:0}")
            addRule("p{margin:2px 0}")
        }
        editorKit = kit
        text = when (entry) {
            is Entry.User  -> "<html><body>${esc(entry.text).replace("\n","<br>")}</body></html>"
            is Entry.Agent -> "<html><body>${md(entry.markdown)}</body></html>"
            is Entry.Meta  -> "<html><body><i style='color:${hex(mutedColor)}'>${esc(entry.text)}</i></body></html>"
        }
    }

    // ── Agent wiring ──────────────────────────────────────────────────────────────
    private fun applyDefaultAgent() {
        val id = service<PluginSettings>().defaultAgentId
        if (id.isNotEmpty()) AgentRegistry.knownAgents.indexOfFirst { it.id == id }
            .takeIf { it >= 0 }?.let { agentSelector.selectedIndex = it }
    }

    private fun reconnect() {
        val agent = agentSelector.selectedItem as? Agent ?: return
        if (agent.id !in installedIds) { addMeta("${agent.displayName} is not installed."); return }
        val cwd = project.basePath ?: System.getProperty("user.home")
        client?.disconnect(); streamBuf.clear(); streamingPane = null

        client = AcpClient(
            onPermissionRequest = { tc, p -> PermissionHandler(project).request(tc, p) },
            onFileWrite = { path, c -> DiffHandler(project).showAndApply(path, c) },
        )
        uiScope.launch {
            try {
                client!!.connect(agent, cwd!!)
                addMeta("Connected to ${agent.displayName}")
                sendButton.isEnabled = true
                refreshModelSelector(client!!)
            }
            catch (e: Exception) { addMeta("Failed to connect: ${e.message}") }
        }
    }

    // ── Chat actions ──────────────────────────────────────────────────────────────
    private fun clearChat() {
        entries.clear(); streamBuf.clear(); thoughtBuf.clear(); toolItems.clear(); streamingPane = null
        messageList.removeAll(); messageList.revalidate(); messageList.repaint()
    }

    private fun addMeta(text: String) {
        entries.add(Entry.Meta(text))
        messageList.add(buildRow(Entry.Meta(text)))
        scrollToBottom()
    }

    private fun sendPrompt() {
        val text = inputField.text.trim(); if (text.isBlank()) return
        val c = client ?: run { addMeta("Not connected — select an installed agent"); return }
        inputField.text = ""; sendButton.isEnabled = false; statusLabel.text = "Agent is thinking…"

        val openFile = FileEditorManager.getInstance(project).selectedEditor?.file?.path
        val prompt = if (openFile != null) "Context: currently open file is $openFile\n\n$text" else text

        entries.add(Entry.User(text))
        messageList.add(buildRow(Entry.User(text)))

        // Streaming placeholder row
        val sPane = ContentPane()
        streamingPane = sPane
        val rowBg = agentRowBg
        val streamRow = JPanel(BorderLayout(12, 0)).apply {
            background = rowBg; alignmentX = LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, Int.MAX_VALUE)
            border = BorderFactory.createEmptyBorder(12, 16, 12, 16)
            add(JPanel(BorderLayout()).apply {
                background = rowBg; isOpaque = false
                add(AvatarCircle("A", agentAvatarColor), BorderLayout.NORTH)
            }, BorderLayout.WEST)
            add(JPanel(BorderLayout(0, 4)).apply {
                background = rowBg; isOpaque = false
                add(JLabel("Agent").apply { font = Font(Font.SANS_SERIF, Font.BOLD, 12); foreground = agentAvatarColor }, BorderLayout.NORTH)
                add(sPane, BorderLayout.CENTER)
            }, BorderLayout.CENTER)
        }
        messageList.add(streamRow)
        streamBuf.clear(); thoughtBuf.clear(); toolItems.clear(); scrollToBottom()

        uiScope.launch {
            try {
                c.prompt(prompt).collect { event ->
                    when (event) {
                        is AcpEvent.TextChunk -> {
                            statusLabel.text = ""
                            streamBuf.append(event.text)
                            scheduleStreamUpdate()
                        }
                        is AcpEvent.ThoughtChunk -> {
                            statusLabel.text = "Thinking…"
                            thoughtBuf.append(event.text)
                            scheduleStreamUpdate()
                        }
                        is AcpEvent.ToolCall -> {
                            if (event.title.isNotBlank()) {
                                statusLabel.text = event.title
                                toolItems += event.title to event.kind
                                scheduleStreamUpdate()
                            }
                        }
                        is AcpEvent.Done -> finaliseStream(streamRow)
                        is AcpEvent.AgentError -> { finaliseStream(streamRow); addMeta("Error: ${event.message}") }
                    }
                }
            } catch (e: Exception) {
                finaliseStream(streamRow); addMeta("Error: ${e.message ?: e.javaClass.simpleName}")
            } finally {
                statusLabel.text = ""; sendButton.isEnabled = true
            }
        }
    }

    private fun scheduleStreamUpdate() {
        renderJob?.cancel()
        renderJob = uiScope.launch {
            delay(40)
            streamingPane?.setText(buildStreamHtml())
            scrollToBottom()
        }
    }

    private fun buildStreamHtml(): String = buildString {
        append("<html><body style='font-family:sans-serif;font-size:13px;color:${fgHex()}'>")
        // Tool calls
        for ((title, kind) in toolItems) {
            append("<div style='color:${hex(mutedColor)};font-size:12px;margin:2px 0'>${kindIcon(kind)} ${esc(title)}</div>")
        }
        if (toolItems.isNotEmpty()) append("<div style='height:4px'></div>")
        // Thoughts
        if (thoughtBuf.isNotEmpty()) {
            append("<div style='color:${hex(mutedColor)};font-style:italic;border-left:3px solid ${hex(mutedColor)};padding-left:8px;margin-bottom:6px;font-size:12px'>")
            append("💭 ${esc(thoughtBuf.toString()).replace("\n", "<br>")}")
            append("</div>")
        }
        // Main response
        if (streamBuf.isNotEmpty()) append(md(streamBuf.toString()))
        append("</body></html>")
    }

    private fun kindIcon(kind: String) = when (kind) {
        "read"    -> "📄"
        "edit"    -> "✏️"
        "delete"  -> "🗑️"
        "execute" -> "⚡"
        "search"  -> "🔍"
        "think"   -> "💭"
        "fetch"   -> "🌐"
        "move"    -> "↔️"
        else      -> "🔧"
    }

    private fun finaliseStream(streamRow: JPanel) {
        renderJob?.cancel()
        val markdown = streamBuf.toString()
        streamBuf.clear(); thoughtBuf.clear(); toolItems.clear(); streamingPane = null
        if (markdown.isNotEmpty()) {
            val entry = Entry.Agent(markdown); entries.add(entry)
            val rowBg = agentRowBg
            val finalPane = buildContentPane(entry, rowBg)
            val contentPanel = (streamRow.getComponent(1) as? JPanel) ?: return
            contentPanel.remove(contentPanel.componentCount - 1)
            contentPanel.add(finalPane, BorderLayout.CENTER)
            contentPanel.revalidate(); contentPanel.repaint()
        }
        scrollToBottom()
    }

    private fun scrollToBottom() {
        messageList.revalidate()
        SwingUtilities.invokeLater {
            val sb = scrollPane.verticalScrollBar
            sb.value = sb.maximum
        }
    }

    // ── Markdown → HTML ───────────────────────────────────────────────────────────
    private fun md(text: String): String {
        val parts = text.split(Regex("```(?:\\w*)\\n?"))
        return buildString {
            parts.forEachIndexed { i, part ->
                if (i % 2 == 0) append(inlineMd(part))
                else append("<pre><code>${esc(part.trimEnd())}</code></pre>")
            }
        }
    }

    private fun inlineMd(s: String) = esc(s)
        .replace(Regex("`([^`\n]+)`"))        { "<code>${it.groupValues[1]}</code>" }
        .replace(Regex("\\*\\*(.+?)\\*\\*"))  { "<b>${it.groupValues[1]}</b>" }
        .replace(Regex("\\*(.+?)\\*"))        { "<i>${it.groupValues[1]}</i>" }
        .replace(Regex("(?m)^#{1,3} (.+)$")) { "<b>${it.groupValues[1]}</b><br>" }
        .replace("\n", "<br>")

    private fun esc(s: String) = s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;")
    private fun hex(c: Color) = "#%02x%02x%02x".format(c.red, c.green, c.blue)
    private fun fgHex() = hex(fgColor)
}
