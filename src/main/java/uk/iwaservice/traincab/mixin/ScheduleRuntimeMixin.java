package uk.iwaservice.traincab.mixin;

import com.simibubi.create.content.trains.entity.Navigation;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.iwaservice.traincab.mascon.DepartureBell;
import uk.iwaservice.traincab.mascon.DrivenSchedule;
import uk.iwaservice.traincab.mascon.Mascon;

/**
 * Scheduled trains wait at a 高度駅 while its departure melody plays, then set off. A scheduled train driven with
 * the mascon runs its schedule by hand (DrivenSchedule).
 */
@Mixin(value = ScheduleRuntime.class, remap = false)
public abstract class ScheduleRuntimeMixin {

    @Shadow
    public Train train;

    @Inject(method = "tick", at = @At("HEAD"))
    private void traincab$checkStranded(Level level, CallbackInfo ci) {
        DrivenSchedule.checkStranded(train);
    }

    // Called every tick while the train is about to set off for its next instruction; null means "not yet".
    // A driven train sets off when its driver does (and the driver rings the melody).
    @Inject(method = "startCurrentInstruction", at = @At("HEAD"), cancellable = true)
    private void traincab$departureMelody(Level level, CallbackInfoReturnable<DiscoveredPath> cir) {
        if (Mascon.isActive(train) ? DrivenSchedule.holdsDeparture(train) : DepartureBell.holdDeparture(train)) {
            cir.setReturnValue(null);
        }
    }

    /** A driven train doesn't navigate: its driver takes it to the schedule's next stop. */
    @Redirect(method = "tick", at = @At(value = "INVOKE",
        target = "Lcom/simibubi/create/content/trains/entity/Navigation;startNavigation(Lcom/simibubi/create/content/trains/graph/DiscoveredPath;)D"))
    private double traincab$driverTakesOver(Navigation navigation, DiscoveredPath path) {
        return DrivenSchedule.takesOver(train, path) ? 0 : navigation.startNavigation(path);
    }

    /** A driven train stopping somewhere else on the way hasn't reached the schedule's stop. */
    @Inject(method = "destinationReached", at = @At("HEAD"), cancellable = true)
    private void traincab$onlyScheduledStop(CallbackInfo ci) {
        if (!DrivenSchedule.arrivalCounts(train)) {
            ci.cancel();
        }
    }
}
