package uk.iwaservice.traincab.mascon;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.client.AnnouncementMenuScreen;

import java.util.List;

/** Server → conductor: the 車内放送 their train carries, to pick one from (answered with ANNOUNCE_CLIP). */
public record AnnouncementMenu(List<String> names, List<String> hashes) implements CustomPacketPayload {

    public static final Type<AnnouncementMenu> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "announcement_menu"));

    private static final StreamCodec<ByteBuf, List<String>> STRINGS = ByteBufCodecs.stringUtf8(64).apply(ByteBufCodecs.list(16));

    public static final StreamCodec<ByteBuf, AnnouncementMenu> STREAM_CODEC = StreamCodec.composite(
        STRINGS, AnnouncementMenu::names,
        STRINGS, AnnouncementMenu::hashes,
        AnnouncementMenu::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(AnnouncementMenu menu, IPayloadContext context) {
        AnnouncementMenuScreen.open(menu.names, menu.hashes);
    }
}
