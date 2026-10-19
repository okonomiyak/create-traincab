package uk.iwaservice.traincab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import uk.iwaservice.traincab.TrainCabItems;
import uk.iwaservice.traincab.client.CustomSoundClient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The sound a departure bell plays: an uploaded one (see CustomSounds), or none for the built-in bell / melody.
 * Also the 車内放送 the conductor can pick from with N once the bell is built into a train.
 */
public class DepartureBellBlockEntity extends BlockEntity {

    /** Most 車内放送 one bell holds. */
    public static final int MAX_ANNOUNCEMENTS = 8;

    /** An uploaded sound: its hash (see CustomSounds), display name and size in bytes. */
    public record Clip(String hash, String name, int bytes) {
    }

    private String soundHash = "";
    private String soundName = "";
    private int soundBytes;
    private final List<Clip> announcements = new ArrayList<>();

    public DepartureBellBlockEntity(BlockPos pos, BlockState state) {
        super(TrainCabItems.DEPARTURE_BELL_ENTITY.get(), pos, state);
    }

    public boolean hasSound() {
        return !soundHash.isEmpty();
    }

    public String soundHash() {
        return soundHash;
    }

    public String soundName() {
        return soundName;
    }

    public int soundBytes() {
        return soundBytes;
    }

    public void setSound(String hash, String name, int bytes) {
        soundHash = hash;
        soundName = name;
        soundBytes = bytes;
        changed();
    }

    public List<Clip> announcements() {
        return Collections.unmodifiableList(announcements);
    }

    public void addAnnouncement(Clip clip) {
        announcements.add(clip);
        changed();
    }

    public void removeAnnouncement(int index) {
        if (index >= 0 && index < announcements.size()) {
            announcements.remove(index);
            changed();
        }
    }

    public void setBuiltin(boolean melody) {
        soundHash = "";
        soundName = "";
        soundBytes = 0;
        level.setBlock(worldPosition, getBlockState().setValue(DepartureBellBlock.MELODY, melody), Block.UPDATE_ALL);
        changed();
    }

    private void changed() {
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (hasSound()) {
            tag.putString("SoundHash", soundHash);
            tag.putString("SoundName", soundName);
            tag.putInt("SoundBytes", soundBytes);
        }
        if (!announcements.isEmpty()) {
            ListTag list = new ListTag();
            for (Clip clip : announcements) {
                CompoundTag c = new CompoundTag();
                c.putString("Hash", clip.hash());
                c.putString("Name", clip.name());
                c.putInt("Bytes", clip.bytes());
                list.add(c);
            }
            tag.put("Announcements", list);
        }
    }

    /** The 車内放送 in a bell's saved tag; Create keeps that tag for bells built into a train. */
    public static List<Clip> readAnnouncements(@Nullable CompoundTag tag) {
        List<Clip> clips = new ArrayList<>();
        if (tag != null) {
            for (Tag t : tag.getList("Announcements", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) t;
                clips.add(new Clip(c.getString("Hash"), c.getString("Name"), c.getInt("Bytes")));
            }
        }
        return clips;
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        soundHash = tag.getString("SoundHash");
        soundName = tag.getString("SoundName");
        soundBytes = tag.getInt("SoundBytes");
        announcements.clear();
        announcements.addAll(readAnnouncements(tag));
        // Download it while the player is still walking up, not when the conductor rings it.
        if (level != null && level.isClientSide && hasSound()) {
            CustomSoundClient.prefetch(soundHash);
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
