# Changelog - Rp Essentials
All notable changes to this project will be documented in this file.

## [4.1.4]

**Bug blitz & RP features — proximity chat, mute system, staff notes, dice rolls, and more.**

---

### Fixed

* **Wrong `SPEC.save()` calls in command setters:** Every config setter in `RpEssentialsCommands` that modified a value belonging to `ChatConfig`, `ScheduleConfig`, or `ModerationConfig` was incorrectly calling `RpEssentialsConfig.SPEC.save()` instead of the owning spec. This silently discarded every in-game config change (`joinMessage`, `leaveMessage`, `chatMessageColor`, `enableTimestamp`, all schedule and moderation setters) on server restart. Each setter now calls `configValue.save()` directly, which resolves to the correct spec.

* **`MixinServerCommonPacketListenerImpl` — crash on early packet:** The blur guard `RpEssentialsConfig.ENABLE_BLUR.get()` was called at the top of `modifyPacket` with no try/catch. Any packet arriving before the config spec was built (e.g. during server startup) would throw an `IllegalStateException` and crash the server. Wrapped in `try/catch (IllegalStateException)`.

* **`opsSeeAll` ignoring `OP_LEVEL_BYPASS` config:** The admin-exemption check was hardcoded to `receiver.hasPermissions(2)` regardless of the configured `opLevelBypass` value (0–4). A server with `opLevelBypass = 3` would grant full name visibility to any OP2+ player. The check now reads `RpEssentialsConfig.OP_LEVEL_BYPASS.get()`.

* **Double nametag handler — `NicknameNametagHandler` + `ClientNametagRenderer`:** Both classes subscribed to `RenderNameTagEvent` on the same player entities and both called `event.setContent()`. The result was a race between the two handlers on every nametag render frame. `NicknameNametagHandler` has been removed; `ClientNametagRenderer` is now the sole handler.

* **Hardcoded world data path in all managers:** `NicknameManager`, `LicenseManager`, `WarnManager`, `LastConnectionManager`, and `DeathRPManager` all used `new File("world")` as the world root — a path that is only valid in vanilla dedicated server layouts. All managers now delegate to the new `RpEssentialsDataPaths.getDataFolder()` utility which correctly calls `server.getWorldPath(LevelResource.ROOT)`.

* **`Thread.sleep()` blocking the ForkJoinPool common pool:** The 500ms nametag sync delay in `RpEssentialsEventHandler.onPlayerLogin` used `CompletableFuture.runAsync(() -> { Thread.sleep(500); ... })`, which blocks one of the shared pool's threads for 500ms per player login. Replaced with `CompletableFuture.delayedExecutor(500, TimeUnit.MILLISECONDS)` — zero threads blocked.

* **Async `saveToFile()` accessing non-thread-safe server state:** `LicenseManager`, `NicknameManager`, and `DeathRPManager` called `ServerLifecycleHooks.getCurrentServer()` and `server.getProfileCache()` from async `CompletableFuture` threads. `ProfileCache` is not thread-safe. The player name snapshot is now taken synchronously on the server thread before the async write begins.

* **`WorldBorderManager` non-concurrent maps:** `hasBeenWarned` and `playerZoneState` were plain `HashMap` instances written from the server tick thread. Converted to `ConcurrentHashMap`.

* **Fallback AFK coordinates hardcoded to a specific server:** The `/rp afk` command had compile-time fallback coordinates (`x = -2408.5, y = 127.0, z = 588.5, yaw = 138.29f`) that would teleport players to an arbitrary location on any other server where `RpConfig` wasn't loaded. The fallback now uses `(0, 255, 0)` with neutral yaw/pitch.

* **`extractPlayerName` fragile string parsing in `MixinPlayerList` (Bugs 10 & 13):** The join/leave message system relied on `message.replace(" joined the game", "").trim()` to extract the player name from the vanilla broadcast, and then called `findPlayerByName()` to look up the player — who might not yet be in `getPlayers()` at that point. The mixin now only cancels the vanilla broadcast; the custom message is sent from `RpEssentialsEventHandler.onPlayerLogin`/`onPlayerLogout` where the `ServerPlayer` object is guaranteed valid. A new `IRpPlayerList` interface exposed as a mixin on `PlayerList` carries the two helper methods that `RpEssentialsEventHandler` now calls via a cast.

* **Static maps surviving server reloads:** `WorldBorderManager.hasBeenWarned`, `playerZoneState`, and `systemInitialized` are static fields that persist in the JVM across server restarts in the same process. A new `@SubscribeEvent` handler on `ServerStoppingEvent` calls `WorldBorderManager.clearAllCache()` to ensure a clean state on the next start.

* **`RpEssentialsPermissions.staffCache` unbounded growth:** The staff permission cache never evicted entries for players who disconnected abnormally (without `invalidateCache` being called). A new `clearExpiredCache()` method removes all entries whose TTL has expired; it is called every 400 ticks from `onServerTick`.

* **Tick-0 load spike:** At server start, `tickCounter = 0` caused all three tick buckets (every 40, every 400, every 1200 ticks) to fire simultaneously on the very first tick. The world border check is now offset by `+13` and the schedule/cleanup block by `+37` to spread the initial load.

* **Registered missing config:** The config `rpessentials-rp.toml` wasn't registered in the RpEssentials.java causing it to not generate in the files. It is now correctly registered.

---

### Added

* **Proximity Chat:** Chat messages are now optionally limited to a configurable radius.
  - New config option `enableProximityChat` (default: `false`) in `rpessentials-chat.toml`.
  - `proximityChatDistance` — radius in blocks (default: 32, range 1–256).
  - `proximityChatBypassPrefix` — prefix to send a global message despite proximity mode (default: `!`). The prefix is stripped before broadcasting.
  - Two separate format strings: `proximityChatFormat` (proximity) and `globalChatFormat` (global / bypass).
  - Staff members outside radius see a spy-log line (configurable via `MessagesConfig.PROXIMITY_CHAT_SPY_FORMAT`).

* **Mute System:** New staff moderation tool for silencing players in chat.
  - `/rpessentials mute <player> [minutes] [reason]` — mute permanently or temporarily.
  - `/rpessentials unmute <player>` — remove mute.
  - `/rpessentials mute list` — list all active mutes.
  - Muted players cannot send chat messages, use `/rp action`, `/rp commerce`, or `/rp incognito`. Staff members are always exempt.
  - Mutes are persisted in `world/data/rpessentials/mutes.json` and survive server restarts.
  - Auto-expiration on player login and via midnight sweep.
  - **Auto-mute from warns:** Optional automatic mute when a player accumulates a configurable number of active warns (`muteAutoFromWarns`, `muteAutoWarnCount`, `muteAutoDurationMinutes` in `rpessentials-moderation.toml`).
  - Players are notified on login if they are still muted (configurable message `MUTE_NOTIFY_ON_JOIN`).

* **Staff Notes:** Permanent per-player notes visible only to staff.
  - `/rpessentials note add <player> <text>` — add a note to a player's file.
  - `/rpessentials note list <player>` — show all notes for a player (also displayed in `/whois` output).
  - `/rpessentials note remove <player> <noteId>` — delete a specific note.
  - Notes stored in `world/data/rpessentials/notes.json`.
  - Requires OP level 2.

* **Death RP History:** Every RP death is now permanently logged.
  - Stores timestamp, player name, UUID, and the broadcast message in `world/data/rpessentials/deathrp-history.json`.
  - `/rpessentials deathrp history [player]` — view all RP deaths (optionally filtered to one player). Requires staff.

* **Silent Staff Broadcast:** New command to send a message visible only to online staff.
  - `/rpessentials staff broadcast <message>` — full color code and `§`/`&` support.
  - Logged to console with a `[STAFF-BROADCAST]` prefix.

* **Staff Statistics Dashboard:** One-command overview of server moderation state.
  - `/rpessentials stats` — displays: online player count with professions, total active warns, mutes, licenses expiring within 7 days, and the 5 most recent player connections.

* **Dice Roll System:** In-game random number generator for RP scenarios.
  - `/rp dice [diceType]` — roll the specified die and broadcast the result to nearby players.
  - Configurable dice types in `rpessentials-rp.toml`: format `name;maxValue` (e.g. `d6;6`) or custom faces `name;face1,face2,face3` (e.g. `coin;Heads,Tails`). Default set: d4, d6, d8, d10, d12, d20, d100.
  - `diceRollDistance` — broadcast radius in blocks (-1 = global, default: 32).
  - Staff outside radius see a spy-log line.
  - Result format and spy format fully configurable via `MessagesConfig` (`DICE_ROLL_FORMAT`, `DICE_ROLL_SPY_FORMAT`).

* **RP Command Cooldowns:** Prevent spam on `/rp action`, `/rp commerce`, and `/rp incognito`.
  - Three independent cooldowns in `rpessentials-rp.toml`: `actionCooldownSeconds`, `commerceCooldownSeconds`, `incognitoCooldownSeconds` (default: 0 = disabled).
  - Cooldown is per-player, stored in-memory, cleared on disconnect.
  - Blocked uses show an action-bar message with remaining time.

