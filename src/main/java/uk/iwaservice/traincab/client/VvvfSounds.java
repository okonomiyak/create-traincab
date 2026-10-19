package uk.iwaservice.traincab.client;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.BufferUtils;
import uk.iwaservice.traincab.TrainCabConfig;
import uk.iwaservice.traincab.TrainCabItems;
import uk.iwaservice.traincab.TrainCabMod;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * VVVF traction sound for the trains near the player that carry a VVVFインバーター, coming from the nearest carriage
 * with one.
 * Clients only see where carriages are, not the notch, so 力行 / 惰行 / 回生 comes from the acceleration
 * (except for the train this player drives with the mascon, which uses the notch).
 */
public final class VvvfSounds {

    private static final ResourceLocation LOCATION = new ResourceLocation(TrainCabMod.MODID, "vvvf");
    private static final AudioFormat FORMAT = new AudioFormat(VvvfSynth.FS, 16, 1, true, false);
    private static final double RANGE = 48;
    /** Heard up to ~48 blocks. */
    private static final float VOLUME = 3;
    /** Streaming sources are scarce (shared with uploaded sounds), so only the nearest trains get one. */
    private static final int MAX_TRAINS = 4;
    /**
     * Minecraft queues 4 stream buffers and tops them up each tick; 100 ms ones keep the sound ~0.3 s behind the
     * train while surviving lag spikes of about that long.
     */
    private static final int CHUNK_SAMPLES = VvvfSynth.FS / 10;
    /** Accelerating or slowing by more than this share of Create's acceleration counts as 力行 / 回生 (N coasts at 1/40). */
    private static final double ACCEL_THRESHOLD = 0.1;

    // Client thread only.
    private static final Map<UUID, TrainSound> SOUNDS = new HashMap<>();

    private VvvfSounds() {
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Map<UUID, CarriageContraptionEntity> nearest = new HashMap<>();
        if (mc.level != null && player != null && TrainCabConfig.vvvfSound) {
            for (Entity entity : mc.level.entitiesForRendering()) {
                if (entity instanceof CarriageContraptionEntity cce && cce.getCarriage() != null
                    && cce.distanceToSqr(player) < RANGE * RANGE && hasInverter(cce)) {
                    nearest.merge(cce.getCarriage().train.id, cce,
                        (a, b) -> a.distanceToSqr(player) <= b.distanceToSqr(player) ? a : b);
                }
            }
        }
        List<UUID> heard = nearest.keySet().stream()
            .sorted(Comparator.comparingDouble(id -> nearest.get(id).distanceToSqr(player)))
            .limit(MAX_TRAINS).toList();
        SOUNDS.entrySet().removeIf(e -> {
            if (heard.contains(e.getKey())) {
                return false;
            }
            e.getValue().stopped = true;
            return true;
        });
        for (UUID id : heard) {
            SOUNDS.computeIfAbsent(id, k -> new TrainSound()).update(mc.getSoundManager(), nearest.get(id));
        }
    }

    // ponytail: scans the carriage's blocks every tick; cache per entity if huge trains show up in profiling.
    private static boolean hasInverter(CarriageContraptionEntity cce) {
        return cce.getContraption() != null && cce.getContraption().getBlocks().values().stream()
            .anyMatch(info -> info.state().is(TrainCabItems.INVERTER.get()));
    }

    private static final class TrainSound extends AbstractSoundInstance implements TickableSoundInstance {

        private final VvvfSynth synth = new VvvfSynth();
        private CarriageContraptionEntity carriage;
        /** Blocks / second. */
        private double speed;
        /** Blocks / second². */
        private double accel;
        private int retry;
        boolean stopped;

        TrainSound() {
            super(LOCATION, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            volume = VOLUME;
        }

        void update(SoundManager sounds, CarriageContraptionEntity cce) {
            carriage = cce;
            tick();
            double moved = cce.position().distanceTo(new Vec3(cce.xo, cce.yo, cce.zo)) * 20;
            double last = speed;
            speed += (moved - speed) * 0.5;
            accel += ((speed - last) * 20 - accel) * 0.2;

            double threshold = AllConfigs.server().trains.trainAcceleration.getF() * ACCEL_THRESHOLD;
            int state = accel > threshold ? VvvfSynth.POWER : accel < -threshold ? VvvfSynth.BRAKE : VvvfSynth.COAST;
            Train train = cce.getCarriage().train;
            // Our own mascon train: the notch says it directly, once moving (power held at a station makes no sound).
            if (train == TrainDrivingState.getActiveTrain() && TrainDrivingState.hasAdvancedCab() && speed > 0.1) {
                int notch = TrainDrivingState.getNotch();
                state = notch > 0 ? VvvfSynth.POWER : notch < 0 ? VvvfSynth.BRAKE : VvvfSynth.COAST;
            }
            synth.state = state;
            synth.target = VvvfSynth.F_MAX * speed / AllConfigs.server().trains.trainTopSpeed.getF();

            // Out of sources, or the stream ran dry in a lag spike: try again every second.
            if (!sounds.isActive(this) && --retry <= 0) {
                retry = 20;
                sounds.play(this);
            }
        }

        @Override
        public void tick() {
            if (carriage != null) {
                x = carriage.getX();
                y = carriage.getY();
                z = carriage.getZ();
            }
        }

        @Override
        public boolean isStopped() {
            return stopped;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            sound = new Sound(LOCATION.toString(), ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
            return new WeighedSoundEvents(LOCATION, null);
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(new AudioStream() {
                private final float[] samples = new float[CHUNK_SAMPLES];

                @Override
                public AudioFormat getFormat() {
                    return FORMAT;
                }

                @Override
                public ByteBuffer read(int size) {
                    synth.render(samples);
                    ByteBuffer buf = BufferUtils.createByteBuffer(samples.length * 2);
                    for (float s : samples) {
                        buf.putShort((short) (s * 32767));
                    }
                    return buf.flip();
                }

                @Override
                public void close() {
                }
            });
        }
    }
}
