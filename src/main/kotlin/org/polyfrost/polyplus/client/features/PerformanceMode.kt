package org.polyfrost.polyplus.client.features

import net.minecraft.client.Minecraft
import org.apache.logging.log4j.LogManager
import org.polyfrost.oneconfig.api.event.v1.eventHandler
import org.polyfrost.oneconfig.api.event.v1.events.FramebufferRenderEvent
import org.polyfrost.oneconfig.api.notifications.v1.NotificationAction
import org.polyfrost.oneconfig.api.notifications.v1.Notifications
import org.polyfrost.polyplus.client.PolyPlusConfig
import org.polyfrost.polyplus.client.PolyPlusPerformanceConfig
import org.polyfrost.polyplus.client.utils.VanillaOptions

/**
 * Keeps the game playable on machines that cannot keep up.
 *
 * The performance mixins themselves live in Polydium, the performance mod that
 * ships inside the PolyPlus jar. What is left for PolyPlus is the part Polydium
 * cannot do: notice that the frame rate is bad, work out why, and cut the work
 * the game asks for when the machine cannot keep up.
 */
object PerformanceMode {
    private val logger = LogManager.getLogger("PolyPlus/Performance")

    /** The frame rate cap the low-end mode settles on. */
    const val LOW_END_FRAME_CAP = 60

    /** The frame rate cap the adaptive mode drops to while the game is struggling. */
    const val ADAPTIVE_FRAME_CAP = 30

    const val LOW_END_RENDER_DISTANCE = 6
    const val LOW_END_SIMULATION_DISTANCE = 5

    /** The particle level that keeps the world readable without every effect. */
    const val PARTICLES_DECREASED = 1

    /** The cap Minecraft calls unlimited. */
    const val UNLIMITED_FRAME_CAP = 260

    private const val SAMPLE_WINDOW_MS = 5_000L
    private const val LOW_WINDOWS_BEFORE_ADAPT = 2
    private const val DIAGNOSE_WINDOWS = 2
    private const val RECOVER_FACTOR = 2
    private const val NOTIFICATION_DURATION_MS = 60_000f
    private const val NOTIFICATION_TITLE = "OneClient Performance"

    /** The OpenGL constant that holds the name of the renderer in use. */
    private const val GL_RENDERER = 0x1F01

    /** Renderers that mean the game is drawing on the processor. */
    internal val SOFTWARE_RENDERERS = listOf(
        "llvmpipe",
        "lavapipe",
        "softpipe",
        "swiftshader",
        "basic render driver",
        "software rasterizer",
    )

    /** One thing that holds the frame rate back, with what to do about it. */
    data class Finding(val id: String, val title: String, val message: String)

    private var lastFrameNanos = 0L
    private var windowStartNanos = 0L
    private var frames = 0
    private var averageFps = 0
    private var lowWindows = 0
    private var windows = 0
    private var diagnosed = false

    /**
     * True when the game is drawing on the processor instead of the graphics card.
     * That is the one reason a laptop ends up at two or three frames per second.
     */
    internal fun isSoftwareRenderer(renderer: String?): Boolean {
        val text = renderer?.lowercase() ?: return false
        return SOFTWARE_RENDERERS.any { text.contains(it) }
    }

    /** What holds the frame rate back, ordered from the worst to the least bad. */
    internal fun diagnose(renderer: String?, averageFps: Int, frameCap: Int, threshold: Int): List<Finding> {
        val findings = mutableListOf<Finding>()
        if (isSoftwareRenderer(renderer)) {
            findings += Finding(
                "software-renderer",
                "Your graphics card is not being used",
                "Minecraft is drawing with the processor instead of the graphics card (renderer: $renderer). " +
                    "That is why the game runs at single digit frames per second. Update your graphics drivers, " +
                    "and if the Vulkan backend from Polydium is on, switch back to OpenGL in the video settings " +
                    "in case the Vulkan driver is the broken part.",
            )
        }
        if (frameCap >= UNLIMITED_FRAME_CAP) {
            findings += Finding(
                "uncapped",
                "Your frame rate is uncapped",
                "The game is allowed to run at $frameCap frames per second. On a laptop that keeps the processor " +
                    "and the graphics card busy enough to start throttling, which drags the frame rate down " +
                    "again. Capping it at $LOW_END_FRAME_CAP usually gives a steadier and higher frame rate.",
            )
        }
        if (averageFps in 1 until threshold) {
            findings += Finding(
                "low-fps",
                "The game is running at $averageFps FPS",
                "Under $threshold frames per second the game feels unplayable. Turn on the low-end mode in the " +
                    "PolyPlus performance settings to cut the render distance, the particles and the frame rate cap.",
            )
        }
        return findings
    }

