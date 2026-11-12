# HyEssentialsX Agent Guide

This file applies to the whole repository.

## Project Overview

- HyEssentialsX is a Hytale server plugin written in Java.
- The main plugin entry point is `src/main/java/xyz/thelegacyvoyage/hyessentialsx/HyEssentialsXPlugin.java`.
- Source code is organized by feature type:
  - `commands/` command handlers and subcommands.
  - `listeners/` event listeners.
  - `managers/` feature state and business logic.
  - `models/` persisted data shapes.
  - `storage/` JSON, SQLite, MySQL, and MongoDB backends.
  - `ui/` Java-side custom UI controllers.
  - `util/` shared helpers, config, permissions, messages, and compatibility helpers.
- Custom UI layouts live under `src/main/resources/Common/UI/Custom/hyessentialsx/`.
- Language strings live under `src/main/resources/lang/`; add or update `en-us.json` keys for new player-facing messages.

## Hytale References

- Hytale API and reference documentation are cached in `docs-cache/`; check that folder before guessing Hytale API behavior.
- `docs-cache/index.json` can help find cached docs.
- Use `rg` inside `docs-cache/` for API names, classes, methods, and examples.
- The local server runtime lives in `server/`.
- The local server jar is `server/HytaleServer.jar`.
- When server behavior is unclear, inspect `server/logs/`, live configs in `server/mods/HyEssentialsX/`, and APIs from `server/HytaleServer.jar`.

## Build And Server Rules

- Use Gradle tasks for compile/test/build checks, usually `./gradlew compileJava` for quick verification and `./gradlew test` when logic or storage behavior changes.
- Do not manually copy compiled plugin jars into `server/`, `server/mods/`, or `server/earlyplugins/`.
- Do not run `deployToServer` unless the user explicitly asks.
- The IDE play/run button handles copying the compiled jar to the server automatically.
- Treat files under `server/` as local runtime state unless the user specifically asks to inspect or edit them.
- `compileJava` may copy `server/HytaleServer.jar` into `libs/` for compile-only use; that is okay because it is not deploying the plugin.

## Config Practices

- `ConfigManager` owns config defaults, loading, migration, and save-back behavior.
- When adding a config option:
  - Add the field default.
  - Add it to the default JSON builder.
  - Load it from the proper section.
  - Write it back in `applyFieldsToRoot()`.
  - Add a getter or setter only where needed.
- Keep existing server config values stable. New defaults should merge into existing configs without overwriting user choices.
- Be careful with pre-migration backups. Only trigger backups for real legacy migrations, not for current valid sections.
- Split config files are intentional: main `config.json`, `economyConfig.json`, `rewardsConfig.json`, `chatConfig.json`, and `scoreboardConfig.json`.
- If a config change is user-facing, update `CHANGELOG.md` and mention the config key in backticks.

## Command And Permission Practices

- Follow existing command patterns before creating new abstractions.
- Prefer the newer command variant/subcommand style with positional required arguments and tab-complete-friendly argument types where possible. Avoid old optional flag-style command layouts such as requiring `--name=` for common flows; use `addUsageVariant`, explicit subcommands, or a clearly named custom variant like `/tpa <player>` does.
- Use `CommandPermissionUtil`, `CommandSenderUtil`, and local command helpers instead of duplicating permission or sender checks.
- Respect `ConfigManager.isUsePermissionsSystem()` through existing permission utilities.
- Player-only commands should fail cleanly for console senders. Console-compatible commands should use the existing `CommandBase` style where appropriate.
- Use `Messages.sendKey`, `Messages.sendPrefixedKey`, or related helpers for player-facing text instead of hard-coded raw strings.
- Add language keys for new messages and keep placeholders explicit, for example `{player}`, `{amount}`, or `{shop}`.

## UI Practices

