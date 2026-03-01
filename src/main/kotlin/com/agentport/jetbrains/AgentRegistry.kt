package com.agentport.jetbrains

object AgentRegistry {

    private val KNOWN_AGENTS = listOf(
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

    fun detectAvailable(): List<Agent> = KNOWN_AGENTS.filter { isOnPath(it.command) }

    fun isOnPath(command: String): Boolean {
        val pathDirs = System.getenv("PATH")?.split(pathSeparator) ?: return false
        return pathDirs.any { dir ->
            val base = java.io.File(dir, command)
            base.canExecute() || windowsExtensions.any { ext -> java.io.File(dir, "$command$ext").canExecute() }
        }
    }

    private val pathSeparator = System.getProperty("path.separator", ":")
    private val windowsExtensions = listOf(".exe", ".cmd", ".bat")
}
