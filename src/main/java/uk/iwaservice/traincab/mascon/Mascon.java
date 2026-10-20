package uk.iwaservice.traincab.mascon;

import com.simibubi.create.AllPackets;
import com.simibubi.create.Create;
import com.simibubi.create.content.trains.TrainHUDUpdatePacket;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.infrastructure.config.AllConfigs;
import uk.iwaservice.traincab.TrainCabServerConfig;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side master controller. While a driver keeps sending notches for a train, this replaces
 * Create's own manual speed handling (see TrainMixin): power notches accelerate, N coasts, brake
 * notches decelerate. Once the driver stops sending, the train goes back to Create.
 */
public final class Mascon {

    /** Notch values: EB (非常) = -8, B7..B1 = -7..-1, N = 0, P1..P5 = 1..5. */
    public static final int EB = -8;
    public static final int MAX_BRAKE = 7;
    public static final int MAX_POWER = 5;

    private static final int STALE_TICKS = 40;
    private static final ResourceLocation STEAM_SOUND = new ResourceLocation("create", "steam");

    // Tuning (acceleration rate, stop tolerance, search range, overrun limit) lives in TrainCabServerConfig.

    /** The track search for the next station is the costly part, so it runs this often (ticks). */
    private static final int SEARCH_INTERVAL = 5;

    /** Passengers' "次は ○○" display doesn't need to be smooth: refresh it every this many ticks. */
    private static final int PASSENGER_SYNC_TICKS = 10;

    private static final class State {
        int notch;
        int direction = 1;
        boolean neutral;
        boolean stationAssist;
        boolean departRequested;
        int lastUpdate;
        ServerPlayer driver;
        GlobalStation station;
        double stationDistance;
        /** Ticks until the next station search; in between the distance just counts down. */
        int searchIn;
        /** StationInfo.GRIP, SLIP (空転) or SLIDE (滑走) this tick. */
        int wheel;
    }

    // Only touched on the server thread (packet handler and Train.tick).
    // ponytail: entries of trains that are never ticked again (removed trains) are not cleaned up.
    private static final Map<UUID, State> STATES = new HashMap<>();

    private Mascon() {
    }

    static void update(Train train, int notch, int direction, boolean stationAssist, ServerPlayer driver) {
        State s = STATES.computeIfAbsent(train.id, k -> new State());
        notch = Mth.clamp(notch, EB, MAX_POWER);
        if (notch > 0 && s.notch <= 0) {
            s.departRequested = true;
        }
        if (notch >= 0 && s.notch < 0) {
            playReleaseSound(driver, s.notch);
        }
        s.notch = notch;
        if (notch == EB) {
            Conductor.acknowledgeEmergency(train);
        }
        // direction 0 = reverser in neutral: keep the last direction so braking still knows which way is "forward".
        s.neutral = direction == 0;
        if (direction != 0) {
            s.direction = direction < 0 ? -1 : 1;
        }
        s.lastUpdate = now();
        s.driver = driver;
        s.stationAssist = stationAssist;
    }

    /** 緩解音: the プシュー of brake air let out when the brake is fully released, louder the harder it was. */
    private static void playReleaseSound(ServerPlayer driver, int fromNotch) {
        float volume = Math.min(1f, 0.4f + 0.08f * -fromNotch);
        SoundEvent steam = BuiltInRegistries.SOUND_EVENT.getOptional(STEAM_SOUND).orElse(SoundEvents.FIRE_EXTINGUISH);
        driver.level().playSound(null, driver.getX(), driver.getY(), driver.getZ(), steam, SoundSource.BLOCKS,
            volume, 0.8f);
        driver.level().playSound(null, driver.getX(), driver.getY(), driver.getZ(), SoundEvents.FIRE_EXTINGUISH,
            SoundSource.BLOCKS, volume * 0.5f, 0.6f);
    }

    /**
     * The player currently at this train's controls, or null. Unlike isActive this includes the times the
     * mascon hands the train to Create (derailed, "hold space" station approach): the driver is still there.
     */
    static ServerPlayer driverOf(Train train) {
        State s = STATES.get(train.id);
        return s == null || now() - s.lastUpdate > STALE_TICKS ? null : s.driver;
    }