- Java UI controllers generally extend `InteractiveCustomUIPage` and pair with `.ui` layout files in `src/main/resources/Common/UI/Custom/hyessentialsx/`.
- Prefer `UICommandBuilder.append(...)` and `appendInline(...)` patterns used by nearby UI classes.
- Avoid deprecated UI builder calls such as `addField`; use `append`-style event data patterns already used in the repo.
- Keep event names and data keys stable between `.ui` files and Java event codecs.
- When adding UI controls, make keyboard and controller navigation practical where possible.
- For scrollable UI areas, consider controller navigation/focus behavior, not just mouse wheel behavior.
- Keep UI text short and make sure controls fit common desktop and controller-driven layouts.

## Storage And Data Practices

- Use `StorageManager` and model classes instead of adding ad hoc file reads/writes.
- All plugin feature data must be persisted through the storage backend selected in config. New shared data must be added to `StorageBackend` and implemented for JSON, SQLite, MySQL/MariaDB, and MongoDB; do not write standalone files unless the active backend is the JSON backend.
- Keep storage changes compatible across JSON, SQLite, MySQL, and MongoDB backends when the data is shared.
- Use `AtomicFileUtil` for atomic local file writes.
- Avoid changing persisted model names or field meaning without a migration plan.
- For economy values, preserve the existing minor-unit/money formatting behavior and configured decimal places.

## Runtime And Shutdown Practices

- Avoid raw long-lived threads. Prefer managed executors/schedulers already present in the relevant manager.
- If a background thread is necessary, make it daemon, keep a reference to it, and stop or interrupt it during plugin shutdown.
- Every manager that starts scheduled work, listeners, entity state, or background work should have a shutdown path.
- Shutdown fixes should be verified by checking logs around `[HyEssentialsX] Shutting down...` and by ensuring the JVM can exit.
- Treat Hytale `Ref` and entity references as unstable after removal or shutdown; validate references before accessing components.

## Shop And NPC Practices

- Admin shops and player shops share some shop infrastructure but have separate config flags and commands.
- Shop NPC prompt behavior should follow native Hytale interaction behavior where possible.
- Existing shops and newly created shops both need to be considered when changing NPC metadata, nameplates, or interactions.
- Use `ShopNpcInteractionRegistry`, `ShopNpcFixTask`, and shop NPC utility classes before inventing another NPC update path.
- Direct shop opening, shop directory browsing, NPC interactions, and shop editing can have different permissions and config toggles; keep those paths separate.

## Changelog Maintenance

- Keep `CHANGELOG.md` updated whenever adding, changing, or fixing user-facing behavior.
- Add concise entries during the same work that introduces the change.
- Treat `CHANGELOG.md` as release notes, not a development diary. If a feature has not been released yet, follow-up tweaks, UI adjustments, bug fixes, or behavior changes to that same unreleased feature should usually update or consolidate the existing changelog entry instead of adding another `Changed` entry.
- Do not add `Changed` entries for iterative modifications to work that is still in progress and unreleased. Use `Changed` for differences server owners would observe when upgrading from the previous released version.
- Before editing `CHANGELOG.md`, inspect the top changelog heading. If it is a concrete version such as `## HyEssentialsX 1.5.2 Changelog`, treat it as released/frozen and do not edit that section unless the user explicitly asks to modify that released version.
- If the top changelog is frozen and release-note work is required, add a new `## HyEssentialsX <version> Changelog` section above it instead of appending to the concrete-version section.
- When modifying an active unreleased changelog section, prefer revising an existing bullet for the same feature over adding a new bullet, especially for follow-up UI polish, config consolidation, validation changes, or implementation hardening.
- When several unreleased changes are part of one feature, summarize the final user-facing result once under the most appropriate section.
- Keep old changelog sections in `CHANGELOG.md`; never delete previous version sections unless the user explicitly asks.
- The top `## HyEssentialsX <version> Changelog` section is the active unreleased changelog. Treat it as unpublished until the user replaces `<version>` with a concrete version.
- Once a changelog heading has a concrete version instead of `<version>`, consider that section frozen/released: do not add new entries to it unless the user explicitly asks to edit that released version.
- When the top changelog section is frozen by a concrete version and new release-note work is needed, insert a fresh `## HyEssentialsX <version> Changelog` section above it using the standard Added/Changed/Fixed structure, and move the frozen versioned section downward.
- Separate each full changelog section with 10-12 blank lines so old releases are visually distinct.
- Do not add changelog entries for purely internal investigation, failed experiments, formatting-only edits, or agent-instruction edits unless the user asks.
- Prefer user-facing wording over implementation details.
- Mention config keys in backticks when a change adds or changes config behavior.
- If the user asks for a Discord changelog, make it concise and ready to paste.

