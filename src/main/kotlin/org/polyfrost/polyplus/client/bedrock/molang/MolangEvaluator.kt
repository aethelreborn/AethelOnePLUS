package org.polyfrost.polyplus.client.bedrock.molang

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object MolangEvaluator {
    fun execute(statements: List<MolangStatement>, context: MolangContext) {
        for (statement in statements) {
            val value = eval(statement.expression, context).toFloat()
            val target = statement.target ?: continue
            writeMember(target, value, context)
        }
    }

    fun eval(expression: MolangExpr, context: MolangContext): Double {
        return when (expression) {
            is MolangExpr.Number -> expression.value
            is MolangExpr.Member -> readMember(expression, context)
            is MolangExpr.Call -> evalCall(expression, context)
            is MolangExpr.Unary -> when (expression.op) {
                MolangExpr.Unary.Op.NEGATE -> -eval(expression.operand, context)
                MolangExpr.Unary.Op.NOT -> if (eval(expression.operand, context) != 0.0) 0.0 else 1.0
            }
            is MolangExpr.Binary -> evalBinary(expression, context)
            is MolangExpr.Ternary -> {
                if (eval(expression.condition, context) != 0.0) {
                    eval(expression.whenTrue, context)
                } else {
                    eval(expression.whenFalse, context)
                }
            }
        }
    }

    private fun evalBinary(expression: MolangExpr.Binary, context: MolangContext): Double {
        val left = eval(expression.left, context)
        val right = eval(expression.right, context)
        return when (expression.op) {
            MolangExpr.Binary.Op.ADD -> left + right
            MolangExpr.Binary.Op.SUB -> left - right
            MolangExpr.Binary.Op.MUL -> left * right
            MolangExpr.Binary.Op.DIV -> if (right == 0.0) 0.0 else left / right
            MolangExpr.Binary.Op.MOD -> flooredMod(left, right)
            MolangExpr.Binary.Op.EQ -> if (left == right) 1.0 else 0.0
            MolangExpr.Binary.Op.NEQ -> if (left != right) 1.0 else 0.0
            MolangExpr.Binary.Op.LT -> if (left < right) 1.0 else 0.0
            MolangExpr.Binary.Op.LTE -> if (left <= right) 1.0 else 0.0
            MolangExpr.Binary.Op.GT -> if (left > right) 1.0 else 0.0
            MolangExpr.Binary.Op.GTE -> if (left >= right) 1.0 else 0.0
            MolangExpr.Binary.Op.AND -> if (left != 0.0 && right != 0.0) 1.0 else 0.0
            MolangExpr.Binary.Op.OR -> if (left != 0.0 || right != 0.0) 1.0 else 0.0
        }
    }

    private fun evalCall(expression: MolangExpr.Call, context: MolangContext): Double {
        val args = expression.args.map { eval(it, context) }

        val name = expression.callee.path.lastOrNull()?.lowercase()
            ?: expression.callee.root.lowercase()

        return when (name) {
            "sin" -> sin(Math.toRadians(args.getOrElse(0) { 0.0 }))
            "cos" -> cos(Math.toRadians(args.getOrElse(0) { 0.0 }))
            "abs" -> abs(args.getOrElse(0) { 0.0 })
            "min" -> min(args.getOrElse(0) { 0.0 }, args.getOrElse(1) { 0.0 })
            "max" -> max(args.getOrElse(0) { 0.0 }, args.getOrElse(1) { 0.0 })
            "clamp" -> {
                val value = args.getOrElse(0) { 0.0 }
                val low = args.getOrElse(1) { 0.0 }
                val high = args.getOrElse(2) { 1.0 }
                min(max(value, low), high)
            }
            "lerp" -> {
                val start = args.getOrElse(0) { 0.0 }
                val end = args.getOrElse(1) { 0.0 }
                val alpha = args.getOrElse(2) { 0.0 }
                start + (end - start) * alpha
            }
            "pow" -> args.getOrElse(0) { 0.0 }.pow(args.getOrElse(1) { 1.0 })
            "sqrt" -> sqrt(args.getOrElse(0) { 0.0 })
            "floor" -> floor(args.getOrElse(0) { 0.0 })
            "ceil" -> ceil(args.getOrElse(0) { 0.0 })
            "mod" -> {
                // Molang's math.mod is floored: the result takes the sign of the
                // divisor, so math.mod(-3, 5) is 2. Kotlin's % is truncated and
                // would give -3, which breaks wrap-around counters that dip below 0.
                flooredMod(args.getOrElse(0) { 0.0 }, args.getOrElse(1) { 1.0 })
            }
            // Bedrock rounds halves away from zero, unlike Kotlin's Math.round which
            // rounds half up, so round(-1.5) must be -2 and not -1.
            "round" -> roundHalfAwayFromZero(args.getOrElse(0) { 0.0 })
            "trunc" -> {
                val value = args.getOrElse(0) { 0.0 }
                if (value < 0.0) ceil(value) else floor(value)
            }
            "sign" -> {
                val value = args.getOrElse(0) { 0.0 }
                when {
                    value > 0.0 -> 1.0
                    value < 0.0 -> -1.0
                    else -> 0.0
                }
            }
            "lerpclamp" -> {
                val start = args.getOrElse(0) { 0.0 }
                val end = args.getOrElse(1) { 0.0 }
                val alpha = args.getOrElse(2) { 0.0 }.coerceIn(0.0, 1.0)
                start + (end - start) * alpha
            }
            // Bedrock: math.inverse_lerp(start, end, value) recovers the blend
            // factor a math.lerp would have used. Not clamped, so values beyond
            // the range answer beyond 0..1 like Bedrock's.
            "inverse_lerp" -> {
                val start = args.getOrElse(0) { 0.0 }
                val end = args.getOrElse(1) { 0.0 }
                val value = args.getOrElse(2) { 0.0 }
                if (end == start) 0.0 else (value - start) / (end - start)
            }
            // Shortest-path angle blend in degrees, so 350 -> 10 goes forward
            // through 0 rather than backwards through 180.
            "lerprotate" -> {
                val start = args.getOrElse(0) { 0.0 }
                val end = args.getOrElse(1) { 0.0 }
                val alpha = args.getOrElse(2) { 0.0 }
                val delta = ((((end - start) + 180.0) % 360.0) + 360.0) % 360.0 - 180.0
                start + delta * alpha
            }
            // Bedrock: math.hermite_blend(t) = 3t^2 - 2t^3, the Hermite basis
            // used to ease a 0..1 value with zero derivative at both ends. The
            // docs say any float is valid input and the curve is meant for
            // [0, 1], so there is no clamping; outside the range it bends back.
            "hermite_blend" -> {
                val t = args.getOrElse(0) { 0.0 }
                t * t * (3.0 - 2.0 * t)
            }
            // Bedrock: math.die_roll(num, low, high) sums `num` random numbers in
            // [low, high); die_roll_integer the same with whole numbers. A
            // fractional `num` is the chance of one extra die, so 2.5 rolls
            // twice and half the time a third time. Deterministic per seed.
            "die_roll" -> dieRoll(
                args.getOrElse(0) { 1.0 },
                args.getOrElse(1) { 0.0 },
                args.getOrElse(2) { 1.0 },
                context,
                asInteger = false,
            )
            "die_roll_integer" -> dieRoll(
                args.getOrElse(0) { 1.0 },
                args.getOrElse(1) { 0.0 },
                args.getOrElse(2) { 1.0 },
                context,
                asInteger = true,
            )
            "random" -> {
                val from = args.getOrElse(0) { 0.0 }
                val to = args.getOrElse(1) { 1.0 }
                from + nextRandom(context) * (to - from)
            }
            // Bedrock: whole value in [low, high] with both ends included.
            "random_integer" -> {
                val from = args.getOrElse(0) { 0.0 }.toInt()
                val to = args.getOrElse(1) { 1.0 }.toInt()
                if (to <= from) from.toDouble()
                else (from + (nextRandom(context) * (to - from + 1)).toInt().coerceAtMost(to - from)).toDouble()
            }
            // Inverse trig returns degrees to match sin/cos taking degrees. Inputs
            // are coerced to the valid domain so a slightly-out-of-range value from
            // accumulated float error yields a real angle instead of NaN.
            "asin" -> Math.toDegrees(asin(args.getOrElse(0) { 0.0 }.coerceIn(-1.0, 1.0)))
            "acos" -> Math.toDegrees(acos(args.getOrElse(0) { 0.0 }.coerceIn(-1.0, 1.0)))
            "atan" -> Math.toDegrees(atan(args.getOrElse(0) { 0.0 }))
            "atan2" -> Math.toDegrees(atan2(args.getOrElse(0) { 0.0 }, args.getOrElse(1) { 0.0 }))
            "tan" -> tan(Math.toRadians(args.getOrElse(0) { 0.0 }))
            "cbrt" -> cbrt(args.getOrElse(0) { 0.0 })
            "exp" -> exp(args.getOrElse(0) { 0.0 })
            // Log of zero or a negative is undefined; Bedrock yields 0 rather than
            // -inf/NaN so a broken expression degrades gracefully.
            "ln" -> if (args.getOrElse(0) { 0.0 } > 0.0) ln(args.getOrElse(0) { 0.0 }) else 0.0
            "log10" -> if (args.getOrElse(0) { 0.0 } > 0.0) log10(args.getOrElse(0) { 0.0 }) else 0.0
            // Like lerprotate but wraps the result into [0, 360) so callers that
            // feed it straight into a rotation do not accumulate out-of-range angles.
            "lerpangle" -> {
                val start = args.getOrElse(0) { 0.0 }
                val end = args.getOrElse(1) { 0.0 }
                val alpha = args.getOrElse(2) { 0.0 }
                val delta = ((((end - start) + 180.0) % 360.0) + 360.0) % 360.0 - 180.0
                ((start + delta * alpha) % 360.0 + 360.0) % 360.0
            }
            else -> 0.0
        }

    }

    private fun readMember(member: MolangExpr.Member, context: MolangContext): Double {
        val root = normalizeRoot(member.root)
        val path = member.path

        return when (root) {
            "query", "q" -> context.query(path.joinToString("_"))
            "variable", "v" -> context.getVariable(path.joinToString("."))
            "temp", "t" -> context.getVariable(MOLANG_TEMP_PREFIX + path.joinToString("."))
            else -> context.getVariable(listOf(root, *path.toTypedArray()).joinToString("."))
        }.toDouble()
    }

    private fun writeMember(member: MolangExpr.Member, value: Float, context: MolangContext) {
        val root = normalizeRoot(member.root)

        val key = when (root) {
            "variable", "v" -> member.path.joinToString(".")
            "temp", "t" -> MOLANG_TEMP_PREFIX + member.path.joinToString(".")
            else -> listOf(root, *member.path.toTypedArray()).joinToString(".")
        }

        context.setVariable(key, value)
    }

    private fun normalizeRoot(root: String): String = when (root.lowercase()) {
        "q" -> "query"
        "v" -> "variable"
        "t" -> "temp"
        else -> root.lowercase()
    }

    /** Bedrock's math.round: ties go away from zero, so round(-1.5) == -2. */
    private fun roundHalfAwayFromZero(value: Double): Double =
        if (value < 0.0) -floor(-value + 0.5) else floor(value + 0.5)

    /**
     * Modulo with the sign of the divisor, which is what both Molang's `%` operator
     * and its math.mod() do. Kotlin's `%` is truncated instead, so (-3 % 5) would be
     * -3 rather than 2.
     */
    private fun flooredMod(value: Double, divisor: Double): Double =
        if (divisor == 0.0) 0.0 else ((value % divisor) + divisor) % divisor

    /** Upper bound on a single die_roll call, a defensive cap Bedrock does not have. */
    private const val MAX_DIE_ROLLS = 10_000

    /**
     * Rolls `rolls` dice between `low` and `high` and returns their sum. Whole
     * rolls are always taken and the fractional part is the chance of one more,
     * matching Bedrock's partial die. The float variant takes continuous values
     * in [low, high), the integer variant whole values in [low, high].
     * Deterministic per seed: the same seed and call sequence produce the same
     * rolls. The roll count is capped so a hostile animation cannot pin the
     * render thread; Bedrock itself has no limit here.
     */
    private fun dieRoll(
        rolls: Double,
        low: Double,
        high: Double,
        context: MolangContext,
        asInteger: Boolean,
    ): Double {
        val whole = rolls.toInt().coerceIn(0, MAX_DIE_ROLLS)
        val extraChance = (rolls - rolls.toInt()).coerceIn(0.0, 1.0)
        var count = whole
        if (extraChance > 0.0 && nextRandom(context) < extraChance) count += 1
        if (count == 0) return 0.0

        if (asInteger) {
            val lo = floor(low).toInt()
            val hi = floor(high).toInt()
            if (hi <= lo) return lo.toDouble() * count
            val faces = hi - lo + 1
            var sum = 0
            repeat(count) {
                sum += lo + (nextRandom(context) * faces).toInt().coerceAtMost(faces - 1)
            }
            return sum.toDouble()
        }

        if (high <= low) return low * count
        val span = high - low
        var sum = 0.0
        repeat(count) {
            sum += low + nextRandom(context) * span
        }
        return sum
    }

    /**
     * Returns the next pseudo-random value in [0, 1) for this evaluation context.
     * Bedrock seeds Molang RNG from the animation's seed so the same animation
     * replay produces identical values; without a fixed seed this falls back to
     * a per-context LCG advanced by call order.
     */
    private fun nextRandom(context: MolangContext): Double {
        context.randomState = (context.randomState * 6364136223846793005L + 1442695040888963407L) and Long.MAX_VALUE
        return (context.randomState ushr 11).toDouble() / (1L shl 53).toDouble()
    }
}
