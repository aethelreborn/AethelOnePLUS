package org.polyfrost.polyplus.client.render

//? if >= 1.21.4 {
import net.minecraft.client.renderer.entity.state.HumanoidRenderState
//?}

//? if = 1.21.1 {
/*import net.minecraft.client.player.AbstractClientPlayer
*///?}

//? if = 1.8.9 {
/*import net.minecraft.client.entity.living.player.ClientPlayerEntity
*///?}

data class PlayerRenderContext(
    val ageInTicks: Float,
    val walkAnimationSpeed: Float,
    val isCrouching: Boolean,
    val isOnGround: Boolean,
    val isInWater: Boolean,
    val swimAmount: Float,
    val isInvisible: Boolean,
    val verticalSpeed: Float = 0f,
    val yawSpeed: Float = 0f,
    val isSprinting: Boolean = false,
    /** Head yaw in degrees, interpolated for the current frame. */
    val headYawDeg: Float = 0f,
    /** Body (render) yaw in degrees, interpolated for the current frame. */
    val bodyYawDeg: Float = 0f,
    /** Head pitch in degrees, interpolated for the current frame. */
    val headPitchDeg: Float = 0f,
) {
    companion object {
        private fun lerp(from: Float, to: Float, delta: Float): Float = from + (to - from) * delta

        //? if >= 1.21.4 {
        fun from(state: HumanoidRenderState): PlayerRenderContext = PlayerRenderContext(
            ageInTicks = state.ageInTicks,
            walkAnimationSpeed = state.walkAnimationSpeed,
            isCrouching = state.isCrouching,
            isOnGround = state.walkAnimationSpeed > 0.01f || state.isCrouching,
            isInWater = state.isInWater,
            swimAmount = state.swimAmount,
            isInvisible = state.isInvisible,
            headYawDeg = state.yRot,
            bodyYawDeg = state.bodyRot,
            headPitchDeg = state.xRot,
        )
        //?} elif = 1.21.1 {
        /*fun from(
            player: AbstractClientPlayer,
            partialTicks: Float,
            limbSwingAmount: Float,
            ageInTicks: Float,
        ): PlayerRenderContext = PlayerRenderContext(
            ageInTicks = ageInTicks,
            walkAnimationSpeed = limbSwingAmount,
            isCrouching = player.isCrouching,
            isOnGround = player.onGround(),
            isInWater = player.isInWater,
            swimAmount = player.getSwimAmount(partialTicks),
            isInvisible = player.isInvisible,
            headYawDeg = lerp(player.yHeadRotO, player.yHeadRot, partialTicks),
            bodyYawDeg = lerp(player.yBodyRotO, player.yBodyRot, partialTicks),
            headPitchDeg = lerp(player.xRotO, player.xRot, partialTicks),
        )
        *///?} else {
        /*@JvmStatic
        fun from(
            player: ClientPlayerEntity,
            partialTicks: Float,
            limbSwingAmount: Float,
            ageInTicks: Float,
        ): PlayerRenderContext = PlayerRenderContext(
            ageInTicks = ageInTicks,
            walkAnimationSpeed = limbSwingAmount,
            isCrouching = player.isSneaking,
            isOnGround = player.onGround,
            isInWater = player.isInWater,
            swimAmount = 0f,
            isInvisible = player.isInvisible,
            headYawDeg = lerp(player.lastHeadYaw, player.headYaw, partialTicks),
            bodyYawDeg = lerp(player.lastBodyYaw, player.bodyYaw, partialTicks),
            headPitchDeg = lerp(player.lastPitch, player.pitch, partialTicks),
        )
        *///?}
    }
}
