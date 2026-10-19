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
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;

/** Client → server: switch the departure bell at {@code pos} back to the built-in melody or electric bell. */
public record BellMode(BlockPos pos, boolean melody) implements CustomPacketPayload {

    public static final Type<BellMode> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "bell_mode"));

    public static final StreamCodec<ByteBuf, BellMode> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, BellMode::pos,
        ByteBufCodecs.BOOL, BellMode::melody,
        BellMode::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BellMode mode, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            DepartureBellBlockEntity be = CustomSounds.editable(player, mode.pos);
            if (be != null) {
                be.setBuiltin(mode.melody);
            }
        }
    }
}
