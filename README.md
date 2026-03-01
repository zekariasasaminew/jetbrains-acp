# jetbrains-acp

A universal [ACP (Agent Client Protocol)](https://agentclientprotocol.com) client plugin for all JetBrains IDEs.

Connect any ACP-compatible AI agent — Claude Code, Gemini CLI, OpenCode, Copilot CLI, OpenAI Codex, and more — directly inside IntelliJ IDEA, PyCharm, WebStorm, GoLand, and every other JetBrains IDE.

## Features

- **Multi-agent support** — automatically detects all ACP agents installed on your PATH
- **Native UI** — uses IntelliJ's built-in components; no browser, no Electron
- **Diff viewer** — agent-proposed file changes shown in IntelliJ's native diff viewer with accept/reject
- **Permission gates** — approve or deny every file write and terminal command the agent requests
- **Streaming responses** — see agent output in real time as it arrives

## Requirements

- IntelliJ IDEA 2024.3+ (or any JetBrains IDE 2024.3+)
- At least one ACP-compatible agent installed and on your PATH:
  - [Claude Code](https://github.com/anthropics/claude-code) — `claude --acp`
  - [Gemini CLI](https://github.com/google-gemini/gemini-cli) — `gemini --acp`
  - [OpenCode](https://github.com/sst/opencode) — `opencode acp`
  - [GitHub Copilot CLI](https://github.com/github/gh-copilot) — `copilot --acp`
  - [OpenAI Codex](https://github.com/openai/codex) — `codex --acp`
  - [and more...](https://github.com/agentclientprotocol/registry)

## Building

```bash
./gradlew buildPlugin
```

The plugin `.jar` is output to `build/distributions/`.

## Running locally

```bash
./gradlew runIde
```

## Contributing

Contributions welcome. See issues labeled `good first issue` for suggested starting points.

## License

MIT