    /** The render distance the low-end mode settles on, never raising the current one. */
    internal fun lowEndRenderDistance(current: Int): Int =
        if (current <= 0) LOW_END_RENDER_DISTANCE else minOf(current, LOW_END_RENDER_DISTANCE)

    /** The simulation distance the low-end mode settles on, never raising the current one. */
    internal fun lowEndSimulationDistance(current: Int): Int =
        if (current <= 0) LOW_END_SIMULATION_DISTANCE else minOf(current, LOW_END_SIMULATION_DISTANCE)

    fun initialize() {
        if (PolyPlusPerformanceConfig.lowEndMode) applyLowEndMode()
        eventHandler { _: FramebufferRenderEvent.End -> onFrame() }
    }

    fun onLowEndModeChanged() {
        if (PolyPlusPerformanceConfig.lowEndMode) applyLowEndMode() else restoreLowEndMode()
    }

    fun onAdaptiveChanged() {
        if (!PolyPlusPerformanceConfig.adaptivePerformance) releaseAdaptive()
    }

    private fun onFrame() {
        val mc = Minecraft.getInstance()
        if (mc.player == null) {
            resetWindow()
            return
        }
        val now = System.nanoTime()
        if (lastFrameNanos != 0L) frames++
        lastFrameNanos = now
        if (windowStartNanos == 0L) windowStartNanos = now
        val elapsed = now - windowStartNanos
        if (elapsed < SAMPLE_WINDOW_MS * 1_000_000L) return

        averageFps = if (frames > 0) (frames * 1_000_000_000L / elapsed).toInt() else 0
        frames = 0
        windowStartNanos = now
        onWindow()
    }

    private fun resetWindow() {
        lastFrameNanos = 0L
        windowStartNanos = 0L
        frames = 0
    }

    private fun onWindow() {
        windows++
        diagnoseOnce()
        if (!PolyPlusPerformanceConfig.adaptivePerformance) return

        val threshold = PolyPlusPerformanceConfig.lowFpsThreshold
        if (averageFps in 1 until threshold) {
            lowWindows++
            if (lowWindows >= LOW_WINDOWS_BEFORE_ADAPT) engageAdaptive()
        } else {
            lowWindows = 0
            if (PolyPlusPerformanceConfig.adaptiveApplied && averageFps >= threshold * RECOVER_FACTOR) {
                releaseAdaptive()
            }
        }
    }

    /**
     * Cuts the work the game asks for when the machine cannot keep up: the frame
     * rate is capped, the render and simulation distances shrink, the particles
     * and the entity shadows go and the motion blur is turned off.
     */
    fun applyLowEndMode() {
        val options = Minecraft.getInstance().options ?: return
        if (!PolyPlusPerformanceConfig.lowEndApplied) {
            PolyPlusPerformanceConfig.savedFrameCap =
                VanillaOptions.intOption(options, VanillaOptions.FRAME_RATE_LIMIT, VanillaOptions.FRAME_RATE_LIMIT_LEGACY) ?: 0
            PolyPlusPerformanceConfig.savedRenderDistance =
                VanillaOptions.intOption(options, VanillaOptions.RENDER_DISTANCE) ?: 0
            PolyPlusPerformanceConfig.savedSimulationDistance =
                VanillaOptions.intOption(options, VanillaOptions.SIMULATION_DISTANCE) ?: 0
            PolyPlusPerformanceConfig.savedParticleLevel = VanillaOptions.particleLevel(options) ?: -1
            PolyPlusPerformanceConfig.savedEntityShadows =
                VanillaOptions.boolOption(options, VanillaOptions.ENTITY_SHADOWS)?.let { if (it) 1 else 0 } ?: -1
            PolyPlusPerformanceConfig.savedMipmapLevels =
                VanillaOptions.intOption(options, VanillaOptions.MIPMAP_LEVELS) ?: -1
            PolyPlusPerformanceConfig.lowEndApplied = true
        }

        val renderDistance = VanillaOptions.intOption(options, VanillaOptions.RENDER_DISTANCE)
        if (renderDistance != null) {
            VanillaOptions.setIntOption(options, lowEndRenderDistance(renderDistance), VanillaOptions.RENDER_DISTANCE)
        }
        val simulationDistance = VanillaOptions.intOption(options, VanillaOptions.SIMULATION_DISTANCE)
        if (simulationDistance != null) {
            VanillaOptions.setIntOption(
                options,
                lowEndSimulationDistance(simulationDistance),
                VanillaOptions.SIMULATION_DISTANCE,
            )
        }
        VanillaOptions.setIntOption(
            options,
            LOW_END_FRAME_CAP,
            VanillaOptions.FRAME_RATE_LIMIT,
            VanillaOptions.FRAME_RATE_LIMIT_LEGACY,
        )
        VanillaOptions.setBoolOption(options, false, VanillaOptions.ENTITY_SHADOWS)
        VanillaOptions.setParticleLevel(options, PARTICLES_DECREASED)
        VanillaOptions.setIntOption(options, 0, VanillaOptions.MIPMAP_LEVELS)
        options.save()

        disableBlur()
        PolyPlusPerformanceConfig.save()
        logger.info("Low-end mode is on: frame rate capped at {}", LOW_END_FRAME_CAP)
    }

