package uk.iwaservice.traincab.sound;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;

/**
 * Client → server, from VvvfEditorScreen's サーバー tab: LIST the サーバーの音 (VvvfLibrary), SAVE the sound in
 * {@code text} (VvvfProfile's text form) under its name, or DELETE the one named {@code text}. The server answers
 * each with VvvfLibraryList.
 */
public record VvvfLibraryAction(int op, String text) implements CustomPacketPayload {

    public static final int LIST = 0;
    public static final int SAVE = 1;
    public static final int DELETE = 2;

    public static final Type<VvvfLibraryAction> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "vvvf_library_action"));

    public static final StreamCodec<ByteBuf, VvvfLibraryAction> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.VAR_INT, VvvfLibraryAction::op,
        ByteBufCodecs.stringUtf8(VvvfProfile.MAX_CODE), VvvfLibraryAction::text,
        VvvfLibraryAction::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(VvvfLibraryAction action, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PacketDistributor.sendToPlayer(player, act(player, action));
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
