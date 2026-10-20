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
            if (v - was > a * grip) {
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
