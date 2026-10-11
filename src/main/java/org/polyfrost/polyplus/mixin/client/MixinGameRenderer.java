package org.polyfrost.polyplus.mixin.client;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
//? if > 1.8.9 {
import net.minecraft.client.renderer.GameRenderer;
//?} else {
/*import net.minecraft.client.render.GameRenderer;
*///?}
import org.polyfrost.polyplus.client.PolyPlusConfig;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreen;
import org.polyfrost.polyplus.client.gui.PolyPlusMainMenuScreenKt;
import org.polyfrost.polyplus.client.utils.ClientPlatform;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

//? if >= 26.2 {
import net.minecraft.util.ARGB;
//?}

//? if >= 26.1 {
import net.minecraft.client.renderer.state.gui.GuiRenderState;
import org.polyfrost.polyplus.client.gui.MenuPanorama;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

//? if >= 26.1 && < 26.3 {
/*import net.minecraft.client.DeltaTracker;
*///?}

@Mixin(GameRenderer.class)
public class MixinGameRenderer {
    @Unique
    private static final int POLYPLUS_PANORAMA_BLUR_RADIUS = 7;

    //? if > 1.8.9 {
    @ModifyExpressionValue(
        //? if >= 26.1 {
        method = "extractOptions",
        //?} elif >= 1.21.8 {
        /*method = "render",
        *///?} else {
        /*method = "processBlurEffect",
        *///?}
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;getMenuBackgroundBlurriness()I")
    )
    private int polyplus$forcePanoramaBlurRadius(int original) {
        if (ClientPlatform.INSTANCE.currentScreen() instanceof PolyPlusMainMenuScreen
            && PolyPlusMainMenuScreenKt.mainMenuPanoramaEnabled()) {
            return POLYPLUS_PANORAMA_BLUR_RADIUS;
        }
        return original;
    }

    //? if > 1.8.9 && < 26.1 {
    @Inject(method = "getFov", at = @At("HEAD"), cancellable = true)
    //? if >= 1.21.11 {
    private void polyplus$disableDynamicFov(CallbackInfoReturnable<Float> cir) {
    //?} elif < 1.21.4 {
    /*private void polyplus$disableDynamicFov(CallbackInfoReturnable<Double> cir) {
    *///?} else {
    /*private void polyplus$disableDynamicFov(CallbackInfoReturnable<Float> cir) {
    *///?}
        if (!PolyPlusConfig.getDynamicFov()) cir.setReturnValue(1.0F);
    }
    //?}
    //?}

    //? if >= 26.1 {
    @Inject(method = "render", at = @At("HEAD"))
    //? if >= 26.3 {
    private void polyplus$dropCoveredPanorama(CallbackInfo ci) {
    //?} else {
    /*private void polyplus$dropCoveredPanorama(DeltaTracker deltaTracker, boolean advanceGameTime, CallbackInfo ci) {
    *///?}
        if (!MenuPanorama.suppressPanorama()) return;

        GameRenderer self = (GameRenderer) (Object) this;
        //? if >= 26.2 {
        GuiRenderState state = self.gameRenderState().guiRenderState;
        //?} else {
        /*GuiRenderState state = self.getGameRenderState().guiRenderState;
        *///?}
        if (state.panoramaRenderState == null) return;
        state.panoramaRenderState = null;

        if (MenuPanorama.backdropFilled()) return;
        //? if >= 26.2 {
        ARGB.setVector4fFromARGB32(state.clearColorOverride, MenuPanorama.BASE_COLOR);
        //?} else {
        /*state.clearColorOverride = MenuPanorama.BASE_COLOR;
        *///?}
    }
    //?}
}
