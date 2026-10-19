package uk.iwaservice.traincab.mascon;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
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
public record Announcement(String text, String hash, UUID train) implements CustomPacketPayload {

    public static final Type<Announcement> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "announcement"));

    public static final StreamCodec<ByteBuf, Announcement> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8, Announcement::text,
        ByteBufCodecs.stringUtf8(64), Announcement::hash,
        UUIDUtil.STREAM_CODEC, Announcement::train,
        Announcement::new);

    // Last one received on the client and client ticks since then (the client chimes at age 0).
    public static Announcement latest;
    public static int age;
    /** {@code train} of an announcement that isn't about a train's next stop. */
    public static final UUID NO_TRAIN = new UUID(0, 0);
    /** Trains whose 車内放送 this client heard, and when (ms): CRN's displays on them show the next stop. */
    public static final Map<UUID, Long> HEARD = new HashMap<>();

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(Announcement announcement, IPayloadContext context) {
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
