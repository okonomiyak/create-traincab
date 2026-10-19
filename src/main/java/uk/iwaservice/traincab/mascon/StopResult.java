package uk.iwaservice.traincab.mascon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;

/**
 * Server → driver, once, when they stop a train at a station by hand: how far the front ended up from the stop
 * point (blocks; positive = short of it, negative = past it).
 */
public record StopResult(float error) implements CustomPacketPayload {

    public static final Type<StopResult> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "stop_result"));

    public static final StreamCodec<ByteBuf, StopResult> STREAM_CODEC =
        ByteBufCodecs.FLOAT.map(StopResult::new, StopResult::error);

    // Last one received on the client and client ticks since then, read by the HUD.
    public static StopResult latest;
    public static int age;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(StopResult result, IPayloadContext context) {
        latest = result;
        age = 0;
    }
}
