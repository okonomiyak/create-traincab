package uk.iwaservice.traincab.mascon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.client.CustomSoundClient;

/**
 * Server → everyone on board: a 車内放送. The client shows {@code text} for a while, and plays the uploaded sound
 * {@code hash}, or just chimes when there is none.
 */
public record Announcement(String text, String hash) implements CustomPacketPayload {

    public static final Type<Announcement> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "announcement"));

    public static final StreamCodec<ByteBuf, Announcement> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, Announcement::text,
        ByteBufCodecs.stringUtf8(64), Announcement::hash,
        Announcement::new);

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
        if (!announcement.hash.isEmpty()) {
            CustomSoundClient.playOnBoard(announcement.hash);
        }
    }
}
