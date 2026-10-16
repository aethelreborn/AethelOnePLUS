package org.polyfrost.polyplus.test

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.polyfrost.polyplus.client.bedrock.molang.MolangContext
import org.polyfrost.polyplus.client.bedrock.molang.MolangEvaluator
import org.polyfrost.polyplus.client.bedrock.molang.MolangExpr
import org.polyfrost.polyplus.client.bedrock.molang.MolangParser
import org.polyfrost.polyplus.client.render.PlayerRenderContext
import kotlin.math.floor

class MolangMathTest {

    private fun call(name: String, vararg args: Double, context: MolangContext = MolangContext(0f)): Double {
        val expr = MolangExpr.Call(
            callee = MolangExpr.Member("math", listOf(name)),
            args = args.map { MolangExpr.Number(it) },
        )
        return MolangEvaluator.eval(expr, context)
    }

    @Test
    fun `hermite_blend eases a zero to one value`() {
        assertEquals(0.0, call("hermite_blend", 0.0), 1e-9)
        assertEquals(1.0, call("hermite_blend", 1.0), 1e-9)
        // smoothstep(0.25) = 0.25 * 0.25 * (3 - 0.5) = 0.15625, not 0.25 (linear).
        assertEquals(0.15625, call("hermite_blend", 0.25), 1e-9)
        assertEquals(0.5, call("hermite_blend", 0.5), 1e-9)
        assertEquals(0.84375, call("hermite_blend", 0.75), 1e-9)
    }

    @Test
    fun `hermite_blend accepts floats beyond zero to one without clamping`() {
        // The docs say any float is valid input though the curve is meant for
        // [0,1], so 2 bends back to 2 * 2 * (3 - 4) = -4.
        assertEquals(-4.0, call("hermite_blend", 2.0), 1e-9)
        assertEquals(5.0, call("hermite_blend", -1.0), 1e-9)
    }

    @Test
    fun `inverse_lerp recovers the blend factor`() {
        assertEquals(0.5, call("inverse_lerp", 0.0, 10.0, 5.0), 1e-9)
        assertEquals(0.0, call("inverse_lerp", 0.0, 10.0, 0.0), 1e-9)
        assertEquals(1.0, call("inverse_lerp", 0.0, 10.0, 10.0), 1e-9)
        // Value beyond the range answers beyond 0..1 rather than clamping.
        assertEquals(2.0, call("inverse_lerp", 0.0, 10.0, 20.0), 1e-9)
        // Equal ends have no progress to recover.
        assertEquals(0.0, call("inverse_lerp", 4.0, 4.0, 9.0), 1e-9)
    }

    @Test
    fun `die_roll_integer sums whole dice between low and high`() {
        val ctx = MolangContext(0f, randomState = 42L)
        repeat(100) {
            // Like 3d6: three rolls of one to six, summed.
            val value = call("die_roll_integer", 3.0, 1.0, 6.0, context = ctx)
            assertTrue(value in 3.0..18.0, "die_roll_integer(3,1,6)=$value out of [3,18]")
            assertEquals(value, floor(value), "integer variant rolled a fraction: $value")
        }
    }

    @Test
    fun `die_roll_integer is deterministic for the same seed`() {
        val a = call("die_roll_integer", 2.0, 6.0, 3.0, context = MolangContext(0f, randomState = 7L))
        val b = call("die_roll_integer", 2.0, 6.0, 3.0, context = MolangContext(0f, randomState = 7L))
        assertEquals(a, b, 1e-9)
    }

    @Test
    fun `die_roll sums continuous values below high`() {
        val ctx = MolangContext(0f, randomState = 99L)
        repeat(50) {
            // Four values in [2, 3), so the sum is in [8, 12).
            val value = call("die_roll", 4.0, 2.0, 3.0, context = ctx)
            assertTrue(value in 8.0..<12.0, "die_roll(4,2,3)=$value out of [8,12)")
        }
    }

