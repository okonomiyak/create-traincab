package uk.iwaservice.traincab.mixin;

import com.simibubi.create.content.trains.entity.CarriageContraption;
import com.tterrag.registrate.util.entry.BlockEntry;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import uk.iwaservice.traincab.TrainCabItems;

@Mixin(value = CarriageContraption.class, remap = false)
public abstract class CarriageContraptionMixin {

    /**
     * Assembly (which ends a train can be driven from) and inControl both test {@code AllBlocks.TRAIN_CONTROLS.has};
     * the 高度運転台 counts as train controls too.
     */
    @Redirect(method = {"capture", "inControl"}, at = @At(value = "INVOKE",
        target = "Lcom/tterrag/registrate/util/entry/BlockEntry;has(Lnet/minecraft/world/level/block/state/BlockState;)Z"))
    private boolean traincab$advancedCabIsControls(BlockEntry<?> controls, BlockState state) {
        return controls.has(state) || state.is(TrainCabItems.ADVANCED_CAB.get());
    }
}
