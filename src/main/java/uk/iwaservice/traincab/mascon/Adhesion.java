package uk.iwaservice.traincab.mascon;

import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import uk.iwaservice.traincab.TrainCabServerConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** Gradients and wheel slip, for mascon driving (Mascon.tick) and for Create's own automatic driving (auto). */
public final class Adhesion {

    /**
     * Most the rails take before the wheels slip, as a share of full power (P5, or Create's acceleration when
     * automatic): rain spins P4 and up, snow P3 and up. Brakes grip BRAKE_GRIP times that (rain slides B6 and up,
     * snow B4 and up; EB always when wet).
     */
    private static final double RAIN_GRIP = 0.7;
    private static final double SNOW_GRIP = 0.5;
    static final double BRAKE_GRIP = 2.2;
    /** Spinning or sliding wheels give only this share of what the rails could take. */
    static final double SLIPPING_GRIP = 0.5;
    private static final int SLIP_SOUND_TICKS = 4;
    /** Wheels only spin or slide below this speed. */
    private static final double SLIP_MAX_KMH = 45;
    /** Chance per tick, at a standstill, that wheels asked too much of start to spin or slide (less the faster). */
    private static final double SLIP_CHANCE = 0.04;
    /** Each spin or slide lasts this many ticks, picked at random in [min, max). */
    private static final int SLIP_MIN_TICKS = 10;
    private static final int SLIP_MAX_TICKS = 40;

    /** Ticks left of each train's current spin or slide. Server thread only. */
    // ponytail: entries of trains removed mid-slip stay (one small entry each).
    private static final Map<UUID, Integer> SLIPPING = new HashMap<>();
    private static final Random RANDOM = new Random();

    private Adhesion() {
    }

    /**
     * Create's automatic driving (a schedule, or holding space to approach a station), once it has set this tick's
     * speed from {@code before}: on wet rails its acceleration spins the wheels, and uphill the train is held back.
     * Downhill it brakes to keep its speed and its wheels never slide, so Create's stopping distances stay right.
     * Not called while the mascon drives.
     */
    public static void auto(Train train, double before) {
        double dir = Math.signum(train.speed);
        if (dir == 0 || train.derailed || train.graph == null || train.navigation.destination == null) {
            return;
        }
        double a = train.acceleration();
        double v = train.speed * dir;
        double was = before * dir;
        CarriageContraptionEntity cce = train.carriages.get(0).anyAvailableEntity();
        if (TrainCabServerConfig.wheelSlip && cce != null) {
            double grip = grip(cce.level(), cce.blockPosition());
            if (slips(train, v - was > a * grip, was)) {
                v = was + a * grip * SLIPPING_GRIP;
                slipSound(cce.level(), cce.getX(), cce.getY(), cce.getZ(), StationInfo.SLIP);
            }
        }
        // The same pull as on a mascon-driven train (Mascon.tick), whose full power is masconRate of Create's.
        double uphill = slope(train) * dir;
        if (uphill > 0) {
            v = Math.max(v - a * TrainCabServerConfig.masconRate * TrainCabServerConfig.gradeEffect * uphill, 0);
        }
        train.speed = v * dir;
    }

    /**
     * Whether a train whose wheels are asked more than the rails take ({@code over}) spins or slides this tick,
     * moving at {@code speed} blocks / tick. Only below SLIP_MAX_KMH, and not all the time: each tick a spin or slide
     * of SLIP_MIN_TICKS..SLIP_MAX_TICKS may start, the likelier the slower; asking less stops it at once.
     */
    static boolean slips(Train train, boolean over, double speed) {
        double kmh = Math.abs(speed) * 20 * 3.6;
        if (!over || kmh >= SLIP_MAX_KMH) {
            SLIPPING.remove(train.id);
            return false;
        }
        int left = SLIPPING.getOrDefault(train.id, 0);
        if (left == 0 && RANDOM.nextDouble() < SLIP_CHANCE * (1 - kmh / SLIP_MAX_KMH)) {
            left = SLIP_MIN_TICKS + RANDOM.nextInt(SLIP_MAX_TICKS - SLIP_MIN_TICKS);
        }
        if (left == 0) {
            return false;
        }
        if (left == 1) {
            SLIPPING.remove(train.id);
        } else {
            SLIPPING.put(train.id, left - 1);
        }
        return true;
    }

    /** Sine of the train's average gradient, positive when its leading end is higher. */
    static double slope(Train train) {
        double rise = 0;
        double length = 0;
        for (Carriage carriage : train.carriages) {
            Vec3 lead = carriage.getLeadingPoint().getPosition(train.graph);
            Vec3 trail = carriage.getTrailingPoint().getPosition(train.graph);
            double d = lead.distanceTo(trail);
            // A carriage halfway through a portal has its two points in different dimensions.
            if (d > 0.5 && d < 64) {
                rise += lead.y - trail.y;
                length += d;
            }
        }
        return length == 0 ? 0 : rise / length;
    }

    /** RAIN_GRIP or SNOW_GRIP where it rains or snows on pos (not in tunnels or under roofs), else no limit. */
    static double grip(Level level, BlockPos pos) {
        if (!level.isRaining() || !level.canSeeSky(pos)) {
            return Double.POSITIVE_INFINITY;
        }
        return switch (level.getBiome(pos).value().getPrecipitationAt(pos)) {
            case RAIN -> RAIN_GRIP;
            case SNOW -> SNOW_GRIP;
            default -> Double.POSITIVE_INFINITY;
        };
    }

    /** Wheels scraping, every few ticks while they spin (StationInfo.SLIP) or slide. */
    static void slipSound(Level level, double x, double y, double z, int wheel) {
        if (level.getGameTime() % SLIP_SOUND_TICKS == 0) {
            level.playSound(null, x, y, z, SoundEvents.GRINDSTONE_USE, SoundSource.BLOCKS, 0.6f,
                wheel == StationInfo.SLIP ? 1.2f : 1.8f);
        }
    }
}
