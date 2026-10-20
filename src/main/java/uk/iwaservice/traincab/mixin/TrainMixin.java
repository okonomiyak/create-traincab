package uk.iwaservice.traincab.mixin;

import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import uk.iwaservice.traincab.mascon.Adhesion;
import uk.iwaservice.traincab.mascon.Mascon;

@Mixin(Train.class)
public abstract class TrainMixin {

    @Unique
    private double traincab$speedBefore;

    /** The speed before Create's navigation sets this tick's, for Adhesion.auto. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void traincab$rememberSpeed(Level level, CallbackInfo ci) {
        traincab$speedBefore = ((Train) (Object) this).speed;
    }

    /** Create's W/S driving: ignored while the mascon owns the train. */
    @Inject(method = "approachTargetSpeed", at = @At("HEAD"), cancellable = true)
    private void traincab$skipManualAcceleration(float accelerationMod, CallbackInfo ci) {
        if (Mascon.isActive((Train) (Object) this)) {
            ci.cancel();
        }
    }

    /**
     * Runs every Train.tick right before the train moves; the mascon applies its notch here instead. Otherwise
     * gradients and wheel slip are applied to Create's automatic driving.
     */
    @Inject(method = "tickPassiveSlowdown", at = @At("HEAD"), cancellable = true)
    private void traincab$applyMascon(CallbackInfo ci) {
        Train train = (Train) (Object) this;
        if (Mascon.tick(train)) {
            ci.cancel();
        } else {
            Adhesion.auto(train, traincab$speedBefore);
        }
    }
}
