package uk.iwaservice.traincab.block;

import com.simibubi.create.content.trains.station.StationBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import uk.iwaservice.traincab.client.CustomSoundClient;

/** Create's station plus the departure melody it plays (see AdvancedStationBlock). */
public class AdvancedStationBlockEntity extends StationBlockEntity implements BellSound {

    private String soundHash = "";
    private String soundName = "";
    private int soundBytes;
    private boolean melody = true;

    public AdvancedStationBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public boolean hasSound() {
        return !soundHash.isEmpty();
    }

    @Override
    public String soundHash() {
        return soundHash;
    }

    @Override
    public String soundName() {
        return soundName;
    }

    @Override
    public int soundBytes() {
        return soundBytes;
    }

    @Override
    public boolean melody() {
        return melody;
    }

    @Override
    public void setSound(String hash, String name, int bytes) {
        soundHash = hash;
        soundName = name;
        soundBytes = bytes;
        notifyUpdate();
    }

    @Override
    public void setBuiltin(boolean melody) {
        soundHash = "";
        soundName = "";
        soundBytes = 0;
        this.melody = melody;
        notifyUpdate();
    }

    @Override
    protected void write(CompoundTag tag, boolean clientPacket) {
        super.write(tag, clientPacket);
        tag.putBoolean("Melody", melody);
        if (hasSound()) {
            tag.putString("SoundHash", soundHash);
            tag.putString("SoundName", soundName);
            tag.putInt("SoundBytes", soundBytes);
        }
    }

    @Override
    protected void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        melody = !tag.contains("Melody") || tag.getBoolean("Melody");
        soundHash = tag.getString("SoundHash");
        soundName = tag.getString("SoundName");
        soundBytes = tag.getInt("SoundBytes");
        if (clientPacket && hasSound()) {
            CustomSoundClient.prefetch(soundHash);
        }
    }
}
