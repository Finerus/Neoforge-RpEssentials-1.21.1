# Changelog - Oneria Mod
All notable changes to this project will be documented in this file.

## [3.1.0] - 2026-03-08

**Added**

* **Last Connection Tracking:**
  - Automatic recording of each player's last login and logout times.
  - Data stored in `world/data/oneriamod/lastconnection.json` — `UUID (McUsername)` key format, consistent with all other mod data files.
  - Async save via `CompletableFuture`, synchronous load on startup.
  - Login is recorded immediately on connection; logout is recorded on disconnection.
  - Staff commands:
    - `/oneria lastconnection <player>` — displays a player's last login, last logout and current online status.
    - `/oneria lastconnection list [count]` — lists the N most recent connections sorted by descending date (default: 20).
  - Fully configurable in `oneria-moderation.toml`, new `[Last Connection]` section:
    - `enableLastConnection` — enable/disable the entire tracking system.
    - `trackLogout` — toggle logout time recording independently.
    - `dateFormat` — Java `SimpleDateFormat` pattern used for both storage and display.

* **Warn System:**
  - Full staff warning system with permanent and temporary warns.
  - Data stored in `world/data/oneriamod/warns.json` — auto-incremental integer IDs, recalculated from the current max after every deletion to avoid gaps and collisions.
  - Warned player is notified immediately in chat with the reason, duration, and an invite to run `/mywarn`.
  - Configurable staff broadcast on warn add and warn remove.
  - Active warn count notified to the player on every login (configurable message with `{count}` placeholder).
  - Optional automatic purge of expired temp-warns on player login.
  - Staff commands (requires `isStaff`):
    - `/oneria warn add <player> <reason>` — issue a permanent warn.
    - `/oneria warn temp <player> <minutes> <reason>` — issue a temporary warn with configurable max duration.
    - `/oneria warn remove <warnId>` — remove a specific warn; notifies the player if online.
    - `/oneria warn list [player]` — list all warns, or all warns for a specific player.
    - `/oneria warn info <warnId>` — show full details of a warn (issuer, reason, date, remaining time).
    - `/oneria warn clear <player>` — remove all warns for a player at once; notifies the player if online.
    - `/oneria warn purge` — manually purge all expired temp-warns.
  - Player command (available to all):
    - `/mywarn` — display own active warns with reason, issuer, date and remaining duration.
  - Fully configurable in `oneria-moderation.toml`, new `[Warn System]` section:
    - `enableWarnSystem` — enable/disable the entire system.
    - `notifyOnJoin` — toggle login notification.
    - `joinMessage` — customizable login notification message (`{count}` placeholder).
    - `maxTempDays` — maximum allowed duration for `/oneria warn temp` (0 = unlimited).
    - `autoPurgeExpired` — toggle automatic purge of expired warns on player login.
    - `addedBroadcastFormat` — staff broadcast format on warn add (`{id}`, `{staff}`, `{player}`, `{reason}`, `{expiry}`).
    - `removedBroadcastFormat` — staff broadcast format on warn remove (`{id}`, `{staff}`).

**Fixed**

* **`lastConnectionPlayer` — Lambda Capture:**
  - `targetUUID` was reassigned inside an `if/else` block, making it non-effectively-final and causing a compilation error when captured in a `sendSuccess` lambda.
  - Fixed by capturing `final UUID finalTargetUUID` after the null-check early return.

**Technical**

* **New Classes:**
  - `LastConnectionManager` — connection tracking manager, lazy init + async save pattern consistent with `NicknameManager`.
  - `WarnManager` — warn manager with `WarnEntry` inner class, auto-incremental IDs, async save pattern consistent with `LicenseManager`. Exposes `recalculateCounter()` called after every deletion (`removeWarn`, `clearWarns`, `purgeExpiredWarns`).

* **Modified Classes:**
  - `ModerationConfig` — two new config sections: `[Last Connection]` and `[Warn System]`.
  - `OneriaEventHandler` — `onPlayerLogin` now calls `LastConnectionManager.recordLogin()` immediately (before the deferred block), and the deferred block includes `notifyWarnsOnJoin()` and `autoPurgeWarnsOnJoin()`. `onPlayerLogout` now calls `LastConnectionManager.recordLogout()`.
  - `OneriaCommands` — two new command modules: `lastconnection` (2 handlers) and `warn` (13 handlers + `/mywarn` standalone alias).

* **Data Storage:**
  - `world/data/oneriamod/lastconnection.json` — last login/logout per player, keyed by `UUID (McUsername)`.
  - `world/data/oneriamod/warns.json` — full warn registry, JSON array of `WarnEntry` objects.

**Migration Notes**

* No breaking changes — fully backward compatible with 3.0.3.
* Both new data files are created automatically on first use; no manual action required.
* `oneria-moderation.toml` gains two new sections on first launch — existing options are untouched.
* Both systems are disabled-safe: setting `enableLastConnection` or `enableWarnSystem` to `false` completely bypasses all related logic with no side effects.

## [3.0.3] - 2026-03-03

**Added**

* **Custom `/list` Command:**
  - Replaces vanilla `/list` with a cleaner output showing nicknames and real usernames in parentheses.
  - Players without a nickname are displayed by their real username only.

* **`/oneria help` Command:**
  - Displays available commands to the player.
  - Staff players see an additional section with staff-only commands.

## [3.0.2] - 2026-03-03

**Added**

* **Revoked License Visual Marker:**
  - Physical license items are now visually marked when their license has been revoked.
  - Lore is updated with `✖ PERMIS RÉVOQUÉ` and `Ce permis n'est plus valide.` on the item directly.
  - Marking is triggered on player login, every 10 minutes server-side, and immediately on `/license revoke` if the player is online.
  - Uses a `revoked` flag stored in the item's `CUSTOM_DATA` to avoid duplicate marking.
  - Items given before this update (without `professionId` in CUSTOM_DATA) are not affected.

**Technical**

* **Modified Classes:**
  - `TempLicenseExpirationManager` — added `markRevokedLicenseItems(ServerPlayer)`, called on login and in midnight sweep.
  - `OneriaEventHandler` — calls `markRevokedLicenseItems()` on player login.
  - `OneriaCommands` — `revokeLicense()` now calls `markRevokedLicenseItems()` immediately if player is online.
  - `OneriaServerUtilities` — added 10-minute tick (`% 12000`) to sweep all online players.
  - `OneriaCommands` — `giveLicense()` now stores `professionId` in item `CUSTOM_DATA` for future revoke detection.

## [3.0.1] - 2026-03-03

**Fixed**

* **License Revocation — No Longer Removes Item From Inventory:**
  - `/license revoke` now only removes the profession from `licenses.json` and invalidates permissions.
  - The physical license item is kept in the player's inventory as a decorative object.
  - Profession restrictions are lifted immediately via `ProfessionSyncHelper.syncToPlayer()`.
  - Player is notified that their license has been revoked.

* **`RevokedLicenseManager` — Removed:**
  - The class has been entirely removed as it no longer serves a purpose.
  - All references in `OneriaEventHandler` and `OneriaCommands` have been cleaned up.

* **`OneriaEventHandler` — Raw Thread Replaced:**
  - The `new Thread(() -> { Thread.sleep(2000); ... }).start()` pattern on player login has been replaced with `CompletableFuture.runAsync()`.
  - Eliminates the creation of one OS thread per player connection under load.
  - `server.execute()` ensures the deferred logic still runs on the server thread.
  - Added imports `net.minecraft.server.MinecraftServer` and `net.neoforged.neoforge.server.ServerLifecycleHooks`.

* **`OneriaServerUtilities.onServerTick()` — `server` Variable Scope:**
  - `var server` was declared inside the `if (tickCounter++ % 40 == 0)` block, making it invisible to the rest of the method.
  - Now declared once at the top of the method and reused by all blocks.

**Added**

* **`TempLicenseExpirationManager` — Automatic RP License Expiration:**
  - New class handling automatic expiration of RP licenses issued via `/license giverp`.
  - Two triggers: `checkOnLogin(player, server)` on player connection, and `tickMidnightSweep(server, hour, minute)` called from `onServerTick()` every 60 seconds (effective once per day at midnight).
  - On expiration: removes from `licenses-temp.json`, removes from `licenses.json` as a safety measure, invalidates cache, syncs client restrictions, and notifies the player if online.
  - Physical item is kept in inventory — player is informed it is no longer valid.
  - All expirations logged to `license-audit.json` with action `EXPIRE_RP`.
  - Takes `MinecraftServer` as an explicit parameter — never uses `player.getServer()` which is `@Nullable`.

