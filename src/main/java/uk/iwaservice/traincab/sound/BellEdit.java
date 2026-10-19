package uk.iwaservice.traincab.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.traincab.block.BellSound;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;

import java.util.function.Supplier;

/**
 * Client → server: change the departure bell at {@code pos}. {@code op} is MELODY / BELL (back to that built-in
 * sound), MUSIC_HORN (back to the built-in MH), or the index of a 車内放送 to remove.
 */
public record BellEdit(BlockPos pos, int op) {

    public static final int MELODY = -1;
    public static final int BELL = -2;
    public static final int MUSIC_HORN = -3;

    public static void encode(BellEdit edit, FriendlyByteBuf buf) {
        buf.writeBlockPos(edit.pos);
        buf.writeVarInt(edit.op);
    }

    public static BellEdit decode(FriendlyByteBuf buf) {
        return new BellEdit(buf.readBlockPos(), buf.readVarInt());
    }

    public static void handle(BellEdit edit, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player == null) {
            return;
        }
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
