package uk.iwaservice.traincab.compat.crn;

import com.simibubi.create.content.trains.entity.Train;
import de.mrjulsen.crn.core.delay.DelayCause;
import de.mrjulsen.crn.core.delay.DelayCauseRegistry;
import de.mrjulsen.crn.core.delay.DelayContext;
import de.mrjulsen.crn.core.delay.DelayInstance;
import de.mrjulsen.crn.core.delay.DelaySeverity;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.mascon.Conductor;

import java.util.Collection;
import java.util.function.Predicate;

/**
 * Create Railways Navigator, when installed: what this mod holds trains for, as reasons on its displays
 * (gui.traincab.delay_cause.* in the lang files). Only loaded when CRN is present.
 */
public final class CrnCompat {

    public static final String MODID = "createrailwaysnavigator";

    private CrnCompat() {
    }

    public static void init() {
        // Always shown: a train standing on its emergency brake.
        DelayCauseRegistry.register(TrainCabMod.MODID, "conductor_emergency",
            new Cause(DelaySeverity.IMPORTANT, Conductor::emergency));
        // Shown while the train runs late: the conductor keeping the doors open.
        DelayCauseRegistry.register(TrainCabMod.MODID, "door_handling",
            new Cause(DelaySeverity.DELAY, train -> Conductor.doorsOpen(train) && train.getCurrentStation() != null));
    }

    private static final class Cause extends DelayCause {

        private final DelaySeverity severity;
        private final Predicate<Train> applies;

        Cause(DelaySeverity severity, Predicate<Train> applies) {
            this.severity = severity;
            this.applies = applies;
        }

        @Override
        public DelaySeverity severity() {
            return severity;
        }

        @Override
        public Collection<DelayInstance> detect(DelayContext context) {
            Train train = context.createTrain();
            return train != null && applies.test(train) ? present(context) : absent();
        }
    }
}