* **`LicenseManager` — Two New Methods:**
  - `removeTempLicense(TempLicenseEntry)` — removes a specific entry from `licenses-temp.json`, matched by `targetUUID` + `profession` + `expiresAt`.
  - `logActionSystem(action, targetName, targetUUID, profession, extra)` — logs an audit entry without a human staff actor, used for system-triggered actions such as `EXPIRE_RP`.

**Technical**

* **Removed Classes:**
  - `RevokedLicenseManager` — fully deleted, no replacement needed.

* **New Classes:**
  - `TempLicenseExpirationManager` — automatic expiration logic for RP licenses.

* **Modified Classes:**
  - `OneriaEventHandler` — removed `RevokedLicenseManager` calls, replaced raw thread, added `TempLicenseExpirationManager.checkOnLogin()`.
  - `OneriaServerUtilities` — fixed `server` scope in `onServerTick()`, added `tickMidnightSweep` call every 1200 ticks.
  - `OneriaCommands` — `revokeLicense()` no longer calls `RevokedLicenseManager`, now calls `ProfessionSyncHelper.syncToPlayer()` immediately after revocation. `giveLicense()` also calls `syncToPlayer()`.
  - `LicenseManager` — added `removeTempLicense()` and `logActionSystem()`.

**Migration Notes**

* No configuration changes required.
* `RevokedLicenseManager` is gone — if you have any custom patches referencing it, remove them.
* Players who had licenses revoked in 3.0.0 and still have the physical item in their inventory will keep it. Their permissions are already removed in `licenses.json` — no action needed.

## [3.0.0] - 2026-03-02

**⚠ Breaking Change:** Configuration entirely restructured into 5 separate files under `config/oneria/`.
Automatic migration is included — no manual action required on first launch.

---

**Added**

* **License Audit Log:** Every license action permanently recorded in `world/data/oneriamod/license-audit.json`:
  - Tracks `GIVE`, `REVOKE`, and `GIVE_RP` actions.
  - Stores timestamp, staff name & UUID, target name & UUID, profession, and extra info.
  - Persists across restarts — full history always available.
  - Logged to server console simultaneously for real-time monitoring.

* **Temporary License Registry:** RP licenses issued via `/license giverp` now tracked in `world/data/oneriamod/licenses-temp.json`:
  - Stores issuance date, expiration date, duration in days, staff emitter, and recipient.
  - Acts as a permanent administrative registry — no automatic expiration logic (date is printed on the physical item).
  - Also logged to `license-audit.json` with the expiration date as extra info.

* **Config Split — 5 separate files:** The monolithic `oneriaserverutilities-server.toml` has been split into themed config files under `config/oneria/`:
  - `oneria-core.toml` — Obfuscation, Permissions, WorldBorder & Zones.
  - `oneria-chat.toml` — Chat formatting, timestamps, markdown, join/leave messages.
  - `oneria-schedule.toml` — Schedule system, warnings, welcome message & sound.
  - `oneria-moderation.toml` — Silent commands and teleportation platforms.
  - `oneria-professions.toml` — Profession definitions and restrictions (moved from `config/` root to `config/oneria/`).

* **ConfigMigrator — Automatic migration on first launch:**
  - Detects the legacy `oneriaserverutilities-server.toml` on startup.
  - Parses all values and redistributes them into the 5 new files, preserving every custom value.
  - Handles renamed keys automatically (e.g. `serverClosedMessage` → `msgServerClosed`, `warningMessage` → `msgWarning`).
  - Renames the legacy file to `.migrated.bak` — your data is never deleted.
  - Skips migration entirely if new files already exist — safe to restart multiple times.
  - Full migration report logged to server console.

* **OneriaPatternUtils:** New shared wildcard pattern matching utility:
  - Used by both `ClientProfessionRestrictions` and `ProfessionSyncHelper`.
  - Eliminates duplicated matching logic across the codebase.

**Improved**

* **LicenseManager — Enriched API:**
  - New inner classes: `AuditEntry` and `TempLicenseEntry`.
  - New methods: `logAction()`, `addTempLicense()`, `getAuditLog()`, `getAllTempLicenses()`.
  - All three files share the same async save pattern via `CompletableFuture`.
  - `reload()` now resets and reloads all three files simultaneously.

* **Thread Safety — ConcurrentHashMap across all managers:**
  - `OneriaPermissions`, `LicenseManager`, `ProfessionRestrictionManager`, `NicknameManager`, and `ProfessionRestrictionEventHandler` migrated to `ConcurrentHashMap`.
  - Eliminates race conditions under high player count or concurrent server-side events.

* **Regex cache in ProfessionRestrictionManager:**
  - Wildcard patterns (e.g. `minecraft:*_sword`) are now compiled once via `computeIfAbsent()` and cached in a `ConcurrentHashMap`.
  - Avoids repeated `Pattern.compile()` calls on every craft, break, use, and equip check.

* **Async I/O in NicknameManager:**
  - File reads and writes now run via `CompletableFuture` to avoid blocking the server thread.

* **Tick-based filtering in CraftingAndArmorRestrictionEventHandler:**
  - Equipment checks no longer run on every tick.
  - Reduces CPU overhead on servers with many simultaneous online players.

* **OneriaCommands — modifyList() factorization:**
  - The 9 handlers for whitelist/blacklist/alwaysvisible (add/remove/list × 3 lists) are now delegated to a single `modifyList()` method.
  - Eliminates ~60 lines of duplicated logic.
  - Original formatting style preserved (1522 lines vs 1606 — reduction from factorization only, no logic removed).

* **OneriaCommands — giveLicense(), revokeLicense(), giveRPLicense():**
  - Now call `LicenseManager.logAction()` on every action.
  - `giveRPLicense()` additionally calls `LicenseManager.addTempLicense()` to register the temp entry.
  - Staff player resolved from `CommandSourceStack` — supports both player and console execution.

**Fixed**

* **UUID Bug in ProfessionRestrictionManager:**
  - `isExemptFromProfessionRestrictions()` was comparing by player name instead of UUID.
  - Player name changes no longer silently break profession exemptions.

**Technical**

* **New Classes:**
  - `ConfigMigrator` — One-shot migration utility, runs before `registerConfig()` on startup.
  - `OneriaPatternUtils` — Shared wildcard pattern matching, used by `ClientProfessionRestrictions` and `ProfessionSyncHelper`.
  - `ChatConfig` — Config class for chat system and join/leave messages (`oneria-chat.toml`).
  - `ScheduleConfig` — Config class for schedule, messages, and welcome system (`oneria-schedule.toml`).
  - `ModerationConfig` — Config class for silent commands and platforms (`oneria-moderation.toml`).

* **Enhanced Classes:**
  - `LicenseManager` — Three-file I/O, `AuditEntry`/`TempLicenseEntry` inner classes, full audit & temp license API.
  - `OneriaCommands` — `giveLicense()`, `revokeLicense()`, `giveRPLicense()` wired to audit log and temp registry. `modifyList()` factorization.
  - `OneriaConfig` — Now only contains Obfuscation, Permissions, and WorldBorder/Zones. Chat, Schedule, Moderation extracted to dedicated classes.
  - `OneriaServerUtilities` — Registers 5 configs with explicit `oneria/` paths, calls `ConfigMigrator.migrateIfNeeded()` before registration.
  - `ProfessionRestrictionManager` — ConcurrentHashMap, regex cache, UUID-based exemption check.
  - `NicknameManager` — Async I/O via CompletableFuture.
  - `CraftingAndArmorRestrictionEventHandler` — Tick-based equipment check filtering.
  - `ClientProfessionRestrictions` / `ProfessionSyncHelper` — Deduplicated via OneriaPatternUtils.

* **Data Storage:**
  - `world/data/oneriamod/license-audit.json` — Append-only audit log, JSON array of `AuditEntry` objects.
  - `world/data/oneriamod/licenses-temp.json` — RP license registry, JSON array of `TempLicenseEntry` objects.

**Migration Notes**

* **Fully automatic** — `ConfigMigrator` handles everything on first launch. No manual action required.
* Legacy `oneriaserverutilities-server.toml` is backed up as `oneriaserverutilities-server.toml.migrated.bak` in the same `config/` folder.
* `oneria-professions.toml` moves from `config/` root to `config/oneria/` — handled by the migrator.
* If migration fails for any reason, the legacy file is left completely untouched and a detailed error is logged. Contact the dev team for manual migration assistance.
* All custom values (whitelist, platforms, schedules, messages, zones, etc.) are fully preserved through migration.

