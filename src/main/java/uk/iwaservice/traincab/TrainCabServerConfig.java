package uk.iwaservice.traincab;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/** Per-world server settings (serverconfig/traincab-server.toml), synced to clients that join. */
@Mod.EventBusSubscriber(modid = TrainCabMod.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TrainCabServerConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    private static final ForgeConfigSpec.DoubleValue MASCON_RATE = BUILDER
        .comment("Acceleration / braking of every mascon notch, relative to Create's own train acceleration.")
        .defineInRange("masconRate", 0.5, 0.05, 4.0);

    private static final ForgeConfigSpec.DoubleValue STOP_TOLERANCE = BUILDER
        .comment("Stopping within this many blocks of a station's stop point counts as arriving there.")
        .defineInRange("stopTolerance", 2.0, 0.1, 20.0);

    private static final ForgeConfigSpec.DoubleValue STATION_SEARCH_RANGE = BUILDER
        .comment("How far ahead (blocks along the track) the next station is looked for.")
        .defineInRange("stationSearchRange", 300.0, 16.0, 2000.0);

    private static final ForgeConfigSpec.DoubleValue OVERRUN_LIMIT = BUILDER
        .comment("How far past a stop point it is still tracked (shown as a negative distance) before moving on.")
        .defineInRange("overrunLimit", 10.0, 0.0, 100.0);

    private static final ForgeConfigSpec.DoubleValue CONDUCTOR_RANGE = BUILDER
        .comment("How far (blocks) an on-duty conductor may be from their train before going off duty.")
        .defineInRange("conductorRange", 5.0, 1.0, 32.0);

    private static final ForgeConfigSpec.BooleanValue UPLOAD_OP_ONLY = BUILDER
        .comment("Only operators may upload departure bell sounds (otherwise anyone who can build at the bell).")
        .define("uploadOpOnly", false);

    private static final ForgeConfigSpec.IntValue MAX_SOUND_SECONDS = BUILDER
        .comment("Longest departure bell sound that may be uploaded, in seconds (longer files are cut).")
        .defineInRange("maxSoundSeconds", 60, 1, 300);

    private static final ForgeConfigSpec.DoubleValue APPROACH_DISTANCE = BUILDER
        .comment("How far (blocks along the track) from a 高度駅 an approaching train sets off its 接近メロディ.")
        .defineInRange("approachDistance", 150.0, 16.0, 1000.0);

    private static final ForgeConfigSpec.BooleanValue SCHEDULE_WHILE_DRIVING = BUILDER
        .comment("A scheduled train driven with the mascon keeps to its schedule (stopping at its next stop counts as",
            "arriving, setting off moves it on), so departure boards keep showing it. Off: Create pauses the schedule.")
        .define("scheduleWhileDriving", true);

    static final ForgeConfigSpec SPEC = BUILDER.build();

    public static double masconRate = 0.5;
    public static double stopTolerance = 2.0;
    public static double stationSearchRange = 300.0;
    public static double overrunLimit = 10.0;
    public static double conductorRange = 5.0;
    public static boolean uploadOpOnly = false;
    public static int maxSoundSeconds = 60;
    public static double approachDistance = 150.0;
    public static boolean scheduleWhileDriving = true;

    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) {
            return;
        }
        masconRate = MASCON_RATE.get();
        stopTolerance = STOP_TOLERANCE.get();
        stationSearchRange = STATION_SEARCH_RANGE.get();
        overrunLimit = OVERRUN_LIMIT.get();
        conductorRange = CONDUCTOR_RANGE.get();
        uploadOpOnly = UPLOAD_OP_ONLY.get();
        maxSoundSeconds = MAX_SOUND_SECONDS.get();
        approachDistance = APPROACH_DISTANCE.get();
        scheduleWhileDriving = SCHEDULE_WHILE_DRIVING.get();
    }
}
