package uk.iwaservice.traincab.mascon;

import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.state.BlockState;
import uk.iwaservice.traincab.TrainCabItems;
import uk.iwaservice.traincab.block.DepartureBellBlock;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 発車ベル / 発車メロディ. The conductor of a train stopped at a station rings the departure bell block nearest to
 * them; the doors cannot be closed until it has finished (or the conductor stops it with the same key).
 */
final class DepartureBell {

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
        int tick;
    }

    // Server thread only.
    private static final Map<UUID, Ringing> RINGING = new HashMap<>();

    private DepartureBell() {
    }

    /** The conductor's key: start ringing the nearest departure bell, or stop it if it is already ringing. */
    static void toggle(ServerPlayer conductor, Train train) {
        if (RINGING.remove(train.id) != null) {
            return;
        }
        if (train.getCurrentStation() == null) {
            conductor.displayClientMessage(Component.literal("発車ベルは駅停車中のみ鳴らせます"), true);
            return;
        }
        ServerLevel level = conductor.serverLevel();
        Optional<BlockPos> pos = BlockPos.findClosestMatch(conductor.blockPosition(), SEARCH_RANGE, SEARCH_RANGE,
            p -> level.getBlockState(p).is(TrainCabItems.DEPARTURE_BELL.get()));
        if (pos.isEmpty()) {
            conductor.displayClientMessage(Component.literal("近くに発車ベルがありません"), true);
            return;
        }
        BlockState state = level.getBlockState(pos.get());
        Ringing ringing = new Ringing();
        ringing.level = level;
        ringing.pos = pos.get();
        ringing.melody = state.getValue(DepartureBellBlock.MELODY);
        RINGING.put(train.id, ringing);
    }

    static boolean isRinging(Train train) {
        return RINGING.containsKey(train.id);
    }

    static void tick() {
        for (Iterator<Ringing> it = RINGING.values().iterator(); it.hasNext(); ) {
            Ringing r = it.next();
            if (r.melody) {
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
            if (r.tick >= (r.melody ? MELODY_LENGTH * MELODY_REPEATS : BELL_TICKS)
                || !r.level.getBlockState(r.pos).is(TrainCabItems.DEPARTURE_BELL.get())) {
                it.remove();
            }
        }
    }

    private static void play(Ringing r, net.minecraft.sounds.SoundEvent sound, float volume, float pitch) {
        r.level.playSound(null, r.pos, sound, SoundSource.BLOCKS, volume, pitch);
    }
}
