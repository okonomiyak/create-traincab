package uk.iwaservice.traincab.sound;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.client.VvvfSounds;

/**
 * Server → players near a train: the inverter at local {@code pos} of contraption {@code entity} (one of its
 * carriages) got a new sound, {@code code} (VvvfProfile's text form).
 */
public record VvvfSync(int entity, BlockPos pos, String code) implements CustomPacketPayload {

    public static final Type<VvvfSync> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "vvvf_sync"));

    public static final StreamCodec<ByteBuf, VvvfSync> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, VvvfSync::entity,
        BlockPos.STREAM_CODEC, VvvfSync::pos,
        ByteBufCodecs.stringUtf8(VvvfProfile.MAX_CODE), VvvfSync::code,
        VvvfSync::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VvvfSync sync, IPayloadContext context) {
        VvvfSounds.onSync(sync.entity, sync.pos, sync.code);
    }
}
