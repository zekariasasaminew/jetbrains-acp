# Contributing to jetbrains-acp

Thank you for taking the time to contribute! This is an open-source project welcoming all levels of experience.

## Getting started

### Prerequisites

- JDK 21
- IntelliJ IDEA (any edition) recommended for editing Kotlin
- Git

### Build and run

```bash
git clone https://github.com/zekariasasaminew/jetbrains-acp.git
cd jetbrains-acp
./gradlew runIde        # opens a sandbox IntelliJ to test the plugin live
./gradlew test          # run unit tests
./gradlew buildPlugin   # produce the installable .zip
```

## Workflow

- **Never push to `main` directly.** Open a PR from a feature branch.
- Branch naming: `feat/<description>`, `fix/<description>`, `chore/<description>`, `docs/<description>`
- Keep commits small — one logical change per commit, one file per commit when possible.
- Conventional commit messages: `feat:`, `fix:`, `chore:`, `docs:`, `test:`, `refactor:`

## Code style

- Kotlin only in `src/main/kotlin/` — no Java
- Keep files under ~200 lines; split when a file grows beyond that
- Only comment code that isn't self-explanatory
- Prefer `val` and immutability
- No unused imports or dead code

## Tests

Unit tests live in `src/test/kotlin/`. Run them with `./gradlew test`.

When adding a new feature, add a corresponding test in the same PR. For complex UI or integration scenarios that are hard to unit test, add a `// TODO: test` comment and open a follow-up issue.

## Issues

- Search existing issues before opening a new one.
- Bug reports: use the **Bug report** template and include IDE version, plugin version, and steps to reproduce.
- Feature requests: use the **Feature request** template.
- Good first issues are tagged [`good first issue`](https://github.com/zekariasasaminew/jetbrains-acp/issues?q=label%3A%22good+first+issue%22).

## Pull requests

- Reference the issue your PR closes: `Closes #123`
- Fill out the PR template
- All CI checks must pass before merging
- One approving review is appreciated but not required for now (small team)

## Questions

Open a [GitHub Discussion](https://github.com/zekariasasaminew/jetbrains-acp/discussions) for questions that aren't bugs or feature requests.
