package uk.iwaservice.traincab.sound;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import uk.iwaservice.traincab.mascon.TrainCabNetwork;

import java.util.function.Supplier;

/**
 * Client → server, from VvvfEditorScreen's サーバー tab: LIST the サーバーの音 (VvvfLibrary), SAVE the sound in
 * {@code text} (VvvfProfile's text form) under its name, or DELETE the one named {@code text}. The server answers
 * each with VvvfLibraryList.
 */
public record VvvfLibraryAction(int op, String text) {

    public static final int LIST = 0;
    public static final int SAVE = 1;
    public static final int DELETE = 2;

    public static void encode(VvvfLibraryAction action, FriendlyByteBuf buf) {
        buf.writeVarInt(action.op);
        buf.writeUtf(action.text, VvvfProfile.MAX_CODE);
    }

    public static VvvfLibraryAction decode(FriendlyByteBuf buf) {
        return new VvvfLibraryAction(buf.readVarInt(), buf.readUtf(VvvfProfile.MAX_CODE));
    }

    public static void handle(VvvfLibraryAction action, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            TrainCabNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), act(player, action));
        }
    }

    private static VvvfLibraryList act(ServerPlayer player, VvvfLibraryAction action) {
        VvvfLibrary library = VvvfLibrary.get(player.server);
        if (action.op == LIST) {
            return library.list(player, "");
        }
        if (!player.mayBuild()) {
            return library.list(player, "ブロックを置けないので、サーバーの音は変えられません");
        }
        return library.list(player, action.op == SAVE ? library.save(player, action.text) : library.delete(player, action.text));
    }
}
