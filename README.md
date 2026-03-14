# Rp Essentials

**Rp Essentials** is a comprehensive server-side utility mod built for immersive Roleplay servers running Minecraft 1.21.1 on NeoForge. It provides a complete suite of RP tools — proximity-based name obfuscation, a profession and license system, a warn system, connection tracking, private messaging, schedule automation, staff moderation tools, advanced chat formatting, world border warnings, named zones, and deep LuckPerms integration — all configurable in real-time without restarts.

> Current version: **4.0.0** — See [CHANGELOG](CHANGELOG.md) for full history.

---

## Requirements

| Dependency | Version | Side | Required |
|:-----------|:--------|:-----|:---------|
| Minecraft | 1.21.1 | Both | ✅ |
| NeoForge | 21.1.215+ | Both | ✅ |
| LuckPerms | Any | Server | ⬜ Optional |
| ImmersiveMessages | neoforge-1.21.1:1.0.18 | Client | ⬜ Optional |
| TxniLib | neoforge-1.21.1:1.0.24 | Client | ⬜ Optional |

> ImmersiveMessages and TxniLib are only required on the **client** if you use `zoneMessageMode = IMMERSIVE`. The server runs fine without them.

---

## Installation

1. Download the latest `oneriamod-X.X.X.jar` from the releases page.
2. Place the JAR in your server's `mods/` folder.
3. *(Optional)* Install [LuckPerms](https://luckperms.net/) for prefix/suffix and group-based staff permissions.
4. Start the server — all config files are generated automatically under `config/oneria/`.
5. Edit the relevant config files (see [Configuration](#configuration) below).
6. Reload in-game with `/oneria config reload` or restart the server.

---

## Features

### Proximity Obfuscation

Prevents metagaming by hiding player identities based on distance:

- Player names in the TabList and above heads are replaced with `?????` beyond a configurable range (default: 8 blocks).
- **Sneak Stealth Mode:** Crouching players are only detectable at 2 blocks (configurable).
- **Nametag Hiding:** Optionally hide all player nametags above heads server-wide.
- **Whitelist:** Players who always see all names clearly.
- **Blacklist:** Players who are always hidden regardless of distance (stealth staff, NPCs).
- **Always Visible List:** Players who are never obfuscated to anyone.
- **Spectator Blur:** Players in spectator mode are automatically hidden from TabList.
- LuckPerms rank prefixes are always hidden during obfuscation to prevent rank-based metagaming.
- Staff with `opsSeeAll` see both nickname and real name simultaneously: `Nickname (RealName)`.

### Nickname System

Persistent RP nicknames fully integrated across all mod systems:

- Set custom nicknames with full `§` and `&` color code support.
- Nicknames appear in TabList, above heads, chat, and private messages.
- Stored persistently in `world/data/oneriamod/nicknames.json`.
- `/whois` allows staff to reverse-lookup any nickname to its real MC username and UUID.

### Profession & License System

A complete job and economy restriction system for RP servers:

- Define unlimited professions with custom names and color codes.
- Physical **license items** are given to players, carrying profession metadata and issuance date.
- **RP Licenses:** Decorative-only licenses for events, with a printed expiration date and no actual permissions.
- **Restriction types:** crafting, block breaking, item usage, equipment, and attacks.
- **Global restrictions** apply to all players by default, with **profession-specific overrides** for licensed players.
- Wildcard pattern support: `minecraft:*_pickaxe` blocks entire item categories.
- Restriction messages shown to players via action bar with anti-spam cooldown.
- Tooltips show required professions on restricted items (client-side packet sync).
- **License Audit Log:** Every `GIVE`, `REVOKE`, and `GIVE_RP` action is permanently logged to `world/data/oneriamod/license-audit.json`.
- **Temp License Registry:** RP licenses tracked in `world/data/oneriamod/licenses-temp.json`.
- Whitelist players bypass all profession restrictions.

### Warn System

Full staff warning system for moderation:

- Issue **permanent** or **temporary** warns to any online player.
- Warned player receives an immediate in-chat notification with reason and duration.
- On every login, players are notified of their active warn count and invited to run `/mywarn`.
- `/mywarn` lets any player view their own active warns with reason, issuer, date and remaining time.
- Configurable staff broadcast on every warn add and warn remove.
- Automatic purge of expired temp-warns on player login (configurable).
- Warn IDs are auto-incremental integers, recalculated from the current max after every deletion to avoid gaps and collisions.
- Data stored in `world/data/oneriamod/warns.json`.

### Last Connection Tracking

Automatic recording of player connection history:

- Records last login and last logout for every player.
- Data stored in `world/data/oneriamod/lastconnection.json` — human-readable `UUID (McUsername)` key format.
- Staff can look up any player's last connection time, even offline players.
- List view shows the N most recent connections sorted by date.

### Private Messaging

Custom `/msg` system fully replacing vanilla messaging:

- `/msg <player> <message>`, `/tell`, `/w`, `/whisper` — send private messages with Rp Essentials formatting.
- `/r <message>` — reply to the last person who messaged you.
- Nicknames are clickable — hover for prompt, click to auto-fill `/msg <player> `.
- Last interlocutor tracked per player, reset on logout.
- Vanilla `/msg`, `/tell`, `/w`, `/whisper` are fully replaced.

### Whois Command

Identity lookup for staff:

- `/whois <nickname>` — find the real MC username and UUID behind any nickname.
- Case-insensitive search, strips color codes for comparison.
- UUID is clickable — opens the player's NameMC profile on hover/click.
- Works on offline players via the server profile cache.

### Named Zones

Admin-configurable geographic zones with entry/exit messages:

- Zones defined in config: `name;centerX;centerZ;radius;messageEnter;messageExit`.
- Entry and exit messages sent via action bar, chat, or ImmersiveMessageAPI overlay (configurable).
- Multiple simultaneous zones fully supported.
- Per-player state tracking — no message spam.
- Zone distance uses 2D (X/Z) calculation; altitude is ignored.

### World Border Warnings

Distance-based warnings to keep players within boundaries:

- Players receive a warning when exceeding the configured distance from spawn (default: 2000 blocks).
- Warning resets when the player returns, allowing re-warning if they leave again.
- Sound notification on warning.
- Display mode configurable: `ACTION_BAR` (default, no client mod), `CHAT`, or `IMMERSIVE` (requires ImmersiveMessages client-side).

### Schedule System

Automated server opening and closing:

- Define opening and closing times in `HH:MM` format.
- Automatic warnings sent at configurable intervals before closing.
- Non-staff players are automatically kicked at closing time.
- Staff receive a connection notice when the server is closed.
- Schedule status visible to all players via `/oneria schedule`.

### Advanced Chat System

Professional chat formatting with markdown and LuckPerms support:

- Fully customizable message format with variables (`$time`, `$name`, `$msg`).
- LuckPerms prefix and suffix integration.
- Markdown support: `**bold**`, `*italic*`, `__underline__`, `~~strikethrough~~`.
- Full `&` and `§` color code support.
- Optional timestamp with customizable Java `SimpleDateFormat`.
- `/colors` displays all available colors and formatting codes with visual preview.

### Join / Leave Messages

- Fully customizable join and leave messages with `{player}` and `{nickname}` placeholders.
- Full color code support.
- Can be disabled entirely.

### Welcome Message

- Configurable multi-line welcome message sent to players on login.
- Optional sound effect with configurable volume and pitch.

### Staff Moderation Tools

Silent staff commands with full logging:

- Silent gamemode, teleport, and effect commands — invisible to other players.
- All actions logged to console and broadcast to other online staff (configurable).
- Target notification disabled by default (stealth mode).

### Teleportation Platforms

- Define named platforms across dimensions with custom coordinates.
- Staff can teleport themselves or other players to any platform.

---

## Commands

### Configuration
| Command | Permission | Description |
|:--------|:-----------|:------------|
| `/oneria config reload` | OP 2 | Reload all configs and clear caches. |
| `/oneria config status` | OP 2 | Display current status of all mod systems. |
| `/oneria config set <option> <value>` | OP 2 | Modify any config option in real-time. |

### Nicknames
| Command | Permission | Description |
|:--------|:-----------|:------------|
| `/oneria nick <player> <nickname>` | OP 2 | Set a nickname (supports color codes). |
| `/oneria nick <player>` | OP 2 | Reset a nickname. |
| `/oneria nick list` | OP 2 | List all active nicknames. |
| `/whois <nickname>` | OP 2 | Find the MC username and UUID behind a nickname. |

### Licenses
| Command | Permission | Description |
|:--------|:-----------|:------------|
| `/oneria license give <player> <profession>` | OP 2 | Grant a functional license. |
| `/oneria license giverp <player> <profession> <days>` | OP 2 | Grant a decorative RP-only license. |
| `/oneria license revoke <player> <profession>` | OP 2 | Revoke a license. |
| `/oneria license list [player]` | OP 2 | List all licenses, or for a specific player. |
| `/oneria license check <player> <profession>` | OP 2 | Check if a player holds a specific license. |

### Warns
| Command | Permission | Description |
|:--------|:-----------|:------------|
| `/oneria warn add <player> <reason>` | Staff | Issue a permanent warn. |
| `/oneria warn temp <player> <minutes> <reason>` | Staff | Issue a temporary warn. |
| `/oneria warn remove <warnId>` | Staff | Remove a specific warn. |
| `/oneria warn list [player]` | Staff | List all warns, or all warns for a player. |
| `/oneria warn info <warnId>` | Staff | Show full details of a warn. |
| `/oneria warn clear <player>` | Staff | Remove all warns for a player. |
| `/oneria warn purge` | Staff | Purge all expired temp-warns. |
| `/mywarn` | Everyone | View your own active warns. |

### Last Connection
| Command | Permission | Description |
|:--------|:-----------|:------------|
| `/oneria lastconnection <player>` | Staff | Show last login/logout for a player. |
| `/oneria lastconnection list [count]` | Staff | List the N most recent connections (default: 20). |

### Staff
| Command | Permission | Description |
|:--------|:-----------|:------------|
| `/oneria staff gamemode <mode> [player]` | Staff | Silent gamemode change. |
| `/oneria staff tp <player>` | Staff | Silent teleport. |
| `/oneria staff effect <player> <effect> <duration> <amplifier>` | Staff | Silent effect application. |
| `/oneria staff platform [player] [id]` | Staff | Teleport to a named platform. |

### Whitelist / Blacklist
| Command | Permission | Description |
|:--------|:-----------|:------------|
| `/oneria whitelist add/remove/list` | OP 2 | Manage the blur bypass whitelist. |
| `/oneria blacklist add/remove/list` | OP 2 | Manage the always-hidden blacklist. |
| `/oneria alwaysvisible add/remove/list` | OP 2 | Manage the never-obfuscated list. |

### Messaging
| Command | Permission | Description |
|:--------|:-----------|:------------|
| `/msg <player> <message>` | Everyone | Send a private message. |
| `/tell`, `/w`, `/whisper` | Everyone | Aliases for `/msg`. |
| `/r <message>` | Everyone | Reply to the last person who messaged you. |

### Public
| Command | Permission | Description |
|:--------|:-----------|:------------|
| `/oneria schedule` / `/schedule` / `/horaires` | Everyone | View server schedule and status. |
| `/oneria help` | Everyone | Display available commands (staff section shown to staff). |
| `/colors` | Everyone | Display all color codes and formatting options. |
| `/list` | Everyone | Custom player list showing nicknames and real usernames. |
| `/mywarn` | Everyone | View own active warns. |

---

## Configuration

The mod uses **5 config files** under `config/oneria/`, generated automatically on first launch. All files are fully documented with inline comments.

| File | Contents |
|:-----|:---------|
| `oneria-core.toml` | Obfuscation, permissions, world border & zones |
| `oneria-chat.toml` | Chat formatting, markdown, timestamps, join/leave messages |
| `oneria-schedule.toml` | Schedule, warnings, welcome message & sound |
| `oneria-moderation.toml` | Silent commands, platforms, last connection, warn system |
| `oneria-professions.toml` | Profession definitions and all restriction lists |

### oneria-core.toml

#### [Obfuscation Settings]
| Option | Default | Description |
|:-------|:--------|:------------|
| `enableBlur` | `true` | Master switch for the obfuscation system. |
| `proximityDistance` | `8` | Detection range in blocks (normal). |
| `obfuscatedNameLength` | `5` | Number of `?` characters in the obfuscated name. |
| `obfuscatePrefix` | `true` | Hide LuckPerms rank prefix when obfuscating. |
| `opsSeeAll` | `true` | Staff always see real names. |
| `hideNametags` | `false` | Hide all player nametags above heads. |
| `showNametagPrefixSuffix` | `true` | Show LuckPerms prefix/suffix in nametags. |
| `enableSneakStealth` | `true` | Crouching reduces detection range. |
| `sneakProximityDistance` | `2` | Detection range for crouching players (blocks). |
| `blurSpectators` | `true` | Hide spectator-mode players from TabList. |
| `whitelist` | `[]` | Players who always see all names clearly. |
| `blacklist` | `[]` | Players who are always hidden. |
| `alwaysVisibleList` | `[]` | Players who are never obfuscated to anyone. |
| `whitelistExemptProfessions` | `true` | Whitelist players bypass profession restrictions. |

#### [Permissions System]
| Option | Default | Description |
|:-------|:--------|:------------|
| `staffTags` | `["admin","moderateur","modo","staff","builder"]` | LuckPerms tags/groups considered staff. |
| `opLevelBypass` | `2` | Minimum OP level to bypass restrictions (0 = disabled). |
| `useLuckPermsGroups` | `true` | Use LuckPerms groups for staff detection. |
| `luckPermsStaffGroups` | `["admin","moderateur","staff"]` | LuckPerms groups treated as staff. |

#### [World Border Warning]
| Option | Default | Description |
|:-------|:--------|:------------|
| `enableWorldBorderWarning` | `true` | Enable distance-based warnings. |
| `worldBorderDistance` | `2000` | Warning trigger distance from spawn (blocks). |
| `worldBorderMessage` | `"..."` | Warning message (`{distance}`, `{player}` variables). |
| `worldBorderCheckInterval` | `40` | Check frequency in ticks (40 = 2 seconds). |
| `zoneMessageMode` | `ACTION_BAR` | Display mode: `ACTION_BAR`, `CHAT`, or `IMMERSIVE`. |
| `namedZones` | `[]` | Named zones (`name;cx;cz;radius;msgEnter;msgExit`). |

### oneria-chat.toml

#### [Chat System]
| Option | Default | Description |
|:-------|:--------|:------------|
| `enableChatFormat` | `true` | Enable custom chat formatting. |
| `playerNameFormat` | `"$prefix $name $suffix"` | Name format in chat. |
| `chatMessageFormat` | `"$time \| $name: $msg"` | Full message template. |
| `chatMessageColor` | `WHITE` | Global message text color. |
| `enableTimestamp` | `true` | Show timestamp in messages. |
| `timestampFormat` | `"HH:mm"` | Java `SimpleDateFormat` for timestamps. |
| `markdownEnabled` | `true` | Enable markdown styling in chat. |
| `enableColorsCommand` | `true` | Enable the `/colors` command. |

#### [Join / Leave Messages]
| Option | Default | Description |
|:-------|:--------|:------------|
| `enableCustomJoinLeave` | `true` | Enable custom join/leave messages. |
| `joinMessage` | `"§e{player} §7joined the game"` | Join message (`{player}`, `{nickname}`). |
| `leaveMessage` | `"§e{player} §7left the game"` | Leave message (`{player}`, `{nickname}`). |

### oneria-schedule.toml

#### [Schedule System]
| Option | Default | Description |
|:-------|:--------|:------------|
| `enableSchedule` | `true` | Enable automated opening/closing. |
| `openingTime` | `"19:00"` | Server opening time (HH:MM). |
| `closingTime` | `"23:59"` | Server closing time (HH:MM). |
| `warningTimes` | `[45,30,10,1]` | Minutes before closing to send warnings. |
| `kickNonStaff` | `true` | Kick non-staff at closing time. |

#### [Welcome Message]
| Option | Default | Description |
|:-------|:--------|:------------|
| `enableWelcome` | `true` | Enable the welcome message on login. |
| `welcomeLines` | `[...]` | List of lines sent on login (`{player}`, `{nickname}`). |
| `welcomeSound` | `""` | Sound played on login (resource location string). |
| `welcomeSoundVolume` | `1.0` | Welcome sound volume. |
| `welcomeSoundPitch` | `1.0` | Welcome sound pitch. |

### oneria-moderation.toml

#### [Silent Commands]
| Option | Default | Description |
|:-------|:--------|:------------|
| `enableSilentCommands` | `true` | Enable silent staff commands. |
| `logToStaff` | `true` | Broadcast silent command usage to other staff. |
| `logToConsole` | `true` | Log silent commands to the server console. |
| `notifyTarget` | `false` | Notify the target of the action (stealth off). |

#### [Teleportation Platforms]
| Option | Default | Description |
|:-------|:--------|:------------|
| `enablePlatforms` | `true` | Enable the platform teleport system. |
| `platforms` | `[...]` | Platform list (`id;DisplayName;dimension;x;y;z`). |

#### [Last Connection]
| Option | Default | Description |
|:-------|:--------|:------------|
| `enableLastConnection` | `true` | Enable login/logout time tracking. |
| `trackLogout` | `true` | Also record disconnection times. |
| `dateFormat` | `"dd/MM/yyyy HH:mm:ss"` | Java `SimpleDateFormat` for stored dates. |

#### [Warn System]
| Option | Default | Description |
|:-------|:--------|:------------|
| `enableWarnSystem` | `true` | Enable the warn system. |
| `notifyOnJoin` | `true` | Notify players of active warns on login. |
| `joinMessage` | `"§c⚠ Vous avez §l{count}..."` | Login notification message (`{count}`). |
| `maxTempDays` | `30` | Max duration for temp warns in days (0 = unlimited). |
| `autoPurgeExpired` | `true` | Auto-purge expired warns on player login. |
| `addedBroadcastFormat` | `"..."` | Staff broadcast on warn add (`{id}`, `{staff}`, `{player}`, `{reason}`, `{expiry}`). |
| `removedBroadcastFormat` | `"..."` | Staff broadcast on warn remove (`{id}`, `{staff}`). |

### oneria-professions.toml

| Option | Description |
|:-------|:------------|
| `professions` | Profession definitions (`id;DisplayName;§ColorCode`). |
| `globalBlockedCrafts` | Items blocked from crafting for all players. |
| `globalUnbreakableBlocks` | Blocks that cannot be broken by anyone. |
| `globalBlockedItems` | Items that cannot be used or interacted with. |
| `globalBlockedEquipment` | Armor/weapons that cannot be equipped. |
| `professionAllowedCrafts` | Per-profession craft overrides (`profession;item1,item2`). |
| `professionAllowedBlocks` | Per-profession block breaking overrides. |
| `professionAllowedItems` | Per-profession item usage overrides. |
| `professionAllowedEquipment` | Per-profession equipment overrides. |

---

## Data Storage

All persistent data is stored in the world folder. Files are human-readable JSON and can be edited manually. Keys use `UUID (McUsername)` format for readability.

| File | Contents |
|:-----|:---------|
| `world/data/oneriamod/nicknames.json` | Player nicknames. |
| `world/data/oneriamod/licenses.json` | Player licenses. |
| `world/data/oneriamod/license-audit.json` | Append-only audit log of all license actions. |
| `world/data/oneriamod/licenses-temp.json` | Registry of all issued RP licenses. |
| `world/data/oneriamod/lastconnection.json` | Last login/logout per player. |
| `world/data/oneriamod/warns.json` | All warns (permanent and temporary). |

All saves are **asynchronous** via `CompletableFuture` — no server thread blocking.

---

## Technical Details

### Architecture

- **Server-side only** for all core features — no client installation required.
- **Mixin-based** low-level interception for TabList obfuscation and chat formatting.
- **Event-driven** via the NeoForge event system (login/logout, restrictions, nametags).
- **5 separate config files** under `config/oneria/` — each system has its own file.
- **Automatic config migration** from legacy `RpEssentials-server.toml` on first launch.
- **JSON storage** with Gson — async writes, sync reads on startup.

### Performance

- Permission checks cached for 30 seconds per player.
- Profession restriction checks use a 30-second lazy-init cache.
- Schedule system checks every 20 seconds (400 ticks).
- World border checks configurable (default: 40 ticks).
- Anti-spam cooldown maps on all action bar notifications (1–3 seconds).
- Cache cleanup every 20 seconds to prevent memory leaks.
- Equipment restriction checks are tick-filtered to reduce CPU overhead.
- Wildcard patterns compiled once via regex cache (`computeIfAbsent`).

### Compatibility

- **LuckPerms:** Full prefix, suffix, and group-based permission support. Gracefully disabled when absent.
- **ImmersiveMessages + TxniLib:** Optional client-side — used for zone/border overlay messages. Server does not crash without them.

---

## Authors

- **Finerus** — Development

---

*All Rights Reserved*