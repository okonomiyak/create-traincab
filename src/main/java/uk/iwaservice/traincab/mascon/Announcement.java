package uk.iwaservice.traincab.mascon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Server → everyone on board: a conductor's 車内放送. The client chimes and shows {@code text} for a while. */
public record Announcement(String text) {

    // Last one received on the client and client ticks since then (the client chimes at age 0).
    public static Announcement latest;
    public static int age;

    public static void encode(Announcement announcement, FriendlyByteBuf buf) {
        buf.writeUtf(announcement.text);
    }

    public static Announcement decode(FriendlyByteBuf buf) {
        return new Announcement(buf.readUtf());
    }

    public static void handle(Announcement announcement, Supplier<NetworkEvent.Context> context) {
        latest = announcement;
        age = 0;
    }
}
