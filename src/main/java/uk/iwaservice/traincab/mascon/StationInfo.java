package uk.iwaservice.traincab.mascon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server → driver, every tick: the next station ahead and the distance (blocks) from the train's
 * front to its stop point, negative once overrun. An empty name means no station in range. Also whether
 * the wheels are spinning or sliding (wheel).
 */
public record StationInfo(String name, float distance, int wheel) {

    public static final int GRIP = 0;
    /** 空転: wheels spinning under power. */
    public static final int SLIP = 1;
    /** 滑走: wheels locked under braking. */
    public static final int SLIDE = 2;

    // Last value received on the client and client ticks since then, read by the HUD. Kept here rather
    // than in a client class so registering the handler never loads client code on a dedicated server.
    public static StationInfo latest;
    public static int age;

    public static void encode(StationInfo info, FriendlyByteBuf buf) {
        buf.writeUtf(info.name);
        buf.writeFloat(info.distance);
        buf.writeVarInt(info.wheel);
    }

    public static StationInfo decode(FriendlyByteBuf buf) {
        return new StationInfo(buf.readUtf(), buf.readFloat(), buf.readVarInt());
    }

    public static void handle(StationInfo info, Supplier<NetworkEvent.Context> context) {
        latest = info;
        age = 0;
    }
}
