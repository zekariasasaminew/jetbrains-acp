# jetbrains-acp

> Free, open-source ACP client for all JetBrains IDEs — no subscription required.

[![Build](https://github.com/zekariasasaminew/jetbrains-acp/actions/workflows/build.yml/badge.svg)](https://github.com/zekariasasaminew/jetbrains-acp/actions/workflows/build.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![IDE: 2024.2+](https://img.shields.io/badge/IDE-2024.2%2B-blue)](https://www.jetbrains.com/idea/)

Connect any [ACP-compatible](https://agentclientprotocol.com) AI coding agent — Claude Code, Gemini CLI, GitHub Copilot CLI, OpenAI Codex, OpenCode, and 20+ more — directly inside IntelliJ IDEA, PyCharm, WebStorm, GoLand, and every other JetBrains IDE.

## Why jetbrains-acp?

JetBrains recently launched official ACP support, but it requires **IDE 2025.3.2+** and a **JetBrains AI subscription (~$10/month)**. This plugin has no such restrictions.

| Feature | jetbrains-acp | JetBrains Official ACP |
|---------|:---:|:---:|
| Minimum IDE version | **2024.2** | 2025.3.2 |
| Subscription required | **No** | Yes (~$10/mo) |
| ACP Registry browser | ✅ | ✅ |
| npx agent auto-launch | ✅ | ✅ |
| Agent thoughts / tool calls | ✅ | — |
| Model selector | ✅ | — |
| Open source | ✅ | ❌ |

## Features

- **ACP Registry browser** — browse and connect to 20+ agents from the official registry without pre-installation (uses `npx`)
- **Multi-agent support** — auto-detects Claude Code, Gemini CLI, Copilot CLI, OpenCode, Codex, Goose, Aider, and more on your PATH
- **Agent thoughts & tool calls** — see what the agent is thinking and which tools it calls, in real time
- **Model selector** — switch models mid-session when the agent supports it
- **Streaming responses** — output renders as it arrives, character by character
- **Native Swing UI** — zero Electron, zero JCEF; feels like a first-party JetBrains tool

## Requirements

- Any JetBrains IDE **2024.2 or newer** (IntelliJ IDEA, PyCharm, WebStorm, GoLand, …)
- **Node.js + npx** on your PATH if you want to use registry agents without pre-installing them
- Or install any ACP agent manually:
  - [Claude Code](https://github.com/anthropics/claude-code) — `npm install -g @anthropic-ai/claude-code`
  - [Gemini CLI](https://github.com/google-gemini/gemini-cli) — `npm install -g @google/gemini-cli`
  - [GitHub Copilot CLI](https://docs.github.com/en/copilot/github-copilot-in-the-cli) — via `gh extension install github/gh-copilot`
  - [OpenCode](https://opencode.ai) — `npm install -g opencode-ai`
  - [OpenAI Codex](https://github.com/openai/codex) — `npm install -g @openai/codex`

## Installation

### From JetBrains Marketplace *(coming soon)*

Search for **"AgentPort"** in **Settings → Plugins → Marketplace**.

### Manual install

1. Download the latest `.zip` from [Releases](https://github.com/zekariasasaminew/jetbrains-acp/releases)
2. In your IDE: **Settings → Plugins → ⚙ → Install Plugin from Disk…**
3. Select the downloaded file and restart

### Build from source

```bash
git clone https://github.com/zekariasasaminew/jetbrains-acp.git
cd jetbrains-acp
./gradlew buildPlugin          # output: build/distributions/*.zip
./gradlew runIde               # launch a sandbox IDE to test
```

## Quick start

1. Open the **AgentPort** tool window (View → Tool Windows → AgentPort)
2. Select an agent from the dropdown — detected agents appear automatically
3. Or click **Browse Registry…** to pick any agent from the ACP registry
4. Type your prompt and press **Enter** (Shift+Enter for new line)
5. Watch the agent think, call tools, and stream its response in real time

## Contributing

All contributions are welcome — bug fixes, new features, tests, docs. Read [CONTRIBUTING.md](CONTRIBUTING.md) to get started.

Good first issues are labeled [`good first issue`](https://github.com/zekariasasaminew/jetbrains-acp/issues?q=label%3A%22good+first+issue%22).

## License

[MIT](LICENSE)
