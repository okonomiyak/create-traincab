package uk.iwaservice.traincab.compat.crn;

import de.mrjulsen.crn.api.core.snapshot.TrainSnapshot;
import de.mrjulsen.crn.block.blockentity.AdvancedDisplayBlockEntity;
import uk.iwaservice.traincab.mascon.Announcement;

import java.util.UUID;

/** CRN's passenger information displays on a train switch to the next stop once the conductor announces it. */
public final class CrnDisplays {

    /** How long after a 車内放送 the next stop stays up (ms), unless the train arrives first. */
    private static final long SHOW_MS = 60_000;
    /** A 車内放送 made at a station, just before setting off, still counts for the next stop. */
    private static final long AT_STATION_MS = 10_000;

    private CrnDisplays() {
    }

    public static boolean announced(AdvancedDisplayBlockEntity display) {
        UUID train = display.getTrain().map(TrainSnapshot::trainId).orElse(null);
        Long heard = train == null ? null : Announcement.HEARD.get(train);
        if (heard == null) {
            return false;
        }
        long age = System.currentTimeMillis() - heard;
        if (age > SHOW_MS || display.isWaitingAtStation() && age > AT_STATION_MS) {
            Announcement.HEARD.remove(train);
            return false;
        }
        return true;
    }
}
