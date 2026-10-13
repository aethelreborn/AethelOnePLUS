package org.polyfrost.polyplus.client.social

import net.minecraft.client.Minecraft

/**
 * Keybind handler for "host the world you are currently in".
 *
 * Hosting only makes sense with a loaded world, so presses outside of gameplay are
 * ignored instead of opening the overlay onto a world list it cannot fill.
 */
object HostWorldKeybind {
    fun onKeybindState(pressed: Boolean) {
        if (!pressed) return

        val mc = Minecraft.getInstance()
        // A non-null player already implies a loaded world, so this needs no
        // per-version level accessor (1.8.9 spells it `world`).
        if (mc.player == null) return

        SocialOverlay.openHostCurrentWorld()
    }
}