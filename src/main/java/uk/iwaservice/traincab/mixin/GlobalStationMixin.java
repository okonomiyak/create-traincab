package uk.iwaservice.traincab.mixin;

import com.simibubi.create.content.trains.graph.DimensionPalette;
import com.simibubi.create.content.trains.graph.TrackNode;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.iwaservice.traincab.block.TwoWayStation;

/**
 * Create's stations only take trains travelling one way. A 高度駅 set to 双方向 takes them from both sides; the
 * setting is saved with the station on the track, so it also holds while the 高度駅's chunk is unloaded.
 */
@Mixin(GlobalStation.class)
public abstract class GlobalStationMixin implements TwoWayStation {

    @Shadow
    public boolean assembling;

    @Unique
    private boolean traincab$twoWay;

    @Override
    public boolean traincab$twoWay() {
        return traincab$twoWay;
    }

    @Override
    public void traincab$setTwoWay(boolean twoWay) {
        traincab$twoWay = twoWay;
    }

    @Inject(method = "canApproachFrom", at = @At("HEAD"), cancellable = true)
    private void traincab$canApproachFrom(TrackNode side, CallbackInfoReturnable<Boolean> cir) {
        if (traincab$twoWay && !assembling) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "write(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;Lcom/simibubi/create/content/trains/graph/DimensionPalette;)V",
        at = @At("TAIL"))
    private void traincab$write(CompoundTag nbt, HolderLookup.Provider registries, DimensionPalette dimensions,
                                CallbackInfo ci) {
        nbt.putBoolean("TraincabTwoWay", traincab$twoWay);
    }

    @Inject(method = "read(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;ZLcom/simibubi/create/content/trains/graph/DimensionPalette;)V",
        at = @At("TAIL"))
    private void traincab$read(CompoundTag nbt, HolderLookup.Provider registries, boolean migration,
                               DimensionPalette dimensions, CallbackInfo ci) {
        traincab$twoWay = nbt.getBoolean("TraincabTwoWay");
    }
}
