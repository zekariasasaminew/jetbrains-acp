package com.agentport.jetbrains

import com.intellij.openapi.options.Configurable
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class PluginSettingsConfigurable : Configurable {
    // TODO: Build full settings UI — feat/plugin-settings
    override fun getDisplayName() = "AgentPort"
    override fun createComponent(): JComponent = JPanel().apply { add(JLabel("Settings coming soon.")) }
    override fun isModified() = false
    override fun apply() = Unit
}
