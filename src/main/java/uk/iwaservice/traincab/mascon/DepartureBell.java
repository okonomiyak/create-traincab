package uk.iwaservice.traincab.mascon;

import com.simibubi.create.Create;
import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import uk.iwaservice.traincab.TrainCabItems;
import uk.iwaservice.traincab.block.AdvancedStationBlockEntity;
import uk.iwaservice.traincab.block.BellSound;
import uk.iwaservice.traincab.block.SpeakerLinks;
import uk.iwaservice.traincab.sound.CustomSounds;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 発車ベル / 発車メロディ. The conductor of a train stopped at a station rings the departure bell block nearest to
 * them; the doors cannot be closed until it has finished (or the conductor stops it with the same key).
 * It plays the bell's uploaded sound once (see CustomSounds), or else the built-in bell / melody.
 * A 高度駅 is a departure bell too, and also rings by itself before a scheduled train leaves it.
 */
public final class DepartureBell {

    /** How far (blocks) from the conductor a departure bell block is looked for. */
    private static final int SEARCH_RANGE = 16;
    /** Above 1 only widens the range: heard up to ~32 blocks away. */
    private static final float VOLUME = 2f;
    /** Electric bell: a note every BELL_INTERVAL ticks for BELL_TICKS. */
    private static final int BELL_TICKS = 100;
    private static final int BELL_INTERVAL = 2;
    /** Melody: {tick, note-block note (0 = F#3 .. 24 = F#5)}, played twice. */
    private static final int[][] MELODY = {
        {0, 22}, {4, 18}, {8, 13}, {12, 18}, {16, 20}, {20, 13}, {24, 17}, {28, 20},
        {32, 22}, {36, 20}, {40, 18}, {44, 22}, {48, 20}, {52, 17}, {56, 18},
    };
    private static final int MELODY_LENGTH = 64;
    private static final int MELODY_REPEATS = 2;

    private static final class Ringing {
        ServerLevel level;
        BlockPos pos;
        boolean melody;
        /** Uploaded sound playing on the clients, and its length in ticks. */
        String sound;
        int soundTicks;
        int tick;
    }

    // Server thread only.
    private static final Map<UUID, Ringing> RINGING = new HashMap<>();
    /** Scheduled trains whose departure melody has played at this station (its id), so they may now leave. */
    private static final Map<UUID, UUID> RUNG = new HashMap<>();

    private DepartureBell() {
    }

    /** The conductor's key: start ringing the nearest departure bell, or stop it if it is already ringing. */
    static void toggle(ServerPlayer conductor, Train train) {
        Ringing stopped = RINGING.remove(train.id);
        if (stopped != null) {
            if (stopped.sound != null) {
                CustomSounds.play(stopped.level, stopped.pos, "");
            }
            return;
        }
        if (train.getCurrentStation() == null) {
            conductor.displayClientMessage(Component.literal("発車ベルは駅停車中のみ鳴らせます"), true);
            return;
        }
        ServerLevel level = conductor.serverLevel();
        Optional<BlockPos> pos = BlockPos.findClosestMatch(conductor.blockPosition(), SEARCH_RANGE, SEARCH_RANGE,
            p -> isBell(level.getBlockState(p)));
        if (pos.isEmpty() || !(level.getBlockEntity(pos.get()) instanceof BellSound bell)) {
            conductor.displayClientMessage(Component.literal("近くに発車ベルがありません"), true);
            return;
        }
        ring(train, level, pos.get(), bell);
    }

    private static boolean isBell(BlockState state) {
        return state.is(TrainCabItems.DEPARTURE_BELL.get()) || state.is(TrainCabItems.ADVANCED_STATION.get());
    }

    private static void ring(Train train, ServerLevel level, BlockPos pos, BellSound bell) {
        Ringing ringing = new Ringing();
        ringing.level = level;
        ringing.pos = pos;
        ringing.melody = bell.melody();
        if (bell.hasSound() && CustomSounds.exists(level.getServer(), bell.soundHash())) {
            ringing.sound = bell.soundHash();
            ringing.soundTicks = CustomSounds.ticks(bell.soundBytes());
            CustomSounds.play(level, pos, ringing.sound);
        }
        RINGING.put(train.id, ringing);
    }

    /**
     * Called by ScheduleRuntimeMixin while a scheduled train is about to set off for its next instruction: at a
     * 高度駅, rings its departure melody once and keeps the train there until it has finished.
     */
    public static boolean holdDeparture(Train train) {
        if (RINGING.containsKey(train.id)) {
            return true;
        }
        GlobalStation station = train.getCurrentStation();
        if (station == null || station.id.equals(RUNG.get(train.id))) {
            return false;
        }
        ServerLevel level = ServerLifecycleHooks.getCurrentServer().getLevel(station.getBlockEntityDimension());
        BlockPos pos = station.getBlockEntityPos();
        if (level == null || pos == null || !level.isLoaded(pos)
            || !(level.getBlockEntity(pos) instanceof AdvancedStationBlockEntity bell)) {
            return false;
        }
        RUNG.put(train.id, station.id);
        ring(train, level, pos, bell);
        return true;
    }

    public static boolean isRinging(Train train) {
        return RINGING.containsKey(train.id);
    }

    static void tick() {
        // Forget the stop once the train has left it, so it rings again next time.
        RUNG.entrySet().removeIf(e -> {
            Train train = Create.RAILWAYS.trains.get(e.getKey());
            GlobalStation at = train == null ? null : train.getCurrentStation();
            return at == null || !at.id.equals(e.getValue());
        });
        for (Iterator<Ringing> it = RINGING.values().iterator(); it.hasNext(); ) {
            Ringing r = it.next();
            if (r.sound != null) {
                // Playing on the clients; just wait for it to end.
            } else if (r.melody) {
                int t = r.tick % MELODY_LENGTH;
                for (int[] note : MELODY) {
                    if (note[0] == t) {
                        float pitch = (float) Math.pow(2, (note[1] - 12) / 12.0);
                        play(r, SoundEvents.NOTE_BLOCK_HARP.value(), VOLUME, pitch);
                        play(r, SoundEvents.NOTE_BLOCK_CHIME.value(), VOLUME / 2, pitch);
                    }
                }
            } else if (r.tick % BELL_INTERVAL == 0) {
                play(r, SoundEvents.NOTE_BLOCK_BELL.value(), VOLUME, 1.5f);
            }
            r.tick++;
            if (!isBell(r.level.getBlockState(r.pos))) {
                if (r.sound != null) {
                    CustomSounds.play(r.level, r.pos, "");
                }
                it.remove();
            } else if (r.tick >= (r.sound != null ? r.soundTicks
                : r.melody ? MELODY_LENGTH * MELODY_REPEATS : BELL_TICKS)) {
                it.remove();
            }
        }
    }

    private static void play(Ringing r, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        r.level.playSound(null, r.pos, sound, SoundSource.BLOCKS, volume, pitch);
        for (BlockPos speaker : SpeakerLinks.of(r.level, r.pos)) {
            r.level.playSound(null, speaker, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }
}
