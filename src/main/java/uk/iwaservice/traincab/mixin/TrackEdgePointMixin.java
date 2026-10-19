package uk.iwaservice.traincab.mixin;

import com.simibubi.create.content.trains.signal.SignalBoundary;
import com.simibubi.create.content.trains.signal.TrackEdgePoint;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.iwaservice.traincab.block.AdvancedStationBlockEntity;

/**
 * A 高度駅's departure signal lists the station as its block entity. When the track under it changes, Create would
 * hand the signal's data to the station's own track targeting and overwrite the station's; the 高度駅 puts its
 * signal back by itself instead.
 */
@Mixin(value = TrackEdgePoint.class, remap = false)
public abstract class TrackEdgePointMixin {

    @Inject(method = "invalidateAt", at = @At("HEAD"), cancellable = true)
    private void traincab$departureSignal(LevelAccessor level, BlockPos pos, CallbackInfo ci) {
        if ((Object) this instanceof SignalBoundary && level.getBlockEntity(pos) instanceof AdvancedStationBlockEntity) {
            ci.cancel();
        }
    }
}
