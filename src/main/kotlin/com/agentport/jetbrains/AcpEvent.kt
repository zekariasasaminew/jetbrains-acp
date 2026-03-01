package com.agentport.jetbrains

import com.agentclientprotocol.model.StopReason

sealed class AcpEvent {
    data class TextChunk(val text: String) : AcpEvent()
    data class ThoughtChunk(val text: String) : AcpEvent()
    data class ToolCall(val title: String, val kind: String) : AcpEvent()
    data class Done(val stopReason: StopReason) : AcpEvent()
    data class AgentError(val message: String) : AcpEvent()
}