---

### Changed

* **`SyncNametagDataPacket` → `SyncFullPlayerStatePacket`:** The two existing nametag packets (`SyncNametagDataPacket` and `HideNametagsPacket`) have been merged into a single `SyncFullPlayerStatePacket`. The new packet carries: UUID, display name, prefix, suffix, profession, isStaff, and the `hideNametags` flag in one payload. This eliminates the race condition where `HideNametagsPacket` and `SyncNametagDataPacket` could arrive in any order and cause a nametag flicker. All senders and `NetworkHandler` registrations updated accordingly. `ClientNametagCache` updated to store the new record type.

* **`RpEssentials.onServerTick` — spread tick offsets:** The three periodic buckets now use `(tickCounter + offset) % period` with offsets of `0`, `+13`, and `+37` respectively to prevent the tick-0 multi-burst.

---

### Technical

**New classes:**
- `RpEssentialsDataPaths` — shared utility for world data path resolution.
- `MuteManager` — mute lifecycle management (add, remove, check, persist, expire).
- `StaffNoteManager` — staff notes lifecycle management.
- `IRpPlayerList` — mixin interface exposing `rpSendCustomJoinMessage(ServerPlayer)` and `rpSendCustomLeaveMessage(ServerPlayer)` from `MixinPlayerList` to `RpEssentialsEventHandler`.
- `SyncFullPlayerStatePacket` — replaces `SyncNametagDataPacket` + `HideNametagsPacket`.

**Modified configs:**
- `rpessentials-chat.toml` — new `[Proximity Chat]` section.
- `rpessentials-moderation.toml` — new `[Mute System]` section.
- `rpessentials-rp.toml` — new `[Dice System]` and `[RP Cooldowns]` sections.
- `rpessentials-messages.toml` — new keys: `PROXIMITY_CHAT_SPY_FORMAT`, `MUTE_RECEIVED`, `MUTE_EXPIRED`, `MUTE_NOTIFY_ON_JOIN`, `DICE_ROLL_FORMAT`, `DICE_ROLL_SPY_FORMAT`, `ACTION_COOLDOWN_REMAINING`, `NOTE_*` family, `DEATHRP_HISTORY_*` family.

**`rpessentials.mixin.json`** — `MixinPlayerList` no longer uses `@Invoker`; `MixinPlayerListAccessor` removed. `IRpPlayerList` used instead.

---

### Migration Notes

* No breaking changes for existing servers.
* All new systems are disabled by default — no behavior change unless explicitly configured.
* `SyncNametagDataPacket` and `HideNametagsPacket` are gone — clients connecting to a 4.1.4 server **must** also run 4.1.4.
* `mutes.json`, `notes.json`, and `deathrp-history.json` are created automatically on first use.
* The wrong-SPEC-save fix means some config values that appeared to save but didn't will now actually persist. Review your `rpessentials-chat.toml`, `rpessentials-schedule.toml`, and `rpessentials-moderation.toml` after the first start to confirm values are as expected.

## [4.1.3]

**GUI overhaul, cross-midnight schedule, tooltip fixes, license system improvements.**

---

### Added

* **`/rpessentials license reissue <player> <profession>`:** New staff command to give a replacement license item to a player who already has the profession but lost the physical item. The profession data and restrictions are unchanged — only the item is re-created and given. The action is logged to the audit log as `REISSUE`. Requires OP level 2.

* **`LicenseHelper` (new class):** Shared utility that creates and gives a physical license item to a player. Used by both `giveLicense()` in `RpEssentialsCommands` and `SetPlayerProfilePacket`, ensuring both paths produce an identical item and perform the same side-effects (tag add, cache invalidation, client restriction sync).

* **Cross-midnight schedule support (`RpEssentialsScheduleManager`):** The schedule system now handles sessions that span midnight (e.g. 22:00 → 02:00). Before this fix, players were incorrectly kicked at 00:00 even if the configured closing time was 02:00.
  - `DaySchedule.isOpen()` now handles both same-day (`open < close`) and cross-midnight (`close < open`) sessions.
  - New `getActiveSchedule()` method: checks today's session first, then yesterday's if it crosses midnight and is still active.
  - `minutesUntilClose()` calculates remaining time correctly across midnight.
  - `tickMidnightSweep()` no longer resets `hasOpenedToday`/`hasClosedToday` if a cross-midnight session is still ongoing at 00:00.
  - `checkWarnings()` is now based on `minutesUntilClose()` with a 2-minute tolerance window instead of exact clock comparison, preventing missed warnings due to tick granularity.
  - `isInSlot()` (Death Hours, HRP Hours) reuses `DaySchedule.isOpen()` — cross-midnight support applies to those systems as well.

* **Profession selector revoke button (Profile Manager GUI):** When navigating to a profession the selected player already owns, a **§c✖ Revoke license** button appears between the `◀` and `▶` arrows. Clicking it sends a revoke packet to the server, removes the license, applies the tag removal, marks the physical item as revoked, and updates the local list immediately for visual feedback.

* **Full i18n for admin GUIs:** All previously hardcoded French strings in `ProfessionEditorScreen` and `PlayerProfileScreen` are now translated via the Minecraft language system (`Component.translatable` / `I18n.get`). New keys added to `en_us.json` and `fr_fr.json`:
  - Color palette: `rpessentials.gui.color.*` (10 colors, shared between both screens)
  - Profession editor: `rpessentials.gui.profession_editor.*`
  - Player profile: `rpessentials.gui.player_profile.*`
  - Shared: `rpessentials.gui.btn_more`

* **Tab autocomplete for restrictions (Profession Editor GUI):** The restriction input field now supports keyboard-driven autocomplete identical to Minecraft command completion.
  - Typing any character immediately filters the item/block registry.
  - **Tab** cycles forward through suggestions, **Shift+Tab** cycles backward.
  - **↓ / ↑** arrows navigate the list.
  - **Enter** validates and adds the selected suggestion.
  - **Escape** closes the dropdown without adding.
  - **Click** on any line also selects it.
  - The dropdown is rendered as an overlay after `super.render()`, so it always appears above all other widgets and is never hidden behind them.
  - Two-pass matching: prefix matches first, then contains matches.

* **Full profession list in Profile Manager GUI:** `OpenPlayerProfileGuiPacket.PlayerData` now carries `List<String> currentLicenses` (the complete license list) instead of a single `String currentLicense`. The profession selector navigates all professions; owned ones are shown in red with a `✔ Already owned` indicator. The list of active licenses is displayed below the selector. On load, the cursor starts on the first unowned profession for convenience.

* **Profession save confirmation with summary (`SaveProfessionPacket`):** After saving a profession from the GUI, the admin now receives a detailed confirmation message listing the display name, color preview, and each restriction category with its entry count and the first 3 items (e.g. `Equipment: minecraft:diamond_sword, minecraft:iron_sword (2)`).

---

### Changed

* **`ProfessionEditorScreen` — "New profession" button relocated:** The button has moved from the right-hand form area to the bottom of the left panel (the profession list), which is the more instinctive position since that is where the list of professions lives.

* **`SetPlayerProfilePacket` — full license flow from GUI:** Granting a profession through the Profile Manager GUI now triggers the same complete flow as the `/license give` command: physical item created with correct name/lore/NBT, vanilla scoreboard tag added, cache invalidated, client restriction sync sent. Previously the item was never created and the tag was never set.

* **`SetPlayerProfilePacket` — duplicate license guard:** If the selected player already holds the chosen profession when clicking "Apply", the server returns an informational message to the admin (`already has this license — use /license reissue to give a replacement item`) instead of silently duplicating the entry.

* **`SetPlayerProfilePacket` — `revokeMode` field:** The packet now carries a `boolean revokeMode`. When `true`, the `licenseId` is revoked rather than granted. This cleanly separates the give and revoke paths in a single packet type without needing a second packet.

* **`ProfessionConfig.GLOBAL_UNBREAKABLE_BLOCKS` and `CONTAINER_OPEN_RESTRICTIONS` now shown in item tooltips:** `ProfessionRestrictionEventHandler.onItemTooltip` previously only showed craft, item-use, and equipment restrictions. It now also shows:
  - `Mining: ✘ — <profession>` for blocks in `globalUnbreakableBlocks`.
  - `Open: ✘ — <profession>` for containers in `containerOpenRestrictions` (wildcard patterns supported).
  - The whole method is wrapped in `try/catch (IllegalStateException)` to avoid crashes if the config is not yet loaded at tooltip render time.

---

### Fixed

* **License item name ignoring color code from GUI:** The GUI saves profession colors as `&e`-style codes. `Component.literal()` does not parse `&` or `§` — it renders them as literal characters. `LicenseHelper` now calls `.replace("&", "§")` on the color prefix before passing it through `ColorHelper.parseColors()`, producing a correctly colored item name regardless of whether the code was entered via the GUI (`&e`) or hand-edited in the config (`§e`).

