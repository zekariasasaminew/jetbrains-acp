# Changelog

All notable changes to **AgentPort** will be documented in this file.

## [0.1.0] — Unreleased

### Added
- Universal ACP client for all JetBrains IDEs (IntelliJ 2024.3+)
- Auto-detection of ACP-compatible agents on PATH (Claude Code, Gemini CLI, OpenCode, GitHub Copilot CLI, Codex, and more)
- Native Swing chat panel with streaming text output
- "Agent is thinking…" status indicator
- Diff viewer using IntelliJ's built-in DiffManager; applies changes via WriteCommandAction
- Native IntelliJ permission dialog for agent permission requests
- Persistent settings page (Tools → AgentPort): default agent ID, per-agent custom binary paths
- Unit tests for agent detection, ACP client, diff handler, and permission handler
