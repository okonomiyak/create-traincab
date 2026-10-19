package uk.iwaservice.traincab.sound;

import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.block.BellSound;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;

/**
 * Client → server: change the departure bell at {@code pos}. {@code op} is MELODY / BELL (back to that built-in
 * sound), MUSIC_HORN (back to the built-in MH), or the index of a 車内放送 to remove.
 */
public record BellEdit(BlockPos pos, int op) implements CustomPacketPayload {

    public static final int MELODY = -1;
    public static final int BELL = -2;
    public static final int MUSIC_HORN = -3;

    public static final Type<BellEdit> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "bell_edit"));

    public static final StreamCodec<ByteBuf, BellEdit> STREAM_CODEC = StreamCodec.composite(
        BlockPos.STREAM_CODEC, BellEdit::pos,
        ByteBufCodecs.VAR_INT, BellEdit::op,
        BellEdit::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BellEdit edit, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            BellSound be = CustomSounds.editable(player, edit.pos);
            if (be == null) {
                return;
            }
            if (edit.op == MELODY || edit.op == BELL) {
                be.setBuiltin(edit.op == MELODY);
            } else if (!(be instanceof DepartureBellBlockEntity bell)) {
                return;
            } else if (edit.op == MUSIC_HORN) {
                bell.setMusicHorn(null);
            } else {
                bell.removeAnnouncement(edit.op);
            }
        }
    }
}