    /** The train this player is currently driving with the mascon, or null. */
    static Train trainDrivenBy(ServerPlayer player) {
        for (Map.Entry<UUID, State> entry : STATES.entrySet()) {
            if (player.equals(entry.getValue().driver)) {
                Train train = Create.RAILWAYS.trains.get(entry.getKey());
                if (train != null && player.equals(driverOf(train))) {
                    return train;
                }
            }
        }
        return null;
    }

    /** What the driver's HUD shows as the next station, or null when there is no driver or station assist is off. */
    static StationInfo stationInfoOf(Train train) {
        State s = active(train);
        return s == null || !s.stationAssist ? null : stationInfo(s);
    }

    /** A train driven with a mascon whose next stop is {@code station}, within {@code range} of it, or null. */
    public static Train approaching(GlobalStation station, double range) {
        for (Map.Entry<UUID, State> entry : STATES.entrySet()) {
            State s = entry.getValue();
            if (s.station == station && s.stationDistance < range && now() - s.lastUpdate <= STALE_TICKS) {
                return Create.RAILWAYS.trains.get(entry.getKey());
            }
        }
        return null;
    }

    /** True while a driver's mascon owns this train's speed (Create's manual acceleration is skipped). */
    public static boolean isActive(Train train) {
        return active(train) != null;
    }

