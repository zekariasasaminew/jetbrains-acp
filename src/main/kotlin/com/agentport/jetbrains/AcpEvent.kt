package com.agentport.jetbrains

import com.agentclientprotocol.model.StopReason

sealed class AcpEvent {
    data class TextChunk(val text: String) : AcpEvent()
    data class ToolCallStarted(val title: String) : AcpEvent()
    data class Done(val stopReason: StopReason) : AcpEvent()
    data class AgentError(val message: String) : AcpEvent()
}
