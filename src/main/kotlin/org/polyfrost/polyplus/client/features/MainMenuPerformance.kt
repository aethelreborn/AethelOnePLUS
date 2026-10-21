package org.polyfrost.polyplus.client.features

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import org.polyfrost.polyplus.client.PolyPlusMainMenuConfig
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen
import org.polyfrost.polyplus.client.utils.ClientPlatform

/**
 * Keeps the main menu from asking for frames the machine cannot deliver.
 *
 * The menu frame cap is a target, not a promise: on a weak laptop the menu asks
 * for a hundred and twenty frames a second and gets twenty, which leaves the
 * whole system busy for a menu that scrolls anyway. When that happens the cap
 * walks down until the machine keeps up, and walks back up when it copes again.
 */
object MainMenuPerformance {
    private val logger = LogManager.getLogger("PolyPlus/MainMenuPerformance")

    /** The frame caps the menu walks through, from the fastest to the slowest. */
    internal val CAPS = listOf(120, 60, 30, 20, 15)

    private const val WINDOW_NANOS = 5_000_000_000L
    private const val DIAGNOSE_WINDOWS = 2
    private const val SHORT_OF_TARGET = 0.9
    private const val ABOVE_TARGET = 1.05

    private var windowStartNanos = 0L
    private var frames = 0
    private var windows = 0
    private var learnedCap = 0

    fun initialize() {
        eventHandler { _: FramebufferRenderEvent.End -> onFrame() }
    }

    /** The cap the menu should use, which is never above the one the user asked for. */
    fun cap(base: Int): Int = if (learnedCap in 1 until base) learnedCap else base

    /**
     * The cap the menu should use after a window of [averageFps] frames.
     *
     * [current] is the cap in use and [base] the one the user asked for. A cap of
     * zero means the menu runs at the cap the user asked for.
     */
    internal fun nextCap(current: Int, averageFps: Int, base: Int): Int {
        if (averageFps <= 0 || current <= 0) return current
        if (averageFps < current * SHORT_OF_TARGET) {
            return CAPS.filter { it < current }.maxOrNull() ?: current
        }
        if (averageFps < current * ABOVE_TARGET) return current
        val higher = CAPS.filter { it > current }.minOrNull() ?: return current
        return if (higher > base) 0 else higher
    }

    private fun onWindow(averageFps: Int, base: Int) {
        windows++
        // the menu is the first thing a player sees, so a slow one is worth reporting
        if (windows >= DIAGNOSE_WINDOWS) {
            PerformanceMode.report(averageFps, "in the main menu")
        }
        val current = cap(base)
        val next = nextCap(current, averageFps, base)
        if (next == current) return
        learnedCap = next
        logger.info(
            "The main menu managed {} FPS with a cap of {}, the cap is now {}",
            averageFps,
            current,
            if (next == 0) base else next,
        )
    }

    private fun onFrame() {
        if (!onMainMenu()) {
            reset()
            return
        }
        val now = System.nanoTime()
        if (windowStartNanos == 0L) windowStartNanos = now
        frames++
        if (now - windowStartNanos < WINDOW_NANOS) return
        val averageFps = (frames * 1_000_000_000L / (now - windowStartNanos)).toInt()
        frames = 0
        windowStartNanos = now
        onWindow(averageFps, PolyPlusMainMenuConfig.baseMainMenuFpsLimit())
    }

    private fun onMainMenu(): Boolean {
        val mc = Minecraft.getInstance()
        if (mc.player != null) return false
        return ClientPlatform.currentScreen() is PolyPlusMainMenuScreen
    }

    private fun reset() {
        windowStartNanos = 0L
        frames = 0
    }
}
