package uk.iwaservice.traincab.mascon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.traincab.client.CustomSoundClient;

import java.util.function.Supplier;

/**
 * Server → everyone on board: a 車内放送. The client shows {@code text} for a while, and plays the uploaded sound
 * {@code hash}, or just chimes when there is none.
 */
public record Announcement(String text, String hash) {

    // Last one received on the client and client ticks since then (the client chimes at age 0).
    public static Announcement latest;
    public static int age;

    public static void encode(Announcement announcement, FriendlyByteBuf buf) {
        buf.writeUtf(announcement.text);
        buf.writeUtf(announcement.hash, 64);
    }

    public static Announcement decode(FriendlyByteBuf buf) {
        return new Announcement(buf.readUtf(), buf.readUtf(64));
    }

    public static void handle(Announcement announcement, Supplier<NetworkEvent.Context> context) {
        latest = announcement;
        age = 0;
        if (!announcement.hash.isEmpty()) {
            CustomSoundClient.playOnBoard(announcement.hash);
        }
    }
}
