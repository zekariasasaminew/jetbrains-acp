# Changelog

All notable changes to **AgentPort** will be documented in this file.

## [0.2.0] — Unreleased

### Added
- **ACP Registry browser** — browse and one-click connect to 20+ agents from the official ACP registry; npx agents launch automatically without pre-installation
- **Agent thoughts & tool calls** — streaming view shows agent reasoning and every tool invocation with status icons
- **Model selector** — switch AI models mid-session when the agent reports available models via ACP
- VS Code-style chat UI polish: agent name header, paragraph spacing, blockquote rendering, animated loading dots

### Changed
- Minimum IDE version remains 2024.2 (build 242)

## [0.1.0] — Unreleased

### Added
- Universal ACP client for all JetBrains IDEs (IntelliJ 2024.2+)
- Auto-detection of ACP-compatible agents on PATH (Claude Code, Gemini CLI, OpenCode, GitHub Copilot CLI, Codex, and more)
- Native Swing chat panel with streaming text output
- "Agent is thinking…" status indicator
- Diff viewer using IntelliJ's built-in DiffManager; applies changes via WriteCommandAction
- Native IntelliJ permission dialog for agent permission requests
- Persistent settings page (Tools → AgentPort): default agent ID, per-agent custom binary paths
- Unit tests for agent detection, ACP client, diff handler, and permission handler
