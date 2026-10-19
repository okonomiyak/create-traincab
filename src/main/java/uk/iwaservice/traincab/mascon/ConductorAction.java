package uk.iwaservice.traincab.mascon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;

/** Client → server: a conductor key press (one of the Conductor action constants), {@code arg} if it takes one. */
public record ConductorAction(int action, int arg) implements CustomPacketPayload {

    public static final Type<ConductorAction> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "conductor_action"));

    public static final StreamCodec<ByteBuf, ConductorAction> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, ConductorAction::action,
        ByteBufCodecs.VAR_INT, ConductorAction::arg,
        ConductorAction::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(ConductorAction payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            Conductor.handle(player, payload.action, payload.arg);
        }
    }
}
