package uk.iwaservice.traincab.sound;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;

import java.util.List;

/**
 * Server → client, the answer to VvvfLibraryAction: the サーバーの音 (VvvfLibrary) by name, each with who saved it
 * and whether this player may delete it, and what the action did ({@code message}, empty for a plain LIST).
 */
public record VvvfLibraryList(List<Entry> entries, String message) implements CustomPacketPayload {

    public record Entry(String name, String code, String owner, boolean deletable) {

        static final StreamCodec<ByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(64), Entry::name,
            ByteBufCodecs.stringUtf8(VvvfProfile.MAX_CODE), Entry::code,
            ByteBufCodecs.stringUtf8(64), Entry::owner,
            ByteBufCodecs.BOOL, Entry::deletable,
            Entry::new);
    }

    public static final Type<VvvfLibraryList> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "vvvf_library_list"));

    public static final StreamCodec<ByteBuf, VvvfLibraryList> STREAM_CODEC = StreamCodec.composite(
        Entry.STREAM_CODEC.apply(ByteBufCodecs.list(VvvfLibrary.MAX_TOTAL)), VvvfLibraryList::entries,
        ByteBufCodecs.stringUtf8(256), VvvfLibraryList::message,
        VvvfLibraryList::new);

    // Last list received on the client, and how many have come, read by VvvfEditorScreen. Kept here rather than in a
    // client class so registering the handler never loads client code on a dedicated server.
    public static VvvfLibraryList latest;
    public static int received;

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VvvfLibraryList list, IPayloadContext context) {
        latest = list;
        received++;
    }
}
