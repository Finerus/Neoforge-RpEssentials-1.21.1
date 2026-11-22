package net.rp.rpessentials.client.skin;

/**
 * Screen with tabs: its sprites are looked up in {@code <screen>/<tab>/} before {@code <screen>/}.
 * See {@link GuiSkin}.
 */
public interface SkinScope {

    /** Folder of the open tab (snake_case), or null / "" for none. */
    String skinTab();
}