    /** Runs in place of Create's passive slowdown. Returns false to leave the train to Create. */
    public static boolean tick(Train train) {
        State s = active(train);
        if (s == null) {
            return false;
        }
        train.manualTick = false;
        DrivenSchedule.drive(train);
        Conductor.driving(train, s.driver);
        s.wheel = StationInfo.GRIP;

        // Never move with the doors open (the conductor can open them anywhere once stopped).
        if (Conductor.doorsOpen(train)) {
            train.speed = 0;
            s.station = null;
            syncDriver(train, s);
            return true;
        }

        // Stay put at a station until the driver notches up into power (reverser not in neutral) after arriving,
        // and, with a conductor on board, until the conductor has closed the doors.
        if (train.getCurrentStation() != null) {
            if (!s.departRequested || s.neutral || !Conductor.departureAllowed(train)) {
                train.speed = 0;
                s.station = null;
                syncDriver(train, s);
                return true;
            }
            train.leaveStation();
            DrivenSchedule.departed(train);
        }
        s.departRequested = false;

        double a = train.acceleration() * TrainCabServerConfig.masconRate;
        double top = train.maxSpeed() * AllConfigs.server().trains.manualTrainSpeedModifier.getF();
        double v = train.speed * s.direction;
        // The conductor's emergency brake overrides the handle; neutral cuts traction only (brakes still work).
        int notch = Conductor.emergency(train) ? EB : s.neutral && s.notch > 0 ? 0 : s.notch;

        // Power while rolling the wrong way acts as a plain brake; N has a little rolling resistance.
        double traction = notch > 0 && v >= 0 ? a * notch / MAX_POWER : 0;
        double brake = notch > 0 ? (v < 0 ? a : 0)
            : notch == 0 ? a / 40
            : notch == EB ? a * 3
            : a * 2 * -notch / MAX_BRAKE;
        if (notch > 0 && v >= 0) {
            train.burnFuel();
        }

        // Wet rails: too much power spins the wheels (空転), too much brake locks them (滑走), and then they
        // give only part of what the rails could take until the driver notches back.
        double grip = TrainCabServerConfig.wheelSlip ? Adhesion.grip(s.driver.level(), s.driver.blockPosition())
            : Double.POSITIVE_INFINITY;
        boolean spin = traction > a * grip;
        boolean slide = v != 0 && brake > a * grip * Adhesion.BRAKE_GRIP;
        if (Adhesion.slips(train, spin || slide, v)) {
            if (spin) {
                traction = a * grip * Adhesion.SLIPPING_GRIP;
                s.wheel = StationInfo.SLIP;
            } else {
                brake = a * grip * Adhesion.BRAKE_GRIP * Adhesion.SLIPPING_GRIP;
                s.wheel = StationInfo.SLIDE;
            }
        }
        if (s.wheel != StationInfo.GRIP) {
            Adhesion.slipSound(s.driver.level(), s.driver.getX(), s.driver.getY(), s.driver.getZ(), s.wheel);
        }

        // A gradient pulls the train downhill; standing still, the brake holds it as long as it is the stronger.
        v += Math.min(traction, Math.max(top - v, 0))
            - a * TrainCabServerConfig.gradeEffect * Adhesion.slope(train) * s.direction;
        v = Math.abs(v) <= brake ? 0 : v - Math.signum(v) * brake;
        train.speed = Mth.clamp(v, -train.maxSpeed(), train.maxSpeed()) * s.direction;

        if (s.stationAssist) {
            trackStation(train, s);
            // Create only arrives at a station through its own navigation; a driver stopping by hand counts too.
            if (train.speed == 0 && s.station != null && Math.abs(s.stationDistance) <= TrainCabServerConfig.stopTolerance) {
                train.arriveAt(s.station);
                TrainCabNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> s.driver), new StopResult((float) s.stationDistance));
                s.station = null;
            }
        } else {
            s.station = null;
        }
        syncDriver(train, s);
        return true;
    }

    /** Updates the next station ahead and the distance from the train's front to its stop point. */
    private static void trackStation(Train train, State s) {
        // Between searches the train only moves along the path already found.
        if (--s.searchIn > 0) {
            if (s.station != null) {
                s.stationDistance -= Math.abs(train.speed);
            }
            return;
        }
        s.searchIn = SEARCH_INTERVAL;
        GlobalStation[] found = {null};
        double[] distance = {0};
        train.navigation.search(TrainCabServerConfig.stationSearchRange, s.direction > 0, null, (d, cost, reachedVia, current, station) -> {
            Train present = station.getPresentTrain();
            if (present != null && present != train) {
                return false;
            }
            found[0] = station;
            distance[0] = d - (current.getSecond().getLength() - station.getLocationOn(current.getSecond()));
            return true;
        });

        // Once the front passes the stop point the search no longer finds that station, so keep counting
        // the overrun down by the distance travelled instead of jumping straight to the next one.
        if (s.station != null && found[0] != s.station && s.stationDistance < TrainCabServerConfig.overrunLimit) {
            s.stationDistance -= Math.abs(train.speed);
            if (s.stationDistance < -TrainCabServerConfig.overrunLimit) {
                s.station = null;
            }
            return;
        }
        s.station = found[0];
        s.stationDistance = distance[0];
    }

    private static StationInfo stationInfo(State s) {
        return s.station == null ? new StationInfo("", 0, s.wheel)
            : new StationInfo(s.station.name, (float) s.stationDistance, s.wheel);
    }


    /**
     * Create only sends the driver the train's speed while a movement key is held (every 5 ticks),
     * but mascon drivers mostly have their hands off the keys. Send it every tick so the HUD stays smooth,
     * together with the next station.
     */
    private static void syncDriver(Train train, State s) {
        AllPackets.getChannel().send(PacketDistributor.PLAYER.with(() -> s.driver), new TrainHUDUpdatePacket(train));
        TrainCabNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> s.driver), stationInfo(s));
        if (now() % PASSENGER_SYNC_TICKS == 0) {
            StationInfo info = stationInfo(s);
            for (ServerPlayer passenger : Conductor.onBoard(train)) {
                if (!passenger.equals(s.driver)) {
                    TrainCabNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> passenger), info);
                }
            }
        }
    }

    private static State active(Train train) {
        State s = STATES.get(train.id);
        if (s == null) {
            return null;
        }
        if (now() - s.lastUpdate > STALE_TICKS) {
            STATES.remove(train.id);
            DrivenSchedule.release(train);
            return null;
        }
        // Derailment and Create's own "hold space to approach station" navigation keep priority.
        return train.derailed || train.navigation.destination != null ? null : s;
    }

    private static int now() {
        return ServerLifecycleHooks.getCurrentServer().getTickCount();
    }
}
