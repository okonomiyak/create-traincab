package uk.iwaservice.traincab;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Per-world server settings (serverconfig/traincab-server.toml), synced to clients that join. */
@EventBusSubscriber(modid = TrainCabMod.MODID)
public class TrainCabServerConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    private static final ModConfigSpec.DoubleValue MASCON_RATE = BUILDER
        .comment("Acceleration / braking of every mascon notch, relative to Create's own train acceleration.")
        .defineInRange("masconRate", 0.5, 0.05, 4.0);

    private static final ModConfigSpec.DoubleValue STOP_TOLERANCE = BUILDER
        .comment("Stopping within this many blocks of a station's stop point counts as arriving there.")
        .defineInRange("stopTolerance", 2.0, 0.1, 20.0);

    private static final ModConfigSpec.DoubleValue STATION_SEARCH_RANGE = BUILDER
        .comment("How far ahead (blocks along the track) the next station is looked for.")
        .defineInRange("stationSearchRange", 300.0, 16.0, 2000.0);

    private static final ModConfigSpec.DoubleValue OVERRUN_LIMIT = BUILDER
        .comment("How far past a stop point it is still tracked (shown as a negative distance) before moving on.")
        .defineInRange("overrunLimit", 10.0, 0.0, 100.0);

    private static final ModConfigSpec.DoubleValue CONDUCTOR_RANGE = BUILDER
        .comment("How far (blocks) an on-duty conductor may be from their train before going off duty.")
        .defineInRange("conductorRange", 5.0, 1.0, 32.0);

    static final ModConfigSpec SPEC = BUILDER.build();

    public static double masconRate = 0.5;
    public static double stopTolerance = 2.0;
    public static double stationSearchRange = 300.0;
    public static double overrunLimit = 10.0;
    public static double conductorRange = 5.0;

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
    }
}
