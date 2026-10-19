package uk.iwaservice.traincab;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsInteractionBehaviour;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsMovementBehaviour;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import uk.iwaservice.traincab.block.DepartureBellBlock;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;
import uk.iwaservice.traincab.item.ConductorCapItem;
import uk.iwaservice.traincab.mascon.ConductorPanelInteraction;

import java.util.function.Supplier;

public final class TrainCabItems {

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(TrainCabMod.MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(TrainCabMod.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, TrainCabMod.MODID);

    /** 車掌ブロック: built into a train, right-click it to go on / off duty as conductor. */
    public static final DeferredBlock<Block> CONDUCTOR_PANEL = BLOCKS.registerSimpleBlock("conductor_panel",
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL));
    public static final DeferredItem<BlockItem> CONDUCTOR_PANEL_ITEM = ITEMS.registerSimpleBlockItem(CONDUCTOR_PANEL);

    /** 高度運転台: Create's train controls (drives the same way) plus the brake cylinder pressure gauge on the HUD. */
    public static final DeferredBlock<ControlsBlock> ADVANCED_CAB = BLOCKS.register("advanced_cab", () -> new ControlsBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion()));
    public static final DeferredItem<BlockItem> ADVANCED_CAB_ITEM = ITEMS.registerSimpleBlockItem(ADVANCED_CAB);

    /** 発車ベル: on the platform, rung by a conductor stopped nearby. */
    public static final DeferredBlock<DepartureBellBlock> DEPARTURE_BELL = BLOCKS.register("departure_bell",
        () -> new DepartureBellBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL)));
    public static final DeferredItem<BlockItem> DEPARTURE_BELL_ITEM = ITEMS.registerSimpleBlockItem(DEPARTURE_BELL);
    public static final Supplier<BlockEntityType<DepartureBellBlockEntity>> DEPARTURE_BELL_ENTITY =
        BLOCK_ENTITIES.register("departure_bell",
            () -> BlockEntityType.Builder.of(DepartureBellBlockEntity::new, DEPARTURE_BELL.get()).build(null));

    public static final DeferredItem<Item> CONDUCTOR_CAP =
        ITEMS.register("conductor_cap", () -> new ConductorCapItem(new Item.Properties().stacksTo(1)));

    private TrainCabItems() {
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            MovingInteractionBehaviour.REGISTRY.register(CONDUCTOR_PANEL.get(), new ConductorPanelInteraction());
            // Same behaviours Create gives its own controls: right-click to drive, animated lever.
            MovingInteractionBehaviour.REGISTRY.register(ADVANCED_CAB.get(), new ControlsInteractionBehaviour());
            MovementBehaviour.REGISTRY.register(ADVANCED_CAB.get(), new ControlsMovementBehaviour());
        }));
        modEventBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
                event.accept(CONDUCTOR_CAP);
                event.accept(CONDUCTOR_PANEL_ITEM);
                event.accept(ADVANCED_CAB_ITEM);
                event.accept(DEPARTURE_BELL_ITEM);
            }
        });
    }
}