    @Test
    fun `a fractional roll count is a chance of one extra die`() {
        val ctx = MolangContext(0f, randomState = 21L)
        var ones = 0
        repeat(1000) {
            // Landing a one takes two things: the 50% coin for the extra die,
            // then the die itself rolling 1 out of 0..1. That is ~25% of
            // draws, not 50%, and a draw is never anything but 0 or 1.
            val value = call("die_roll_integer", 0.5, 0.0, 1.0, context = ctx)
            assertTrue(value == 0.0 || value == 1.0, "half a die rolled $value")
            if (value == 1.0) ones++
        }
        assertTrue(ones in 150..350, "half a die landed a one $ones times out of 1000, expected roughly 250")
    }

    @Test
    fun `die_roll with no dice is zero`() {
        assertEquals(0.0, call("die_roll", 0.0, 1.0, 6.0), 1e-9)
        assertEquals(0.0, call("die_roll_integer", -3.2, 1.0, 6.0), 1e-9)
    }

    @Test
    fun `random stays within the requested range`() {
        val ctx = MolangContext(0f, randomState = 123L)
        val samples = (1..100).map { call("random", 5.0, 15.0, context = ctx) }
        assertTrue(samples.all { it >= 5.0 && it < 15.0 }, "random(5,15) out of [5,15): $samples")
        // A draw that never reaches the upper half means the generator is
        // stuck in [low, midpoint) rather than sampling the whole range.
        assertTrue(samples.any { it >= 10.0 }, "random(5,15) never reached the upper half")
    }

    @Test
    fun `random_integer is inclusive and deterministic`() {
        val a = call("random_integer", 1.0, 10.0, context = MolangContext(0f, randomState = 55L))
        val b = call("random_integer", 1.0, 10.0, context = MolangContext(0f, randomState = 55L))
        assertEquals(a, b, 1e-9)
        assertTrue(a in 1.0..10.0, "random_integer(1,10)=$a out of [1,10]")
        assertEquals(a, floor(a), "random_integer(1,10)=$a rolled a fraction")
    }

