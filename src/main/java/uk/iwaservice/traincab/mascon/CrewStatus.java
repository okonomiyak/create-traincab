package uk.iwaservice.traincab.mascon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;

/**
 * Server → conductor and driver, every tick while a train has a conductor. {@code speed} is in blocks/tick
 * (Create only syncs speed to the driver, so the conductor's HUD needs it from here).
 */
public record CrewStatus(boolean youAreConductor, boolean doorsOpen, boolean departureReady, boolean emergency,
                         boolean atStation, float speed) implements CustomPacketPayload {

    public static final Type<CrewStatus> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "crew_status"));

    public static final StreamCodec<ByteBuf, CrewStatus> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.BOOL, CrewStatus::youAreConductor,
        ByteBufCodecs.BOOL, CrewStatus::doorsOpen,
        ByteBufCodecs.BOOL, CrewStatus::departureReady,
        ByteBufCodecs.BOOL, CrewStatus::emergency,
        ByteBufCodecs.BOOL, CrewStatus::atStation,
        ByteBufCodecs.FLOAT, CrewStatus::speed,
        CrewStatus::new);

    // Last value received on the client and client ticks since then (same pattern as StationInfo).
    public static CrewStatus latest;
    public static int age;

    /** The latest status if it is still fresh, else null (no conductor, or this player is not crew). */
    public static CrewStatus current() {
        return age <= 10 ? latest : null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(CrewStatus status, IPayloadContext context) {
        latest = status;
        age = 0;
    }
}
