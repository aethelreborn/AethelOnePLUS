package org.polyfrost.polyplus.client.bedrock.controller

import org.joml.Vector3f
import org.polyfrost.polyplus.client.bedrock.BedrockConstants
import org.polyfrost.polyplus.client.bedrock.animation.BedrockAnimation
import org.polyfrost.polyplus.client.bedrock.molang.MolangContext
import org.polyfrost.polyplus.client.bedrock.molang.MolangEvaluator
import org.polyfrost.polyplus.client.bedrock.molang.clearMolangTemps
import org.polyfrost.polyplus.client.bedrock.playback.AnimationSampler
import org.polyfrost.polyplus.client.bedrock.playback.BedrockAnimationPlayback
import org.polyfrost.polyplus.client.bedrock.playback.BoneTransform
import org.polyfrost.polyplus.client.render.PlayerRenderContext

class BedrockAnimationControllerRunner(
    private val controller: BedrockAnimationController,
    private val animations: Map<String, BedrockAnimation>,
    private val blendTicks: Int = 0,
) {
    var currentState: String = controller.initialState
        private set

    var blendProgress: Float = 1f
        private set

    private var timeInStateSeconds = 0f
    private var previousState: String? = null
    private var timeInPreviousStateSeconds = 0f

    private val blendDurationSeconds: Float
        get() = blendTicks / BedrockConstants.TICKS_PER_SECOND

    fun sample(
        deltaSeconds: Float,
        molangVariables: MutableMap<String, Float>,
        renderContext: PlayerRenderContext? = null,
    ): Map<String, BoneTransform> {
        // `temp.` is scoped to one evaluation; `variable.` is not. Everything below
        // this frame shares the map, so clear once here rather than per call site.
        molangVariables.clearMolangTemps()
        timeInStateSeconds += deltaSeconds
        if (previousState != null) {
            timeInPreviousStateSeconds += deltaSeconds
            blendProgress = (blendProgress + deltaSeconds / blendDurationSeconds).coerceIn(0f, 1f)
        }
        evaluateTransitions(molangVariables, renderContext)

        val result = sampleState(currentState, timeInStateSeconds, molangVariables, renderContext)

        val previous = previousState ?: return result
        if (blendProgress >= 1f) {
            previousState = null
            return result
        }

        val previousPose = sampleState(previous, timeInPreviousStateSeconds, molangVariables, renderContext)
        return crossfade(previousPose, result, blendProgress)
    }

    private fun sampleState(
        stateName: String,
        timeSeconds: Float,
        molangVariables: MutableMap<String, Float>,
        renderContext: PlayerRenderContext?,
    ): Map<String, BoneTransform> {
        val state = controller.states[stateName] ?: return emptyMap()
        val context = MolangContext.forAnimation(
            animTimeSeconds = timeSeconds,
            renderContext = renderContext,
            variables = molangVariables,
        )

        var result: Map<String, BoneTransform> = emptyMap()
        for (stateAnimation in state.animations) {
            val animation = animations[stateAnimation.animationName] ?: continue
            val weight = MolangEvaluator.eval(stateAnimation.weight, context).toFloat().coerceIn(0f, 1f)
            if (weight <= 0f) continue

            val elapsedTicks = timeSeconds * BedrockConstants.TICKS_PER_SECOND
            val timeTicks = BedrockAnimationPlayback.resolveTimeTicks(animation, elapsedTicks)
            val sample = AnimationSampler.sample(
                animation = animation,
                timeTicks = timeTicks,
                renderContext = renderContext,
                molangVariables = molangVariables,
            )
            result = blend(result, sample, weight)
        }

        return result
    }

    private fun evaluateTransitions(molangVariables: MutableMap<String, Float>, renderContext: PlayerRenderContext?) {
        val state = controller.states[currentState] ?: return
        if (state.transitions.isEmpty()) return

        val context = MolangContext.forAnimation(
            animTimeSeconds = timeInStateSeconds,
            renderContext = renderContext,
            variables = molangVariables,
        )

        for (transition in state.transitions) {
            val target = controller.states[transition.targetState] ?: continue
            if (MolangEvaluator.eval(transition.condition, context) == 0.0) continue

            MolangEvaluator.execute(state.onExit, context)
            if (blendTicks > 0 && transition.targetState != currentState) {
                previousState = currentState
                timeInPreviousStateSeconds = timeInStateSeconds
                blendProgress = 0f
            }
            currentState = transition.targetState
            timeInStateSeconds = 0f

            val entryContext = MolangContext.forAnimation(
                animTimeSeconds = 0f,
                renderContext = renderContext,
                variables = molangVariables,
            )
            MolangEvaluator.execute(target.onEntry, entryContext)
            return
        }
    }

    private fun crossfade(
        previous: Map<String, BoneTransform>,
        current: Map<String, BoneTransform>,
        weight: Float,
    ): Map<String, BoneTransform> {
        if (previous.isEmpty()) return current
        if (current.isEmpty()) return previous
        if (weight <= 0f) return previous
        if (weight >= 1f) return current

        val result = LinkedHashMap<String, BoneTransform>()
        for (bone in previous.keys + current.keys) {
            val from = previous[bone] ?: BoneTransform()
            val to = current[bone] ?: BoneTransform()
            result[bone] = lerp(from, to, weight)
        }
        return result
    }

    private fun blend(base: Map<String, BoneTransform>, add: Map<String, BoneTransform>, weight: Float): Map<String, BoneTransform> {
        if (base.isEmpty()) {
            return if (weight >= 1f) add else add.mapValues { (_, transform) -> lerp(BoneTransform(), transform, weight) }
        }

        val result = LinkedHashMap<String, BoneTransform>(base)
        for ((bone, addTransform) in add) {
            result[bone] = lerp(base[bone] ?: BoneTransform(), addTransform, weight)
        }
        return result
    }

    private fun lerp(from: BoneTransform, to: BoneTransform, weight: Float): BoneTransform = BoneTransform(
        position = Vector3f(from.position).lerp(to.position, weight),
        rotation = Vector3f(from.rotation).lerp(to.rotation, weight),
        scale = Vector3f(from.scale).lerp(to.scale, weight),
    )
}
