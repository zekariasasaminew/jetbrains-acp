package com.agentport.jetbrains

data class RegistryAgent(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val repository: String,
    val icon: String?,
    val distributionType: String,  // "npx" | "binary" | "unknown"
    val npxPackage: String?,
    val npxArgs: List<String>,
)