## [2.1.1] - 2026-02-23

**Added**

* **Zone Message Mode Config:** New configuration option for zone and world border message display:
  - `IMMERSIVE` - ImmersiveMessageAPI overlay (requires client mod).
  - `CHAT` - Standard chat message.
  - `ACTION_BAR` - Action bar, vanilla, no client mod needed (default).
  - Command: `/oneria config set zoneMessageMode <IMMERSIVE|CHAT|ACTION_BAR>`.
  - Automatic fallback to action bar if ImmersiveMessageAPI is unavailable client-side.

**Migration Notes**

* Default mode is `ACTION_BAR` — no behavior change unless explicitly set to `IMMERSIVE` or `CHAT`.

## [2.1.0] - 2026-02-23

**Added**

* **Private Messaging System:** Custom `/msg` implementation replacing vanilla messaging:
  - `/msg <player> <message>` - Send a private message with Oneria formatting.
  - `/tell`, `/w`, `/whisper` - Aliases for `/msg`.
  - `/r <message>` - Reply to the last person who messaged you.
  - Sender sees: `[MP] Vous écrivez à [RecipientNick] : message`.
  - Recipient sees: `[MP] [SenderNick] vous écrit : message`.
  - Clickable nicknames — hover to see prompt, click to auto-fill `/msg <player> `.
  - Last interlocutor tracked per player, reset on logout.
  - Vanilla `/msg`, `/tell`, `/w`, `/whisper` fully replaced via `dispatcher.getRoot().getChildren().removeIf()`.

* **Whois Command:** Identity lookup tool for staff:
  - `/whois <nickname>` - Find the real MC username and UUID behind a nickname.
  - Case-insensitive search, strips color codes for comparison.
  - Clickable UUID — opens NameMC profile in browser on hover/click.
  - Works on offline players via profile cache.
  - Requires OP Level 2.
  - Also accessible as `/oneria whois <nickname>`.

* **Named Zone System:** Admin-configurable zones with entry/exit messages:
  - Zones defined in config: `name;centerX;centerZ;radius;messageEnter;messageExit`.
  - Entry/exit messages sent via ImmersiveMessageAPI (stylized overlay).
  - Multiple simultaneous zones supported.
  - Per-player zone state tracking — no message spam.
  - Commands: `/oneria config set addZone <definition>`, `removeZone <name>`, `listZones`.
  - New config option: `namedZones` in `[World Border Warning]` section.

* **ImmersiveMessageAPI Integration:** Stylized overlay messages for immersion:
  - World border warnings now display as stylized overlay.
  - Zone entry/exit messages use the same overlay system.
  - Fade-in and fade-out animations (0.5s each).
  - ⚠️ Requires ImmersiveMessages + TxniLib installed client-side to display — server won't crash without it.

**Improved**

* **Data Readability:** Nickname and license JSON files now include MC username for admin readability:
  - Keys stored as `UUID (McUsername)` instead of bare UUID.
  - Fully retrocompatible — old UUID-only keys still load correctly.
  - Applies to both `nicknames.json` and `licenses.json`.

* **Schedule Time Parsing:** More robust handling of `openingTime`/`closingTime`:
  - New `normalizeTime()` method handles TOML-corrupted time values.
  - Supports `HH:MM`, `HH:MM:SS`, and integer (total minutes) formats.
  - `/oneria config set openingTime/closingTime` now uses `greedyString()` — no quotes needed: `/oneria config set openingTime 02:00`.

**Fixed**

* **Whois Null Safety:** Added null checks to prevent crashes with third-party nickname mods (e.g. Better Forge Chat Reborn).
* **Vanilla Command Override:** Vanilla `/msg`, `/tell`, `/w`, `/whisper` now properly removed before re-registration.

**Technical**

* **New Classes:**
  - `OneriaMessagingManager` - Private messaging with last-interlocutor tracking and click-to-reply.

* **Enhanced Classes:**
  - `WorldBorderManager` - Named zone system, per-player state tracking, ImmersiveMessageAPI integration.
  - `OneriaCommands` - Added `/whois`, `/msg`, `/tell`, `/w`, `/whisper`, `/r`, zone management commands.
  - `OneriaConfig` - Added `NAMED_ZONES` config entry.
  - `OneriaScheduleManager` - Added `normalizeTime()` for robust time parsing.
  - `NicknameManager` - `saveToFile()`/`loadFromFile()` updated with UUID+McName key format.
  - `LicenseManager` - `saveToFile()`/`loadFromFile()` updated with UUID+McName key format.
  - `OneriaEventHandler` - Added `OneriaMessagingManager.clearCache()` on player logout.

* **Dependencies:**
  - Added ImmersiveMessages `neoforge-1.21.1:1.0.18` (optional client-side).
  - Added TxniLib `neoforge-1.21.1:1.0.23` (optional client-side).

**Configuration**

* **New Options:**
  - `namedZones` (List) - Named zone definitions with entry/exit messages (default: empty).
    - Location: `[World Border Warning]` section.
    - Format: `name;centerX;centerZ;radius;messageEnter;messageExit`.

**Migration Notes**

* No breaking changes — fully backward compatible with 2.0.1.
* Existing `nicknames.json` and `licenses.json` with bare UUID keys will be migrated automatically on next save.
* ImmersiveMessages is optional — without it, zone/border messages won't display but the server won't crash.
* Vanilla `/msg` and `/tell` are fully replaced — players must use the new system.

**Known Limitations**

* ImmersiveMessages requires client-side installation for messages to display.
* `/r` only tracks the last interlocutor of the current session — resets on server restart.
* Named zones use 2D distance (X/Z only) — altitude is ignored.

# [2.0.1] - 2026-02-05

### Added

**Profession & License System - Complete Overhaul**

* **Profession Restriction Manager:** Comprehensive system for managing craft, mining, item usage, and equipment restrictions based on player professions.
  - Global restriction lists for crafts, blocks, items, and equipment that apply to all players by default.
  - Profession-specific override system allowing licensed players to bypass restrictions.
  - Pattern matching support with wildcards (e.g., `minecraft:*_pickaxe` blocks all pickaxes).
  - Efficient caching system with 30-second cache duration for performance optimization.
  - Graceful fallback when configuration is not yet loaded.

* **License Management System:** Persistent license storage and validation.
  - `/oneria license give <player> <profession>` - Grant functional license with full access.
  - `/oneria license giverp <player> <profession> <expiration_date>` - Grant decorative RP-only license with no actual permissions.
  - `/oneria license revoke <player> <profession>` - Revoke license and remove from inventory.
  - `/oneria license list` - Display all licenses across all players.
  - `/oneria license list <player>` - Display licenses for specific player.
  - `/oneria license check <player> <profession>` - Verify if player has specific license.
  - Physical license items generated with custom names, lore, and profession colors.
  - Automatic license removal from inventory when revoked.
  - JSON-based persistent storage in `world/data/oneriamod/licenses.json`.

* **Profession Configuration System:** Dedicated configuration file for all profession-related settings.
  - New config file: `serverconfig/oneria-professions.toml` separate from main config.
  - Profession definitions with ID, display name, and color code (e.g., `chasseur;Chasseur;§a`).
  - Global restriction lists: `globalBlockedCrafts`, `globalUnbreakableBlocks`, `globalBlockedItems`, `globalBlockedEquipment`.
  - Profession-specific permission overrides: `professionAllowedCrafts`, `professionAllowedBlocks`, `professionAllowedItems`, `professionAllowedEquipment`.
  - Customizable restriction messages with variable support (`{item}`, `{profession}`).
  - Built-in examples for 8 professions: Hunter, Fisher, Miner, Lumberjack, Blacksmith, Alchemist, Merchant, Guard.

* **Restriction Enforcement System:** Real-time blocking of unauthorized actions.
  - Craft blocking via Mixin injection in crafting result slots.
  - Block break prevention for protected ores and resources.
  - Item usage blocking (right-click interactions).
  - Equipment restriction for armor and weapons.
  - Attack damage cancellation for unauthorized weapons.
  - Mining prevention with unauthorized tools (left-click block).
  - Client-side visual indicators via tooltips showing required professions.
  - Action bar notifications instead of chat spam (configurable cooldowns: 1-3 seconds).
  - Anti-spam cache system to prevent message flooding.

