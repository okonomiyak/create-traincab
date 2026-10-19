package uk.iwaservice.traincab.block;

import com.simibubi.create.content.trains.entity.Train;
import com.simibubi.create.content.trains.station.GlobalStation;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import org.jetbrains.annotations.Nullable;
import uk.iwaservice.traincab.TrainCabServerConfig;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity.Clip;
import uk.iwaservice.traincab.client.CustomSoundClient;
import uk.iwaservice.traincab.mascon.Mascon;
import uk.iwaservice.traincab.sound.CustomSounds;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 接近メロディ of a 高度駅 or 放送装置: played once at the block (and its スピーカー) when a train that will stop at the
 * station comes within approachDistance, and cut off when that train stops. An uploaded sound, the built-in chime,
 * or nothing.
 */
public final class ApproachMelody {

    /** Built-in chime: {tick, note-block note (0 = F#3 .. 24 = F#5)}, a rising chime played twice. */
    private static final int[][] CHIME = {
        {0, 10}, {4, 14}, {8, 17}, {12, 22}, {24, 10}, {28, 14}, {32, 17}, {36, 22},
    };
    private static final int CHIME_LENGTH = 48;
    /** Range of the per-block distance (blocks along the track). */
    public static final int MIN_DISTANCE = 10, MAX_DISTANCE = 1000;
    /** No 接近メロディ this long after a train leaves the station (ticks), so it doesn't follow the departure. */
    private static final int QUIET_AFTER_DEPARTURE = 100;

    @Nullable
    private Clip clip;
    private boolean chime;
    /** What chime is when nothing was saved yet. */
    private final boolean defaultChime;
    /** The train it last played for, so it plays once per approach. */
    @Nullable
    private UUID approachedTrain;
    /** Tick of the built-in chime being played, or -1. */
    private int chimeTick = -1;
    /** Game time the uploaded sound ends, so stopping it can't cut off a departure melody played after it. */
    private long soundEnds;
    /** Starts this far from the station (blocks along the track); 0 = the server's approachDistance. */
    private int distance;
    /** The train standing at the station at the last update, and when the last one left. */
    @Nullable
    private UUID lastPresent;
    private long departedAt = Long.MIN_VALUE / 2;

    public ApproachMelody(boolean defaultChime) {
        this.defaultChime = defaultChime;
        chime = defaultChime;
    }

    @Nullable
    public Clip clip() {
        return clip;
    }

    public boolean chime() {
        return chime;
    }

    /** How far from the station it starts (blocks along the track). */
    public int distance() {
        return distance > 0 ? distance : (int) TrainCabServerConfig.approachDistance;
    }

    public void setDistance(int distance) {
        this.distance = Math.max(MIN_DISTANCE, Math.min(MAX_DISTANCE, distance));
    }

    public void set(@Nullable Clip clip, boolean chime) {
        this.clip = clip;
        this.chime = chime;
    }

    /** Every server tick: plays the built-in chime's notes. */
    public void tick(ServerLevel level, BlockPos pos) {
        if (chimeTick < 0) {
            return;
        }
        for (int[] note : CHIME) {
            if (note[0] == chimeTick) {
                float pitch = (float) Math.pow(2, (note[1] - 12) / 12.0);
                // Volume above 1 only widens the range: heard up to ~32 blocks away, like the departure bell.
                List<BlockPos> at = new ArrayList<>(SpeakerLinks.of(level, pos));
                at.add(pos);
                for (BlockPos p : at) {
                    level.playSound(null, p, SoundEvents.NOTE_BLOCK_CHIME.value(), SoundSource.BLOCKS, 2f, pitch);
                    level.playSound(null, p, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 1f, pitch);
                }
            }
        }
        if (++chimeTick >= CHIME_LENGTH) {
            chimeTick = -1;
        }
    }

    /** Every few ticks: starts it for a train approaching {@code station}, stops it once that train is there. */
    public void update(ServerLevel level, BlockPos pos, @Nullable GlobalStation station) {
        Train present = station == null ? null : station.getPresentTrain();
        UUID presentId = present == null ? null : present.id;
        if (lastPresent != null && !lastPresent.equals(presentId)) {
            departedAt = level.getGameTime();
        }
        lastPresent = presentId;
        // The train it played for has stopped: done (another train already standing at a 双方向 station doesn't
        // cut it off).
        if (present != null && present.id.equals(approachedTrain)) {
            stop(level, pos);
        }
        Train approaching = station == null ? null : approachingTrain(station, distance());
        if (approaching == null) {
            approachedTrain = null;
        } else if (!approaching.id.equals(approachedTrain)
            && level.getGameTime() - departedAt >= QUIET_AFTER_DEPARTURE) {
            // Within the quiet time it stays unmarked, so it still plays once that is over if the train is coming.
            approachedTrain = approaching.id;
            play(level, pos);
        }
    }

    /** A train on its way to stop at the station and close enough: scheduled, or driven with a mascon. */
    @Nullable
    private static Train approachingTrain(GlobalStation station, double range) {
        Train train = station.getNearestTrain();
        if (train != null && train.navigation.destination == station && train.navigation.distanceToDestination < range) {
            return train;
        }
        return Mascon.approaching(station, range);
    }

    private void stop(ServerLevel level, BlockPos pos) {
        chimeTick = -1;
        if (level.getGameTime() < soundEnds) {
            CustomSounds.play(level, pos, "");
        }
        soundEnds = 0;
    }

    private void play(ServerLevel level, BlockPos pos) {
        if (clip != null && CustomSounds.exists(level.getServer(), clip.hash())) {
            CustomSounds.play(level, pos, clip.hash());
            soundEnds = level.getGameTime() + CustomSounds.ticks(clip.bytes());
        } else if (clip == null && chime) {
            chimeTick = 0;
        }
    }

    public void write(CompoundTag tag) {
        tag.putBoolean("ApproachChime", chime);
        if (distance > 0) {
            tag.putInt("ApproachDistance", distance);
        }
        if (clip != null) {
            CompoundTag c = new CompoundTag();
            c.putString("Hash", clip.hash());
            c.putString("Name", clip.name());
            c.putInt("Bytes", clip.bytes());
            tag.put("Approach", c);
        }
    }

    /** {@code client}: read from a packet on the client, which starts downloading the sound. */
    public void read(CompoundTag tag, boolean client) {
        chime = tag.contains("ApproachChime") ? tag.getBoolean("ApproachChime") : defaultChime;
        distance = tag.getInt("ApproachDistance");
        CompoundTag c = tag.getCompound("Approach");
        clip = c.contains("Hash") ? new Clip(c.getString("Hash"), c.getString("Name"), c.getInt("Bytes")) : null;
        if (client && clip != null) {
            CustomSoundClient.prefetch(clip.hash());
        }
    }
}
