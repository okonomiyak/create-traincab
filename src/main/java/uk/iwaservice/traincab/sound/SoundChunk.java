package uk.iwaservice.traincab.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.traincab.client.CustomSoundClient;

import java.util.function.Supplier;

/**
 * A piece of a departure bell sound ({@code total} bytes, this piece starting at {@code offset}).
 * Client → server: an upload for the bell at {@code pos}, {@code id} being its display name, {@code target}
 * saying what it becomes: the bell's sound, one more 車内放送, the MH, or a
 * 高度駅's 接近メロディ.
 * Server → client: a download, {@code id} being its hash ({@code pos} unused).
 */
public record SoundChunk(BlockPos pos, String id, int target, int total, int offset, byte[] data) {

    public static final int BELL = 0;
    public static final int ANNOUNCEMENT = 1;
    public static final int MUSIC_HORN = 2;
    /** A 高度駅's 接近メロディ. */
    public static final int APPROACH = 3;

    public static void encode(SoundChunk chunk, FriendlyByteBuf buf) {
        buf.writeBlockPos(chunk.pos);
        buf.writeUtf(chunk.id, 64);
        buf.writeVarInt(chunk.target);
        buf.writeVarInt(chunk.total);
        buf.writeVarInt(chunk.offset);
        buf.writeByteArray(chunk.data);
    }

    public static SoundChunk decode(FriendlyByteBuf buf) {
        return new SoundChunk(buf.readBlockPos(), buf.readUtf(64), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readByteArray(CustomSounds.CHUNK_SIZE));
    }

    public static void handle(SoundChunk chunk, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            CustomSounds.receive(player, chunk);
        } else {
            CustomSoundClient.onChunk(chunk);
        }
    }
}
