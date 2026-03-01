package com.agentport.jetbrains

object AgentRegistry {

    val knownAgents = listOf(
        Agent("claude",    "Claude Code",     "claude",    listOf("--acp")),
        Agent("gemini",    "Gemini CLI",      "gemini",    listOf("--acp")),
        Agent("opencode",  "OpenCode",        "opencode",  listOf("acp")),
        Agent("copilot",   "GitHub Copilot",  "copilot",   listOf("--acp")),
        Agent("codex",     "OpenAI Codex",    "codex",     listOf("--acp")),
        Agent("goose",     "Goose",           "goose",     listOf("--acp")),
        Agent("aider",     "Aider",           "aider",     listOf("--acp")),
        Agent("amp",       "Amp",             "amp",       listOf("--acp")),
        Agent("augment",   "Augment",         "augment",   listOf("--acp")),
        Agent("kimi",      "Kimi",            "kimi",      listOf("--acp")),
        Agent("mistral",   "Mistral",         "mistral",   listOf("--acp")),
        Agent("qwen",      "Qwen",            "qwen",      listOf("--acp")),
        Agent("kiro",      "Kiro",            "kiro",      listOf("--acp")),
    )

    fun detectAvailable(): List<Agent> = knownAgents.filter { isOnPath(it.command) }

    fun isOnPath(command: String, pathOverride: String? = null): Boolean {
        val pathValue = pathOverride ?: System.getenv("PATH") ?: return false
        val pathDirs = pathValue.split(pathSeparator)
        return pathDirs.any { dir ->
            val base = java.io.File(dir, command)
            base.canExecute() || windowsExtensions.any { ext -> java.io.File(dir, "$command$ext").canExecute() }
        }
    }

    private val pathSeparator = System.getProperty("path.separator", ":")
    private val windowsExtensions = listOf(".exe", ".cmd", ".bat")
}
