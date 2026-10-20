package uk.iwaservice.traincab.sound;

import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import uk.iwaservice.traincab.block.InverterBlockEntity;
import uk.iwaservice.traincab.mascon.TrainCabNetwork;

import java.util.function.Supplier;

/**
 * Client → server: keep the sound edited in VvvfEditorScreen ({@code code}, VvvfProfile's text form) on an inverter:
 * the placed one at {@code pos}, or with an {@code entity} id the one at local {@code pos} of that contraption (a
 * train's carriage), whose watchers then get VvvfSync.
 */
public record VvvfEdit(int entity, BlockPos pos, String code) {

    /** {@code entity} for a placed inverter. */
    public static final int PLACED = -1;

    public static void encode(VvvfEdit edit, FriendlyByteBuf buf) {
        buf.writeVarInt(edit.entity);
        buf.writeBlockPos(edit.pos);
        buf.writeUtf(edit.code, VvvfProfile.MAX_CODE);
    }

    public static VvvfEdit decode(FriendlyByteBuf buf) {
        return new VvvfEdit(buf.readVarInt(), buf.readBlockPos(), buf.readUtf(VvvfProfile.MAX_CODE));
    }

    public static void handle(VvvfEdit edit, Supplier<NetworkEvent.Context> context) {
        ServerPlayer player = context.get().getSender();
        VvvfProfile profile = VvvfProfile.parse(edit.code);
        if (player == null || !player.mayBuild() || profile == null) {
            return;
        }
        String code = profile.code();
        if (edit.entity == PLACED) {
            InverterBlockEntity.edit(player, edit.pos, code);
            return;
        }
        AbstractContraptionEntity carriage = InverterBlockEntity.editOnTrain(player, edit.entity, edit.pos, code);
        if (carriage != null) {
            TrainCabNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> carriage),
                new VvvfSync(edit.entity, edit.pos, code));
        }
    }
}