* **Revoked license message displayed twice:** `TempLicenseExpirationManager.markRevokedLicenseItems()` was writing the revoke title and body directly into `DataComponents.LORE`, while `LicenseItem.appendHoverText()` was independently reading the `revoked` flag and appending the same lines at render time. Because the Minecraft tooltip system concatenates lore and `appendHoverText` output, the message appeared twice. `markRevokedLicenseItems()` now only sets the `revoked = true` flag in CUSTOM_DATA and leaves all visual rendering exclusively to `appendHoverText()`.

* **Cross-midnight session players kicked at midnight:** `isServerOpen()` only checked today's `DaySchedule`, so any session defined with `close < open` (e.g. 22:00 → 02:00) would appear closed from 00:00 onwards even though it should remain open until 02:00. Fixed by `getActiveSchedule()` which additionally checks yesterday's schedule for an ongoing cross-midnight session.

* **`SaveProfessionPacket` compilation error:** `String MAX_INLINE = 3;` — incompatible types (`int` assigned to `String`). Fixed to `int max = 3;`.

---

### Technical

* **`LicenseHelper.java`** — new shared class in `net.rp.rpessentials.profession`.
* **`OpenPlayerProfileGuiPacket.PlayerData`** — `String currentLicense` replaced by `List<String> currentLicenses`. StreamCodec updated accordingly.
* **`RequestOpenGuiPacket.handlePlayerProfileGui()`** — now populates the full license list via `LicenseManager.getLicenses()`.
* **`RpEssentialsScheduleManager`** — `getActiveSchedule()` added as the main entry point for all open/close checks. `DaySchedule` gains `crossesMidnight()` and `minutesUntilClose()`. `isInSlot()` refactored to delegate to `DaySchedule.isOpen()`.
* **`RpEssentials.onServerTick()`** — schedule block updated to use `getActiveSchedule()` for warning and closing detection. Opening detection still uses `getTodaySchedule()` to fire at the correct wall-clock time.
* **`MessagesConfig`** — three new keys: `LICENSE_REISSUE_STAFF`, `LICENSE_REISSUE_PLAYER`, `LICENSE_REISSUE_NOT_FOUND`.

## [4.1.2]

**Profession & Restriction System — Complete Overhaul**

Complete rewrite of the profession restriction enforcement system. The old tick-based polling approach has been replaced with event-driven handlers and Mixin injection points, eliminating all performance overhead and fixing several long-standing bugs.

---

### Added

* **`/myprofession` command (alias `/myjob`):** New player-facing command to view active licenses without needing staff.
  - Displays all active licenses with their formatted profession name and color.
  - Distinguishes permanent licenses (`[Permanent]`) from RP temporary licenses (`[RP] expires on dd/MM/yyyy`).
  - Accessible to all players — no permission required.

* **Container Open Restrictions:** New restriction type allowing admins to block specific containers from being opened without the required profession.
  - New config key `containerOpenRestrictions` in `rpessentials-professions.toml`.
  - Format: `block_id;profession1,profession2` — e.g. `minecraft:anvil;forgeron`.
  - Wildcard support (e.g. `minecraft:*_shulker_box`).
  - Implemented via `MixinServerPlayerGameMode` — fires exactly once per open attempt, zero overhead.
  - New message key `containerOpenBlockedMessage` in `[Messages]` section of `rpessentials-professions.toml`.
  - New methods in `ProfessionRestrictionManager`: `canOpenContainer()`, `getContainerOpenBlockedMessage()`, `getContainerRequiredProfessions()`.

* **New Mixins — Craft restrictions extended to all workstations:**
  - `MixinResultSlot` — Injects into `Slot.mayPickup()` to block pickup from any result slot whose container is a `ResultContainer` (covers `GrindstoneMenu`, `StonecutterMenu`, `LoomMenu`, `CartographyTableMenu`) or whose slot type is `ResultSlot` (vanilla crafting table). Zero tick polling.
  - `MixinItemCombinerMenuBase` — Abstract Mixin base declaring `@Shadow protected ResultContainer resultSlots`, shared by the two concrete subclass mixins below.
  - `MixinSmithingMenu` — Injects into `SmithingMenu.mayPickup()` to block netherite upgrades at the smithing table.
  - `MixinAnvilMenu` — Injects into `AnvilMenu.mayPickup()` to block repair/enchant results at the anvil.
  - `MixinServerPlayerGameMode` — Injects into the container open flow to enforce `containerOpenRestrictions`.

* **New Mixins — Armor equip restrictions:**
  - `ArmorSlotAccessor` — `@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")` accessor interface exposing the `owner` field via `@Accessor`. Required because `ArmorSlot` is package-private.
  - `MixinArmorSlot` — `@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")` injecting into `mayPlace(ItemStack)` to block armor from being dragged into an armor slot.
  - `MixinArmorItemUse` — `@Mixin(ArmorItem.class)` injecting into `use()` to block armor equip via right-click. Runs on **both sides**: client-side uses `ClientProfessionRestrictions` (synced at login) to prevent the equip animation before it starts; server-side enforces the restriction authoritatively. Eliminates the visual bug where armor briefly appeared equipped before being removed.

---

### Changed

* **`ProfessionRestrictionManager` — Simplified via generic `canPerformAction()`:**
  - `canCraft()`, `canBreakBlock()`, `canUseItem()`, `canEquip()` all previously duplicated the same guard logic (init check, exemption check, global block check, permission check). All four now delegate to a single private `canPerformAction(player, resourceId, globalList, allowedList)` method, reducing the class by ~60 lines.
  - Added `sendCraftBlockedMessage()` utility used by the new mixins.
  - No behavior change — all existing functionality preserved.

* **`ProfessionRestrictionEventHandler` — Refactored and bug-fixed:**
  - **Bug fix:** `onBlockBreak`, `onLeftClickBlock`, and `onAttackEntity` were missing the `if (player.isCreative()) return;` guard. Creative mode players were incorrectly blocked by profession restrictions.
  - `onRightClickItem` and `onRightClickBlock` now share a single `checkItemUse()` helper — eliminates duplicated logic.
  - Removed `onEquipmentChange` (`LivingEquipmentChangeEvent`) entirely — replaced by `MixinArmorSlot` which is event-driven and has no visual artifacts.
  - Removed `lastEquipmentWarning` cache and `EQUIPMENT_WARNING_COOLDOWN` constant (no longer needed).
  - `cleanupCaches()` updated accordingly.

* **`ProfessionConfig` — New entries:**
  - `containerOpenRestrictions` (List) — blocks container opening by profession. Default: empty.
  - `msgContainerOpenBlocked` (String) — message shown when container open is blocked. Variables: `{profession}`. Supports color codes.

* **`rpessentials.mixin.json` — New entries:**
  - `MixinResultSlot`
  - `ArmorSlotAccessor`
  - `MixinArmorSlot`
  - `MixinArmorItemUse`
  - `MixinItemCombinerMenuBase`
  - `MixinSmithingMenu`
  - `MixinAnvilMenu`
  - `MixinServerPlayerGameMode`

---

### Removed

* **`CraftingAndArmorRestrictionEventHandler`** — Entirely deleted.
  - Previously polled the craft result slot every 5 ticks for every player with a crafting menu open.
  - Tracked container open/close events (`PlayerContainerEvent.Open/Close`) to filter active players.
  - Replaced by `MixinResultSlot` which fires only when the player actually attempts to pick up the result — zero polling overhead.
  - `cleanupCaches()` call removed from `RpEssentials.onServerTick()`.

---

### Fixed

* **Creative mode bypass:** Creative players were incorrectly blocked by block break, left-click, and attack restrictions. All three handlers now return early for creative players.
* **Smithing Table (netherite upgrade) not restricted:** Upgrading items with netherite bypassed all profession restrictions because `SmithingMenu` uses `ItemCombinerMenu.mayPickup()` rather than `ResultSlot`. Fixed via `MixinSmithingMenu`.
* **Anvil not restricted:** Same root cause as smithing table. Fixed via `MixinAnvilMenu`.
* **Grindstone, Stonecutter, Loom, Cartography Table not restricted:** These menus use anonymous `Slot` subclasses with `ResultContainer` as their container — not `ResultSlot`. `MixinResultSlot` now also checks `slot.container instanceof ResultContainer` to cover all these cases.
* **Armor equip visual bug:** The previous `LivingEquipmentChangeEvent` approach removed armor server-side after it had already been equipped client-side, causing a visible flash. `MixinArmorItemUse` now blocks the equip on both sides simultaneously — the client never starts the equip animation.
* **Schedule system:** The previous version was not checking the schedule time on player login, it is now fixed and checks properly when a player login.

---

### Technical

**Mixin design notes:**

- `MixinItemCombinerMenuBase` exists solely to expose `@Shadow protected ResultContainer resultSlots` to `MixinSmithingMenu` and `MixinAnvilMenu` via Mixin inheritance. Direct `@Shadow` on a field declared in a parent class is not supported by the Mixin processor — the base abstract mixin pattern is the correct workaround.
- `ArmorSlot` is package-private (`class ArmorSlot` without `public`) — direct Java references to it cause compile errors. Both `ArmorSlotAccessor` and `MixinArmorSlot` use `@Mixin(targets = "net.minecraft.world.inventory.ArmorSlot")` (string form) to avoid this.
- All `@Inject` annotations on mapped methods use `remap = false` to avoid obfuscation mapping failures during compilation.
- All new mixins guard against creative mode and client-side execution where appropriate.

