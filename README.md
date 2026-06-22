# HyEssentialsX

HyEssentialsX is a modern essentials core for Hytale servers. It bundles the day-to-day systems server owners usually need across multiple plugins: economy, shops, auction house, moderation, teleportation, kits, stats, leaderboards, scoreboards, holograms, mail, custom commands, migration tools, storage backends, and Hytale-native UI pages.

This project is open source and actively developed by MystifiedSky. Expect active iteration while Hytale and its server API continue to evolve.

## Links

- Download: https://www.curseforge.com/hytale/mods/hyessentialsx
- Wiki: https://wiki.thelegacyvoyage.xyz/
- Community Discord: https://discord.gg/eNuWh2Urww
- License: GPL-3.0-or-later

## Highlights

- Staff command center with live server snapshots, player lookup, moderation state, health checks, and quick access to deeper admin panels.
- Admin shops, player shops, linked-container stock, barter trades, NPC access, searchable shop browsing, and bulk quantity controls.
- Server-wide auction house with search, sorting, listing creation, configurable fees, listing limits, duration limits, and auction NPCs.
- Economy system with balances, payments, balance leaderboards, admin economy tools, optional HUD, paychecks, block rewards, mob rewards, and VaultUnlocked support.
- Progression systems for playtime, playtime rewards, rankups, and configurable payouts.
- Holograms with text, images, GIFs, placeholders, animations, editor tools, and cleanup commands.
- Stats, leaderboards, and configurable scoreboard HUDs.
- Homes, warps, spawn, back, random teleport, TPA, TPA-here, top, through, and jump-to-block commands.
- Kits, kit editor, inventory utilities, repair, trash, clear inventory, invsee, fly, god, heal, freecam, and stamina tools.
- Moderation tools including mute, unmute, ban, tempban, unban, IP bans, ban lists, freeze, vanish, whois, seen, admin chat, broadcasts, combat log handling, and spawn protection.
- Storage support for SQLite, JSON, MySQL/MariaDB, and MongoDB.
- Integrations for PlaceholderAPI, VaultUnlocked, and LuckPerms-aware formatting.
- Migration tooling for several essentials, economy, homes, warps, and playtime plugins.

## Status

HyEssentialsX is usable but still under active development. Public APIs, configuration shape, commands, UI layouts, and storage models may continue to change as the project matures and as Hytale server APIs stabilize.

For stable server setup instructions, use the wiki first. The README is a project overview for GitHub contributors and server owners evaluating the plugin.

## Requirements

- Java 25 toolchain.
- Gradle wrapper included in this repository.
- A compatible Hytale server jar available at one of the paths checked by `build.gradle`, usually `server/HytaleServer.jar` or `libs/HytaleServer.jar`.
- Hytale server version range currently targeted by the manifest: `^0.5.0`.

Optional runtime integrations:

- PlaceholderAPI for placeholders.
- VaultUnlocked for economy provider compatibility.
- LuckPerms-compatible permission/rank data where supported.

## Building

From the repository root:

```powershell
.\gradlew build
```

For a quick Java compile check:

```powershell
.\gradlew compileJava
```

For tests:

```powershell
.\gradlew test
```

The default build creates the shaded plugin jar through the Shadow plugin. The project also creates a stable development jar name, `hyessentialsx-dev.jar`, so local run configs do not need to change on every version bump.

## Installing

For normal server use, download the latest release from CurseForge:

https://www.curseforge.com/hytale/mods/hyessentialsx

For local development builds, build the jar with Gradle and install it into your Hytale server's mods folder according to your local server workflow.

## Configuration

HyEssentialsX uses split configuration files for major systems, including:

- `config.json`
- `economyConfig.json`
- `rewardsConfig.json`
- `chatConfig.json`
- `scoreboardConfig.json`

Most major systems can be enabled, disabled, tuned, or permission-gated. See the wiki for current command, permission, storage, and setup documentation:

https://wiki.thelegacyvoyage.xyz/

## Contributing

Contributions are welcome although the project is actively in development. Before opening a pull request:

1. Keep changes scoped and explain the server-owner impact.
2. Follow the existing Java package structure and feature patterns.
3. Use existing managers, storage backends, permission helpers, and message utilities instead of adding one-off systems.
4. Add or update player-facing language keys for new messages.
5. Update documentation when commands, permissions, config, storage behavior, UI workflows, or user-facing features change.
6. Run `.\gradlew compileJava` before submitting Java changes.
7. Run `.\gradlew test` when touching storage, config migration, economy logic, permissions, or non-trivial manager behavior.

For larger changes, open an issue or start a discussion in Discord first so the design can be aligned before implementation work begins.

## Reporting Issues

When reporting a bug, include:

- HyEssentialsX version.
- Hytale server version.
- Storage backend in use.
- Relevant config snippets.
- Exact command, UI workflow, or server action that triggered the issue.
- Server log output around the error.
- Whether the issue happens on a clean config.

Please avoid posting private database credentials, tokens, or full production config files.

## License

HyEssentialsX is licensed under the GNU General Public License v3.0 or later.

Copyright (C) 2026 MystifiedSky.

See [LICENSE](LICENSE) for the full license text.

