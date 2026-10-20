package uk.iwaservice.traincab.block;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * VVVFインバーター: built into a train, it makes the train sound like one (see VvvfSounds). Right-click (placed, or on
 * a train through InverterInteraction) picks the sound, kept in the block state so it travels with the train.
 */
public class InverterBlock extends Block {

    /** Names of the sounds, in the order of VvvfSynth.PROFILES. */
    public static final String[] SOUNDS = {"GTO", "GTO・音階", "IGBT", "SiC"};
    public static final IntegerProperty SOUND = IntegerProperty.create("sound", 0, SOUNDS.length - 1);

    public InverterBlock(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(SOUND, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SOUND);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (!level.isClientSide) {
            BlockState next = next(state);
            level.setBlock(pos, next, Block.UPDATE_ALL);
            notify(player, next);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    static BlockState next(BlockState state) {
        return state.setValue(SOUND, (state.getValue(SOUND) + 1) % SOUNDS.length);
    }

    static void notify(Player player, BlockState state) {
        player.displayClientMessage(Component.literal("VVVF: " + SOUNDS[state.getValue(SOUND)]), true);
    }
}