    /** Puts the video options back the way they were before the low-end mode ran. */
    fun restoreLowEndMode() {
        if (!PolyPlusPerformanceConfig.lowEndApplied) return
        val options = Minecraft.getInstance().options ?: return

        val frameCap = PolyPlusPerformanceConfig.savedFrameCap
        if (frameCap > 0) {
            VanillaOptions.setIntOption(
                options,
                frameCap,
                VanillaOptions.FRAME_RATE_LIMIT,
                VanillaOptions.FRAME_RATE_LIMIT_LEGACY,
            )
        }
        val renderDistance = PolyPlusPerformanceConfig.savedRenderDistance
        if (renderDistance > 0) VanillaOptions.setIntOption(options, renderDistance, VanillaOptions.RENDER_DISTANCE)
        val simulationDistance = PolyPlusPerformanceConfig.savedSimulationDistance
        if (simulationDistance > 0) {
            VanillaOptions.setIntOption(options, simulationDistance, VanillaOptions.SIMULATION_DISTANCE)
        }
        val particleLevel = PolyPlusPerformanceConfig.savedParticleLevel
        if (particleLevel >= 0) VanillaOptions.setParticleLevel(options, particleLevel)
        val entityShadows = PolyPlusPerformanceConfig.savedEntityShadows
        if (entityShadows >= 0) {
            VanillaOptions.setBoolOption(options, entityShadows == 1, VanillaOptions.ENTITY_SHADOWS)
        }
        val mipmapLevels = PolyPlusPerformanceConfig.savedMipmapLevels
        if (mipmapLevels >= 0) VanillaOptions.setIntOption(options, mipmapLevels, VanillaOptions.MIPMAP_LEVELS)
        options.save()

        PolyPlusPerformanceConfig.lowEndApplied = false
        PolyPlusPerformanceConfig.save()
        restoreBlur()
        logger.info("Low-end mode is off, the video options are back")
    }

    /** Caps the frame rate and drops the effects while the game is struggling. */
    private fun engageAdaptive() {
        if (PolyPlusPerformanceConfig.adaptiveApplied) return
        val options = Minecraft.getInstance().options ?: return
        val cap = VanillaOptions.intOption(
            options,
            VanillaOptions.FRAME_RATE_LIMIT,
            VanillaOptions.FRAME_RATE_LIMIT_LEGACY,
        )
        if (cap == null || cap > ADAPTIVE_FRAME_CAP) {
            PolyPlusPerformanceConfig.adaptiveSavedFrameCap = cap ?: 0
            VanillaOptions.setIntOption(
                options,
                ADAPTIVE_FRAME_CAP,
                VanillaOptions.FRAME_RATE_LIMIT,
                VanillaOptions.FRAME_RATE_LIMIT_LEGACY,
            )
            options.save()
        }
        disableBlur()
        PolyPlusPerformanceConfig.adaptiveApplied = true
        PolyPlusPerformanceConfig.save()
        logger.info("The frame rate stayed at {} FPS, capping it at {}", averageFps, ADAPTIVE_FRAME_CAP)
        notifyAdaptive()
    }