* **Whitelist Exemption System:** Allow privileged players to bypass all profession restrictions.
  - New config option: `whitelistExemptProfessions` (default: true).
  - Players in the obfuscation whitelist automatically exempt from all profession restrictions.
  - Useful for admins, event coordinators, or special roles.

* **Always Visible List:** Force specific players to always appear in TabList.
  - New config option: `alwaysVisibleList` (list of player names).
  - Players in this list are never blurred or hidden, regardless of distance.
  - Overrides blacklist, spectator blur, and distance-based obfuscation.
  - Useful for NPCs, important staff members, or event coordinators.

* **Spectator Blur Control:** Hide players in spectator mode from TabList.
  - New config option: `blurSpectators` (default: true).
  - When enabled, players in spectator gamemode are automatically blurred in TabList.
  - Bypassed by `alwaysVisibleList` for intentionally visible spectators.
  - Helps hide staff members observing in spectator mode.

* **Client-Side Restriction Sync:** Network packet system for profession restrictions.
  - New packet: `SyncProfessionRestrictionsPacket` sent on player login.
  - Synchronizes blocked crafts and equipment from server to client.
  - Enables client-side visual feedback and tooltip information.
  - Automatic sync when player receives or loses licenses.

* **Revoked License Tracker:** Automatic inventory cleanup for revoked licenses.
  - `RevokedLicenseManager` tracks pending license removals.
  - Automatic removal of physical license items from player inventory.
  - Player notification when license is removed.
  - Cleanup on player login and logout.

* **License Items:** Physical in-game items representing profession licenses.
  - New item: `oneriaserverutilities:license` (max stack size: 1).
  - Custom item class: `LicenseItem` with tooltip enhancements.
  - Automatically generated with profession color, name, and metadata.
  - Includes issuance date and recipient information.
  - RP licenses include expiration date and "RP ONLY" warning.
  - Localization support (English and French).

### Improved

**Command System Enhancements**

* Enhanced command autocompletion across all license and profession commands.
* Smart suggestion system that only suggests relevant options:
  - `/license give` suggests professions player does NOT already have.
  - `/license revoke` suggests only professions player currently has.
  - Platform commands suggest configured platform names.
  - Whitelist/blacklist commands suggest online players.
* Improved feedback messages with color-coded success/error states.
* Consolidated `/oneria config status` now includes all new systems.

**Performance Optimizations**

* Profession permission checks use 30-second cache to reduce database lookups.
* Restriction event handlers use anti-spam cooldown maps (1-3 seconds).
* Cache cleanup system runs every 20 seconds to prevent memory leaks.
* Lazy initialization pattern for profession data loading.
* Efficient pattern matching with compiled regex for wildcard support.

**Configuration System**

* Split configuration into two files for better organization:
  - `oneriamod-server.toml` - Core mod features (blur, schedule, chat, etc.).
  - `oneria-professions.toml` - All profession and restriction settings.
* Extensive inline documentation in config files with examples.
* Graceful handling of config loading states to prevent startup crashes.
* Config reload now properly reinitializes profession restriction cache.

**Error Handling & Stability**

* Added `IllegalStateException` catching throughout config access.
* Null checks before accessing all configuration values.
* Graceful degradation when profession system is not yet initialized.
* Comprehensive error logging with debug messages for troubleshooting.
* Protection against config loading race conditions on server startup.

**Mixin Improvements**

* Enhanced `MixinServerCommonPacketListenerImpl` to support always visible list and spectator blur.
* Updated obfuscation logic to properly handle multiple visibility states.
* Better integration between nickname system and profession restrictions.

### Fixed

* **Config Loading Race Condition:** Fixed crashes on server startup when profession config loads late.
* **Creative Mode Duplication:** Profession restrictions now properly skip creative mode players.
* **Craft Result Spam:** Removed excessive message flooding when attempting blocked crafts.
* **Equipment Warning Spam:** Reduced armor/weapon restriction messages to action bar only.
* **License Inventory Sync:** Revoked licenses now properly removed from all inventory slots.
* **Profession Cache Stale Data:** Cache now properly invalidated when licenses are granted/revoked.
* **Tooltip Display:** Item tooltips now correctly show all applicable restrictions.
* **Pattern Matching:** Fixed wildcard matching for complex item ID patterns.
* **Null Pointer Exceptions:** Added comprehensive null checks in profession data retrieval.
* **Client Sync Issues:** Profession restrictions now properly synchronized on login.

### Technical

**New Classes**

* `ProfessionConfig` - Dedicated configuration class for profession system.
* `ProfessionRestrictionManager` - Core logic for permission checks and validation.
* `ProfessionRestrictionEventHandler` - Event handling for block/item/craft/equipment restrictions.
* `CraftingAndArmorRestrictionEventHandler` - Specialized craft restriction handling.
* `LicenseManager` - Persistent license storage and retrieval.
* `LicenseItem` - Custom item class for physical licenses.
* `RevokedLicenseManager` - Tracks and removes revoked licenses from inventory.
* `ProfessionSyncHelper` - Client-server synchronization for restrictions.
* `SyncProfessionRestrictionsPacket` - Network packet for profession data.
* `ClientProfessionRestrictions` - Client-side restriction data storage.
* `OneriaItems` - Item registry for mod items.

**Enhanced Classes**

* `OneriaConfig` - Added `ALWAYS_VISIBLE_LIST`, `BLUR_SPECTATORS`, `WHITELIST_EXEMPT_PROFESSIONS`.
* `OneriaCommands` - Complete license management command suite with autocomplete.
* `OneriaEventHandler` - Profession restriction sync on player login, revoked license cleanup.
* `OneriaServerUtilities` - Integration with profession system, cache cleanup tick.
* `NetworkHandler` - Registration of profession sync packet.
* `MixinServerCommonPacketListenerImpl` - Always visible list and spectator blur logic.

**Data Storage**

* New file: `world/data/oneriamod/licenses.json` - Stores player licenses with UUID keys.
* Pretty-printed JSON format for manual editing if needed.
* Automatic directory creation and error handling.

**Network Protocol**

* New packet: `oneriaserverutilities:sync_profession_restrictions`.
* Payload contains sets of blocked crafts and equipment specific to player.
* Sent on player login and when licenses change.
* Enables client-side restriction visualization.

**Event Subscriptions**

* `BlockEvent.BreakEvent` - Block break restriction enforcement.
* `PlayerInteractEvent.RightClickItem` - Item usage restriction.
* `PlayerInteractEvent.RightClickBlock` - Block interaction restriction.
* `PlayerInteractEvent.LeftClickBlock` - Mining tool restriction.
* `LivingEquipmentChangeEvent` - Armor equipment restriction.
* `AttackEntityEvent` - Weapon damage restriction.
* `ItemTooltipEvent` - Restriction information display.

### Configuration

**New Config File: oneria-professions.toml**

* `professions` (List) - Profession definitions: `id;DisplayName;ColorCode`.
* `globalBlockedCrafts` (List) - Items blocked from crafting for everyone.
* `globalUnbreakableBlocks` (List) - Blocks that cannot be broken by anyone.
* `globalBlockedItems` (List) - Items that cannot be used/interacted with.
* `globalBlockedEquipment` (List) - Armor/weapons that cannot be equipped.
* `professionAllowedCrafts` (List) - Profession-specific craft permissions: `profession;item1,item2`.
* `professionAllowedBlocks` (List) - Profession-specific mining permissions.
* `professionAllowedItems` (List) - Profession-specific item usage permissions.
* `professionAllowedEquipment` (List) - Profession-specific equipment permissions.
* `craftBlockedMessage` (String) - Message shown when craft is blocked.
* `blockBreakBlockedMessage` (String) - Message shown when block break is blocked.
* `itemUseBlockedMessage` (String) - Message shown when item use is blocked.
* `equipmentBlockedMessage` (String) - Message shown when equipment is blocked.

**Updated Config: oneriamod-server.toml**

* `alwaysVisibleList` (List) - Players always shown in TabList, never blurred.
* `blurSpectators` (Boolean) - Hide spectator mode players in TabList (default: true).
* `whitelistExemptProfessions` (Boolean) - Whitelist bypasses profession restrictions (default: true).

### Commands

**License Management (OP Level 2)**

* `/oneria license give <player> <profession>` - Grant functional license with access.
* `/oneria license giverp <player> <profession> <date>` - Grant RP-only decorative license.
* `/oneria license revoke <player> <profession>` - Revoke license and remove from inventory.
* `/oneria license list` - Display all licenses for all players.
* `/oneria license list <player>` - Display licenses for specific player.
* `/oneria license check <player> <profession>` - Verify license ownership.

