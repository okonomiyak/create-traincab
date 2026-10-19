package uk.iwaservice.traincab;

import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsBlock;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsInteractionBehaviour;
import com.simibubi.create.content.contraptions.actors.trainControls.ControlsMovementBehaviour;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.iwaservice.traincab.item.ConductorCapItem;
import uk.iwaservice.traincab.mascon.ConductorPanelInteraction;

public final class TrainCabItems {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TrainCabMod.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TrainCabMod.MODID);

    /** 車掌ブロック: built into a train, right-click it to go on / off duty as conductor. */
    public static final RegistryObject<Block> CONDUCTOR_PANEL = BLOCKS.register("conductor_panel", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL)));
    public static final RegistryObject<Item> CONDUCTOR_PANEL_ITEM =
        ITEMS.register("conductor_panel", () -> new BlockItem(CONDUCTOR_PANEL.get(), new Item.Properties()));

    /** 高度運転台: Create's train controls (drives the same way) plus the brake cylinder pressure gauge on the HUD. */
    public static final RegistryObject<ControlsBlock> ADVANCED_CAB = BLOCKS.register("advanced_cab", () -> new ControlsBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Item> ADVANCED_CAB_ITEM =
        ITEMS.register("advanced_cab", () -> new BlockItem(ADVANCED_CAB.get(), new Item.Properties()));

    public static final RegistryObject<Item> CONDUCTOR_CAP =
        ITEMS.register("conductor_cap", () -> new ConductorCapItem(new Item.Properties().stacksTo(1)));

    private TrainCabItems() {
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
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
            }
        });
    }
}
