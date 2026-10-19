package uk.iwaservice.traincab.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;

import java.util.function.Supplier;

/** Client → server: switch the departure bell at {@code pos} back to the built-in melody or electric bell. */
public record BellMode(BlockPos pos, boolean melody) {

    public static void encode(BellMode mode, FriendlyByteBuf buf) {
        buf.writeBlockPos(mode.pos);
        buf.writeBoolean(mode.melody);
    }

    public static BellMode decode(FriendlyByteBuf buf) {
        return new BellMode(buf.readBlockPos(), buf.readBoolean());
    }

    public static void handle(BellMode mode, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        if (player != null) {
            DepartureBellBlockEntity be = CustomSounds.editable(player, mode.pos);
            if (be != null) {
                be.setBuiltin(mode.melody);
            }
        }
    }
}
