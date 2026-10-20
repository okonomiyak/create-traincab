package uk.iwaservice.traincab.block;

import com.mojang.logging.LogUtils;
import com.simibubi.create.content.contraptions.AbstractContraptionEntity;
import com.simibubi.create.content.contraptions.Contraption;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import uk.iwaservice.traincab.TrainCabItems;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * VVVFインバーター: keeps the sound edited in VvvfEditorScreen, in its text form (VvvfProfile.code()), empty for the
 * default sound. Built into a train, Create carries it in the contraption (the block's NBT, and its update tag for the
 * clients), where VvvfSounds reads it.
 */
public class InverterBlockEntity extends BlockEntity {

    public static final String TAG = "Profile";
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final double EDIT_DISTANCE_SQR = 8 * 8;
    /** A train may roll on a little while the editor is open. */
    private static final double TRAIN_EDIT_DISTANCE_SQR = 16 * 16;
    /** Contraption.updateTags: what Create sends players who come near the train later. Null if Create renamed it. */
    private static final Field UPDATE_TAGS = updateTagsField();

    private String code = "";

    public InverterBlockEntity(BlockPos pos, BlockState state) {
        super(TrainCabItems.INVERTER_ENTITY.get(), pos, state);
    }

    public String code() {
        return code;
    }

    /** Keeps {@code code} (normalized) on the placed inverter at pos, if the player may change it. */
    public static void edit(ServerPlayer player, BlockPos pos, String code) {
        Level level = player.level();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > EDIT_DISTANCE_SQR
            || !level.mayInteract(player, pos) || !level.getBlockState(pos).is(TrainCabItems.INVERTER.get())) {
            return;
        }
        // Inverters placed before they had a block entity get one now.
        if (level.getChunkAt(pos).getBlockEntity(pos, LevelChunk.EntityCreationType.IMMEDIATE)
            instanceof InverterBlockEntity be) {
            be.code = code;
            be.setChanged();
            level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Keeps {@code code} (normalized) on the inverter at localPos of a train's carriage, if the player is near it.
     * Returns the carriage, whose watchers then need VvvfSync, or null when nothing changed.
     */
    public static AbstractContraptionEntity editOnTrain(ServerPlayer player, int entityId, BlockPos localPos, String code) {
        if (!(player.level().getEntity(entityId) instanceof AbstractContraptionEntity entity)
            || entity.getContraption() == null) {
            return null;
        }
        Contraption contraption = entity.getContraption();
        StructureBlockInfo info = contraption.getBlocks().get(localPos);
        if (info == null || !info.state().is(TrainCabItems.INVERTER.get())
            || player.distanceToSqr(entity.toGlobalVector(Vec3.atCenterOf(localPos), 1)) > TRAIN_EDIT_DISTANCE_SQR) {
            return null;
        }
        // The block's NBT is what is saved and what a disassembled inverter gets back.
        contraption.getBlocks().put(localPos, new StructureBlockInfo(localPos, info.state(), withCode(info.nbt(), code)));
        Map<BlockPos, CompoundTag> updateTags = updateTags(contraption);
        if (updateTags != null) {
            updateTags.put(localPos, withCode(updateTags.get(localPos), code));
        }
        return entity;
    }

    /** A copy of {@code tag} (or a new tag) holding {@code code}. */
    public static CompoundTag withCode(CompoundTag tag, String code) {
        CompoundTag copy = tag == null ? new CompoundTag() : tag.copy();
        copy.putString(TAG, code);
        return copy;
    }

    private static Field updateTagsField() {
        try {
            Field field = Contraption.class.getDeclaredField("updateTags");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Create's contraption update tags not found: an inverter sound edited on a train reaches "
                + "players who come near later only once the train is reassembled", e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<BlockPos, CompoundTag> updateTags(Contraption contraption) {
        try {
            return UPDATE_TAGS == null ? null : (Map<BlockPos, CompoundTag>) UPDATE_TAGS.get(contraption);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!code.isEmpty()) {
            tag.putString(TAG, code);
        }
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        code = tag.getString(TAG);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
