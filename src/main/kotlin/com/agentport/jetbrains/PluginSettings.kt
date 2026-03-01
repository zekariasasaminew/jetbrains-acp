package com.agentport.jetbrains

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

@State(name = "AgentPortSettings", storages = [Storage("agentport.xml")])
class PluginSettings : PersistentStateComponent<PluginSettings> {
    var defaultAgentId: String = ""
    var customAgentPaths: MutableMap<String, String> = mutableMapOf()

    override fun getState(): PluginSettings = this

    override fun loadState(state: PluginSettings) {
        XmlSerializerUtil.copyBean(state, this)
    }
}
