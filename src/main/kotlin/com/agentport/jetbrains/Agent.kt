package com.agentport.jetbrains

data class Agent(
    val id: String,
    val displayName: String,
    val command: String,
    val args: List<String>,
)
