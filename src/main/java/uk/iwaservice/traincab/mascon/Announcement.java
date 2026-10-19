package uk.iwaservice.traincab.mascon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.traincab.client.CustomSoundClient;

import java.util.function.Supplier;

/**
 * Server → everyone on board: a 車内放送. The client shows {@code text} for a while, and plays the uploaded sound
 * {@code hash}, or just chimes when there is none.
 */
public record Announcement(String text, String hash, UUID train) {

    // Last one received on the client and client ticks since then (the client chimes at age 0).
    public static Announcement latest;
    public static int age;
    /** {@code train} of an announcement that isn't about a train's next stop. */
    public static final UUID NO_TRAIN = new UUID(0, 0);
    /** Trains whose 車内放送 this client heard, and when (ms): CRN's displays on them show the next stop. */
    public static final Map<UUID, Long> HEARD = new HashMap<>();

    public static void encode(Announcement announcement, FriendlyByteBuf buf) {
        buf.writeUtf(announcement.text);
        buf.writeUtf(announcement.hash, 64);
        buf.writeUUID(announcement.train);
    }

    public static Announcement decode(FriendlyByteBuf buf) {
        return new Announcement(buf.readUtf(), buf.readUtf(64), buf.readUUID());
    }

    public static void handle(Announcement announcement, Supplier<NetworkEvent.Context> context) {
        latest = announcement;
        age = 0;
        if (!announcement.train.equals(NO_TRAIN)) {
            HEARD.put(announcement.train, System.currentTimeMillis());
        }
        if (!announcement.hash.isEmpty()) {
            CustomSoundClient.playOnBoard(announcement.hash);
        }
    }
}
