package uk.iwaservice.traincab.mascon;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.graph.DiscoveredPath;
import com.simibubi.create.content.trains.schedule.Schedule;
import com.simibubi.create.content.trains.schedule.ScheduleRuntime;
import com.simibubi.create.content.trains.schedule.destination.DestinationInstruction;
import com.simibubi.create.content.trains.station.GlobalStation;
import uk.iwaservice.traincab.TrainCabServerConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A scheduled train driven with the mascon keeps to its schedule instead of pausing it (Create pauses it when a
 * player takes the controls). The schedule still heads for its next stop, but the driver takes the train there:
 * stopping at that station counts as arriving, and setting off from it moves the schedule on. So departure boards
 * (Create's, and Create Railways Navigator's) keep showing the train. When the driver leaves, the schedule is paused
 * again, as Create would have left it.
 */
public final class DrivenSchedule {

    /** Trains whose schedule runs while driven, and the station it is heading for (null until known). */
    // Only touched on the server thread.
    private static final Map<UUID, GlobalStation> RUNNING = new HashMap<>();

    private DrivenSchedule() {
    }

    /** Every tick a driver has the train. */
    static void drive(Train train) {
        ScheduleRuntime runtime = train.runtime;
        if (!TrainCabServerConfig.scheduleWhileDriving || runtime.getSchedule() == null || runtime.completed
            || RUNNING.containsKey(train.id)) {
            return;
        }
        runtime.paused = false;
        RUNNING.put(train.id, null);
    }

    /** The driver has gone: pause the schedule again, ready for the schedule item to resume it. */
    static void release(Train train) {
        if (!RUNNING.containsKey(train.id)) {
            return;
        }
        RUNNING.remove(train.id);
        ScheduleRuntime runtime = train.runtime;
        if (runtime.getSchedule() == null) {
            return;
        }
        runtime.paused = true;
        if (runtime.state == ScheduleRuntime.State.IN_TRANSIT && train.navigation.destination == null) {
            runtime.state = ScheduleRuntime.State.PRE_TRANSIT;
        }
    }

    /** In place of Create setting off along {@code path}: true when the driver takes the train there instead. */
    public static boolean takesOver(Train train, DiscoveredPath path) {
        if (!Mascon.isActive(train)) {
            return false;
        }
        RUNNING.put(train.id, path.destination);
        return true;
    }

    /** The schedule waits for the driver to set off rather than starting its next leg at the station. */
    public static boolean holdsDeparture(Train train) {
        return Mascon.isActive(train) && train.getCurrentStation() != null;
    }

    /** Whether stopping at the train's current station counts as reaching the schedule's destination. */
    public static boolean arrivalCounts(Train train) {
        if (!Mascon.isActive(train)) {
            return true;
        }
        GlobalStation station = train.getCurrentStation();
        if (station == null) {
            return false;
        }
        if (station == RUNNING.get(train.id)) {
            return true;
        }
        // Also any other station the destination's name filter matches.
        ScheduleRuntime runtime = train.runtime;
        Schedule schedule = runtime.getSchedule();
        return schedule != null && runtime.currentEntry < schedule.entries.size()
            && schedule.entries.get(runtime.currentEntry).instruction instanceof DestinationInstruction destination
            && station.name.matches(destination.getFilterForRegex());
    }

    /** The driver set off from a station: the schedule's wait there is over. */
    static void departed(Train train) {
        ScheduleRuntime runtime = train.runtime;
        if (RUNNING.containsKey(train.id) && !runtime.paused && runtime.state == ScheduleRuntime.State.POST_TRANSIT) {
            runtime.state = ScheduleRuntime.State.PRE_TRANSIT;
            runtime.currentEntry++;
        }
    }

    /**
     * A schedule left in transit with nobody taking the train there (the server stopped mid-drive) would wait
     * forever: start that leg again.
     */
    public static void checkStranded(Train train) {
        ScheduleRuntime runtime = train.runtime;
        if (!runtime.paused && runtime.state == ScheduleRuntime.State.IN_TRANSIT
            && train.navigation.destination == null && !Mascon.isActive(train)) {
            runtime.state = ScheduleRuntime.State.PRE_TRANSIT;
        }
    }
}
