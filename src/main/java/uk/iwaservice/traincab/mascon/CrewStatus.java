package uk.iwaservice.traincab.mascon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → conductor and driver, every tick while a train has a conductor. {@code speed} is in blocks/tick
 * (Create only syncs speed to the driver, so the conductor's HUD needs it from here).
 */
public record CrewStatus(boolean youAreConductor, boolean doorsOpen, boolean departureReady, boolean emergency,
                         boolean atStation, float speed) {

    // Last value received on the client and client ticks since then (same pattern as StationInfo).
    public static CrewStatus latest;
    public static int age;

    /** The latest status if it is still fresh, else null (no conductor, or this player is not crew). */
    public static CrewStatus current() {
        return age <= 10 ? latest : null;
    }

    public static void encode(CrewStatus status, FriendlyByteBuf buf) {
        buf.writeBoolean(status.youAreConductor);
        buf.writeBoolean(status.doorsOpen);
        buf.writeBoolean(status.departureReady);
        buf.writeBoolean(status.emergency);
        buf.writeBoolean(status.atStation);
        buf.writeFloat(status.speed);
    }

    public static CrewStatus decode(FriendlyByteBuf buf) {
        return new CrewStatus(buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
            buf.readBoolean(), buf.readFloat());
    }

    public static void handle(CrewStatus status, Supplier<NetworkEvent.Context> context) {
        latest = status;
        age = 0;
    }
}
