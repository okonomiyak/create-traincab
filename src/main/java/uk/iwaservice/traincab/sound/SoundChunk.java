package uk.iwaservice.traincab.sound;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.client.CustomSoundClient;

/**
 * A piece of a departure bell sound ({@code total} bytes, this piece starting at {@code offset}).
 * Client → server: an upload for the bell at {@code pos}, {@code id} being its display name; {@code announcement}
 * adds it to the bell's 車内放送 instead of making it the bell's sound.
 * Server → client: a download, {@code id} being its hash ({@code pos} unused).
 */
public record SoundChunk(BlockPos pos, String id, boolean announcement, int total, int offset, byte[] data) implements CustomPacketPayload {

    public static final Type<SoundChunk> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "sound_chunk"));

    public static final StreamCodec<ByteBuf, SoundChunk> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SoundChunk::pos,
        ByteBufCodecs.stringUtf8(64), SoundChunk::id,
        ByteBufCodecs.BOOL, SoundChunk::announcement,
        ByteBufCodecs.VAR_INT, SoundChunk::total,
        ByteBufCodecs.VAR_INT, SoundChunk::offset,
        ByteBufCodecs.byteArray(CustomSounds.CHUNK_SIZE), SoundChunk::data,
        SoundChunk::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SoundChunk chunk, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CustomSounds.receive(player, chunk);
        } else {
            CustomSoundClient.onChunk(chunk);
        }
    }
}
