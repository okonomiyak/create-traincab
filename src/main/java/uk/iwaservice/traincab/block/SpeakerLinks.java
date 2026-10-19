package uk.iwaservice.traincab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import uk.iwaservice.traincab.TrainCabItems;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The スピーカー linked to a 放送装置 or 高度駅 (with the スピーカー接続ツール): each plays whatever the source plays.
 * Kept on the source's block entity.
 */
public final class SpeakerLinks {

    /** How far a speaker may be from its source. */
    public static final int RANGE = 128;

    private final Set<BlockPos> speakers = new LinkedHashSet<>();

    /** Links the speaker, or unlinks it if it already was; true if it is linked now. */
    public boolean toggle(BlockPos speaker) {
        if (speakers.remove(speaker)) {
            return false;
        }
        speakers.add(speaker.immutable());
        return true;
    }

    public int size() {
        return speakers.size();
    }

    public void write(CompoundTag tag) {
        if (!speakers.isEmpty()) {
            tag.putLongArray("Speakers", speakers.stream().mapToLong(BlockPos::asLong).toArray());
        }
    }

    public void read(CompoundTag tag) {
        speakers.clear();
        for (long pos : tag.getLongArray("Speakers")) {
            speakers.add(BlockPos.of(pos));
        }
    }

    /** The speakers linked to the 放送装置 / 高度駅 at {@code source}; speakers since broken are forgotten. */
    public static List<BlockPos> of(Level level, BlockPos source) {
        if (!(level.getBlockEntity(source) instanceof BellSound bell)) {
            return List.of();
        }
        Set<BlockPos> linked = bell.speakers().speakers;
        if (linked.removeIf(p -> level.isLoaded(p) && !level.getBlockState(p).is(TrainCabItems.SPEAKER.get()))) {
            ((BlockEntity) bell).setChanged();
        }
        return new ArrayList<>(linked);
    }
}
