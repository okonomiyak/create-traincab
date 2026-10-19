package uk.iwaservice.traincab.mixin;

import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlock;
import com.simibubi.create.content.redstone.nixieTube.NixieTubeBlockEntity;
import com.simibubi.create.content.trains.signal.SignalBlockEntity.SignalState;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.iwaservice.traincab.block.AdvancedStationBlock;

/** A nixie tube on a 高度駅 shows its departure lamp in red and green, like one on a Create signal. */
@Mixin(value = NixieTubeBlockEntity.class, remap = false)
public abstract class NixieTubeBlockEntityMixin {

    @Shadow
    public SignalState signalState;
    @Shadow
    public NixieTubeBlockEntity.ComputerSignal computerSignal;

    // Create's tick works out signalState on the client only, from a signal block behind the tube.
    @Inject(method = "tick", at = @At("TAIL"))
    private void traincab$advancedStation(CallbackInfo ci) {
        NixieTubeBlockEntity self = (NixieTubeBlockEntity) (Object) this;
        if (!self.getLevel().isClientSide || signalState != null || computerSignal != null) {
            return;
        }
        BlockState behind = self.getLevel().getBlockState(
            self.getBlockPos().relative(NixieTubeBlock.getFacing(self.getBlockState()).getOpposite()));
        if (behind.hasProperty(AdvancedStationBlock.CLEAR)) {
            signalState = behind.getValue(AdvancedStationBlock.CLEAR) ? SignalState.GREEN : SignalState.RED;
        }
    }
}
