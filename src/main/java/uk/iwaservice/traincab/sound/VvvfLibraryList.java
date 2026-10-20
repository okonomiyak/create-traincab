package uk.iwaservice.traincab.sound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.List;
import java.util.function.Supplier;

/**
 * Server → client, the answer to VvvfLibraryAction: the サーバーの音 (VvvfLibrary) by name, each with who saved it
 * and whether this player may delete it, and what the action did ({@code message}, empty for a plain LIST).
 */
public record VvvfLibraryList(List<Entry> entries, String message) {

    public record Entry(String name, String code, String owner, boolean deletable) {
    }

    // Last list received on the client, and how many have come, read by VvvfEditorScreen. Kept here rather than in a
    // client class so registering the handler never loads client code on a dedicated server.
    public static VvvfLibraryList latest;
    public static int received;

    public static void encode(VvvfLibraryList list, FriendlyByteBuf buf) {
        buf.writeCollection(list.entries, (b, e) -> {
            b.writeUtf(e.name, 64);
            b.writeUtf(e.code, VvvfProfile.MAX_CODE);
            b.writeUtf(e.owner, 64);
            b.writeBoolean(e.deletable);
        });
        buf.writeUtf(list.message, 256);
    }

    public static VvvfLibraryList decode(FriendlyByteBuf buf) {
        List<Entry> entries = buf.readList(b -> new Entry(b.readUtf(64), b.readUtf(VvvfProfile.MAX_CODE), b.readUtf(64),
            b.readBoolean()));
        return new VvvfLibraryList(entries, buf.readUtf(256));
    }

    public static void handle(VvvfLibraryList list, Supplier<NetworkEvent.Context> context) {
        latest = list;
        received++;
    }
}