**Performance comparison:**

| System | Before | After |
|:-------|:-------|:------|
| Craft restriction check | Every 5 ticks per player with open menu | On pickup attempt only |
| Armor equip restriction | Every tick via `LivingEquipmentChangeEvent` | On `mayPlace()` / `use()` only |
| Container open restriction | Not implemented | On open attempt only |
| Total tick overhead | ~12 checks/sec/player (crafting) + continuous armor | Zero |

**Migration Notes:**

- No breaking changes — fully backward compatible with 4.1.1.
- `CraftingAndArmorRestrictionEventHandler` is gone — remove any external references if you have custom patches.
- New config keys (`containerOpenRestrictions`, `msgContainerOpenBlocked`) are generated automatically on first launch with empty defaults.
- Clients connecting to a 4.1.2 server must also run 4.1.2 — `MixinArmorItemUse` adds a client-side restriction path that requires the `SyncProfessionRestrictionsPacket` already sent since 4.0.0.

## [4.1.1]

**Nametag system overhaul — Realistic Nametag behavior + nickname sync fix**
GUYS I FINALLY MADE IT OMG, THE NAMETAG ARE FINALLY HIDDEN EVEN WHEN BEHIND A BLOCK
I'M SO HAPPY GUYYYYSSSSS WOWOWOWOWOW, so just to be clear, you can now hide nametag, change them with the nickname and Im so happy wowowow

---

### Added

* **Realistic Nametag behavior:** Nametags are now hidden behind opaque blocks without any client-side raycast.
  - Implemented via `MixinEntityRenderer` — changes `Font.DisplayMode.SEE_THROUGH` to `Font.DisplayMode.NORMAL` on `drawInBatch`, activating the GPU depth test. This is the exact mechanism used by the Realistic Nametag mod.
  - Works on both `ordinal = 0` (background layer, visible while sneaking) and `ordinal = 1` (white text layer, visible when not sneaking).
  - No performance overhead — the depth test is handled entirely by the GPU.

* **Server nickname displayed on nametag:** Nametags now display the nickname set on the server (via `/rpessentials nick`) instead of any locally cached nickname.
  - `MixinEntityRenderer` captures the entity being rendered via `@Inject HEAD` into a `@Unique` field, then resolves the nickname from `ClientNametagCache` in each `@ModifyArg`.
  - Fallback to the real MC username (`getGameProfile().getName()`) if the server data has not yet been received — never falls back to a locally-injected nickname.
  - Supports LuckPerms prefix with color codes via `ColorHelper.parseColors()`.

