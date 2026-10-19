package uk.iwaservice.traincab.mascon;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;

/**
 * Client → server: the driver's current notch for the carriage entity holding their controls.
 * {@code direction} is +1/-1 in Train.speed's sign convention (reverser and controls facing applied),
 * or 0 with the reverser in neutral.
 */
public record MasconPayload(int entityId, int notch, int direction, boolean stationAssist) implements CustomPacketPayload {

    public static final Type<MasconPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "mascon"));

    public static final StreamCodec<ByteBuf, MasconPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, MasconPayload::entityId,
        ByteBufCodecs.VAR_INT, MasconPayload::notch,
        ByteBufCodecs.VAR_INT, MasconPayload::direction,
        ByteBufCodecs.BOOL, MasconPayload::stationAssist,
        MasconPayload::new);

    // ponytail: distance check only, any nearby player can drive; check Create's controls ownership if griefing matters.
    private static final double MAX_DISTANCE_SQR = 64 * 64;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(MasconPayload payload, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().getEntity(payload.entityId) instanceof CarriageContraptionEntity cce
            && cce.getCarriage() != null
            && player.distanceToSqr(cce) < MAX_DISTANCE_SQR) {
            Mascon.update(cce.getCarriage().train, payload.notch, payload.direction, payload.stationAssist, player);
        }
    }
}
