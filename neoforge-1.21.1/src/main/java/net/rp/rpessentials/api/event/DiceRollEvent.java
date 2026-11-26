package net.rp.rpessentials.api.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
//#if forge
//? import net.minecraftforge.eventbus.api.Cancelable;
//#else
import net.neoforged.bus.api.ICancellableEvent;
//#endif
import net.rp.rpessentials.gameplay.DiceManager;

import java.util.List;

/**
 * Posted on NeoForge.EVENT_BUS for every dice roll (/roll, the dice GUI and RpEssentialsAPI.rollDice).
 * Pre: before the result is shown. It can be cancelled (nothing is shown, no cooldown) or the result replaced.
 * Post: after the result was sent, with the players who saw it.
 */
public abstract class DiceRollEvent extends Event {

    private final ServerPlayer player;
    private final DiceManager.DiceType dice;
    private final DiceManager.Roll roll;

    protected DiceRollEvent(ServerPlayer player, DiceManager.DiceType dice, DiceManager.Roll roll) {
        this.player = player;
        this.dice = dice;
        this.roll = roll;
    }

    /**
     * Every detail of the roll: each dice value, the modifier, advantage/disadvantage, secret roll,
     * and the expression typed by the player (for example "2d6+3"). Never null.
     */
    public DiceManager.Roll getRoll() { return roll; }

    /** Expression shown as the dice name ("d20", "2d6+3", or the configured dice name). */
    public String getExpression() { return roll.request().expression(); }

    /** True for a roll only shown to the roller and the staff. */
    public boolean isSecret() { return roll.request().secret(); }

    public DiceManager.Mode getMode() { return roll.request().mode(); }

    public ServerPlayer getPlayer() { return player; }

    public DiceManager.DiceType getDice() { return dice; }

    public String getDiceName() { return dice.name(); }

    /** True for a numeric dice (d6, d20...), false for a dice with custom faces. */
    public boolean isNumeric() { return !dice.hasCustomFaces(); }

    /** Highest value of a numeric dice, 0 for custom faces. */
    public int getMaxValue() { return dice.maxValue(); }

    /** Faces of a custom dice, empty for a numeric dice. */
    public List<String> getFaces() { return dice.hasCustomFaces() ? dice.customFaces() : List.of(); }

    //#if forge
    //? public abstract String getRollResult();
    //#else
    public abstract String getResult();
    //#endif

    /** The result as a number, or null when it is not a number (custom face). */
    public Integer getNumericResult() {
        try {
            //#if forge
            //? return Integer.parseInt(getRollResult().trim());
            //#else
            return Integer.parseInt(getResult().trim());
            //#endif
        } catch (NumberFormatException e) {
            return null;
        }
    }

    //#if forge
    //? @Cancelable
//?
    //? public static class Pre extends DiceRollEvent {
    //#else
    public static class Pre extends DiceRollEvent implements ICancellableEvent {
    //#endif
        private String result;

        public Pre(ServerPlayer player, DiceManager.DiceType dice, String result, DiceManager.Roll roll) {
            super(player, dice, roll);
            this.result = result;
        }

        @Override
        //#if forge
        //? public String getRollResult() { return result; }
        //#else
        public String getResult() { return result; }
        //#endif

        /** Replaces the result shown to the players (the details are then hidden). Null is ignored. */
        //#if forge
        //? public void setRollResult(String result) {
        //#else
        public void setResult(String result) {
        //#endif
            if (result != null) this.result = result;
        }
    }

    public static class Post extends DiceRollEvent {
        private final String result;
        private final List<ServerPlayer> audience;

        public Post(ServerPlayer player, DiceManager.DiceType dice, String result, List<ServerPlayer> audience,
                    DiceManager.Roll roll) {
            super(player, dice, roll);
            this.result = result;
            this.audience = List.copyOf(audience);
        }

        @Override
        //#if forge
        //? public String getRollResult() { return result; }
        //#else
        public String getResult() { return result; }
        //#endif

        /** Players who received the roll message (the roller included), staff spies excluded. */
        public List<ServerPlayer> getAudience() { return audience; }
    }
}
