package net.rp.rpessentials.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Same behaviour for every save / close button of the RpEssentials screens:
 * a save button is greyed out when there is nothing to save, and bold when there is;
 * closing a screen with unsaved changes asks for a second click.
 */
final class SaveUi {

    private SaveUi() {}

    /** Updates a save button: active and bold only when {@code dirty}. Call it from render() so it follows typing. */
    static void refresh(Button button, String label, boolean dirty) {
        if (button == null) return;
        button.active = dirty;
        String text = dirty ? "§l" + label : label;
        // A literal keeps its § codes in getString(): only replace the message when it really changes.
        if (!button.getMessage().getString().equals(text)) button.setMessage(Component.literal(text));
    }

    /** Label of a Close button: "Discard?" in red after a first click with unsaved changes. */
    static String closeLabel(boolean confirm, String closeKey) {
        return confirm ? "§c" + I18n.get("rpessentials.gui.unsaved.discard") : I18n.get(closeKey);
    }

    /** Tooltip of a Close button waiting for confirmation (null otherwise). */
    static Tooltip closeTooltip(boolean confirm) {
        return confirm ? Tooltip.create(Component.translatable("rpessentials.gui.unsaved.discard_tooltip")) : null;
    }
}
