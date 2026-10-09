package net.rp.rpessentials.profession;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.rp.rpessentials.RpEssentialsItems;
import net.rp.rpessentials.config.MessagesConfig;
import net.rp.rpessentials.identity.NicknameManager;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility class shared by RpEssentialsCommands.giveLicense() and SetPlayerProfilePacket
 * to create and give a physical license item to a player.
 *
 * Centralizing the logic here ensures both entry points produce identical items
 * and perform the same side effects (tag, cache invalidation, sync, audit log).
 */
public class LicenseHelper {

    private LicenseHelper() {}

    /**
     * Creates a physical permanent license item and gives it to the target player.
     * Does NOT call LicenseManager.addLicense() — the caller is responsible for that
     * so the method can be used for both first-time grants and re-issues.
     *
     * Side-effects performed here:
     *  - ItemStack creation and inventory insertion (drop if full)
     *  - Vanilla scoreboard tag add
     *  - Cache invalidation + client restriction sync
     *
     * @param server      the running server instance
     * @param staff       the admin performing the action (null = console/system)
     * @param target      the player receiving the item
     * @param professionId the profession id (must exist in ProfessionRestrictionManager)
     * @return true if the profession data was found and the item was created, false otherwise
     */
    public static boolean giveLicenseItem(MinecraftServer server, ServerPlayer staff,
                                          ServerPlayer target, String professionId) {
        return giveLicenseItem(server, staff, target, professionId, null);
    }

    public static boolean giveLicenseItem(MinecraftServer server, ServerPlayer staff,
                                          ServerPlayer target, String professionId, String expiryDate) {
        ProfessionRestrictionManager.ProfessionData profData =
                ProfessionRestrictionManager.getProfessionData(professionId);
        if (profData == null) return false;

        String displayName = NicknameManager.getDisplayName(target);
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        // ── Build the ItemStack ───────────────────────────────────────────────
        ItemStack license = new ItemStack(RpEssentialsItems.LICENSE.get());
        String colorPrefix = profData.colorCode.replace("&", "§");
        license.set(DataComponents.CUSTOM_NAME,
                net.rp.rpessentials.ColorHelper.parseColors(
                        colorPrefix + MessagesConfig.get(MessagesConfig.LICENSE_ITEM_NAME) + profData.displayName));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_LORE_ISSUED_TO, "player", displayName)));
        lore.add(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_LORE_DATE, "date", dateStr)));
        if (expiryDate != null) {
            lore.add(Component.literal(MessagesConfig.get(MessagesConfig.LICENSE_LORE_VALID_UNTIL, "date", expiryDate)));
        }
        license.set(DataComponents.LORE, new ItemLore(lore));

        CompoundTag tag = new CompoundTag();
        tag.putString("professionId", professionId);
        tag.putString("holderName", displayName);
        tag.putString("holderUuid", target.getUUID().toString());
        tag.putString("issueDate", dateStr);
        if (expiryDate != null) tag.putString("expiryDate", expiryDate);
        license.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));

        // ── Give to player ────────────────────────────────────────────────────
        if (!target.getInventory().add(license)) {
            target.drop(license, false);
        }

        if (!ProfessionRestrictionManager.isReservedTag(professionId)) {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput().withPermission(4),
                    "tag " + target.getName().getString() + " add " + professionId);
        }

        // ── Cache + client sync ───────────────────────────────────────────────
        ProfessionRestrictionManager.invalidatePlayerCache(target.getUUID());
        ProfessionSyncHelper.syncToPlayer(target);
        return true;
    }
}