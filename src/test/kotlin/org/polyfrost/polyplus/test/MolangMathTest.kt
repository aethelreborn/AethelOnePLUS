package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.bedrock.molang.MolangContext
import org.polyfrost.polyplus.client.bedrock.molang.MolangEvaluator
import org.polyfrost.polyplus.client.bedrock.molang.MolangExpr

class MolangMathTest {

    private fun call(name: String, vararg args: Double, context: MolangContext = MolangContext(0f)): Double {
        val expr = MolangExpr.Call(
            callee = MolangExpr.Member("math", listOf(name)),
            args = args.map { MolangExpr.Number(it) },
        )
        return MolangEvaluator.eval(expr, context)
    }

    @Test
    fun `hermite_blend is zero at start and one at end`() {
        assertEquals(0.0, call("hermite_blend", 0.0, 10.0, 0.0), 1e-9)
        assertEquals(1.0, call("hermite_blend", 0.0, 10.0, 10.0), 1e-9)
        // Midpoint is 0.5, not 0.5 linear: smoothstep(0.5) = 0.5 * 0.5 * (3 - 1) = 0.5
        assertEquals(0.5, call("hermite_blend", 0.0, 10.0, 5.0), 1e-9)
    }

    @Test
    fun `hermite_blend clamps outside the range`() {
        assertEquals(0.0, call("hermite_blend", 0.0, 10.0, -5.0), 1e-9)
        assertEquals(1.0, call("hermite_blend", 0.0, 10.0, 20.0), 1e-9)
    }

    @Test
    fun `hermite_blend eases in and out`() {
        // At 25% of the range, smoothstep is 0.15625, not 0.25 (linear).
        assertEquals(0.15625, call("hermite_blend", 0.0, 100.0, 25.0), 1e-9)
        // At 75%, smoothstep is 0.84375, not 0.75.
        assertEquals(0.84375, call("hermite_blend", 0.0, 100.0, 75.0), 1e-9)
    }

    @Test
    fun `die_roll_integer stays within bounds`() {
        val ctx = MolangContext(0f, randomState = 42L)
        repeat(100) {
            val value = call("die_roll_integer", 3.0, 6.0, 1.0, context = ctx)
            assertTrue(value in 3.0..18.0, "die_roll_integer(3,6,1)=$value out of [3,18]")
        }
    }

    @Test
    fun `die_roll_integer is deterministic for the same seed`() {
        val a = call("die_roll_integer", 2.0, 6.0, 3.0, context = MolangContext(0f, randomState = 7L))
        val b = call("die_roll_integer", 2.0, 6.0, 3.0, context = MolangContext(0f, randomState = 7L))
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun `die_roll is the average of round sums`() {
        val ctx = MolangContext(0f, randomState = 99L)
        repeat(50) {
            val value = call("die_roll", 4.0, 8.0, 2.0, context = ctx)
            // Sum of 8 rolls in [1,8] is [8,64], average is [1,8].
            assertTrue(value in 1.0..8.0, "die_roll(4,8,2)=$value out of [1,8]")
        }
    }

    @Test
    fun `random stays within the requested range`() {
        val ctx = MolangContext(0f, randomState = 123L)
        repeat(100) {
            val value = call("random", 5.0, 15.0, context = ctx)
            assertTrue(value >= 5.0 && value < 15.0, "random(5,15)=$value out of [5,15)")
        }
    }

    @Test
    fun `random_integer is inclusive and deterministic`() {
        val a = call("random_integer", 1.0, 10.0, context = MolangContext(0f, randomState = 55L))
        val b = call("random_integer", 1.0, 10.0, context = MolangContext(0f, randomState = 55L))
        assertEquals(a, b, 1e-9)
        assertTrue(a in 1.0..10.0, "random_integer(1,10)=$a out of [1,10]")
    }

    @Test
    fun `random_integer returns from when to is not greater`() {
        assertEquals(3.0, call("random_integer", 3.0, 3.0, context = MolangContext(0f, randomState = 1L)), 1e-9)
        assertEquals(5.0, call("random_integer", 5.0, 2.0, context = MolangContext(0f, randomState = 1L)), 1e-9)
    }

    @Test
    fun `die_roll with zero total returns zero`() {
        assertEquals(0.0, call("die_roll", 0.0, 6.0, 1.0, context = MolangContext(0f, randomState = 1L)), 1e-9)
        assertEquals(0.0, call("die_roll_integer", 0.0, 6.0, 1.0, context = MolangContext(0f, randomState = 1L)), 1e-9)
    }
}
