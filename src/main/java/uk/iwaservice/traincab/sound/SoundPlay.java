package uk.iwaservice.traincab.sound;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.client.CustomSoundClient;

/** Server → nearby players: play the uploaded sound {@code hash} at the departure bell {@code pos}, or stop it if empty. */
public record SoundPlay(BlockPos pos, String hash) implements CustomPacketPayload {

    public static final Type<SoundPlay> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "sound_play"));

    public static final StreamCodec<ByteBuf, SoundPlay> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, SoundPlay::pos,
        ByteBufCodecs.stringUtf8(64), SoundPlay::hash,
        SoundPlay::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SoundPlay play, IPayloadContext context) {
        CustomSoundClient.onPlay(play);
    }
}