    @Test
    fun `random_integer actually varies across draws`() {
        val ctx = MolangContext(0f, randomState = 55L)
        val seen = mutableSetOf<Double>()
        repeat(200) { seen.add(call("random_integer", 1.0, 10.0, context = ctx)) }
        assertTrue(seen.size > 1, "random_integer always returned ${seen.first()}")
        assertTrue(seen.all { it in 1.0..10.0 }, "random_integer samples outside [1,10]: $seen")
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

    @Test
    fun `inverse trig returns degrees`() {
        assertEquals(90.0, call("asin", 1.0), 1e-9)
        assertEquals(0.0, call("acos", 1.0), 1e-9)
        assertEquals(45.0, call("atan", 1.0), 1e-9)
        assertEquals(45.0, call("atan2", 1.0, 1.0), 1e-9)
        assertEquals(90.0, call("atan2", 1.0, 0.0), 1e-9)
    }

    @Test
    fun `inverse trig coerces out-of-domain input to a real angle`() {
        // Float accumulation can push sin slightly past 1; must not yield NaN.
        assertEquals(90.0, call("asin", 1.0001), 1e-9)
        assertEquals(0.0, call("acos", 1.0001), 1e-9)
        assertEquals(-90.0, call("asin", -1.0001), 1e-9)
    }

    @Test
    fun `tan matches degrees semantics`() {
        assertEquals(1.0, call("tan", 45.0), 1e-9)
        assertEquals(0.0, call("tan", 0.0), 1e-9)
    }

    @Test
    fun `cbrt and exp handle negatives and zero`() {
        assertEquals(2.0, call("cbrt", 8.0), 1e-9)
        assertEquals(-2.0, call("cbrt", -8.0), 1e-9)
        assertEquals(0.0, call("cbrt", 0.0), 1e-9)
        assertEquals(1.0, call("exp", 0.0), 1e-9)
    }

    @Test
    fun `ln and log10 return zero for non-positive input`() {
        assertEquals(0.0, call("ln", 0.0), 1e-9)
        assertEquals(0.0, call("ln", -5.0), 1e-9)
        assertEquals(0.0, call("log10", 0.0), 1e-9)
        assertEquals(0.0, call("log10", -1.0), 1e-9)
        assertEquals(1.0, call("ln", Math.E), 1e-9)
        assertEquals(2.0, call("log10", 100.0), 1e-9)
    }

    @Test
    fun `lerpangle takes the shortest path and wraps into 0 to 360`() {
        // 350 -> 10 is +20 forward through 0, not -340 backward.
        assertEquals(0.0, call("lerpangle", 350.0, 10.0, 0.5), 1e-9)
        assertEquals(350.0, call("lerpangle", 350.0, 10.0, 0.0), 1e-9)
        assertEquals(10.0, call("lerpangle", 350.0, 10.0, 1.0), 1e-9)
        // Result stays in [0, 360) even when start is out of range.
        val wrapped = call("lerpangle", 720.0, 90.0, 0.0)
        assertTrue(wrapped in 0.0..<360.0, "lerpangle wrapped=$wrapped out of [0,360)")
    }

    private fun renderContext(
        headYawDeg: Float = 0f,
        bodyYawDeg: Float = 0f,
        headPitchDeg: Float = 0f,
    ) = PlayerRenderContext(
        ageInTicks = 0f,
        walkAnimationSpeed = 0f,
        isCrouching = false,
        isOnGround = true,
        isInWater = false,
        swimAmount = 0f,
        isInvisible = false,
        headYawDeg = headYawDeg,
        bodyYawDeg = bodyYawDeg,
        headPitchDeg = headPitchDeg,
    )

    @Test
    fun `rotation queries read the render context in degrees`() {
        val context = MolangContext.forAnimation(0f, renderContext = renderContext(90f, 30f, 45f))
        assertEquals(60f, context.query("head_y_rotation"), 1e-6f)
        assertEquals(30f, context.query("body_y_rotation"), 1e-6f)
        assertEquals(45f, context.query("head_x_rotation"), 1e-6f)
    }

    @Test
    fun `rotation queries wrap into their Bedrock ranges`() {
        // Head 350 against body 10 is a -20 degree turn, not +340.
        val context = MolangContext.forAnimation(0f, renderContext = renderContext(350f, 10f))
        assertEquals(-20f, context.query("head_y_rotation"), 1e-6f)
        // Body yaw comes back as 0..360 even past a full turn.
        val spun = MolangContext.forAnimation(0f, renderContext = renderContext(0f, 370f))
        assertEquals(10f, spun.query("body_y_rotation"), 1e-6f)
        val negative = MolangContext.forAnimation(0f, renderContext = renderContext(0f, -30f))
        assertEquals(330f, negative.query("body_y_rotation"), 1e-6f)
    }

    @Test
    fun `query calls with arguments reach the query table`() {
        // query.head_y_rotation(0) used to fall through to 0; the index is a
        // head selector we do not model, so it is ignored.
        val expr = MolangExpr.Call(
            callee = MolangExpr.Member("query", listOf("head_y_rotation")),
            args = listOf(MolangExpr.Number(0.0)),
        )
        val context = MolangContext.forAnimation(0f, renderContext = renderContext(90f, 30f))
        assertEquals(60.0, MolangEvaluator.eval(expr, context), 1e-6)
    }

    @Test
    fun `copy sign takes the sign of the second argument`() {
        assertEquals(-3.0, call("copy_sign", 3.0, -2.0), 1e-9)
        assertEquals(4.0, call("copy_sign", -4.0, 2.0), 1e-9)
        assertEquals(-5.0, call("copy_sign", 5.0, -0.0), 1e-9)
    }

    @Test
    fun `min angle folds degrees into minus 180 until 180`() {
        assertEquals(-170.0, call("min_angle", 190.0), 1e-9)
        assertEquals(170.0, call("min_angle", -190.0), 1e-9)
        assertEquals(90.0, call("min_angle", 90.0), 1e-9)
        // The range is half-open: exactly 180 comes back as -180.
        assertEquals(-180.0, call("min_angle", 180.0), 1e-9)
        assertEquals(-180.0, call("min_angle", 540.0), 1e-9)
    }

    @Test
    fun `math pi is a bare constant`() {
        val expr = MolangExpr.Member("math", listOf("pi"))
        assertEquals(Math.PI, MolangEvaluator.eval(expr, MolangContext(0f)), 1e-12)
    }

    @Test
    fun `null coalescing prefers the default only while the variable is unset`() {
        val context = MolangContext(0f)
        val expr = MolangExpr.Coalesce(
            MolangExpr.Member("variable", listOf("y")),
            MolangExpr.Number(1.5),
        )
        assertEquals(1.5, MolangEvaluator.eval(expr, context), 1e-9)
        context.setVariable("y", 0f)
        // Set to zero still counts as defined, so the default is not used.
        assertEquals(0.0, MolangEvaluator.eval(expr, context), 1e-9)
        context.setVariable("y", 2.5f)
        assertEquals(2.5, MolangEvaluator.eval(expr, context), 1e-9)
    }

    @Test
    fun `null coalescing parses with the lowest precedence`() {
        val context = MolangContext(0f)
        // The right side is the whole additive expression, not just 1.0.
        assertEquals(3.0, MolangEvaluator.eval(MolangParser.parseExpression("variable.y ?? 1.0 + 2.0"), context), 1e-9)
        // A computed left side is always defined, so ?? answers it instead.
        assertEquals(1.0, MolangEvaluator.eval(MolangParser.parseExpression("1.0 + variable.y ?? 2.0"), context), 1e-9)
        // The ternary binds tighter: (1 ? 2 : 3) ?? 4 answers 2.
        assertEquals(2.0, MolangEvaluator.eval(MolangParser.parseExpression("1.0 ? 2.0 : 3.0 ?? 4.0"), context), 1e-9)
        // Both work through parentheses and call arguments, temp root included.
        assertEquals(7.0, MolangEvaluator.eval(MolangParser.parseExpression("math.max((temp.a ?? 7.0), 0.0)"), context), 1e-9)
    }

    private val easeNames = listOf(
        "ease_in_sine", "ease_out_sine", "ease_in_out_sine",
        "ease_in_quad", "ease_out_quad", "ease_in_out_quad",
        "ease_in_cubic", "ease_out_cubic", "ease_in_out_cubic",
        "ease_in_quart", "ease_out_quart", "ease_in_out_quart",
        "ease_in_quint", "ease_out_quint", "ease_in_out_quint",
        "ease_in_expo", "ease_out_expo", "ease_in_out_expo",
        "ease_in_circ", "ease_out_circ", "ease_in_out_circ",
        "ease_in_back", "ease_out_back", "ease_in_out_back",
        "ease_in_elastic", "ease_out_elastic", "ease_in_out_elastic",
        "ease_in_bounce", "ease_out_bounce", "ease_in_out_bounce",
    )

    @Test
    fun `every ease curve maps zero to start and one to end`() {
        for (name in easeNames) {
            assertEquals(2.0, call(name, 2.0, 7.0, 0.0), 1e-9, "$name at t=0")
            assertEquals(7.0, call(name, 2.0, 7.0, 1.0), 1e-9, "$name at t=1")
        }
    }

    @Test
    fun `ease curves match their classic midpoint shapes`() {
        assertEquals(0.5, call("ease_in_out_quad", 0.0, 1.0, 0.5), 1e-9)
        assertEquals(0.875, call("ease_out_cubic", 0.0, 1.0, 0.5), 1e-9)
        assertEquals(0.9375, call("ease_out_quart", 0.0, 1.0, 0.5), 1e-9)
        assertEquals(0.765625, call("ease_out_bounce", 0.0, 1.0, 0.5), 1e-9)
        // Elastic overshoots the end on purpose: 2^-5 * sin(5pi/6) + 1.
        assertEquals(1.015625, call("ease_out_elastic", 0.0, 1.0, 0.5), 1e-9)
        // Back winds up below the start before launching.
        assertEquals(-0.0876975, call("ease_in_back", 0.0, 1.0, 0.5), 1e-6)
        assertEquals(1.0876975, call("ease_out_back", 0.0, 1.0, 0.5), 1e-6)
    }

    @Test
    fun `contained ease curves stay within start and end`() {
        val overshooting = setOf(
            "ease_in_back", "ease_out_back", "ease_in_out_back",
            "ease_in_elastic", "ease_out_elastic", "ease_in_out_elastic",
        )
        for (name in easeNames - overshooting) {
            var t = 0.0
            while (t <= 1.0) {
                val value = call(name, 10.0, 20.0, t)
                assertTrue(value in 10.0..20.0, "$name at t=$t was $value, out of [10,20]")
                t += 0.05
            }
        }
    }
}
