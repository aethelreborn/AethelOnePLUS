package org.polyfrost.polyplus.client

import org.polyfrost.oneconfig.api.config.v1.Config
import org.polyfrost.oneconfig.api.config.v1.annotations.Dropdown
import org.polyfrost.oneconfig.api.config.v1.annotations.Include
import org.polyfrost.oneconfig.api.config.v1.annotations.Slider
import org.polyfrost.oneconfig.api.config.v1.annotations.Switch
import org.polyfrost.polyplus.PolyPlusConstants
import org.polyfrost.polyplus.client.features.PerformanceMode

/**
 * The performance page of the OneClient settings.
 *
 * The performance mixins live in Polydium, the performance mod that ships inside
 * the PolyPlus jar. These settings are the part PolyPlus owns: noticing that the
 * frame rate is bad, working out why, and cutting the work the game asks for
 * when the machine cannot keep up.
 */
object PolyPlusPerformanceConfig : Config(
    "${PolyPlusConstants.ID}-performance.json",
    "PolyPlus Performance",
    Category.PERFORMANCE,
) {
    @JvmStatic
    @Switch(
        title = "Low-End Mode",
        description = "Cut the work the game asks for on a weak machine: the frame rate is capped at " +
            "60 FPS, the render and simulation distances shrink, the particles and the entity shadows go and " +
            "the motion blur is turned off. Turn it off to get your video options back.",
        subcategory = "Performance",
    )
    var lowEndMode = false

    @JvmStatic
    @Switch(
        title = "Adaptive Performance",
        description = "Watch the frame rate while you play and cap the game at 30 FPS with the motion blur " +
            "off whenever it stays low. The cap goes away again once the frame rate recovers.",
        subcategory = "Performance",
    )
    var adaptivePerformance = false

    @JvmStatic
    @Slider(
        title = "Low FPS Threshold",
        description = "The frame rate under which the adaptive performance kicks in.",
        min = 10f,
        max = 60f,
        step = 1f,
        subcategory = "Performance",
    )
    var lowFpsThreshold = 24

    @JvmStatic
    @Dropdown(
        title = "Frame Rate Cap",
        description = "The frame rate the game is allowed to reach. Unlimited is the fastest on a strong machine, " +
            "but on a laptop it makes the processor and the graphics card run hot enough to start slowing the " +
            "game down again. The low-end mode overrides this with its own cap.",
        options = ["Unlimited", "Monitor refresh rate", "120 FPS", "60 FPS", "30 FPS"],
        subcategory = "Performance",
    )
    var frameRateCap: FrameRateCap = FrameRateCap.UNLIMITED

    @JvmStatic
    @Switch(
        title = "Performance Notifications",
        description = "Tell you what holds your frame rate back and when the adaptive performance changes " +
            "something.",
        subcategory = "Performance",
    )
    var performanceNotifications = true

    @JvmStatic @Include
    var lowEndApplied = false

    @JvmStatic @Include
    var adaptiveApplied = false

    @JvmStatic @Include
    var savedFrameCap = 0

    @JvmStatic @Include
    var savedRenderDistance = 0

    @JvmStatic @Include
    var savedSimulationDistance = 0

    @JvmStatic @Include
    var savedParticleLevel = -1

    @JvmStatic @Include
    var savedEntityShadows = -1

    @JvmStatic @Include
    var savedMipmapLevels = -1

    @JvmStatic @Include
    var savedGraphicsMode = ""

    /** Whether the frame rate cap was ever picked by hand, so it survives the next launch. */
    @JvmStatic @Include
    var frameRateCapTouched = false

    @JvmStatic @Include
    var adaptiveSavedFrameCap = 0

    init {
        addCallback("lowEndMode") {
            PerformanceMode.onLowEndModeChanged()
        }

        addCallback("adaptivePerformance") {
            PerformanceMode.onAdaptiveChanged()
        }

        addCallback("frameRateCap") {
            PerformanceMode.onFrameRateCapChanged()
        }
    }
}