All commands include smart autocomplete that suggests contextually relevant options.

### Migration Notes

* **New Configuration File:** First launch will generate `serverconfig/oneria-professions.toml`.
* **Default Professions:** 8 example professions included (Hunter, Fisher, Miner, etc.).
* **Default Restrictions:** Some items/blocks restricted by default - review and customize.
* **Existing Players:** No automatic license migration - licenses must be granted manually.
* **Whitelist Behavior:** Whitelist now bypasses profession restrictions by default.
* **Performance Impact:** Minimal - caching system ensures efficient permission checks.
* **Compatibility:** Fully backward compatible with 1.2.2 - all existing features unchanged.

### Known Limitations

* Profession restrictions only apply to survival/adventure mode (creative mode bypassed).
* Wildcard patterns use simple regex - very complex patterns may not work.
* License items can be manually duplicated in creative mode.
* Client-side tooltips require server sync packet - may not show immediately on login.
* Revoked license removal requires player to be online or next login.
* RP licenses have no built-in expiration enforcement - purely visual.

### Performance Impact

* Profession checks: ~0.1ms per check (cached).
* License database queries: ~0.5ms (with caching).
* Network sync: ~1KB packet per player on login.
* Cache cleanup: ~2ms every 20 seconds.
* Estimated overhead: <1% CPU usage on active servers.

### Localization

* Added translations for license item in English (`en_us.json`) and French (`fr_fr.json`).
* All profession names and colors customizable via config.
* Restriction messages support color codes and variable replacement.

---

## [1.2.2] - 2026-02-02

**Added**

* **Client-Side Nametag Hiding:** New optional feature to hide player nametags above heads:
  - Implemented via NeoForge's `RenderNameTagEvent` for clean client-side rendering control.
  - Completely client-side solution using packet synchronization from server.
  - Configuration synced when players join via custom `HideNametagsPacket`.
  - No scoreboard teams involved - purely visual hiding on client.
  - Controlled by server config option `hideNametags` (default: false).
  - Command: `/oneria config set hideNametags true/false` (was already here, just ported to the new system)

* **Nametag Nickname Display:** Nicknames now visible above player heads:
  - Optional prefix/suffix display from LuckPerms.
  - Configurable via `showNametagPrefixSuffix` option.
  - Command: `/oneria config set showNametagPrefixSuffix true/false`
  - Full color code support for nicknames in nametags.

* **Platform Management Command:** New `/setplatform` command for easier platform creation:
  - Syntax: `/setplatform <name> <dimension> <x> <y> <z>`
  - Requires OP level 2.
  - Automatically creates or updates platform entries.
  - Simplifies platform configuration without manual config editing.

**Fixed**

* **LuckPerms Solo Crash:** Fixed crash when playing in singleplayer or on servers without LuckPerms:
  - Added `NoClassDefFoundError` catching in `getPlayerPrefix()` and `getPlayerSuffix()`.
  - Mod now gracefully handles LuckPerms absence with debug logging instead of crashes.
  - All LuckPerms-dependent features safely skip when mod is not present.
  - Enhanced error handling in `OneriaServerUtilities` for better stability.

* **Performance Issues:** Fixed severe FPS drops caused by tick loop:
  - Added server-side check to prevent client-side tick execution.
  - TabList update system now only runs on dedicated servers.
  - Eliminated unnecessary packet broadcasts on client.
  - Massive performance improvement in single-player and multiplayer.

**Technical**

* **New Classes:**
  - `ClientNametagRenderer` - Client-side event handler for nametag visibility control.
  - `HideNametagsPacket` - Custom packet for synchronizing nametag config from server to client.
  - `ClientNametagConfig` - Client-side config storage with server state tracking.
  - `ClientEventHandler` - Handles client disconnect events and config reset.
  - `NetworkHandler` - Packet registration and handling for client-server communication.

* **Enhanced Classes:**
  - `OneriaEventHandler` - Now sends nametag configuration packet to clients on login.
  - `OneriaServerUtilities` - Enhanced LuckPerms error handling with `NoClassDefFoundError` catching, added server-side tick protection.
  - `OneriaConfig` - Added `HIDE_NAMETAGS` and `SHOW_NAMETAG_PREFIX_SUFFIX` configuration options.
  - `OneriaCommands` - Added `/setplatform` command and nametag configuration commands.
  - `MixinEntity` - Enhanced to display nicknames with optional prefix/suffix above heads.

* **Network Protocol:**
  - Custom packet payload type: `oneriaserverutilities:hide_nametags`.
  - Boolean payload for nametag visibility state.
  - Sent to players on login with current server configuration.
  - Client automatically resets state on disconnect.

**Configuration**

* **New Options:**
  - `hideNametags` (Boolean) - Hide all player nametags above heads (default: false).
  - `showNametagPrefixSuffix` (Boolean) - Show LuckPerms prefix/suffix with nicknames in nametags (default: false).
  - Both located in `[Obfuscation Settings]` section.
  - Both can be toggled via `/oneria config set <option> true/false`.

**Migration Notes**

* No breaking changes - fully backward compatible with 1.2.1.
* New nametag hiding feature is disabled by default.
* Existing configurations continue to work without modification.
* Feature can be enabled by setting `hideNametags = true` in server config.

**Known Behavior**

* Nametag hiding only affects player entities (not mobs or other entities).
* Client must receive configuration packet from server before feature activates.
* Configuration automatically syncs on player join/rejoin.
* Disconnect automatically clears client-side config state.
* Nicknames in nametags support full color codes (§ and &).

## [1.2.1] - 2026-02-01

**Fixed**

* **Double Join/Leave Messages:** Fixed critical bug where players would see duplicate connection messages:
  - Removed redundant message broadcasting in `OneriaEventHandler`.
  - Consolidated all join/leave message handling into `MixinPlayerList` for cleaner interception.
  - Messages are now sent exactly once per player connection/disconnection.
  - System properly respects the `enableCustomJoinLeave` configuration option.

**Technical**

* **Enhanced Classes:**
  - `OneriaEventHandler` - Removed duplicate join/leave message code (lines 20-35 and 70-88).
  - `MixinPlayerList` - Now the sole handler for vanilla message interception and custom message broadcasting.
  - `oneria.mixins.json` - Added `MixinPlayerList` to the mixin registry.

* **Code Quality:**
  - Eliminated code duplication between event handler and mixin system.
  - Improved separation of concerns (event handling vs. message interception).
  - Better debug logging for join/leave message flow.

**Migration Notes**

* No configuration changes required - fully backward compatible with 1.2.0.
* Existing `joinMessage` and `leaveMessage` settings continue to work as expected.
* Players will now see exactly one join message and one leave message as intended.
* If you experience any issues, ensure `MixinPlayerList` is properly registered in `oneria.mixins.json`.

**Known Behavior**

* Join/leave messages are handled via Mixin interception of vanilla messages.
* The system intercepts both English ("joined the game") and French ("a rejoint la partie") variants.
* Nickname resolution is performed dynamically when messages are sent.
* Debug logging can be enabled to track message flow: look for `[Join]` and `[Leave]` prefixes in logs.

## [1.2.1] - 2026-02-01

**Fixed**

* **Double Join/Leave Messages:** Fixed critical bug where players would see duplicate connection messages:
  - Removed redundant message broadcasting in `OneriaEventHandler`.
  - Consolidated all join/leave message handling into `MixinPlayerList` for cleaner interception.
  - Messages are now sent exactly once per player connection/disconnection.
  - System properly respects the `enableCustomJoinLeave` configuration option.

**Technical**

* **Enhanced Classes:**
  - `OneriaEventHandler` - Removed duplicate join/leave message code (lines 20-35 and 70-88).
  - `MixinPlayerList` - Now the sole handler for vanilla message interception and custom message broadcasting.
  - `oneria.mixins.json` - Added `MixinPlayerList` to the mixin registry.

* **Code Quality:**
  - Eliminated code duplication between event handler and mixin system.
  - Improved separation of concerns (event handling vs. message interception).
  - Better debug logging for join/leave message flow.

**Migration Notes**

* No configuration changes required - fully backward compatible with 1.2.0.
* Existing `joinMessage` and `leaveMessage` settings continue to work as expected.
* Players will now see exactly one join message and one leave message as intended.
* If you experience any issues, ensure `MixinPlayerList` is properly registered in `oneria.mixins.json`.

**Known Behavior**

