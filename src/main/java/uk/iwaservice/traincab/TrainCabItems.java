package uk.iwaservice.traincab;

import com.simibubi.create.AllDisplaySources;
import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.api.behaviour.interaction.MovingInteractionBehaviour;
import com.simibubi.create.api.behaviour.movement.MovementBehaviour;
import uk.iwaservice.traincab.block.AdvancedCabBlock;
import uk.iwaservice.traincab.block.AdvancedCabInteraction;
import com.simibubi.create.content.trains.graph.EdgePointType;
import com.simibubi.create.content.trains.track.TrackTargetingBlockItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import uk.iwaservice.traincab.block.AdvancedCabMovementBehaviour;
import uk.iwaservice.traincab.block.AdvancedStationBlock;
import uk.iwaservice.traincab.block.AdvancedStationBlockEntity;
import uk.iwaservice.traincab.block.DepartureBellBlock;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;
import uk.iwaservice.traincab.item.ConductorCapItem;
import uk.iwaservice.traincab.item.SpeakerLinkerItem;
import uk.iwaservice.traincab.mascon.ConductorPanelInteraction;

import java.util.function.Supplier;

public final class TrainCabItems {

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, TrainCabMod.MODID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, TrainCabMod.MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
        DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, TrainCabMod.MODID);

    /** 車掌ブロック: built into a train, right-click it to go on / off duty as conductor. */
    public static final RegistryObject<Block> CONDUCTOR_PANEL = BLOCKS.register("conductor_panel", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL)));
    public static final RegistryObject<Item> CONDUCTOR_PANEL_ITEM =
        ITEMS.register("conductor_panel", () -> new BlockItem(CONDUCTOR_PANEL.get(), new Item.Properties()));

    /** スピーカー: plays what the 放送装置 / 高度駅 it is linked to plays (see SpeakerLinks). */
    public static final RegistryObject<Block> SPEAKER = BLOCKS.register("speaker", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL)));
    public static final RegistryObject<Item> SPEAKER_ITEM =
        ITEMS.register("speaker", () -> new BlockItem(SPEAKER.get(), new Item.Properties()));
    public static final RegistryObject<Item> SPEAKER_LINKER =
        ITEMS.register("speaker_linker", () -> new SpeakerLinkerItem(new Item.Properties().stacksTo(1)));

    /** VVVFインバーター: built into a train, it makes the train sound like one (see VvvfSounds). */
    public static final RegistryObject<Block> INVERTER = BLOCKS.register("inverter", () -> new Block(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL)));
    public static final RegistryObject<Item> INVERTER_ITEM =
        ITEMS.register("inverter", () -> new BlockItem(INVERTER.get(), new Item.Properties()));

    /**
     * 高度運転台: Create's train controls (drives the same way) plus the brake cylinder pressure gauge on the HUD and
     * a ワンマン switch.
     */
    public static final RegistryObject<AdvancedCabBlock> ADVANCED_CAB = BLOCKS.register("advanced_cab", () -> new AdvancedCabBlock(
        BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL).noOcclusion()));
    public static final RegistryObject<Item> ADVANCED_CAB_ITEM =
        ITEMS.register("advanced_cab", () -> new BlockItem(ADVANCED_CAB.get(), new Item.Properties()));

    /** 発車ベル: on the platform, rung by a conductor stopped nearby. */
    public static final RegistryObject<DepartureBellBlock> DEPARTURE_BELL = BLOCKS.register("departure_bell",
        () -> new DepartureBellBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(1.5f).sound(SoundType.METAL)));
    public static final RegistryObject<Item> DEPARTURE_BELL_ITEM =
        ITEMS.register("departure_bell", () -> new BlockItem(DEPARTURE_BELL.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<DepartureBellBlockEntity>> DEPARTURE_BELL_ENTITY =
        BLOCK_ENTITIES.register("departure_bell",
            () -> BlockEntityType.Builder.of(DepartureBellBlockEntity::new, DEPARTURE_BELL.get()).build(null));

    /** 高度駅: Create's train station with a departure bell built in, rung for scheduled trains too. */
    public static final RegistryObject<AdvancedStationBlock> ADVANCED_STATION = BLOCKS.register("advanced_station",
        () -> new AdvancedStationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).strength(1.5f)
            .sound(SoundType.NETHERITE_BLOCK)));
    /** Placed by clicking the track first, like Create's station. */
    public static final RegistryObject<Item> ADVANCED_STATION_ITEM = ITEMS.register("advanced_station",
        () -> new TrackTargetingBlockItem(ADVANCED_STATION.get(), new Item.Properties(), EdgePointType.STATION));
    public static final RegistryObject<BlockEntityType<AdvancedStationBlockEntity>> ADVANCED_STATION_ENTITY =
        BLOCK_ENTITIES.register("advanced_station", () -> BlockEntityType.Builder.of(
            (pos, state) -> new AdvancedStationBlockEntity(TrainCabItems.ADVANCED_STATION_ENTITY.get(), pos, state),
            ADVANCED_STATION.get()).build(null));

    public static final RegistryObject<Item> CONDUCTOR_CAP =
        ITEMS.register("conductor_cap", () -> new ConductorCapItem(new Item.Properties().stacksTo(1)));

    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, TrainCabMod.MODID);
    public static final Supplier<CreativeModeTab> TAB = TABS.register("traincab", () -> CreativeModeTab.builder()
        .title(Component.translatable("itemGroup.traincab"))
        .icon(() -> new ItemStack(ADVANCED_CAB_ITEM.get()))
        .displayItems((params, output) -> {
            output.accept(ADVANCED_CAB_ITEM.get());
            output.accept(ADVANCED_STATION_ITEM.get());
            output.accept(DEPARTURE_BELL_ITEM.get());
            output.accept(CONDUCTOR_PANEL_ITEM.get());
            output.accept(SPEAKER_ITEM.get());
            output.accept(SPEAKER_LINKER.get());
            output.accept(INVERTER_ITEM.get());
            output.accept(CONDUCTOR_CAP.get());
        })
        .build());

    private TrainCabItems() {
    }

    static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        TABS.register(modEventBus);
        modEventBus.addListener((FMLCommonSetupEvent event) -> event.enqueueWork(() -> {
            MovingInteractionBehaviour.REGISTRY.register(CONDUCTOR_PANEL.get(), new ConductorPanelInteraction());
            // Create's controls behaviours: right-click to drive (its levers are part of the model, so not drawn),
            // sneak + right-click for the ワンマン switch.
            MovingInteractionBehaviour.REGISTRY.register(ADVANCED_CAB.get(), new AdvancedCabInteraction());
            MovementBehaviour.REGISTRY.register(ADVANCED_CAB.get(), new AdvancedCabMovementBehaviour());
            // Display links can read a 高度駅 like Create's station.
            DisplaySource.BY_BLOCK.add(ADVANCED_STATION.get(), AllDisplaySources.STATION_SUMMARY.get());
            DisplaySource.BY_BLOCK.add(ADVANCED_STATION.get(), AllDisplaySources.TRAIN_STATUS.get());
        }));
    }
}
