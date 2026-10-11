package org.polyfrost.polyplus.mixin.client;

//? if > 1.8.9 {
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.PlayerScoreEntry;
import org.polyfrost.polyplus.client.PolyPlusBadge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

// the sidebar builds its lines from ownerName() (Gui/Hud#displayScoreboardSidebar -> the
// formatNameForTeam mapping over the objective's scores), and it is the only vanilla caller,
// so badging here lands on scoreboard entries without touching the tab list or chat
@Mixin(PlayerScoreEntry.class)
public class MixinPlayerScoreEntry {
    @ModifyReturnValue(method = "ownerName", at = @At("RETURN"))
    private Component polyplus$badgeScoreboardLine(Component name) {
        return PolyPlusBadge.decorateScoreboardLine(name);
    }
}
//?}
