# RpEssentials

**Rp Essentials** is a comprehensive both client & server side utility mod built for immersive Roleplay servers running Minecraft 1.21.1 on NeoForge or Minecraft 1.20.1 on Forge and NeoForge. It covers identity obfuscation, professions and licenses, moderation, chat formatting, server scheduling, Death RP, world border and zones, and more, all configurable in real time without restarts.

> Current version: **6.2.0**. Full changelog on [Modrinth](https://modrinth.com/mod/rp-essentials/changelog).

Full documentation, configuration reference and setup guides now live on the **[GitHub Wiki](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki)**.

---

## Feature overview

| Feature                                                                                | Wiki page                                                                                                                   |
|:---------------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------|
| Name obfuscation, nicknames, nametags, character sheets                                | [Identity System](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/02.-Identity-System)                         |
| Professions, licenses, crafting/equipment/container restrictions                       | [Profession & License System](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/03.-Profession-&-License-System) |
| Warns, mutes, temp bans, reports, staff notes, player history, vanish, Discord webhook | [Moderation System](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/04.-Moderation-System)                     |
| Chat formatting, private messages, join/leave, announcements                           | [Chat System](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/06.-Chat-System)                                 |
| Opening hours, welcome message, Death Hours, HRP Hours                                 | [Server Schedule](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/05.-Server-Schedule)                         |
| Permanent death system                                                                 | [Death RP](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/07.-Death-RP)                                       |
| Distance warnings (square or circle) and named zones                                   | [World Border & Zones](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/08.-World-Border-&-Zones)               |
| Silent commands, platforms, roles & permissions, dice, auto unwhitelist                | [Staff Tools](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/09.-Staff-Tools)                                 |
| In game Config Manager GUI (search, value checks)                                      | [Config Manager GUI](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/10.-Config-Manager-GUI)                   |
| Full command list                                                                      | [Commands](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/11.-Commands)                                       |
| Config files and data storage                                                          | [Configuration Files](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/12.-Configuration-Files)                 |

---

## Generative AI Disclosure

Hi, here is all you need to know about AI in this project:

- **AI was NOT used to generate any visual assets**

- Every idea added to this mod come from my own little brain **however** I did use AI to code most of them.
  I started this mod for an RP server I had with other people because I needed some utilities for it, and I did not have the skills required for coding all of this.
  Today I'm learning to do most of the new features by myself but AI is still a good tool to help me code complex things.
  From my own perspective, AI is a great tool for coding and I will continue to use it if needed, but I will **never ever** use AI to generate visual assets.

- Even though AI was used for the development, I test the mod every time before posting a new version to be sure that there are no bugs left or no problems. This does not mean that there are not any bugs, but I try as much as I can to find and correct them ^^ And if it matters to you, I use this mod pretty often and for the moment, I never had any issue with it (or they were fixed pretty quickly)! (And if you find an issue, you can always tell it to me on the github page or in the comment section)

- That's pretty much the only things I had to say, have a great day!

---

## Requirements

| Dependency                                                                             | Version                | Side   | Required                                     |
|----------------------------------------------------------------------------------------|------------------------|--------|----------------------------------------------|
| Minecraft                                                                              | 1.21.1 or 1.20.1       | Both   | Yes                                          |
| NeoForge                                                                               | 21.1.219+              | Both   | With Minecraft 1.21.1                        |
| Forge (or NeoForge 47.1.x for 1.20.1)                                                  | 47.1+                  | Both   | With Minecraft 1.20.1                        |
| [RpImmersion](https://modrinth.com/mod/rp-immersion)                                   | 1.1.1                  | Both   | Optional                                     |
| LuckPerms                                                                              | Any                    | Server | Optional                                     |
| ImmersiveMessages                                                                      | neoforge-1.21.1:1.0.18 | Client | Optional                                     |
| TxniLib                                                                                | neoforge-1.21.1:1.0.24 | Client | Optional                                     |
| [Forgified Fabric API](https://modrinth.com/mod/forgified-fabric-api/version/C4eskNuR) | 0.116.15+              | Both   | With ImmersiveMessages on NeoForge 21.1.262+ |

ImmersiveMessages and TxniLib are only required client side if you use the `IMMERSIVE` display mode somewhere in the config. The server runs fine without them.

> **Important: ImmersiveMessages on NeoForge 21.1.262 or newer.** TxniLib (needed by ImmersiveMessages) ships
> Forgified Fabric API 0.115.6, which disconnects every player joining a world from NeoForge 21.1.262 on.
> If you use ImmersiveMessages on those NeoForge versions, also install **Forgified Fabric API 0.116.15 or newer**
> (the newest version is kept automatically). RpEssentials checks it at startup and shows a warning on the
> NeoForge loading screen when the old version is found. On older NeoForge versions, or without
> ImmersiveMessages, nothing is needed.

---

## Installation

1. Download the latest `rpessentials-X.X.X.jar` (NeoForge 1.21.1) or `rpessentials-X.X.X+forge-1.20.1.jar` (Forge or NeoForge 1.20.1) from the releases page.
2. Place the JAR in your server's `mods/` folder.
3. Optionally install [LuckPerms](https://luckperms.net/) for prefix/suffix and group based staff permissions.
4. Start the server, all config files are generated automatically under `config/rpessentials/`.
5. Reload in game with `/rpessentials config reload` after editing, or restart the server.

See the [Installation](https://github.com/Finerus/Neoforge-RpEssentials-1.21.1/wiki/01.-Installation) wiki page for details.

---

## Technical Information

| Field      | Value                 |
|:-----------|:----------------------|
| Mod ID     | `rpessentials`        |
| Group ID   | `net.rp.rpessentials` |
| Version    | 6.2.0                 |
| MC Version | 1.21.1 / 1.20.1       |
| NeoForge   | 21.1.219+ (MC 1.21.1) |
| Forge      | 47.1+ (MC 1.20.1)     |
| Java       | 21 / 17 (MC 1.20.1)   |