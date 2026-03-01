package com.agentport.jetbrains

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import java.awt.*
import javax.swing.*
import javax.swing.ScrollPaneConstants.*

class RegistryBrowserDialog(
    project: Project,
    private val onConnect: (Agent) -> Unit,
) : DialogWrapper(project, true) {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val npxAvailable = AgentRegistry.isOnPath("npx")
    private val statusLabel = JLabel("Loading registry…").apply {
        font = Font(Font.SANS_SERIF, Font.ITALIC, 11)
        foreground = JBColor.GRAY
    }
    private val agentPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        background = JBColor.background()
    }

    init {
        title = "ACP Agent Registry"
        isModal = true
        init()
        loadInBackground()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 8))
        root.preferredSize = Dimension(640, 520)

        // Header row
        root.add(JPanel(BorderLayout(8, 0)).apply {
            background = JBColor.background()
            border = BorderFactory.createEmptyBorder(0, 0, 4, 0)
            add(JLabel("Browse and connect to ACP-compatible agents").apply {
                font = Font(Font.SANS_SERIF, Font.PLAIN, 12)
                foreground = JBColor.GRAY
            }, BorderLayout.WEST)
            add(statusLabel, BorderLayout.EAST)
        }, BorderLayout.NORTH)

        // Scrollable agent list
        root.add(JBScrollPane(agentPanel).apply {
            horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
        }, BorderLayout.CENTER)

        if (!npxAvailable) {
            root.add(JLabel("⚠  npx not found — Node.js is required to connect npx-based agents").apply {
                font = Font(Font.SANS_SERIF, Font.ITALIC, 11)
                foreground = JBColor(Color(0xCC6600), Color(0xFFAA44))
                border = BorderFactory.createEmptyBorder(4, 0, 0, 0)
            }, BorderLayout.SOUTH)
        }
        return root
    }

    override fun createActions(): Array<Action> = arrayOf(cancelAction)

    private fun loadInBackground() {
        scope.launch {
            try {
                val agents = RegistryClient.fetchAgents()
                withContext(Dispatchers.Swing) { populate(agents) }
            } catch (e: Exception) {
                withContext(Dispatchers.Swing) {
                    statusLabel.text = "Failed to load — check your internet connection"
                    statusLabel.foreground = Color.RED
                }
            }
        }
    }

    private fun populate(agents: List<RegistryAgent>) {
        statusLabel.text = "${agents.size} agents available"
        agentPanel.removeAll()
        agents.forEach { agentPanel.add(buildRow(it)) }
        agentPanel.add(Box.createVerticalGlue())
        agentPanel.revalidate()
        agentPanel.repaint()
    }

    private fun buildRow(ra: RegistryAgent): JPanel {
        val sep = JBColor(Color(0xE5E5E5), Color(0x3A3A3A))
        return JPanel(BorderLayout(12, 0)).apply {
            background = JBColor.background()
            maximumSize = Dimension(Int.MAX_VALUE, 90)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, sep),
                BorderFactory.createEmptyBorder(10, 14, 10, 14),
            )

            // Text block
            add(JPanel().apply {
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                background = JBColor.background(); isOpaque = false
                add(JLabel("${ra.name}  ").apply {
                    font = Font(Font.SANS_SERIF, Font.BOLD, 13)
                    alignmentX = Component.LEFT_ALIGNMENT
                }.also { lbl ->
                    // append version in gray
                    lbl.text = "<html><b>${ra.name}</b>&nbsp;<span style='color:gray;font-size:11px'>v${ra.version}</span></html>"
                })
                add(Box.createVerticalStrut(2))
                add(JLabel(ra.description.take(85) + if (ra.description.length > 85) "…" else "").apply {
                    font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                    foreground = JBColor.GRAY
                    alignmentX = Component.LEFT_ALIGNMENT
                })
                add(Box.createVerticalStrut(4))
                add(JLabel(ra.distributionType.uppercase()).apply {
                    font = Font(Font.MONOSPACED, Font.PLAIN, 10)
                    foreground = if (ra.distributionType == "npx")
                        JBColor(Color(0x1A7F1A), Color(0x55CC55))
                    else JBColor(Color(0x1A5FAA), Color(0x6699DD))
                    alignmentX = Component.LEFT_ALIGNMENT
                })
            }, BorderLayout.CENTER)

            // Action buttons
            add(JPanel(GridLayout(0, 1, 0, 4)).apply {
                background = JBColor.background(); isOpaque = false
                if (ra.distributionType == "npx" && ra.npxPackage != null) {
                    add(JButton(if (npxAvailable) "Connect" else "Needs npx").apply {
                        font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                        isEnabled = npxAvailable
                        toolTipText = if (npxAvailable) "Launch via npx (downloads automatically)"
                        else "Install Node.js to use this agent"
                        addActionListener { connectNpx(ra) }
                    })
                }
                if (ra.repository.isNotBlank()) {
                    add(JButton("Repo ↗").apply {
                        font = Font(Font.SANS_SERIF, Font.PLAIN, 11)
                        toolTipText = ra.repository
                        addActionListener {
                            runCatching {
                                Desktop.getDesktop().browse(java.net.URI(ra.repository))
                            }
                        }
                    })
                }
            }, BorderLayout.EAST)
        }
    }

    private fun connectNpx(ra: RegistryAgent) {
        val agent = Agent(
            id          = ra.id,
            displayName = ra.name,
            command     = "npx",
            args        = listOf("--yes", ra.npxPackage!!) + ra.npxArgs,
        )
        onConnect(agent)
        close(OK_EXIT_CODE)
    }

    override fun dispose() {
        scope.cancel()
        super.dispose()
    }
}
