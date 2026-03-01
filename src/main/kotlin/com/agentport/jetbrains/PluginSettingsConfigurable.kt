package com.agentport.jetbrains

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.components.service
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.*

class PluginSettingsConfigurable : Configurable {

    private lateinit var defaultAgentField: JBTextField
    private val pathRows = mutableListOf<Pair<JBTextField, JBTextField>>()
    private lateinit var panel: JPanel

    override fun getDisplayName() = "AgentPort"

    override fun createComponent(): JComponent {
        panel = JPanel(GridBagLayout())
        val gbc = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            insets = Insets(4, 4, 4, 4)
        }

        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.0
        panel.add(JLabel("Default agent ID:"), gbc)
        defaultAgentField = JBTextField()
        gbc.gridx = 1; gbc.weightx = 1.0
        panel.add(defaultAgentField, gbc)

        gbc.gridx = 0; gbc.gridy = 1; gbc.gridwidth = 2; gbc.weightx = 1.0
        panel.add(JLabel("Custom agent paths (agent-id → /path/to/binary):"), gbc)

        AgentRegistry.knownAgents.forEachIndexed { i, agent ->
            val idField = JBTextField(agent.id).apply { isEditable = false }
            val pathField = JBTextField()
            gbc.gridx = 0; gbc.gridy = i + 2; gbc.gridwidth = 1; gbc.weightx = 0.3
            panel.add(idField, gbc)
            gbc.gridx = 1; gbc.weightx = 0.7
            panel.add(pathField, gbc)
            pathRows.add(idField to pathField)
        }

        reset()
        return panel
    }

    override fun isModified(): Boolean {
        val settings = service<PluginSettings>()
        if (defaultAgentField.text != settings.defaultAgentId) return true
        return pathRows.any { (idField, pathField) ->
            pathField.text != (settings.customAgentPaths[idField.text] ?: "")
        }
    }

    override fun apply() {
        val settings = service<PluginSettings>()
        settings.defaultAgentId = defaultAgentField.text.trim()
        settings.customAgentPaths.clear()
        pathRows.forEach { (idField, pathField) ->
            val path = pathField.text.trim()
            if (path.isNotEmpty()) settings.customAgentPaths[idField.text] = path
        }
    }

    override fun reset() {
        val settings = service<PluginSettings>()
        defaultAgentField.text = settings.defaultAgentId
        pathRows.forEach { (idField, pathField) ->
            pathField.text = settings.customAgentPaths[idField.text] ?: ""
        }
    }
}
