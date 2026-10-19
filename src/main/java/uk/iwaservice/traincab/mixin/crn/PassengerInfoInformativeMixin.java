package uk.iwaservice.traincab.mixin.crn;

import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity.EUpdateReason;
import de.mrjulsen.crn.client.ber.AdvancedDisplayRenderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ForgeConfigSpec;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import uk.iwaservice.traincab.compat.crn.CrnDisplays;

/**
 * Create Railways Navigator's detailed passenger display: shows the next stop once the conductor has announced it
 * (車内放送), not only shortly before arriving. Skipped when CRN isn't installed, or has changed this code.
 */
@Pseudo
@Mixin(targets = "de.mrjulsen.crn.client.ber.variants.BERPassengerInfoInformative", remap = false)
public abstract class PassengerInfoInformativeMixin {

    // Not the generic bridge method of the same name. The next stop shows once its arrival is closer than NEXT_STOP_ANNOUNCEMENT ticks: make that "always".
    @Redirect(method = "update(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;"
        + "Lnet/minecraft/world/level/block/state/BlockState;Lde/mrjulsen/crn/block/blockentity/AdvancedDisplayBlockEntity;"
        + "Lde/mrjulsen/crn/client/ber/AdvancedDisplayRenderInstance;"
        + "Lde/mrjulsen/crn/block/blockentity/AdvancedDisplayBlockEntity$EUpdateReason;)V",
        slice = @Slice(from = @At(value = "FIELD",
            target = "Lde/mrjulsen/crn/config/ModServerConfig;NEXT_STOP_ANNOUNCEMENT:Lnet/minecraftforge/common/ForgeConfigSpec$ConfigValue;")),
        at = @At(value = "INVOKE", target = "Lnet/minecraftforge/common/ForgeConfigSpec$ConfigValue;get()Ljava/lang/Object;", ordinal = 0),
        require = 0)
    private Object traincab$announced(ForgeConfigSpec.ConfigValue<?> value, Level level, BlockPos pos, BlockState state,
                                      AdvancedDisplayBlockEntity display, AdvancedDisplayRenderInstance parent,
                                      EUpdateReason reason) {
        return CrnDisplays.announced(display) ? Integer.MAX_VALUE : value.get();
    }
}
