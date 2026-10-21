package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.polyfrost.polyplus.client.FrameRateCap
import org.polyfrost.polyplus.client.features.PerformanceMode
import org.polyfrost.polyplus.compat.PolydiumDefaults
import java.nio.file.Path
import kotlin.io.path.writeText

class PerformanceModeTest {

    @Test
    fun `a renderer that draws on the processor is spotted`() {
        assertTrue(PerformanceMode.isSoftwareRenderer("llvmpipe (LLVM 15.0.7, 256 bits)"))
        assertTrue(PerformanceMode.isSoftwareRenderer("Mesa/X.org, OpenGL 4.6, Mesa 23.0, Lavapipe"))
        assertTrue(PerformanceMode.isSoftwareRenderer("ANGLE (Google, Vulkan 1.3.0 (SwiftShader Device (Subzero))"))
        assertTrue(PerformanceMode.isSoftwareRenderer("Microsoft Basic Render Driver"))
    }

    @Test
    fun `a real graphics card is left alone`() {
        assertFalse(PerformanceMode.isSoftwareRenderer("ANGLE (Intel, Intel(R) UHD Graphics 620, D3D11)"))
        assertFalse(PerformanceMode.isSoftwareRenderer("NVIDIA GeForce RTX 3060/PCIe/SSE2"))
        assertFalse(PerformanceMode.isSoftwareRenderer("AMD Radeon RX 6600 (radeonsi, navi17)"))
        assertFalse(PerformanceMode.isSoftwareRenderer(null))
    }

    @Test
    fun `a machine without a graphics card is told what is wrong`() {
        val findings = PerformanceMode.diagnose("llvmpipe (LLVM 15.0.7)", averageFps = 3, frameCap = 60, threshold = 24)

        assertEquals("software-renderer", findings.first().id)
        assertTrue(findings.any { it.id == "low-fps" })
    }

    @Test
    fun `an uncapped frame rate is called out`() {
        val findings = PerformanceMode.diagnose("NVIDIA GeForce RTX 3060", averageFps = 120, frameCap = 260, threshold = 24)

        assertEquals(listOf("uncapped"), findings.map { it.id })
    }

    @Test
    fun `a capped and healthy game gets no findings`() {
        val findings = PerformanceMode.diagnose("NVIDIA GeForce RTX 3060", averageFps = 120, frameCap = 60, threshold = 24)

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `a frame rate that was never measured is not a finding`() {
        val findings = PerformanceMode.diagnose("NVIDIA GeForce RTX 3060", averageFps = 0, frameCap = 60, threshold = 24)

        assertTrue(findings.isEmpty(), findings.toString())
    }

    @Test
    fun `the low-end mode never raises the distances`() {
        assertEquals(PerformanceMode.LOW_END_RENDER_DISTANCE, PerformanceMode.lowEndRenderDistance(12))
        assertEquals(4, PerformanceMode.lowEndRenderDistance(4))
        assertEquals(PerformanceMode.LOW_END_RENDER_DISTANCE, PerformanceMode.lowEndRenderDistance(0))
        assertEquals(PerformanceMode.LOW_END_SIMULATION_DISTANCE, PerformanceMode.lowEndSimulationDistance(12))
        assertEquals(4, PerformanceMode.lowEndSimulationDistance(4))
    }

    @Test
    fun `the frame rate cap the user picked is the one the game gets`() {
        assertEquals(260, PerformanceMode.capValue(FrameRateCap.UNLIMITED, refreshRate = 144))
        assertEquals(144, PerformanceMode.capValue(FrameRateCap.REFRESH_RATE, refreshRate = 144))
        assertEquals(60, PerformanceMode.capValue(FrameRateCap.REFRESH_RATE, refreshRate = 0))
        assertEquals(120, PerformanceMode.capValue(FrameRateCap.FPS_120, refreshRate = 144))
        assertEquals(60, PerformanceMode.capValue(FrameRateCap.FPS_60, refreshRate = 144))
        assertEquals(30, PerformanceMode.capValue(FrameRateCap.FPS_30, refreshRate = 144))
    }

    @Test
    fun `the low-end mode is read from the settings file`(@TempDir configDir: Path) {
        configDir.resolve("polyplus-performance.json").writeText("""{"lowEndMode": true}""")
        assertTrue(PolydiumDefaults.lowEndEnabled(configDir))
    }

    @Test
    fun `a machine without the low-end mode is left alone`(@TempDir configDir: Path) {
        configDir.resolve("polyplus-performance.json").writeText("""{"lowEndMode": false}""")
        assertFalse(PolydiumDefaults.lowEndEnabled(configDir))
    }

    @Test
    fun `a missing settings file means the low-end mode is off`(@TempDir configDir: Path) {
        assertFalse(PolydiumDefaults.lowEndEnabled(configDir))
    }
}