    private fun releaseAdaptive() {
        if (!PolyPlusPerformanceConfig.adaptiveApplied) return
        val options = Minecraft.getInstance().options ?: return
        val cap = PolyPlusPerformanceConfig.adaptiveSavedFrameCap
        if (cap > 0) {
            VanillaOptions.setIntOption(
                options,
                cap,
                VanillaOptions.FRAME_RATE_LIMIT,
                VanillaOptions.FRAME_RATE_LIMIT_LEGACY,
            )
            options.save()
        }
        PolyPlusPerformanceConfig.adaptiveApplied = false
        PolyPlusPerformanceConfig.adaptiveSavedFrameCap = 0
        PolyPlusPerformanceConfig.save()
        restoreBlur()
        logger.info("The frame rate recovered, the cap is gone")
    }

    private fun diagnoseOnce() {
        if (diagnosed) return
        // the first seconds in a world are spent loading chunks, so they say nothing about the frame rate
        if (windows < DIAGNOSE_WINDOWS) return
        if (!PolyPlusPerformanceConfig.performanceNotifications) return
        val options = Minecraft.getInstance().options ?: return
        val frameCap = VanillaOptions.intOption(
            options,
            VanillaOptions.FRAME_RATE_LIMIT,
            VanillaOptions.FRAME_RATE_LIMIT_LEGACY,
        ) ?: 0
        val findings = diagnose(
            rendererString(),
            averageFps,
            frameCap,
            PolyPlusPerformanceConfig.lowFpsThreshold,
        )
        if (findings.isEmpty()) {
            diagnosed = true
            return
        }
        diagnosed = true

        val message = findings.joinToString("\n\n") { "${it.title}. ${it.message}" }
        runCatching {
            val builder = Notifications.builder(NOTIFICATION_TITLE, message).duration(NOTIFICATION_DURATION_MS)
            builder.action(
                NotificationAction("Turn on the low-end mode", primary = true, onClick = {
                    PolyPlusPerformanceConfig.lowEndMode = true
                    PolyPlusPerformanceConfig.save()
                    applyLowEndMode()
                }),
            )
            builder.send()
        }.onFailure { logger.warn("Could not show the performance notification", it) }
    }

    private fun notifyAdaptive() {
        if (!PolyPlusPerformanceConfig.performanceNotifications) return
        runCatching {
            val builder = Notifications.builder(
                NOTIFICATION_TITLE,
                "The frame rate stayed at $averageFps FPS, so OneClient capped the game at $ADAPTIVE_FRAME_CAP FPS " +
                    "and turned the motion blur off to keep it playable.",
            ).duration(NOTIFICATION_DURATION_MS)
            builder.action(
                NotificationAction("Undo", onClick = {
                    PolyPlusPerformanceConfig.adaptivePerformance = false
                    PolyPlusPerformanceConfig.save()
                    releaseAdaptive()
                }),
            )
            builder.send()
        }.onFailure { logger.warn("Could not show the adaptive performance notification", it) }
    }

    /** The name of the renderer in use, which says whether the graphics card is used at all. */
    internal fun rendererString(): String? = runCatching {
        val gl = Class.forName("org.lwjgl.opengl.GL11")
        gl.getMethod("glGetString", Integer.TYPE).invoke(null, GL_RENDERER) as? String
    }.getOrNull()

    private fun disableBlur() {
        if (!OnboardingFeatures.polyBlurAvailable) return
        runCatching {
            val instance = Class.forName(OnboardingFeatures.POLYBLUR_CONFIG).getField("INSTANCE").get(null)
            OnboardingFeatures.setBoolean(instance, "setEnabled", false)
            instance.javaClass.getMethod("save").invoke(instance)
        }.onFailure { logger.warn("Could not turn the motion blur off", it) }
    }

    private fun restoreBlur() {
        if (!OnboardingFeatures.polyBlurAvailable) return
        // the low-end mode keeps the blur off, so only the adaptive mode gives it back
        if (PolyPlusPerformanceConfig.lowEndMode) return
        runCatching {
            OnboardingFeatures.applyMotionBlur(
                PolyPlusConfig.onboardingMotionBlurMode,
                PolyPlusConfig.onboardingMotionBlur,
            )
        }.onFailure { logger.warn("Could not put the motion blur back", it) }
    }
}