Use this structure for `CHANGELOG.md` entries and Discord changelogs:

```md
## HyEssentialsX <version> Changelog

### Added
- New features and config options.

### Changed
- Behavior changes, UI improvements, and release metadata updates.

### Fixed
- Bug fixes and reliability improvements.
```

Keep entries short, grouped under `Added`, `Changed`, and `Fixed`, and written for server owners rather than code reviewers.

## Planned Features Maintenance

- Keep `PLANNEDFEATURES.md` as an accurate backlog, not a historical implementation log.
- When implementing a feature or sub-feature listed in `PLANNEDFEATURES.md`, update that file in the same work pass:
  - Remove bullets or sections that are now fully implemented.
  - Narrow broad sections to only the remaining missing work when part of the feature is still incomplete.
  - Do not leave completed implementation descriptions in planned-feature sections unless the user explicitly wants a design history note.
- Before removing a planned section, verify the code covers the listed behavior, permissions, persistence, UI, config, and storage expectations.

## CurseForge Description Maintenance

- Keep `CURSEFORGE.md` actively maintained as the Markdown source for the CurseForge project description.
- Update `CURSEFORGE.md` whenever a user-facing feature, command group, UI, integration, storage capability, permission capability, or config-backed behavior is added, removed, or meaningfully changed.
- Treat `CURSEFORGE.md` as a current product description, not a changelog. Update feature descriptions in place to describe what the plugin can do now; do not add release-note wording such as "changed", "fixed", or "new in this version".
- Keep the strongest, most marketable, and most advanced features near the top, especially flagship systems such as shops, auction house, economy, progression, holograms, storage, and admin tooling.
- Do not mention unfinished experiments or unreleased/in-progress work. Only describe behavior that exists in the current codebase.
- When updating feature coverage, scan the relevant commands, config defaults, managers, storage models/backends, UI files, language strings, and integrations so the description stays complete.
- Keep the file in copy/paste friendly Markdown for CurseForge. Use catchy server-owner-facing wording, but avoid stale screenshots, broken links, or implementation-heavy details.

## Wiki Maintenance

- Keep the HyEssentialsX website wiki up to date alongside user-facing plugin changes.
- The wiki lives outside this repository at `C:\Users\MystifiedSky\Documents\GitHub Projects\TLV-Asp.Net-Website\TLVWebsite\TLVWebsite\TLVWebsite\Views\HyEssentialsXWiki`.
- Update the wiki whenever commands, permissions, config options, storage behavior, UI workflows, setup steps, integrations, migrations, or feature behavior are added, removed, or meaningfully changed.
- Treat the wiki as practical server-owner documentation. Prefer accurate setup instructions, command usage, permission nodes, config keys, examples, limits, and troubleshooting notes over marketing wording.
- Keep wiki updates in sync with `CURSEFORGE.md` and `CHANGELOG.md`, but do not copy release-note wording into the wiki. The wiki should describe the current behavior and how to use it.
- When updating wiki pages, inspect the existing ASP.NET view structure and follow the local page/component style instead of inventing a separate documentation format.
- Do not mention unfinished experiments or unreleased/in-progress behavior unless the user explicitly asks for draft documentation.

## Verification Expectations

- For Java code changes, run `./gradlew compileJava` unless the user only asks for analysis or documentation.
- Run `./gradlew test` when touching storage, config migration, economy calculations, permissions, or non-trivial manager logic.
- For UI-only changes, compile and, when practical, run the server/client to visually verify layout and interaction.
- Report which checks were run and any checks that were skipped.

## Git And Workspace Practices

- Do not revert user changes unless explicitly asked.
- Check `git status --short` before and after edits.
- Keep changes scoped to the request.
- Do not commit unless the user asks.
- Preserve local runtime files and generated server state unless they are directly relevant to the task.
