package uk.iwaservice.traincab.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.traincab.block.AdvancedStationBlockEntity;
import uk.iwaservice.traincab.block.BellSound;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;

import java.util.function.Supplier;

/**
 * Client → server: change the departure bell at {@code pos}. {@code op} is MELODY / BELL (back to that built-in
 * sound), MUSIC_HORN (back to the built-in MH), TWO_WAY (switch a 高度駅 between 一方向 and 双方向),
 * APPROACH_CHIME / APPROACH_OFF (the 接近メロディ back to the built-in chime, or off), or the index
 * of a 車内放送 to remove.
 */
public record BellEdit(BlockPos pos, int op) {

    public static final int MELODY = -1;
    public static final int BELL = -2;
    public static final int MUSIC_HORN = -3;
    public static final int TWO_WAY = -4;
    /** 接近メロディ back to the built-in chime, or off. */
    public static final int APPROACH_CHIME = -5;
    public static final int APPROACH_OFF = -6;

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
        } else if (edit.op == TWO_WAY) {
            if (be instanceof AdvancedStationBlockEntity station) {
                station.toggleTwoWay();
            }
        } else if (edit.op == APPROACH_CHIME || edit.op == APPROACH_OFF) {
            be.setApproach(null, edit.op == APPROACH_CHIME);
        } else if (!(be instanceof DepartureBellBlockEntity bell)) {
            return;
        } else if (edit.op == MUSIC_HORN) {
            bell.setMusicHorn(null);
        } else {
            bell.removeAnnouncement(edit.op);
        }
    }
}
