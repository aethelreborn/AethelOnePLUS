package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.features.MainMenuPerformance

class MainMenuPerformanceTest {

    @Test
    fun `a menu that keeps up keeps the cap`() {
        assertEquals(120, MainMenuPerformance.nextCap(current = 120, averageFps = 120, base = 120))
    }

    @Test
    fun `a menu that cannot keep up gets a lower cap`() {
        assertEquals(60, MainMenuPerformance.nextCap(current = 120, averageFps = 40, base = 120))
        assertEquals(30, MainMenuPerformance.nextCap(current = 60, averageFps = 40, base = 120))
        assertEquals(20, MainMenuPerformance.nextCap(current = 30, averageFps = 10, base = 120))
    }

    @Test
    fun `the slowest cap is the end of the road`() {
        assertEquals(15, MainMenuPerformance.nextCap(current = 15, averageFps = 5, base = 120))
    }

    @Test
    fun `the cap walks back up when the machine copes again`() {
        assertEquals(120, MainMenuPerformance.nextCap(current = 60, averageFps = 115, base = 120))
    }

    @Test
    fun `the cap never walks above the one the user asked for`() {
        assertEquals(0, MainMenuPerformance.nextCap(current = 60, averageFps = 115, base = 60))
    }

    @Test
    fun `a frame rate that was never measured changes nothing`() {
        assertEquals(120, MainMenuPerformance.nextCap(current = 120, averageFps = 0, base = 120))
    }
}
