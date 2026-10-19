package uk.iwaservice.traincab.mixin;

import com.simibubi.create.content.trains.entity.Train;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.iwaservice.traincab.mascon.Mascon;

@Mixin(Train.class)
public abstract class TrainMixin {

    /** Create's W/S driving: ignored while the mascon owns the train. */
    @Inject(method = "approachTargetSpeed", at = @At("HEAD"), cancellable = true)
    private void traincab$skipManualAcceleration(float accelerationMod, CallbackInfo ci) {
        if (Mascon.isActive((Train) (Object) this)) {
            ci.cancel();
        }
    }

    /** Runs every Train.tick right before the train moves; the mascon applies its notch here instead. */
    @Inject(method = "tickPassiveSlowdown", at = @At("HEAD"), cancellable = true)
    private void traincab$applyMascon(CallbackInfo ci) {
        if (Mascon.tick((Train) (Object) this)) {
            ci.cancel();
        }
    }
}
