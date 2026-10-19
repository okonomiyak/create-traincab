package uk.iwaservice.traincab.mascon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;

/** Server → everyone on board: a conductor's 車内放送. The client chimes and shows {@code text} for a while. */
public record Announcement(String text) implements CustomPacketPayload {

    public static final Type<Announcement> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "announcement"));

    public static final StreamCodec<ByteBuf, Announcement> STREAM_CODEC =
        ByteBufCodecs.STRING_UTF8.map(Announcement::new, Announcement::text);

    // Last one received on the client and client ticks since then (the client chimes at age 0).
    public static Announcement latest;
    public static int age;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Announcement announcement, IPayloadContext context) {
        latest = announcement;
        age = 0;
    }
}
