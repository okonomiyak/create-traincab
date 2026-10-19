package uk.iwaservice.traincab.mixin;

import com.simibubi.create.content.contraptions.behaviour.MovementContext;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorMovementBehaviour;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.iwaservice.traincab.mascon.Conductor;

// remap = false: Create's own method names, never obfuscated.
@Mixin(value = SlidingDoorMovementBehaviour.class, remap = false)
public abstract class SlidingDoorMixin {

    /**
     * Create opens a train's sliding doors whenever it stands at a station (on the sides the station allows).
     * With a conductor on board they follow the conductor's 戸開 / 戸閉 instead. Server side only.
     */
    @Inject(method = "shouldOpen", at = @At("RETURN"), cancellable = true)
    private void traincab$conductorDoors(MovementContext context, CallbackInfoReturnable<Boolean> cir) {
        if (!context.disabled
            && context.contraption.entity instanceof CarriageContraptionEntity cce
            && cce.getCarriage() != null) {
            cir.setReturnValue(Conductor.doorsShouldOpen(cce.getCarriage().train, cir.getReturnValue()));
        }
    }
}
