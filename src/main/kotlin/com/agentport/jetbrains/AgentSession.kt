package com.agentport.jetbrains

data class AgentSession(
    val sessionId: String,
    val cwd: String,
    val agent: Agent,
)