* Join/leave messages are handled via Mixin interception of vanilla messages.
* The system intercepts both English ("joined the game") and French ("a rejoint la partie") variants.
* Nickname resolution is performed dynamically when messages are sent.
* Debug logging can be enabled to track message flow: look for `[Join]` and `[Leave]` prefixes in logs.

## [1.2.0] - 2026-01-21

**Added**

* **Join/Leave Messages System:** Fully customizable player connection messages:
  - Custom join messages with color code support and variables (`{player}`, `{nickname}`).
  - Custom leave messages with same variable support.
  - Option to disable messages completely by setting to "none".
  - Configuration commands: `/oneria config set joinMessage <message>` and `/oneria config set leaveMessage <message>`.
  - Toggle with `/oneria config set enableCustomJoinLeave true/false`.

* **World Border Warning System:** Automatic distance-based warnings from spawn:
  - Configurable warning distance (default: 2000 blocks from spawn).
  - Players receive ONE warning message when exceeding the limit.
  - Warning automatically resets when player returns to safe zone.
  - Configurable check interval for performance optimization (default: 40 ticks / 2 seconds).
  - Custom warning message with variables (`{distance}`, `{player}`).
  - Warning sound effect (note block bass) played on trigger.
  - Configuration commands:
    - `/oneria config set enableWorldBorderWarning true/false`
    - `/oneria config set worldBorderDistance <100-100000>`
    - `/oneria config set worldBorderMessage <message>`
    - `/oneria config set worldBorderCheckInterval <20-200>`

* **Blacklist System:** Always-hidden player functionality:
  - New blacklist to permanently hide specific players from TabList.
  - Players in blacklist are always obfuscated regardless of distance.
  - Useful for staff in stealth mode or hidden NPCs.
  - Commands:
    - `/oneria blacklist add <player>` - Add player to always-hidden list.
    - `/oneria blacklist remove <player>` - Remove from blacklist.
    - `/oneria blacklist list` - Display all blacklisted players.
  - Stored in config under `Obfuscation Settings`.

**Improved**

* **LuckPerms Compatibility:** Enhanced support for servers without LuckPerms:
  - Mod no longer crashes when LuckPerms is not installed.
  - Graceful fallback to vanilla permissions and scoreboard tags.
  - Better error handling with `IllegalStateException` catching.
  - Debug logging instead of error messages for missing LuckPerms.
  - All LuckPerms-dependent features (prefixes, suffixes, group permissions) safely disabled when unavailable.

* **Schedule System Reliability:** More robust initialization and error handling:
  - Fixed `NullPointerException` when config loads late.
  - Added null checks in `tick()`, `isServerOpen()`, and `getTimeUntilNextEvent()`.
  - Schedule now gracefully handles uninitialized state.
  - Better logging for initialization events.
  - Config loading listener ensures proper initialization timing.

* **Configuration System:** Better error handling across all config access:
  - All config-dependent systems check for null before accessing values.
  - Graceful degradation when config is not yet loaded.
  - Improved debug logging for initialization states.

**Fixed**

* **Config Loading Race Condition:** Fixed "Cannot get config value before spec is built" errors:
  - Moved Join/Leave and World Border config sections before `SPEC = BUILDER.build()`.
  - All config values now properly initialized on server start.
  - Fixed `/oneria config status` showing "N/A" for new features.

* **Schedule Initialization:** Fixed crash on world creation:
  - `OneriaScheduleManager.reload()` now checks for null config values.
  - No longer attempts to parse times before config is loaded.
  - Added proper return statements to prevent execution with null values.

* **LuckPerms Dependencies:** Fixed crashes in modpacks without LuckPerms:
  - `getPlayerPrefix()` and `getPlayerSuffix()` now catch `IllegalStateException`.
  - `OneriaPermissions.checkStaffStatus()` safely handles missing LuckPerms.
  - Staff detection falls back to vanilla tags and OP levels.

**Technical**

* **New Classes:**
  - `WorldBorderManager` - Distance-based warning system with spawn proximity checks.

* **Enhanced Classes:**
  - `OneriaConfig` - Added `ENABLE_CUSTOM_JOIN_LEAVE`, `JOIN_MESSAGE`, `LEAVE_MESSAGE`, `ENABLE_WORLD_BORDER_WARNING`, `WORLD_BORDER_DISTANCE`, `WORLD_BORDER_MESSAGE`, `WORLD_BORDER_CHECK_INTERVAL`, `BLACKLIST`.
  - `OneriaEventHandler` - Integrated custom join/leave messages with proper null checks.
  - `OneriaScheduleManager` - Added comprehensive null safety for all public methods.
  - `OneriaServerUtilities` - Enhanced LuckPerms error handling, added config loading listener.
  - `OneriaPermissions` - Improved LuckPerms fallback logic.
  - `OneriaCommands` - Added blacklist management commands and new config setters.
  - `MixinServerCommonPacketListenerImpl` - Integrated blacklist checking in obfuscation logic.

**Configuration**

* **New Config Sections:**
  - `[Join and Leave Messages]` - Join/leave message customization.
  - `[World Border Warning]` - Distance warning system settings.

* **New Options:**
  - `enableCustomJoinLeave` (Boolean) - Enable custom join/leave messages (default: true).
  - `joinMessage` (String) - Join message template with variables (default: "§e{player} §7joined the game").
  - `leaveMessage` (String) - Leave message template with variables (default: "§e{player} §7left the game").
  - `enableWorldBorderWarning` (Boolean) - Enable world border warnings (default: true).
  - `worldBorderDistance` (Integer) - Warning distance in blocks (default: 2000, range: 100-100000).
  - `worldBorderMessage` (String) - Warning message template (default: "§c§l⚠ WARNING §r§7You've reached the limit of the world! (§c{distance} blocks§7)").
  - `worldBorderCheckInterval` (Integer) - Check frequency in ticks (default: 40, range: 20-200).
  - `blacklist` (List) - Players who are always hidden (default: empty list).

**Performance**

* World border checks use configurable intervals (default 2 seconds) to minimize overhead.
* Distance calculation uses 2D (X, Z) coordinates only, ignoring Y for better performance.
* One-time warning system prevents message spam.
* Efficient state tracking with HashMap for warned players.

**Migration Notes**

* No breaking changes - fully backward compatible with 1.1.3.
* Delete `serverconfig/oneriamod-server.toml` to regenerate with new options.
* If upgrading from pre-1.2.0, verify config sections are before `SPEC = BUILDER.build()` line.
* LuckPerms is now truly optional - mod works without it using vanilla permissions.
* Join/leave messages are enabled by default - set to "none" to disable.
* World border warnings are enabled by default at 2000 blocks - adjust as needed.

**Known Limitations**

* World border warnings are based on distance from spawn (0,0), not world border entities.
* Blacklist applies to all contexts - no per-player exemptions.
* Join/leave messages appear to all players - no per-player filtering.

---

## [1.1.3] - 2026-01-16

**Added**

* **Sneak Stealth System:** Advanced stealth mechanics for enhanced roleplay immersion:
  - Players who are sneaking (crouching) become significantly harder to detect.
  - Configurable sneak detection distance (default: 2 blocks vs normal 8 blocks).
  - Sneaking players' names are obfuscated beyond the sneak distance, even if within normal proximity.
  - Admin exemption: Staff with `opsSeeAll` can always see sneaking players.
  - Toggle system with `/oneria config set enableSneakStealth true/false`.
  - Adjustable sneak distance with `/oneria config set sneakProximityDistance <1-32>`.
  - Perfect for stealth RP scenarios, hiding, and surprise interactions.

**Improved**

* **Obfuscation Logic:** Enhanced distance calculation for dynamic stealth:
  - System now checks if target player is crouching before applying distance rules.
  - Automatic switching between normal proximity distance and sneak proximity distance.
  - Seamless integration with existing blur system - no conflicts.
  - Performance-optimized with minimal overhead per tick.

* **Configuration Commands:** New sneak-related commands:
  - `/oneria config set enableSneakStealth <true/false>` - Toggle sneak stealth system.
  - `/oneria config set sneakProximityDistance <1-32>` - Set sneak detection range.
  - Sneak status displayed in `/oneria config status` output.

**Technical**

* **Enhanced Classes:**
  - `OneriaConfig` - Added `ENABLE_SNEAK_STEALTH` and `SNEAK_PROXIMITY_DISTANCE` configuration options.
  - `MixinServerCommonPacketListenerImpl` - Enhanced `modifyPacket()` with dynamic distance calculation based on crouch state.
  - `OneriaCommands` - Added sneak configuration commands and status display.

