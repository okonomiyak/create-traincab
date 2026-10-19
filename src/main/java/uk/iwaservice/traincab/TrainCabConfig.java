package uk.iwaservice.traincab;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = TrainCabMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TrainCabConfig {

    public enum SpeedUnit {
        KMH,
        BLOCKS_PER_SECOND
    }

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Show the train cab speedometer HUD while driving a Create train.")
        .define("enabled", true);

    private static final ForgeConfigSpec.EnumValue<SpeedUnit> UNIT = BUILDER
        .comment("Unit used to display speed: KMH or BLOCKS_PER_SECOND.")
        .defineEnum("speedUnit", SpeedUnit.KMH);

    private static final ForgeConfigSpec.IntValue OFFSET_X = BUILDER
        .comment("Horizontal offset in pixels from the bottom-center of the screen.")
        .defineInRange("offsetX", 0, -2000, 2000);

    private static final ForgeConfigSpec.IntValue OFFSET_Y = BUILDER
        .comment("Margin in pixels between the panel and the bottom edge of the screen. The panel is drawn over the hotbar.")
        .defineInRange("offsetY", 2, -2000, 2000);

    private static final ForgeConfigSpec.DoubleValue SCALE = BUILDER
        .comment("Scale multiplier applied to the whole panel. Shrunk automatically if the panel would not fit.")
        .defineInRange("scale", 1.0, 0.25, 4.0);

    private static final ForgeConfigSpec.BooleanValue STATION_ASSIST = BUILDER
        .comment("Station stop assist: show the distance to the next station's stop point, and count stopping within",
            "a couple of blocks of it as arriving at that station.")
        .define("stationAssist", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static boolean enabled = true;
    public static SpeedUnit unit = SpeedUnit.KMH;
    public static int offsetX = 0;
    public static int offsetY = 2;
    public static double scale = 1.0;
    public static boolean stationAssist = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) {
            return;
        }
        enabled = ENABLED.get();
        unit = UNIT.get();
        offsetX = OFFSET_X.get();
        offsetY = OFFSET_Y.get();
        scale = SCALE.get();
        stationAssist = STATION_ASSIST.get();
    }
}
