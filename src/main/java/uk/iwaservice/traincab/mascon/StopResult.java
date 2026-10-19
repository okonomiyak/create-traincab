package uk.iwaservice.traincab.mascon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → driver, once, when they stop a train at a station by hand: how far the front ended up from the stop
 * point (blocks; positive = short of it, negative = past it).
 */
public record StopResult(float error) {

    // Last one received on the client and client ticks since then, read by the HUD.
    public static StopResult latest;
    public static int age;

    public static void encode(StopResult result, FriendlyByteBuf buf) {
        buf.writeFloat(result.error);
    }

    public static StopResult decode(FriendlyByteBuf buf) {
        return new StopResult(buf.readFloat());
    }

    public static void handle(StopResult result, Supplier<NetworkEvent.Context> context) {
        latest = result;
        age = 0;
    }
}