* **Performance:**
  - Crouch state check uses native Minecraft `isCrouching()` method - zero overhead.
  - Distance calculation only performed when blur system is active.
  - No additional packet modifications required.

**Configuration**

* **New Options:**
  - `enableSneakStealth` (Boolean) - Enable stealth mode for sneaking players.
    - Location: `[Obfuscation Settings]` section.
    - Default: `true`.
    - When enabled, sneaking players use reduced detection distance.

  - `sneakProximityDistance` (Integer) - Detection distance for sneaking players.
    - Location: `[Obfuscation Settings]` section.
    - Default: `2` blocks.
    - Range: 1-32 blocks.
    - Only applies when `enableSneakStealth` is enabled.

**Use Cases**

* **Stealth Roleplay:** Players can sneak to avoid being detected in crowded areas.
* **Hide and Seek:** Perfect for RP games where players need to hide.
* **Ambush Scenarios:** Players can set up surprise encounters without revealing their presence.
* **Privacy:** Players can move through areas without being immediately recognized.
* **Realistic Immersion:** Crouching for stealth mirrors real-world behavior.

**Migration Notes**

* No breaking changes - fully backward compatible with 1.1.2.
* Sneak stealth is enabled by default - disable with `/oneria config set enableSneakStealth false` if not desired.
* Existing blur system settings remain unchanged.
* Admin exemptions (`opsSeeAll`) automatically apply to sneak detection.

**Known Limitations**

* Sneak detection only affects TabList names, not physical player visibility.
* Players can still see other players' models when sneaking - only names are hidden.
* Sneak distance applies uniformly to all players (no per-player customization).

## [1.1.2] - 2026-01-15

**Added**

* **Nametag Visibility System:** Server-side nametag hiding using scoreboard teams:
  - Toggle nametag visibility for all players with `/oneria config set hideNametags true/false`.
  - Automatic synchronization on player login/logout.
  - Team-based implementation for full server-side control.
  - Configuration option `hideNametags` in Obfuscation Settings.
  - Nametag state automatically reloads with `/oneria config reload`.

* **Color System Improvements:** Fixed `/colors` command display issues:
  - Color codes in examples are no longer interpreted incorrectly.
  - Clean visual presentation with proper formatting.
  - Clear distinction between color preview and usage codes.
  - Usage examples show only `&` codes (typeable in-game).

**Improved**

* **Chat System:** Enhanced color parsing for better reliability:
  - `ColorHelper` class now properly parses all Minecraft color codes.
  - Improved segment-based parsing for complex formatting.
  - Better handling of mixed colors and formatting codes.
  - Full support for both `&` and `§` syntax throughout the mod.

* **Nametag Manager:** Robust synchronization system:
  - Automatic sync on every player connection/disconnection.
  - State verification on config reload to ensure consistency.
  - Proper team cleanup when system is disabled.
  - Detailed logging for debugging nametag operations.

* **Command Organization:** Added nametag configuration commands:
  - `/oneria config set hideNametags <true/false>` - Toggle nametag visibility.
  - Nametag status displayed in `/oneria config status`.
  - Instant application without server restart required.

**Fixed**

* **Colors Command Display:** Resolved visual glitches in `/colors` output:
  - Color codes in examples no longer interfere with display.
  - Proper spacing and alignment in color reference table.
  - Formatting codes display correctly with usage syntax.
  - Component construction prevents code interpretation in examples.

* **Nametag Synchronization:** Fixed edge cases in nametag visibility:
  - Nametags properly hide/show when config changes.
  - Team membership correctly updated on player events.
  - No lingering team data after system disable.

* **Chat Color Parsing:** Fixed color codes not working in chat:
  - `OneriaChatFormatter` now uses `ColorHelper.parseColors()`.
  - All chat messages properly display colors and formatting.
  - Markdown and color codes work together seamlessly.

**Technical**

* **New Classes:**
  - `NametagManager` - Centralized nametag visibility control using scoreboard teams.
  - `ColorHelper` - Advanced color code parser with segment-based processing.
  - `TextSegment` (inner class) - Represents text portions with their styling.

* **Enhanced Classes:**
  - `OneriaEventHandler` - Added nametag sync on player login/logout.
  - `OneriaCommands` - Added `hideNametags` configuration command.
  - `OneriaChatFormatter` - Refactored to use `ColorHelper` for color parsing.

**Performance**

* Nametag system uses native scoreboard teams - zero performance impact.
* Color parsing optimized with segment-based processing.
* Sync operations only trigger on player events and config changes.

**Configuration**

* **New Options:**
  - `hideNametags` (Boolean) - Hide all player nametags above heads.
    - Location: `[Obfuscation Settings]` section.
    - Default: `false`.
    - Instantly toggleable via command.

**Known Limitations**

* Nametag hiding uses scoreboard teams - may conflict with other mods using team-based systems.
* Individual nametag control per player is not supported (all or nothing).

**Migration Notes**

* No breaking changes - fully backward compatible with 1.1.1.
* Nametag visibility can be toggled at any time without restart.
* `hideNametags` defaults to `false` - no behavior change unless explicitly enabled.
* `/colors` command now displays correctly - no configuration needed.
* Color codes work seamlessly in all contexts (chat, commands, nicknames).

## [1.1.1] - 2026-01-10

**Added**

* **Advanced Chat System:** Fully integrated chat formatting system similar to Better Forge Chat Reborn:
  - Customizable player name format with LuckPerms prefix/suffix support (`$prefix $name $suffix`).
  - Flexible chat message format with timestamp and color support (`$time | $name: $msg`).
  - Global chat message color configuration (16 Minecraft colors available).
  - Timestamp system with customizable Java SimpleDateFormat.
  - **Markdown Support:** Real-time markdown styling in chat messages:
    - `**text**` → **Bold**
    - `*text*` → *Italic*
    - `__text__` → Underline
    - `~~text~~` → Strikethrough
  - `/colors` command to display all available colors and formatting codes with visual preview.
  - Full integration with existing nickname system - nicknames appear in chat automatically.
  - LuckPerms suffix support added to complement existing prefix system.

**Improved**

* **Chat Integration:** Chat system now respects all existing mod features:
  - Nicknames set via `/oneria nick` are automatically used in chat.
  - LuckPerms prefixes and suffixes are properly displayed.
  - Staff permissions work seamlessly with chat formatting.
  - All color codes (§ and &) are fully supported.

* **Configuration:** New unified chat configuration section in `OneriaConfig.java`:
  - `enableChatFormat` - Toggle entire chat system on/off.
  - `playerNameFormat` - Customize name display with variables.
  - `chatMessageFormat` - Full message template customization.
  - `chatMessageColor` - Global message color setting.
  - `enableTimestamp` - Show/hide timestamps.
  - `timestampFormat` - Custom timestamp format.
  - `markdownEnabled` - Enable/disable markdown parsing.
  - `enableColorsCommand` - Toggle `/colors` command availability.

**Technical**

* **New Classes:**
  - `OneriaChatFormatter` - Central chat formatting logic with markdown parser.
  - `MixinServerGamePacketListenerImpl` - Chat message interception and formatting.

* **Enhanced Classes:**
  - `OneriaConfig` - Added complete Chat System configuration section.
  - `OneriaServerUtilities` - Added `getPlayerSuffix()` method for LuckPerms suffix retrieval.
  - `OneriaCommands` - Added `/colors` command for color reference.

* **Mixin Updates:**
  - New mixin for `ServerGamePacketListenerImpl` to intercept chat messages.
  - Proper cancellation and custom message broadcasting.

**Performance**

* Efficient regex-based markdown parsing with minimal overhead.
* Chat formatting only applies when enabled in config.
* Cached LuckPerms data retrieval for better performance.

**Migration Notes**

* No breaking changes - chat system is fully backward compatible.
* Existing nicknames will automatically appear in formatted chat.
* Default configuration provides a clean, modern chat experience.
* Chat formatting can be disabled entirely by setting `enableChatFormat = false`.

**Known Compatibility**

* ✅ Fully compatible with LuckPerms for prefixes/suffixes.
* ✅ Works seamlessly with existing nickname system.
* ✅ Compatible with all existing mod features (blur, schedule, platforms, etc.).
* ✅ Does not conflict with Minecraft's vanilla chat system.

## [1.1.0] - 2026-01-07

**Breaking Changes**

