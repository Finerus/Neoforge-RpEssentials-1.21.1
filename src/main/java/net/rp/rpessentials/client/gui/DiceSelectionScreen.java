package net.rp.rpessentials.client.gui;

import net.rp.rpessentials.client.skin.SkinnedEditBox;
import net.rp.rpessentials.client.skin.SkinnedButton;
import net.rp.rpessentials.client.skin.GuiSkin;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.rp.rpessentials.gameplay.DiceManager;
import net.rp.rpessentials.network.DiceRollPacket;

import java.util.List;

/**
 * Petit écran de sélection du type de dé.
 * S'ouvre via la touche configurable dans Options > Contrôles.
 */
@OnlyIn(Dist.CLIENT)
public class DiceSelectionScreen extends Screen {

    private static final int BTN_W = 80;
    private static final int BTN_H = 20;
    private static final int COLS  = 3;
    private static final int PAD   = 6;
    private List<DiceManager.DiceType> dice = List.of();

    public DiceSelectionScreen() {
        super(Component.translatable("rpessentials.gui.dice.title"));
    }

    @Override
    protected void init() {
        dice = DiceManager.getAvailableDice();
        if (dice.isEmpty()) {
            onClose();
            return;
        }

        addRenderableWidget(GuiSkin.toggleButton(this.width, this::rebuildWidgets));
        int totalW  = COLS * (BTN_W + PAD) - PAD;
        int rows    = (dice.size() + COLS - 1) / COLS;
        int totalH  = rows * (BTN_H + PAD) - PAD;
        int startX  = (this.width  - totalW) / 2;
        int startY  = (this.height - totalH) / 2;

        for (int i = 0; i < dice.size(); i++) {
            final DiceManager.DiceType d = dice.get(i);
            int col = i % COLS;
            int row = i / COLS;
            int x   = startX + col * (BTN_W + PAD);
            int y   = startY + row * (BTN_H + PAD);

            String label = d.hasCustomFaces()
                    ? "§e" + d.name() + " §8(" + String.join("/", d.customFaces()) + ")"
                    : "§e" + d.name() + " §8(1-" + d.maxValue() + ")";

            addRenderableWidget(Button.builder(Component.literal(label), btn -> {
                PacketDistributor.sendToServer(new DiceRollPacket(d.name()));
                onClose();
            }).pos(x, y).size(BTN_W, BTN_H).build(SkinnedButton::new));
        }

        addRenderableWidget(Button.builder(Component.translatable("rpessentials.gui.dice.cancel"), btn -> onClose())
                .pos((this.width - 60) / 2, startY + totalH + PAD + 4)
                .size(60, BTN_H).build(SkinnedButton::new));
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        GuiSkin.background(g, this.width, this.height, 0x88000000);

        int rows = (dice.size() + COLS - 1) / COLS;
        g.drawCenteredString(this.font, this.title,
                this.width / 2,
                (this.height / 2) - (rows * (BTN_H + PAD)) / 2 - 16,
                0xFFD700);

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partial) {}

    @Override
    public boolean isPauseScreen() { return false; }
}