* **Full nametag data sync on login:** When a player joins, the server now sends nametag data for all currently online players to the new player (in addition to broadcasting the new player's data to everyone else).
  - Both sends are deferred by 500ms via `CompletableFuture` to ensure the client has finished loading before receiving the packets.
  - This fixes a case where players already in-game would show their local nickname to a freshly connected player whose `ClientNametagCache` was empty.

* **Tab list player head hidden for obfuscated players:** The small face icon next to a player's name in the tab list is now hidden when that player is obfuscated (name replaced with `§k????`).
  - Implemented via `MixinPlayerTabOverlay` using `@WrapOperation` with `@Local` (MixinExtras) to intercept the `PlayerFaceRenderer.draw()` call inside `PlayerTabOverlay.render()`.
  - The `PlayerInfo` local variable is captured directly to check whether the player's display name contains `§k`, which is the obfuscation marker injected by `MixinServerCommonPacketListenerImpl`.
  - When obfuscated, the face draw call is simply skipped — no placeholder is drawn.
  - Non-obfuscated players, staff, whitelisted players, and always-visible players are unaffected.

---

### Changed

* **`MixinEntityRenderer`:** Completely rewritten.
  - Added `@Inject HEAD cancellable = true` → cancels `renderNameTag` entirely when `ClientNametagConfig.shouldHideNametags()` is true (global hide toggle).
  - Added `@Inject HEAD` → captures current entity into `rpessentials$currentEntity`.
  - Added `@Inject RETURN` → clears `rpessentials$currentEntity`.
  - Added `@ModifyArg ordinal=0 index=7` → forces `Font.DisplayMode.NORMAL` (depth test / block occlusion).
  - Added `@ModifyArg ordinal=0 index=0` and `@ModifyArg ordinal=1 index=0` → replaces the rendered `Component` with the server nickname. Never returns `null` (would crash `drawInBatch`) — falls back to `original` if `rpessentials$resolveNickname` returns null.
  - All injections use `remap = false` and the NeoForge 1.21.1 method signature `...MultiBufferSource;IF)V` (with the extra `float partialTick` parameter).

* **`ClientNametagRenderer`:** Rewritten to use `ClientNametagCache` (replaces the removed `ClientNametagConfig` player map).
  - Handles `hideNametags` check via `RpEssentialsConfig.HIDE_NAMETAGS`.
  - Sets `event.setContent()` with the nickname + prefix from cache.
  - Returns early (does not override content) if cache has no entry yet for the player — avoids showing stale data.

* **`ClientNametagConfig`:** Rebuilt as a minimal class — stores only the `hideNametags` boolean received via `HideNametagsPacket`. All advanced nametag config fields removed.

* **`NetworkHandler`:** Restored `HideNametagsPacket` and `SyncNametagDataPacket` registrations that had been accidentally dropped. All GUI admin packets also restored.

* **`RpEssentialsEventHandler`:** Nametag sync logic moved entirely inside the `CompletableFuture` block (500ms delay). Now sends data of all currently online players to the newly joined player in addition to broadcasting the new player's data to everyone.

* **`RpEssentialsConfig`:** Removed unused advanced nametag config fields (`NAMETAG_ADVANCED_ENABLED`, `NAMETAG_FORMAT`, `NAMETAG_OBFUSCATION_DISTANCE`, `NAMETAG_RENDER_DISTANCE`, `NAMETAG_HIDE_BEHIND_BLOCKS`, `NAMETAG_SHOW_WHILE_SNEAKING`, `NAMETAG_STAFF_ALWAYS_SEE_REAL`, `NAMETAG_OBFUSCATION_ENABLED`). Only `HIDE_NAMETAGS` (in `[Obfuscation Settings]`) is kept.

* **`RpEssentialsCommands`:** Removed all `/rpessentials config set nametag*` commands that referenced the now-deleted config fields.

---

### Removed

* `NametagSyncPacket.java` — replaced by the already-existing `SyncNametagDataPacket`.
* `NametagSyncHelper.java` — logic moved directly into `RpEssentialsEventHandler`.
* `NametagFormatter.java` — formatting now handled inline in `MixinEntityRenderer` and `ClientNametagRenderer`.
* `NametagConfig.java` — dedicated nametag config file removed; only `HIDE_NAMETAGS` survives in `rpessentials-core.toml`.
* The `rpessentials-nametag.toml` config file is no longer generated. Existing files can be deleted safely.

---

### Fixed

* **Nametag visible through blocks** — Fixed by switching `Font.DisplayMode` from `SEE_THROUGH` to `NORMAL`, activating the GPU depth test (same technique as Realistic Nametag mod).
* **Server nickname not shown on nametag** — Fixed by reading from `ClientNametagCache` (server data) instead of falling back to `original` (which contained the locally injected nickname from `MixinServerPlayer`).
* **`NullPointerException` in `Font.drawInBatch`** — Fixed by ensuring `@ModifyArg` handlers never return `null`; they now fall back to `original` when `rpessentials$resolveNickname` returns null (e.g. for the local player or non-player entities).
* **Players already online not synced to new joiner** — Fixed by sending `SyncNametagDataPacket` for each online player to the newly connected player in `RpEssentialsEventHandler`.
* **Player head visible in tab list for obfuscated players** — Fixed via `MixinPlayerTabOverlay`, which now skips the `PlayerFaceRenderer.draw()` call for any player whose display name contains the `§k` obfuscation marker, preventing skin-based identification of obfuscated players.

---

### Migration Notes

* No breaking changes for existing servers.
* `rpessentials-nametag.toml` is no longer loaded or generated — delete it from `config/rpessentials/` if present.
* All existing `/rpessentials config set nametag*` commands have been removed. Use `hideNametags` in `rpessentials-core.toml` or `/rpessentials config set hideNametags true/false` to control nametag visibility.
* Clients connecting to a 4.1.1 server must also run 4.1.1 — the packet registry version was bumped.

## [4.1.0]

**Admin GUI System — Two new in-game interfaces for server staff to manage professions and player RP profiles without editing config files.**

---

**Added**

* **Admin GUI — Profession Editor:** New in-game interface for creating and editing professions.
  - Browse all existing professions in a scrollable left-panel list with item count indicator.
  - Create new professions or edit existing ones with live preview.
  - Fields: ID (locked after creation), display name, color selector.
  - Color selector displays each option in its own Minecraft color with bold highlight on the selected one.
  - Live name preview rendered in the selected color next to the color buttons.
  - Per-profession restriction overrides split into four tabs: Crafts, Blocs, Items, Équipement.
  - Dynamic autocompletion for restriction entries, populated from `BuiltInRegistries.ITEM` and `BuiltInRegistries.BLOCK` — compatible with all loaded mods.
  - Wildcard entries (e.g. `minecraft:*_sword`) fully supported.
  - Scroll indicator on the profession list shows remaining hidden entries: `▼ (N de plus)`.
  - Changes saved immediately to `rpessentials-professions.toml` on disk.

* **Admin GUI — RP Profile Manager:** New in-game interface for configuring a player's RP profile in one click.
  - Lists all currently connected players with their active nickname (if any).
  - Select a player to load their current nickname, role, and primary license.
  - Set nickname with color selector (10 colors, live preview with `→ colored name`).
  - Set role via free-text field or one-click shortcut buttons built from the configured roles list.
  - Select primary profession with `◀ / ▶` navigation and `N/Total` counter.
  - Apply button sends all three changes to the server simultaneously.
  - Scroll support for large player lists with `▼ (N de plus)` indicator.

* **Keybindings:** Two configurable key mappings added under the `Oneria RP` category in Options → Controls.
  - `key.rpessentials.open_profession_gui` — Open Profession Editor (no default key).
  - `key.rpessentials.open_player_profile_gui` — Open RP Profile Manager (no default key).
  - No default bindings to avoid conflicts — staff assign their own keys.

* **New Packets (6 total):**
  - `RequestOpenGuiPacket` (Client→Server) — Staff keypress triggers a request; server validates `isStaff()` before responding.
  - `OpenProfessionGuiPacket` (Server→Client) — Sends full profession list with all override data.
  - `OpenPlayerProfileGuiPacket` (Server→Client) — Sends online player list, available professions, and available roles.
  - `SaveProfessionPacket` (Client→Server) — Saves a created or modified profession to config.
  - `SetPlayerProfilePacket` (Client→Server) — Applies nickname, role, and license to a target player.
  - All packets validated server-side with `isStaff()` — client cannot bypass the permission check.

* **New Classes:**
  - `RpKeyBindings` — Key mapping definitions, registered via `modEventBus.addListener()`.
  - `RpClientTickHandler` — Client tick handler detecting key presses and sending request packets.
  - `ClientGuiOpener` — `@OnlyIn(Dist.CLIENT)` indirection class to open Screens from packet handlers without loading client classes on the server.
  - `ProfessionEditorScreen` — Full profession editor GUI.
  - `PlayerProfileScreen` — Full RP profile manager GUI.
  - All five packet classes under `net.rp.rpessentials.network`.

* **Lang keys** (add to `fr_fr.json` and `en_us.json`):
  - `key.categories.rpessentials`
  - `key.rpessentials.open_profession_gui`
  - `key.rpessentials.open_player_profile_gui`

---

**Fixed**

* **`RpEssentialsPermissions` — Server crash on login when LuckPerms is absent:**
  - `checkStaffStatus()` was calling `LuckPermsProvider.get()` inside a `catch (Exception e)` block.
  - `NoClassDefFoundError` is a `java.lang.Error`, not an `Exception` — it was never caught, causing an unhandled crash that killed the server on every player login.
  - Fixed by adding an explicit `catch (NoClassDefFoundError e)` before the existing exception catches.
  - LuckPerms imports removed from the class header and replaced with fully-qualified references inside the method body, preventing the classloader from attempting to resolve LuckPerms classes at startup.

* **Profession GUI — Config not persisted after save:**
  - `SaveProfessionPacket` was calling `configValue.set()` without calling `configValue.save()`.
  - In NeoForge, `.set()` only updates the in-memory value — `.save()` is required to write the change to the `.toml` file on disk.
  - All five `ConfigValue` writes (`PROFESSIONS`, `PROFESSION_ALLOWED_CRAFTS`, `PROFESSION_ALLOWED_BLOCKS`, `PROFESSION_ALLOWED_ITEMS`, `PROFESSION_ALLOWED_EQUIPMENT`) now call both `.set()` and `.save()` via the new `setAndSave()` helper.

* **GUI Screens — All fields reset on every button click:**
  - All mutable form state is now stored in instance variables (`stateNick`, `stateName`, `stateRole`, `stateColorIndex`, etc.).
  - `EditBox` widgets use `setResponder()` to update state on each keystroke without triggering a full rebuild.
  - `rebuild()` is called only from button click handlers — never from `setResponder()`.
  - Fields are restored from state on every `init()` call, so clicking any button no longer clears typed text.

* **GUI Screens — Blur effect applied over the interface:**
  - `renderBackground()` from `Screen` was being called, which applies Minecraft's background blur effect on top of all GUI content.
  - Fixed by overriding `renderBackground()` with an empty body in both screens, and replacing the call in `render()` with a manual `g.fill(0, 0, width, height, 0x99000000)`.

* **Keybinding — Constructor error `Cannot resolve constructor 'KeyMapping(String, Type, Key, String)'`:**
  - `InputConstants.UNKNOWN` is a `Key` object, not an `int`.
  - Fixed by using `InputConstants.UNKNOWN.getValue()` to pass the underlying `int` to the `KeyMapping` constructor.

* **Keybinding — Category and key names displayed as raw translation keys:**
  - Lang keys `key.categories.rpessentials`, `key.rpessentials.open_profession_gui`, and `key.rpessentials.open_player_profile_gui` were missing from the language files.

* **`@EventBusSubscriber(bus = Bus.MOD/GAME)` deprecation warnings:**
  - `bus = EventBusSubscriber.Bus.MOD` and `Bus.GAME` are deprecated since NeoForge 1.21.1.
  - `RpKeyBindings` no longer uses `@EventBusSubscriber` — `RegisterKeyMappingsEvent` is now registered via `modEventBus.addListener(RpKeyBindings::onRegisterKeyMappings)` in the `RpEssentials` constructor.
  - `RpClientTickHandler` retains `@EventBusSubscriber` but without the `bus` parameter (GAME bus is the default).

* **`PlayerProfileScreen` — Compile error `package net.rp.rpessentials.RpEssentialsConfig does not exist`:**
  - `PlayerProfileScreen` is `@OnlyIn(Dist.CLIENT)` and cannot access server-side config classes.
  - Available roles are now sent by the server inside `OpenPlayerProfileGuiPacket` (alongside professions and player data) and passed to the screen via constructor.
  - `RpEssentialsConfig.ROLES` is read exclusively in `RequestOpenGuiPacket.handlePlayerProfileGui()`, which runs on the server thread.

---

**Technical**

* **Enhanced Classes:**
  - `NetworkHandler` — Five new packet registrations (two `playToServer`, three `playToClient`).
  - `RpEssentials` — Added `modEventBus.addListener(RpKeyBindings::onRegisterKeyMappings)`.
  - `RpEssentialsPermissions` — `checkStaffStatus()` rewritten with three-tier catch (`NoClassDefFoundError`, `IllegalStateException`, `Exception`) and fully-qualified LuckPerms references.
  - `SaveProfessionPacket` — New `setAndSave()` helper ensures every `ConfigValue` write is immediately persisted to disk.
  - `SetPlayerProfilePacket` — `applyRole()` extracted as a private method, mirrors the logic of `/rpessentials setrole` exactly.
  - `OpenPlayerProfileGuiPacket` — Added `List<String> availableRoles` field, serialized in the existing `StreamCodec`.

**Security**

* All five new packets validate `RpEssentialsPermissions.isStaff()` server-side before processing.
* Non-staff players receive no response and no error message — the GUI system is invisible to them.
* Config writes are only possible via server-side packet handlers — the client never writes directly to config.

**Migration Notes**

* No breaking changes — fully backward compatible with 4.0.1.
* No new config files.
* Add the three lang keys above to your `fr_fr.json` / `en_us.json` if you use custom language files.
* Clients connecting to a 4.1.0 server must also run 4.1.0 (new packet IDs).

## [4.0.1] 

**Bug Fixes & English Translation Pass + Updated the in-game logo of the mod**

### Fixed

* **Welcome Message — Placeholders Not Replaced:** `{player}` and `{nickname}` were never substituted in welcome lines. The code was using `%player%` (legacy format) while the config defined `{player}`. Both placeholders are now correctly replaced.
  - `{player}` → MC username
  - `{nickname}` → RP nickname (falls back to MC username if no nickname is set)
  - Affected file: `RpEssentialsEventHandler.java`

* **Death RP Messages — Wrong Placeholder Format:** All Death RP message handlers were using `%..%` placeholders while every other system in the mod uses `{..}`. This caused death messages, toggle notifications, and global broadcasts to display raw placeholder text instead of the actual values.
  - `%player%` → `{player}`, `%realname%` → `{realname}`, `%staff%` → `{staff}` in all `.replace()` calls
  - Affected file: `DeathRPManager.java`

* **Wrong Method Call in `onPlayerLogout`:** `RpEssentialsPermissions.clearCacheForPlayer()` was called but does not exist. Replaced with the correct `RpEssentialsPermissions.invalidateCache(UUID)`.
  - Affected file: `RpEssentialsEventHandler.java`

### Changed

* **Full English Translation — Config Default Values:** All remaining French strings in config default values have been translated to English.

  **`RpEssentialsConfig.java` — DeathRP section:**
  | Before | After |
  |---|---|
  | `"&6[Mort RP] &fVotre mort RP a ete &aactivee..."` | `"&6[Death RP] &fYour RP death has been &aenabled..."` |
  | `"&6[Mort RP] &fVotre mort RP a ete &cdesactivee..."` | `"&6[Death RP] &fYour RP death has been &cdisabled..."` |
  | `"&6[Mort RP] &fLe systeme de mort RP a ete &aactive &fpar %staff%."` | `"&6[Death RP] &fThe Death RP system has been &aenabled &fby {staff}."` |
  | `"&6[Mort RP] &fLe systeme de mort RP a ete &cdesactive &fpar %staff%."` | `"&6[Death RP] &fThe Death RP system has been &cdisabled &fby {staff}."` |
  | `"&c[Mort RP] &f%player% &7(%realname%)..."` | `"&c[Death RP] &f{player} &7({realname})..."` |

  **`ProfessionConfig.java` — Messages section:**
  | Before | After |
  |---|---|
  | `"§c✘ Vous ne pouvez pas crafter cet objet ! §7Métier requis: §e{profession}"` | `"§c✘ You cannot craft this item. §7Required profession: §e{profession}"` |
  | `"§c✘ Vous ne pouvez pas casser ce bloc ! §7Métier requis: §e{profession}"` | `"§c✘ You cannot break this block. §7Required profession: §e{profession}"` |
  | `"§c✘ Vous ne pouvez pas utiliser cet objet ! §7Métier requis: §e{profession}"` | `"§c✘ You cannot use this item. §7Required profession: §e{profession}"` |
  | `"§c✘ Vous ne pouvez pas équiper cet objet ! §7Métier requis: §e{profession}"` | `"§c✘ You cannot equip this item. §7Required profession: §e{profession}"` |

  **`RpEssentialsCommands.java` — `config status` output:**
  | Before | After |
  |---|---|
  | `§7Mort RP` | `§7Death RP` |
  | `§eMort RP global` | `§eGlobal enabled` |
  | `§eRetrait whitelist` | `§eWhitelist removal` |
  | `§eSon de mort` | `§eDeath sound` |

### Technical

* **Modified Files:**
  - `RpEssentialsEventHandler.java` — Welcome placeholder fix (`%player%` → `{player}`, added `{nickname}`), logout method fix (`clearCacheForPlayer` → `invalidateCache`)
  - `DeathRPManager.java` — All `.replace()` calls updated from `%..%` to `{..}` format, internal log comments translated to English
  - `RpEssentialsConfig.java` — 5 French default strings in `[DeathRP]` section translated and placeholder format unified
  - `ProfessionConfig.java` — 4 French default strings in `[Messages]` section translated
  - `RpEssentialsCommands.java` — 4 French labels in `showStatus()` translated

### Migration Notes

* **No breaking changes** — fully backward compatible with 4.0.0.
* **Existing config files are not affected** — default value changes only apply to new installations or if a key is missing from an existing config. To update Death RP messages on an existing server, edit the relevant keys in `rpessentials-core.toml` under `[DeathRP.deathEvent]`, `[DeathRP.playerToggle]`, and `[DeathRP.globalToggle]`. To update profession restriction messages, edit `rpessentials-professions.toml` under `[Messages]`.
* Clients connecting to a 4.0.1 server can still run 4.0.0 — no packet changes.

## [4.0.0]

**⚠ Breaking Change:** The mod ID has changed from `oneriaserverutilities` to `rpessentials`. Automatic migration is included — no manual action required on first launch.

---

**Added**

* **Per-Day Schedule System:** The schedule system now supports individual opening/closing times per day of the week.
  - Each day (`MONDAY` through `SUNDAY`) has its own `enabled`, `open`, and `close` fields.
  - Disabled days are treated as fully closed.
  - `/rpessentials config set scheduleDay <DAY> <open|close|enabled> <value>` — live update without restart.
  - `canPlayerJoin()` now displays the next open day and its hours in the kick message. Placeholders: `{day}`, `{open}`, `{close}`.
  - `/rpessentials schedule` and `/schedule` now display the full week at a glance with the current day highlighted.

* **Death Hours:** New optional schedule layer that activates RP death during configured time slots, independently of the global Death RP toggle.
  - Configurable via `[Death Hours]` section in `rpessentials-schedule.toml`.
  - Supports multiple slots and cross-midnight ranges.
  - When enabled, `isDeathRPEnabled()` checks the current time before applying the global state.
  - Disabled by default.

* **HRP Hours:** New optional schedule layer for HRP (out-of-roleplay) period management.
  - Two tiers: tolerated (noted but not punished) and allowed (fully free).
  - Broadcast message sent once per slot start to all connected players.
  - Configurable display mode: `CHAT`, `ACTION_BAR`, `TITLE`, `IMMERSIVE`.
  - Disabled by default — entirely ignored when `enableHrpHours = false`.

* **Auto-Unwhitelist:** Automatic removal of inactive players from the whitelist.
  - Configurable inactivity threshold (days).
  - Runs once per day at midnight.
  - Requires `enableLastConnection = true`.
  - Staff members online at the time receive a clickable cancel button to immediately re-whitelist the player.
  - Optional extra commands executed per removed player. Placeholders: `{player}`, `{uuid}`.
  - Disabled by default.

* **Vanilla Tag Auto-Management on License:** When a license is given or revoked, the corresponding vanilla `/tag` is automatically added or removed.
  - Tag name = profession ID. No configuration required.

* **`/rpessentials setrole`:** New command to assign a role to a player.
  - Removes all existing role tags, adds the new tag, and sets the corresponding LuckPerms group.
  - Roles configurable in `rpessentials-core.toml` under `[Roles]`. Format: `roleId;lpGroup`.
  - Autocompletion of available roles.
  - No recompilation required to add or modify roles.
  - Requires OP level 3.
  - Replaces `commandadmin.js` from KubeJS.

* **`/warn` — Player-First Autocomplete:** `/warn info` and `/warn remove` now accept a player name as the first argument, followed by a warn ID.
  - Autocompletion step 1: only players who have at least one warn are suggested.
  - Autocompletion step 2: only warn IDs belonging to the selected player are suggested, with the reason shown as tooltip.
  - Existing handlers `warnInfo` and `warnRemove` are reused unchanged — only the command tree structure changed.

**Improved**

* **`RpEssentials.onServerTick()`:**
  - Added `WorldBorderManager.tick()` — was missing, zones were completely silent.
  - Added `TempLicenseExpirationManager.tickMidnightSweep()` — was missing, RP license expiration was never triggered.
  - Added `LastConnectionManager.tickAutoUnwhitelist()` — new.
  - Added `CraftingAndArmorRestrictionEventHandler.cleanupCaches()` every 400 ticks — was missing.
  - `tickCounter` increments at the end of the method to ensure all modulo conditions fire correctly on tick 0.

* **`ScheduleConfig`:**
  - Replaced single `openingTime`/`closingTime` pair with per-day blocks.
  - Added `[Death Hours]` and `[HRP Hours]` sections.
  - Welcome sound volume range extended to 10.0 to match other sound configs.
  - All message placeholders unified: `{open}`, `{close}`, `{day}`, `{minutes}`.

* **`RpEssentialsScheduleManager`:**
  - Full rewrite. Now parses a `Map<DayOfWeek, DaySchedule>` at reload.
  - `isServerOpen()` checks the current day against the map.
  - `canPlayerJoin()` resolves and displays the next open day.
  - `getTimeUntilNextEvent()` walks forward up to 7 days to find the next opening.
  - HRP notification logic (`tickHrpNotifications`) is internal to `tick()` — no external call needed.
  - `getSchedules()` exposed for display in `/rpessentials schedule`.
  - `isInSlot()` public utility for cross-midnight time range checking, shared with Death Hours and HRP Hours.

* **`giveLicense()` / `revokeLicense()`:**
  - Added automatic vanilla tag management (`tag <player> add/remove <professionId>`).

**Technical**

* **New Classes:**
  - `NametagConfig` — Server-side nametag config (`rpessentials-nametag.toml`).
  - `SyncNametagDataPacket` — Server→client packet carrying display name, prefix, suffix, profession, isStaff.
  - `ClientNametagCache` — Thread-safe client-side cache for nametag data received via packet.
  - `NametagOcclusionCache` — Per-UUID raycast result cache with 50ms TTL.
  - `NametagFormatter` — Token resolution for nametag format strings.
  - `NametagEventHandler` — `@EventBusSubscriber(Dist.CLIENT)`, handles `RenderNameTagEvent`.

* **Enhanced Classes:**
  - `RpEssentials` — Registers `NametagConfig.SPEC`, fixed `onServerTick()` (see Improved).
  - `RpEssentialsConfig` — Added `[Roles]` section.
  - `ScheduleConfig` — Full rewrite (see Improved).
  - `RpEssentialsScheduleManager` — Full rewrite (see Improved).
  - `ModerationConfig` — Added `[Auto Unwhitelist]` section (`autoUnwhitelistEnabled`, `autoUnwhitelistDays`, `autoUnwhitelistExtraCommands`).
  - `LastConnectionManager` — Added `tickAutoUnwhitelist()` with staff notification and clickable undo button.
  - `RpEssentialsCommands` — Added `/setrole`, `/warn info <player> [id]`, `/warn remove <player> <id>`, `scheduleDay` config setter. Removed `setOpeningTime`/`setClosingTime` (replaced by `setDayTime`/`setDayEnabled`).
  - `NetworkHandler` — Registered `SyncNametagDataPacket`.
  - `NicknameManager` — `setNickname()`/`removeNickname()` now broadcast `SyncNametagDataPacket` to all players.
  - `LicenseManager` — `addLicense()`/`removeLicense()` now broadcast `SyncNametagDataPacket` to all players.
  - `ClientEventHandler` — Resets `ClientNametagCache` and `NametagOcclusionCache` on disconnect.
  - `DeathRPManager` — `isDeathRPEnabled()` now checks Death Hours before applying the global state.
  - `RpEssentialsEventHandler` — Broadcasts `SyncNametagDataPacket` for all online players on login.

* **Data Storage:**
  - No new files. Data directory path changed from `world/data/oneriamod/` to `world/data/rpessentials/` (migration automatic).

* **Removed:**
  - `commandadmin.js` (KubeJS) — replaced by `/rpessentials setrole`.

**Configuration**

* **New File: `rpessentials-nametag.toml`**

| Section | Key | Default | Description |
|---|---|---|---|
| `[Behaviour]` | `enabled` | `false` | Master switch |
| `[Behaviour]` | `hideBehindBlocks` | `true` | Block occlusion raycast |
| `[Obfuscation]` | `obfuscationEnabled` | `true` | Distance obfuscation |
| `[Obfuscation]` | `obfuscationDistance` | `10.0` | Distance threshold (blocks) |
| `[Obfuscation]` | `obfuscationColor` | `&8` | Color of obfuscated name |
| `[Obfuscation]` | `obfuscationLength` | `-1` | Fixed length (-1 = real name length) |
| `[Format]` | `format` | `$prefix$name` | Readable format |
| `[Format]` | `formatObfuscated` | `$obfuscated` | Obfuscated format |
| `[Rendering]` | `renderDistance` | `-1.0` | Max render distance (-1 = vanilla) |
| `[Rendering]` | `showWhileSneaking` | `false` | Show nametag while sneaking |
| `[Staff]` | `staffAlwaysSeeReal` | `true` | Staff bypass all restrictions |

* **New Section: `[Roles]` in `rpessentials-core.toml`**

| Key | Default | Description |
|---|---|---|
| `roles` | `["admin;admin", "modo;modo", "builder;builder", "joueur;joueur"]` | Role definitions (`roleId;lpGroup`) |

* **New Section: `[Auto Unwhitelist]` in `rpessentials-moderation.toml`**

| Key | Default | Description |
|---|---|---|
| `autoUnwhitelistEnabled` | `false` | Enable auto-unwhitelist |
| `autoUnwhitelistDays` | `30` | Days of inactivity threshold |
| `autoUnwhitelistExtraCommands` | `[]` | Extra commands on removal (`{player}`, `{uuid}`) |

* **New Sections in `rpessentials-schedule.toml`**

  - `[Days.MONDAY]` through `[Days.SUNDAY]` — each with `enabled`, `open`, `close`.
  - `[Death Hours]` — `deathHoursEnabled`, `deathHoursSlots`.
  - `[HRP Hours]` — `enableHrpHours`, `hrpToleratedSlots`, `hrpAllowedSlots`, `hrpToleratedMessage`, `hrpAllowedMessage`, `hrpMessageMode`.

**Migration Notes**

* **Fully automatic** — `ConfigMigrator` handles all four migration phases on first launch:
  - Phase 1: `oneriaserverutilities-server.toml` → `config/oneria/oneria-*.toml` (v1/v2).
  - Phase 2: `config/oneria-professions.toml` → `config/oneria/` (v2 root).
  - Phase 3: `config/oneria/oneria-*.toml` → `config/rpessentials/rpessentials-*.toml` (v3.x).
  - Phase 4: `world/data/oneriamod/` → `world/data/rpessentials/` (all versions, triggered on `ServerStartingEvent`).
* All original files are backed up as `.migrated.bak` — nothing is deleted.
* `rpessentials-schedule.toml` is regenerated with new per-day structure. Previous `openingTime`/`closingTime` values are not migrated — set the new per-day fields manually after first launch.
* The new `rpessentials-nametag.toml` is generated automatically with all defaults on first start.
* Clients connecting to a v4.0.0 server must also run v4.0.0 — packet IDs changed with the modid rename.

## [3.2.0] - 2026-03-14

**The mod is now fully translated into English. Every player-facing message is also configurable via a new dedicated config file.**

**Added**

* **Full English Translation:** Every player-facing message in the mod has been translated to English — this was long overdue.
  - All command feedback, warn notifications, private messaging prompts, last connection output, Death RP status, profession restriction messages, whois results, help menu, and player list are now in English.
  - The `"Hors-ligne"` fallback in `/whois`, `"Aucun joueur avec le nickname"`, `"Cliquer pour répondre"`, `"[MP] Vous écrivez à"`, and every other French string have been replaced.
  - The default value of `WARN_JOIN_MESSAGE` in `oneria-moderation.toml` is now in English for new installations.

* **New Config File — `oneria-messages.toml`:** All translated messages are exposed as configurable values.
  - Server admins can customize or re-translate every message without recompiling the mod.
  - Fully supports `§` and `&` color codes in all values.
  - Reloadable at runtime with `/oneria config reload` — no restart required.
  - Generated automatically on first server start under `config/oneria/oneria-messages.toml`.
  - Organized into clear sections: `[System]`, `[Private Messaging]`, `[Warn System]`, `[Last Connection]`, `[Death RP]`, `[Whois]`, `[Player List]`, `[Help]`, `[Profession Restrictions]`.

**Technical**

* **New Classes:**
  - `MessagesConfig` — NeoForge `ModConfigSpec`-based config class exposing all user-facing strings as `ConfigValue<String>`. Includes `get(configValue, replacements...)` and `formatDuration(minutes)` helpers for placeholder substitution and duration formatting.

* **Enhanced Classes:**
  - `RpEssentials` — Registers `MessagesConfig.SPEC` under `oneria/oneria-messages.toml`.
  - `OneriaCommands` — All French hardcoded strings replaced with `MessagesConfig.get(...)` calls across `warnSystemCheck()`, `updateConfigString()`, `updateConfigDouble()`, `showHelp()`, `playerList()`, `whoisCommand()`, `lastConnectionPlayer()`, `lastConnectionList()`, `warnAdd()`, `warnTemp()`, `warnRemove()`, `warnClear()`, `warnPurge()`, `myWarn()`, `displayWarnList()`, `warnInfo()`, `checkLicense()`, `deathRpSetGlobal()`, `deathRpSetPlayer()`, `deathRpResetPlayer()`, `deathRpStatus()`. All `config set deathRp*` labels also anglicised.
  - `OneriaMessagingManager` — `"Cliquer pour répondre"`, `"[MP] Vous écrivez à"`, `"[MP] ... vous écrit"`, `"[MP] Vous n'avez personne à qui répondre."`, `"[MP] Ce joueur n'est plus connecté."` all replaced with `MessagesConfig` calls.
  - `ProfessionRestrictionManager` — French fallback strings in `getCraftBlockedMessage()`, `getBlockBreakBlockedMessage()`, `getItemUseBlockedMessage()`, `getEquipmentBlockedMessage()`, and `getRequiredProfessions()` replaced with `MessagesConfig` calls.
  - `ModerationConfig` — Default value of `WARN_JOIN_MESSAGE` updated to English.

**Configuration**

* **New File: `config/oneria/oneria-messages.toml`**

| Section | Keys |
|---|---|
| `[System]` | `configNotLoaded`, `configUnavailable`, `configNotBuilt`, `configUpdated`, `commandPlayerOnly` |
| `[Private Messaging]` | `hoverReply`, `toSender`, `fromTarget`, `noOneToReply`, `targetOffline`, `consoleTo`, `consoleFrom` |
| `[Warn System]` | `receivedPermanent`, `receivedTemporary`, `notFound`, `removeFailed`, `removedPlayer`, `clearedPlayer`, `systemDisabled`, `systemDisabledConfig`, `listHeader`, `listNone`, `listNoneSelf`, `listNoneStaff`, `purgeDone`, `infoHeader`, label keys, type/tag keys, duration format keys |
| `[Last Connection]` | `disabled`, `playerNotFound`, `noData`, `noDataList`, `online`, `offline`, `unknown`, box label keys, `listHeader` |
| `[Death RP]` | `configUnavailable`, `globalEnabled`, `globalDisabled`, `playerEnabled`, `playerDisabled`, `overrideReset`, status display keys |
| `[Whois]` | `notFound`, `resultsHeader` |
| `[Player List]` | `header` |
| `[Help]` | All help menu line keys |
| `[Profession Restrictions]` | Fallback message keys, `noneAvailable`, `systemNotInit`, `hasLicense`, `noLicense` |

**Migration Notes**

* No breaking changes — fully backward compatible with 3.1.1.
* The new `oneria-messages.toml` file is generated automatically on first start — no manual action required.
* All existing config files (`oneria-core.toml`, `oneria-chat.toml`, etc.) are unchanged.
* Servers already running 3.1.1 will retain their `WARN_JOIN_MESSAGE` French value from their existing `oneria-moderation.toml` — only new installations get the English default. To update, edit the `joinMessage` key in `oneria-moderation.toml` manually.

## [3.1.1] - 2026-03-12

**Added**

* **Death RP System:** New system allowing staff to mark players as subject to permanent death (RP perma-death).
  - When a marked player dies, the vanilla death message is completely suppressed and replaced by a fully configurable custom message broadcast to all online players.
  - Suppression works reliably in both singleplayer and multiplayer via Mixin `@Redirect` on `PlayerList.broadcastSystemMessage()` inside `ServerPlayer.die()` — the player's death itself is never cancelled, only the broadcast.
  - A configurable sound is played to all online players on death.
  - Optional automatic whitelist removal on death.
  - **Global toggle:** `/oneria deathrp enable <true|false>` — enables or disables the system for all players without an individual override. Broadcasts a configurable message and sound to all online players when toggled.
  - **Individual override:** `/oneria deathrp player <player> enable <true|false>` — sets a per-player override, taking priority over the global state. Sends a configurable message and sound to the target player only.
  - **Override reset:** `/oneria deathrp player <player> reset` — removes the individual override; the player then follows the global state again.
  - **Status command:** `/oneria deathrp status` — displays the current global state, whitelist removal setting, and the full list of individual overrides with player names.
  - All four display modes supported for notification messages: `CHAT`, `ACTION_BAR`, `TITLE`, `IMMERSIVE` (with automatic fallback to `ACTION_BAR` if ImmersiveMessages is absent client-side).
  - Separate configurable messages and sounds for global activation vs. deactivation, and for individual activation vs. deactivation.
  - Individual overrides persisted in `world/data/oneriamod/deathrp.json` — `UUID (McUsername)` key format, async save, survives server restarts.
  - All commands require OP level 2.

**Technical**

* **New Classes:**
  - `DeathRPManager` — Core manager: in-memory state, JSON persistence (lazy init, async save, retrocompatible UUID key format), death handling, toggle notifications, sound broadcasting, and `sendMessageToPlayer()` utility with all four display modes.
  - `MixinDeathMessage` — `@Redirect` mixin on `ServerPlayer.die()` intercepting the single `broadcastSystemMessage()` call to suppress the vanilla death message for marked players without affecting the death itself.

* **Enhanced Classes:**
  - `OneriaConfig` — Added 19 new config entries in a dedicated `[DeathRP]` section (global enabled, whitelist remove, death message, death sound, individual toggle messages/modes/sounds, global toggle messages/modes/sounds).
  - `OneriaCommands` — Added `/oneria deathrp` subcommand tree (4 commands). Added `updateConfigString()` and `updateConfigDouble()` helper methods. Added all DeathRP keys to `/oneria config set`.

* **New Mixin:** `MixinDeathMessage` registered in `oneria.mixins.json`.

**Configuration**

* **New Section: `[DeathRP]` in `oneria-core.toml`**

| Clé | Défaut | Description |
|---|---|---|
| `globalEnabled` | `false` | Activation globale du système |
| `whitelistRemove` | `false` | Retirer de la whitelist à la mort |
| `deathMessage` | `"&c[Mort RP] &f%player%..."` | Message de mort (`%player%`, `%realname%`) |
| `deathSound` | `"minecraft:entity.wither.death"` | Son joué à tous à la mort |
| `deathSoundVolume` | `1.0` | Volume du son de mort |
| `deathSoundPitch` | `1.0` | Pitch du son de mort |
| `playerToggle.enableMessage` | `"&6[Mort RP] ..."` | Message d'activation individuelle |
| `playerToggle.enableMessageMode` | `"CHAT"` | Mode d'affichage (CHAT/ACTION_BAR/TITLE/IMMERSIVE) |
| `playerToggle.disableMessage` | `"&6[Mort RP] ..."` | Message de désactivation individuelle |
| `playerToggle.disableMessageMode` | `"CHAT"` | Mode d'affichage |
| `playerToggle.toggleSound` | `"minecraft:block.note_block.pling"` | Son de toggle individuel |
| `globalToggle.enableMessage` | `"&6[Mort RP] ..."` | Message d'activation globale (`%staff%`) |
| `globalToggle.enableMessageMode` | `"CHAT"` | Mode d'affichage |
| `globalToggle.disableMessage` | `"&6[Mort RP] ..."` | Message de désactivation globale (`%staff%`) |
| `globalToggle.disableMessageMode` | `"CHAT"` | Mode d'affichage |
| `globalToggle.globalToggleSound` | `"minecraft:ui.toast.challenge_complete"` | Son de toggle global |

Tous ces paramètres sont modifiables en live via `/oneria config set <clé> <valeur>` sans redémarrage.

**Migration Notes**

* Aucun changement cassant — entièrement rétrocompatible avec 3.1.0.
* Le système est désactivé par défaut (`globalEnabled = false`).
* Aucun joueur n'est marqué au démarrage — les overrides individuels sont vides jusqu'à usage explicite des commandes.

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
  - `RpEssentials` — added 10-minute tick (`% 12000`) to sweep all online players.
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

* **`RpEssentials.onServerTick()` — `server` Variable Scope:**
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
  - `RpEssentials` — fixed `server` scope in `onServerTick()`, added `tickMidnightSweep` call every 1200 ticks.
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

* **Config Split — 5 separate files:** The monolithic `RpEssentials-server.toml` has been split into themed config files under `config/oneria/`:
  - `oneria-core.toml` — Obfuscation, Permissions, WorldBorder & Zones.
  - `oneria-chat.toml` — Chat formatting, timestamps, markdown, join/leave messages.
  - `oneria-schedule.toml` — Schedule system, warnings, welcome message & sound.
  - `oneria-moderation.toml` — Silent commands and teleportation platforms.
  - `oneria-professions.toml` — Profession definitions and restrictions (moved from `config/` root to `config/oneria/`).

* **ConfigMigrator — Automatic migration on first launch:**
  - Detects the legacy `RpEssentials-server.toml` on startup.
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
  - `RpEssentials` — Registers 5 configs with explicit `oneria/` paths, calls `ConfigMigrator.migrateIfNeeded()` before registration.
  - `ProfessionRestrictionManager` — ConcurrentHashMap, regex cache, UUID-based exemption check.
  - `NicknameManager` — Async I/O via CompletableFuture.
  - `CraftingAndArmorRestrictionEventHandler` — Tick-based equipment check filtering.
  - `ClientProfessionRestrictions` / `ProfessionSyncHelper` — Deduplicated via OneriaPatternUtils.

* **Data Storage:**
  - `world/data/oneriamod/license-audit.json` — Append-only audit log, JSON array of `AuditEntry` objects.
  - `world/data/oneriamod/licenses-temp.json` — RP license registry, JSON array of `TempLicenseEntry` objects.

**Migration Notes**

* **Fully automatic** — `ConfigMigrator` handles everything on first launch. No manual action required.
* Legacy `RpEssentials-server.toml` is backed up as `RpEssentials-server.toml.migrated.bak` in the same `config/` folder.
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
  - New item: `RpEssentials:license` (max stack size: 1).
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
* `RpEssentials` - Integration with profession system, cache cleanup tick.
* `NetworkHandler` - Registration of profession sync packet.
* `MixinServerCommonPacketListenerImpl` - Always visible list and spectator blur logic.

**Data Storage**

* New file: `world/data/oneriamod/licenses.json` - Stores player licenses with UUID keys.
* Pretty-printed JSON format for manual editing if needed.
* Automatic directory creation and error handling.

**Network Protocol**

* New packet: `RpEssentials:sync_profession_restrictions`.
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
  - Enhanced error handling in `RpEssentials` for better stability.

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
  - `RpEssentials` - Enhanced LuckPerms error handling with `NoClassDefFoundError` catching, added server-side tick protection.
  - `OneriaConfig` - Added `HIDE_NAMETAGS` and `SHOW_NAMETAG_PREFIX_SUFFIX` configuration options.
  - `OneriaCommands` - Added `/setplatform` command and nametag configuration commands.
  - `MixinEntity` - Enhanced to display nicknames with optional prefix/suffix above heads.

* **Network Protocol:**
  - Custom packet payload type: `RpEssentials:hide_nametags`.
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
  - `RpEssentials` - Enhanced LuckPerms error handling, added config loading listener.
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
  - `RpEssentials` - Added `getPlayerSuffix()` method for LuckPerms suffix retrieval.
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