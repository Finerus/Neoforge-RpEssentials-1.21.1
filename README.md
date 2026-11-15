# Rp Essentials

**Rp Essentials** is a comprehensive utility mod built for immersive Roleplay servers running Minecraft 1.21.1 on NeoForge. It covers identity obfuscation, nicknames and character sheets, professions and licenses, roles and permissions, moderation, chat formatting, server scheduling, Death RP, world border and zones, and more, all configurable in real time without restarts, from the config files or from in game staff screens.

> Current version: **6.0.0**. Full changelog on [Modrinth](https://modrinth.com/mod/rp-essentials/changelog).

Full documentation, configuration reference and setup guides now live on the **[GitHub Wiki](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki)**.


---

## Feature overview

| Feature                                                                          | Wiki page                                                                                                                   |
|:---------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------|
| Name obfuscation, nicknames, nametags, character sheets                          | [Identity System](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/02.-Identity-System)                         |
| Professions, licenses, crafting/equipment/container restrictions                 | [Profession & License System](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/03.-Profession-&-License-System) |
| Warns, mutes, temp bans, reports, staff notes, player history, vanish, Discord webhook | [Moderation System](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/04.-Moderation-System)               |
| Chat formatting, private messages, join/leave, announcements                     | [Chat System](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/06.-Chat-System)                                 |
| Opening hours, welcome message, Death Hours, HRP Hours                           | [Server Schedule](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/05.-Server-Schedule)                         |
| Permanent death system                                                           | [Death RP](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/07.-Death-RP)                                       |
| Distance warnings (square or circle) and named zones                             | [World Border & Zones](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/08.-World-Border-&-Zones)               |
| Silent commands, platforms, roles & permissions, dice, auto unwhitelist          | [Staff Tools](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/09.-Staff-Tools)                                 |
| In game Config Manager GUI (search, value checks)                                | [Config Manager GUI](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/10.-Config-Manager-GUI)                   |
| Full command list                                                                | [Commands](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/11.-Commands)                                       |
| Config files and data storage                                                    | [Configuration Files](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/12.-Configuration-Files)                 |

Staff also get in game screens for players (Player Profile: profile, stats, notes, reports, history, manage actions) and for professions and roles (Profession Editor). Every screen is available in English and French.

---

## Generative AI Disclosure

Hi, here is all you need to know about AI in this project:

- **AI was NOT used to generate any visual assets**

- Every idea added to this mod come from my own little brain **however** I did use AI to code most of them.
  I started this mod for an RP server I had with other people because I needed some utilities for it, and I did not have the skills required for coding all of this.
  Today I'm learning to do most of the new features by myself but AI is still a good tool to help me code complex things.
  From my own perspective, AI is a great tool for coding and I will continue to use it if needed, but I will **never ever** use AI to generate visual assets.

- That's pretty much the only things I had to say, have a great day!

---

## Requirements

| Dependency                                                              | Version                | Side   | Required |
|-------------------------------------------------------------------------|------------------------|--------|----------|
| Minecraft                                                               | 1.21.1                 | Both   | Yes      |
| NeoForge                                                                | 21.1.219+              | Both   | Yes      |
| [RpImmersion](https://www.curseforge.com/minecraft/mc-mods/rpimmersion) | 1.0.0                  | Both   | Optional |
| LuckPerms                                                               | Any                    | Server | Optional |
| ImmersiveMessages                                                       | neoforge-1.21.1:1.0.18 | Both   | Optional |
| TxniLib                                                                 | neoforge-1.21.1:1.0.24 | Both   | Optional |
| Curios API                                                              | 9.5.1+1.21.1           | Both   | Optional |

The mod must be installed on the server **and** on every client, with the same version on both sides.

---

## Installation

1. Download the latest `rpessentials-X.X.X.jar` from the releases page.
2. Place the JAR in the `mods/` folder of your server and of every client.
3. Optionally install [LuckPerms](https://luckperms.net/) for prefixes/suffixes and a two way sync between the RpEssentials roles and the LuckPerms groups.
4. Start the server, all config files are generated automatically under `config/rpessentials/`.
5. Reload in game with `/rpessentials config reload` after editing, or restart the server.

See the [Installation](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/01.-Installation) wiki page for details.

### Updating to 6.0.0

- Config files from older versions are updated automatically at startup. Each changed file is first copied to `<name>.bak-migration`, and every change is written in the server log with `[Migration]`.
- The `Roles` section moved into `Permissions System`, and `staffTags` / `luckPermsStaffGroups` are replaced by roles with the `isStaff` permission (converted automatically, the same players stay staff).
- `worldBorderShape` defaults to `SQUARE`: set it to `CIRCLE` to keep the previous circular limit.
- Update the server and the clients together.

See the full changelog for every change.

---

## Technical Information

| Field      | Value             |
|:-----------|:------------------|
| Mod ID     | `rpessentials`    |
| Group ID   | `net.rpessentials`|
| Version    | 6.0.0             |
| MC Version | 1.21.1            |
| NeoForge   | 21.1.219+         |
| Java       | 21                |
| Build      | ModDevGradle      |
