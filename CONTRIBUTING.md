# Contributing to HyEssentialsX

Thanks for taking the time to improve HyEssentialsX. The project is open source and actively developed, so contributions should stay practical, scoped, and aligned with the current plugin architecture.

## Before You Start

For larger features or behavior changes, open an issue or start a discussion in Discord first:

https://discord.gg/eNuWh2Urww

This helps avoid duplicate work and keeps big changes aligned with the roadmap.

Good first contributions include:

- Bug fixes with clear reproduction steps.
- Documentation improvements.
- Language string fixes.
- Small UI polish that follows existing layout patterns.
- Focused command, permission, or config improvements.
- Tests for existing behavior.

## Development Setup

Requirements:

- Java 25 toolchain.
- Gradle wrapper from this repository.
- A compatible Hytale server jar available at `server/HytaleServer.jar`, `libs/HytaleServer.jar`, or another path already handled by `build.gradle`.

Useful commands:

```powershell
.\gradlew compileJava
```

```powershell
.\gradlew test
```

```powershell
.\gradlew build
```

Do not commit local runtime files from `server/`, generated build output, private configs, logs, database files, or credentials.

## Project Structure

Main code lives under `src/main/java/xyz/thelegacyvoyage/hyessentialsx/`.

- `commands/` command handlers and subcommands.
- `listeners/` event listeners.
- `managers/` feature state and business logic.
- `models/` persisted data shapes.
- `storage/` JSON, SQLite, MySQL/MariaDB, and MongoDB backends.
- `ui/` Java-side custom UI controllers.
- `util/` shared config, permissions, messages, and helpers.

Custom UI layouts live under:

```text
src/main/resources/Common/UI/Custom/hyessentialsx/
```

Language strings live under:

```text
src/main/resources/lang/
```

## Coding Guidelines

- Follow existing patterns before adding a new abstraction.
- Keep changes scoped to the issue or feature being worked on.
- Use existing managers and model classes instead of ad hoc data files.
- Use existing permission helpers instead of duplicating permission checks.
- Use `Messages` helpers and language keys for player-facing text.
- Keep console senders and player-only commands handled cleanly.
- Prefer simple, readable Java over clever code.
- Add comments only where they explain non-obvious behavior.

## Storage Changes

HyEssentialsX supports multiple storage backends. Shared feature data must work across all supported backends.

If you add persistent shared data:

1. Add or update a model class.
2. Add the required API to the storage layer.
3. Implement JSON, SQLite, MySQL/MariaDB, and MongoDB support where applicable.
4. Keep existing persisted data compatible or provide a migration path.
5. Add tests where practical.

Do not add standalone files for shared plugin data unless the behavior is intentionally JSON-backend-only.

## Config Changes

Config is managed through `ConfigManager`.

When adding a config option:

1. Add the field default.
2. Add it to the default JSON builder.
3. Load it from the proper section.
4. Write it back during save-back.
5. Add a getter or setter only where needed.
6. Document it in the wiki and relevant release notes.

New defaults should merge into existing configs without overwriting server owner choices.

## UI Changes

Java UI controllers generally pair with `.ui` files. Keep event names and event data keys stable between Java and UI layouts.

When changing UI:

- Keep text short.
- Preserve keyboard/controller-friendly navigation where possible.
- Avoid layout changes that depend on one exact screen size.
- Use existing button, list, row, and navigation patterns.
- Test the UI in-game when practical.

Screenshots for GitHub should go under:

```text
docs/screenshots/
```

Use Markdown like this:

```md
![Short description](docs/screenshots/FileName.png)
```

For README tables, keep images at a similar aspect ratio so the page stays readable.

## Documentation

Update documentation when changing user-facing behavior:

- `README.md` for project-level GitHub information.
- `CURSEFORGE.md` for the public CurseForge project description.
- `CHANGELOG.md` for release notes.
- The HyEssentialsX wiki for setup, commands, permissions, config, storage, and workflows.

Keep release notes short and written for server owners, not code reviewers.

## Pull Request Checklist

Before opening a pull request:

- The change is scoped and described clearly.
- Player-facing text uses language keys.
- Commands and permissions are documented when changed.
- Config additions are documented and preserve existing server choices.
- Storage changes work across supported backends.
- `.\gradlew compileJava` passes.
- `.\gradlew test` passes when the change touches storage, config migration, economy logic, permissions, or non-trivial manager behavior.
- Screenshots are included for meaningful UI changes when practical.

## Bug Reports

Please include:

- HyEssentialsX version.
- Hytale server version.
- Storage backend.
- Relevant config snippets.
- Exact command, UI action, or server workflow that triggered the issue.
- Server log output around the error.
- Whether it reproduces on a clean config.

Do not post private credentials, database URLs, tokens, full production configs, or private player data.

## License

By contributing, you agree that your contribution will be licensed under the same license as the project: GPL-3.0-or-later.

