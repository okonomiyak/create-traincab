package uk.iwaservice.traincab.mascon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;

/**
 * Server → driver, every tick: the next station ahead and the distance (blocks) from the train's
 * front to its stop point, negative once overrun. An empty name means no station in range.
 */
public record StationInfo(String name, float distance) implements CustomPacketPayload {

    public static final Type<StationInfo> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "station_info"));

    public static final StreamCodec<ByteBuf, StationInfo> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, StationInfo::name,
        ByteBufCodecs.FLOAT, StationInfo::distance,
        StationInfo::new);

    // Last value received on the client and client ticks since then, read by the HUD. Kept here rather
    // than in a client class so registering the handler never loads client code on a dedicated server.
    public static StationInfo latest;
    public static int age;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StationInfo info, IPayloadContext context) {
        latest = info;
        age = 0;
    }
}
