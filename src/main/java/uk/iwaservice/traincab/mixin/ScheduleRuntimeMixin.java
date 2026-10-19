package uk.iwaservice.traincab.mixin;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import uk.iwaservice.traincab.mascon.DepartureBell;

/** Scheduled trains wait at a 高度駅 while its departure melody plays, then set off. */
@Mixin(ScheduleRuntime.class)
public abstract class ScheduleRuntimeMixin {

    @Shadow
    public Train train;

    // Called every tick while the train is about to set off for its next instruction; null means "not yet".
    @Inject(method = "startCurrentInstruction", at = @At("HEAD"), cancellable = true)
    private void traincab$departureMelody(Level level, CallbackInfoReturnable<DiscoveredPath> cir) {
        if (DepartureBell.holdDeparture(train)) {
            cir.setReturnValue(null);
        }
    }
}
