package uk.iwaservice.traincab.mascon;

import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client → server: the driver's current notch for the carriage entity holding their controls.
 * {@code direction} is +1/-1 in Train.speed's sign convention (reverser and controls facing applied),
 * or 0 with the reverser in neutral.
 */
public record MasconPacket(int entityId, int notch, int direction, boolean stationAssist) {

    // ponytail: distance check only, any nearby player can drive; check Create's controls ownership if griefing matters.
    private static final double MAX_DISTANCE_SQR = 64 * 64;

    public static void encode(MasconPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.entityId);
        buf.writeVarInt(packet.notch);
        buf.writeVarInt(packet.direction);
        buf.writeBoolean(packet.stationAssist);
    }

    public static MasconPacket decode(FriendlyByteBuf buf) {
        return new MasconPacket(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(MasconPacket packet, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null
            && player.level().getEntity(packet.entityId) instanceof CarriageContraptionEntity cce
            && cce.getCarriage() != null
            && player.distanceToSqr(cce) < MAX_DISTANCE_SQR) {
            Mascon.update(cce.getCarriage().train, packet.notch, packet.direction, packet.stationAssist, player);
        }
    }
}