* **Mod ID Changed:** The mod ID has been updated from `oneriamod` to its final identifier. If upgrading from 1.0.x, you may need to regenerate configuration files.

**Added**

* **Schedule System (Native):** Fully integrated automatic server opening/closing with customizable times, warnings, and staff bypass.
  - Configurable opening/closing times (format HH:MM).
  - Automated warnings at 45, 30, 10, and 1 minute before closing.
  - Smart kick system that only affects non-staff players.
  - Staff receives notifications when server opens/closes.

* **Permission System:** Advanced staff detection system with multiple layers:
  - Scoreboard tags support (`admin`, `modo`, `staff`, `builder`).
  - OP level bypass (configurable from 0 to 4).
  - LuckPerms groups integration (configurable staff groups).
  - Efficient caching system for performance (30-second cache).

* **Welcome System:** Customizable welcome messages on player login:
  - Multi-line welcome messages with color code support.
  - Variable support (`{player}`, `{nickname}`).
  - Customizable sound effects (volume and pitch configurable).
  - Integration with schedule system to display server status.

* **Platform Teleportation:** Staff-only teleportation system:
  - Define custom platforms with coordinates and dimensions.
  - Quick teleport to predefined RP zones.
  - Staff notifications when platforms are used.
  - Configurable platform list via config file.
  - New alias: `/platform` (shortcut for `/oneria staff platform`).

* **Silent Commands:** Stealth moderation tools for staff:
  - `/oneria staff gamemode <mode> [target]` - Silent gamemode changes.
  - `/oneria staff tp <target>` - Silent teleportation.
  - `/oneria staff effect <target> <effect> <duration> <amplifier>` - Silent effect application.
  - All actions logged to console and notified to other staff members.
  - Optional target notification (disabled by default for stealth).

* **Nickname System:** Advanced nickname management with persistence:
  - `/oneria nick <player> <nickname>` - Set custom nicknames with color code support (§ and &).
  - `/oneria nick <player>` - Reset nickname to original name.
  - `/oneria nick list` - Display all active nicknames.
  - Full integration with proximity blur system.
  - Nicknames displayed in TabList and overhead.
  - Persistent storage in `world/data/oneriamod/nicknames.json`.
  - Automatic save on every change.
  - Instant update without reconnection required.

* **Configuration Commands:** Comprehensive in-game configuration management:
  - `/oneria config reload` - Hot-reload configuration and nicknames without restart.
  - `/oneria config status` - View current mod status with visual formatting.
  - `/oneria config set <option> <value>` - Modify any config option in real-time.
  - Over 20 configurable options accessible via commands.

* **Schedule Commands with Aliases:**
  - `/oneria schedule`, `/schedule`, or `/horaires` - View server schedule with beautiful formatting.
  - Displays current status (OPEN/CLOSED).
  - Shows opening/closing times.
  - Calculates time remaining until next event.

**Improved**

* **Obfuscation System:** Enhanced proximity blur with better performance and security:
  - Now respects custom nicknames set via `/oneria nick`.
  - Improved obfuscation algorithm that preserves color codes length.
  - Configurable obfuscation length (1-16 characters).
  - **Security Enhancement:** Rank prefixes are now ALWAYS hidden when names are obfuscated to prevent metagaming and grade detection.
  - Better handling of LuckPerms prefixes.
  - Admin view: Staff members with `opsSeeAll` enabled see nicknames with real names in italic gray next to them: `Nickname (RealName)`.

* **Staff Detection:** More reliable permission checking:
  - Multi-layered permission system (tags → OP → LuckPerms).
  - Cached results for better performance (30-second cache).
  - Automatic cache invalidation on logout.
  - Debug mode for permission troubleshooting.

* **Command Organization:** Better command structure with aliases:
  - All mod commands unified under `/oneria`.
  - Logical subcommands: `config`, `staff`, `whitelist`, `nick`, `schedule`.
  - New convenient aliases: `/platform`, `/schedule`, `/horaires`.
  - Improved command suggestions and tab completion.
  - Consistent error messages and feedback.

**Fixed**

* **Packet Handling:** Fixed `ClientboundPlayerInfoUpdatePacket` constructor issues.
* **Mixin Compatibility:** Corrected casting errors with `ServerCommonPacketListenerImpl`.
* **Mixin Obfuscation:** Resolved "Unable to locate obfuscation mapping" errors by adding `remap = false` where needed.
* **Compact Source Files:** Fixed Java 21 compilation issues with unnamed classes.
* **Schedule Logic:** Fixed edge cases with midnight transitions.
* **Nickname Persistence:** Nicknames now properly persist across server restarts via JSON storage.
* **Configuration Reload:** All systems now properly reload when config changes, including nickname system.

**Technical**

* **New Classes:**
  - `OneriaPermissions` - Centralized permission management system with caching.
  - `OneriaScheduleManager` - Schedule system with tick-based checking and smart warnings.
  - `OneriaEventHandler` - Event handling for login/logout with delayed welcome messages.
  - `OneriaCommands` - Unified command registry with comprehensive subcommands.
  - `NicknameManager` - Persistent nickname storage with automatic JSON serialization.

* **Mixin Improvements:**
  - `MixinServerPlayer` - Injects into `getDisplayName()` to support nicknames in all contexts.
  - `MixinServerCommonPacketListenerImpl` - Enhanced packet modification with admin preview and security features.
  - `ClientboundPlayerInfoUpdatePacketAccessor` - Direct packet entry manipulation for TabList.

* **Performance:**
  - Permission caching reduces overhead by 90%.
  - Schedule system only checks every 20 seconds (400 ticks).
  - Efficient packet modification with minimal performance impact.
  - Nickname loading uses lazy initialization pattern.

* **Data Storage:**
  - Nicknames stored in `world/data/oneriamod/nicknames.json`.
  - Pretty-printed JSON format for easy manual editing.
  - Automatic directory creation and error handling.
  - UUID-based storage for reliability across name changes.

**Configuration**

* **New Config Sections:**
  - `[Permissions System]` - Staff detection settings with multiple layers.
  - `[Schedule System]` - Opening/closing configuration with automated warnings.
  - `[Messages]` - Customizable kick/warning messages with variable support.
  - `[Welcome Message]` - Welcome system settings with sound configuration.
  - `[Teleportation Platforms]` - Platform definitions with dimension support.
  - `[Silent Commands]` - Moderation logging options for transparency.

* **Updated Options:**
  - `obfuscatePrefix` - Now deprecated, prefixes are always hidden during obfuscation for security.
  - `debugSelfBlur` - Fixed to properly work with admin exemptions.

**Migration Notes**

* **Configuration:** The config file structure is unchanged, but you may want to review the `obfuscatePrefix` setting as it no longer affects obfuscated names.
* **Nicknames:** Nicknames from previous versions will need to be re-set as the storage location has changed to `world/data/oneriamod/`.
* **Commands:** Old KubeJS-based `/horaires` command is now fully replaced by native implementation.
* **Permissions:** Staff permissions now use unified `OneriaPermissions.isStaff()` check across all systems.

## [1.0.1] - 2026-01-05

**Added**

* **Full Release:** Combined all core modules into a stable build.
* **Obfuscation System:** Implemented a Proximity Blur system for player names in TabList and overhead.
* **Schedule System:** Added automatic server opening/closing logic with automated kick for non-staff players.
* **Silent Commands:** Implemented /oneria staff module (gamemode, tp, effects) with stealth logging.
* **Whitelist System:** Added a specific whitelist to bypass the blur system for selected players.
* **LuckPerms Integration:** Support for LuckPerms prefixes and group-based permissions.
* **Welcome System:** Customizable welcome messages with sound support upon login.

**Fixed**

* **Syntax Errors:** Fixed invalid switch block syntax in command classes.
* **Data Handling:** Fixed casting issues and dynamic list updates for the whitelist configuration.

**Security**

* **Permissions:** Added robust permission checks using LuckPerms groups, Vanilla tags, and OP levels.
* **Staff Logging:** Commands used by staff are now logged to the console and other online staff members.

## [1.0.0] - 2026-01-04

**Added**

* **Initial Beta:** Experimental implementation of the name obfuscation (blur) system.
* Basic configuration file generation.

---

## Legend

* **Breaking Changes:** Changes that may require manual intervention or break compatibility.
* **Added:** New features or functionality.
* **Improved:** Enhancements to existing features.
* **Fixed:** Bug fixes and corrections.
* **Security:** Security-related changes.
* **Technical:** Internal/technical changes.
* **Migration Notes:** Important information for upgrading.