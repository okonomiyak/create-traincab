package uk.iwaservice.traincab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import uk.iwaservice.traincab.TrainCabItems;
import uk.iwaservice.traincab.client.CustomSoundClient;

/** The sound a departure bell plays: an uploaded one (see CustomSounds), or none for the built-in bell / melody. */
public class DepartureBellBlockEntity extends BlockEntity {

    private String soundHash = "";
    private String soundName = "";
    private int soundBytes;

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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (hasSound()) {
            tag.putString("SoundHash", soundHash);
            tag.putString("SoundName", soundName);
            tag.putInt("SoundBytes", soundBytes);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        soundHash = tag.getString("SoundHash");
        soundName = tag.getString("SoundName");
        soundBytes = tag.getInt("SoundBytes");
        // Download it while the player is still walking up, not when the conductor rings it.
        if (level != null && level.isClientSide && hasSound()) {
            CustomSoundClient.prefetch(soundHash);
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
