package uk.iwaservice.traincab.mascon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client → server: a conductor key press (one of the Conductor action constants), {@code arg} if it takes one. */
public record ConductorAction(int action, int arg) {

    public static void encode(ConductorAction payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.action);
        buf.writeVarInt(payload.arg);
    }

    public static ConductorAction decode(FriendlyByteBuf buf) {
        return new ConductorAction(buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(ConductorAction payload, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            Conductor.handle(player, payload.action, payload.arg);
        }
    }
}
