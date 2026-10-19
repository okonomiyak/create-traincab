package uk.iwaservice.traincab.sound;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;

/** Client → server: send me the departure bell sound with this hash (answered with SoundChunks). */
public record SoundRequest(String hash) implements CustomPacketPayload {

    public static final Type<SoundRequest> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "sound_request"));

    public static final StreamCodec<ByteBuf, SoundRequest> STREAM_CODEC =
        ByteBufCodecs.stringUtf8(64).map(SoundRequest::new, SoundRequest::hash);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SoundRequest request, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            CustomSounds.send(player, request.hash);
        }
    }
}
