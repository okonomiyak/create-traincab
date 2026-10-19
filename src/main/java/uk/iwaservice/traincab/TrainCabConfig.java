package uk.iwaservice.traincab;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

@EventBusSubscriber(modid = TrainCabMod.MODID)
public class TrainCabConfig {

    public enum SpeedUnit {
        KMH,
        BLOCKS_PER_SECOND
    }

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.BooleanValue ENABLED = BUILDER
        .comment("Show the train cab speedometer HUD while driving a Create train.")
        .define("enabled", true);

    private static final ModConfigSpec.EnumValue<SpeedUnit> UNIT = BUILDER
        .comment("Unit used to display speed: KMH or BLOCKS_PER_SECOND.")
        .defineEnum("speedUnit", SpeedUnit.KMH);

    private static final ModConfigSpec.IntValue OFFSET_X = BUILDER
        .comment("Horizontal offset in pixels from the bottom-center of the screen.")
        .defineInRange("offsetX", 0, -2000, 2000);

    private static final ModConfigSpec.IntValue OFFSET_Y = BUILDER
        .comment("Margin in pixels between the panel and the bottom edge of the screen. The panel is drawn over the hotbar.")
        .defineInRange("offsetY", 2, -2000, 2000);

    private static final ModConfigSpec.DoubleValue SCALE = BUILDER
        .comment("Scale multiplier applied to the whole panel. Shrunk automatically if the panel would not fit.")
        .defineInRange("scale", 1.0, 0.25, 4.0);

    private static final ModConfigSpec.BooleanValue STATION_ASSIST = BUILDER
        .comment("Station stop assist: show the distance to the next station's stop point, and count stopping within",
            "a couple of blocks of it as arriving at that station.")
        .define("stationAssist", true);

    private static final ModConfigSpec.BooleanValue VVVF_SOUND = BUILDER
        .comment("Play a VVVF inverter traction sound (GTO, like the Hankyu 8000 series) from moving trains nearby.")
        .define("vvvfSound", true);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static boolean enabled = true;
    public static SpeedUnit unit = SpeedUnit.KMH;
    public static int offsetX = 0;
    public static int offsetY = 2;
    public static double scale = 1.0;
    public static boolean stationAssist = true;
    public static boolean vvvfSound = true;

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
        vvvfSound = VVVF_SOUND.get();
    }
}
