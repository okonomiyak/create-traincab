package uk.iwaservice.traincab.mascon;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.traincab.client.AnnouncementMenuScreen;

import java.util.List;
import java.util.function.Supplier;

/** Server → conductor: the 車内放送 their train carries, to pick one from (answered with ANNOUNCE_CLIP). */
public record AnnouncementMenu(List<String> names, List<String> hashes) {

    public static void encode(AnnouncementMenu menu, FriendlyByteBuf buf) {
        buf.writeCollection(menu.names, (b, s) -> b.writeUtf(s, 64));
        buf.writeCollection(menu.hashes, (b, s) -> b.writeUtf(s, 64));
    }

    public static AnnouncementMenu decode(FriendlyByteBuf buf) {
        return new AnnouncementMenu(buf.readList(b -> b.readUtf(64)), buf.readList(b -> b.readUtf(64)));
    }

    public static void handle(AnnouncementMenu menu, Supplier<NetworkEvent.Context> context) {
        AnnouncementMenuScreen.open(menu.names, menu.hashes);
    }
}
