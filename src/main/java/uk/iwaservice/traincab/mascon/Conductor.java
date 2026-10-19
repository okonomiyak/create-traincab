package uk.iwaservice.traincab.mascon;

import com.simibubi.create.Create;
import com.simibubi.create.content.decoration.slidingDoor.SlidingDoorBlock;
import com.simibubi.create.content.trains.entity.Carriage;
import com.simibubi.create.content.trains.station.GlobalStation;
import com.simibubi.create.content.trains.entity.CarriageContraptionEntity;
import com.simibubi.create.content.trains.entity.Train;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.TrainCabServerConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server-side conductor (車掌). A player (not the driver) goes on and off duty by right-clicking a 車掌ブロック
 * built into the train, and stays on duty while seated on it or within conductorRange (server config) of it. While a train
 * has a conductor, its sliding doors stay shut on arrival until the conductor opens them (戸開; also possible
 * when stopped between stations), and the
 * driver cannot depart until the conductor has closed them again (戸閉). The conductor can also pull the
 * emergency brake (車掌弁), which holds the train in EB until the conductor releases it again or the
 * driver moves the handle to EB. Trains
 * without a conductor run one-man as before (Create opens their doors automatically).
 */
@EventBusSubscriber(modid = TrainCabMod.MODID)
public final class Conductor {

    public static final int TOGGLE_DOORS = 1;
    public static final int EMERGENCY = 2;
    /** 合図ベル: rings for the driver and the conductor, whichever of them pressed it. */
    public static final int BELL = 3;
    /** 車内放送: chime and "次は ○○ です" for everyone on board. By the conductor, or the driver when one-man. */
    public static final int ANNOUNCE = 4;
    /** 警笛: one blast of Create's train whistle. Driver only. */
    public static final int HORN = 5;
    /** ミュージックホーン: a short melody, separate from the horn. Driver only. */
    public static final int MUSIC_HORN = 6;

    private static final ResourceLocation WHISTLE_SOUND = ResourceLocation.fromNamespaceAndPath("create", "whistle_train");
    /** Horns carry: volume above 1 only widens the range (16 blocks per 1). */
    private static final float HORN_VOLUME = 4f;
    /** MH melody: {tick, note-block note (0 = F#3 .. 24 = F#5)}. G4 C5 E5, D5 C5, G4 C5. */
    private static final int[][] MUSIC_HORN_NOTES = {
        {0, 13}, {3, 18}, {6, 22}, {12, 20}, {15, 18}, {21, 13}, {24, 18},
    };
    /** Drivers whose MH is playing, and ticks into the melody. */
    private static final Map<ServerPlayer, Integer> MUSIC_HORNS = new HashMap<>();

    /** Length (ticks) of the 戸閉 chime played from every carriage while the doors close. */
    private static final int DOOR_CHIME_LENGTH = 14;

    /** Players this close to a carriage (standing on or in it) count as on board for announcements. */
    private static final double ON_BOARD_RANGE = 0.5;

    private static final class Crew {
        UUID conductor;
        boolean doorsOpen;
        /** Doors were closed by the conductor at this station: the driver may depart. */
        boolean departureReady;
        boolean emergency;
        boolean wasAtStation;
        /** Ticks into the 戸閉 chime, or -1 when not playing. */
        int doorChimeTick = -1;
    }

    // Server thread only (packet handlers, server tick, Train.tick).
    private static final Map<UUID, Crew> CREWS = new HashMap<>();

    private Conductor() {
    }

    /** Seated on one of this train's carriages, or within {@code range} blocks of one. */
    private static boolean nearTrain(Player player, Train train, double range) {
        if (player.getVehicle() instanceof CarriageContraptionEntity cce && cce.getCarriage() != null
            && cce.getCarriage().train == train) {
            return true;
        }
        for (CarriageContraptionEntity cce : player.level().getEntitiesOfClass(CarriageContraptionEntity.class,
            player.getBoundingBox().inflate(range))) {
            if (cce.getCarriage() != null && cce.getCarriage().train == train) {
                return true;
            }
        }
        return false;
    }

    /** The train this player is the conductor of, or null. */
    private static Train conductingTrain(ServerPlayer player) {
        for (Map.Entry<UUID, Crew> entry : CREWS.entrySet()) {
            if (player.getUUID().equals(entry.getValue().conductor)) {
                return Create.RAILWAYS.trains.get(entry.getKey());
            }
        }
        return null;
    }

    /** Every player on (standing on / in) or seated on this train. */
    static List<ServerPlayer> onBoard(Train train) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayers()) {
            if (nearTrain(player, train, ON_BOARD_RANGE)) {
                players.add(player);
            }
        }
        return players;
    }

    /**
     * Someone (player or mob, other than {@code except}) standing in one of the train's open sliding
     * doorways, or null. Checked before closing the doors (戸挟み検知).
     */
    private static LivingEntity trappedInDoor(Train train, Player except) {
        for (Carriage carriage : train.carriages) {
            CarriageContraptionEntity cce = carriage.anyAvailableEntity();
            if (cce == null) {
                continue;
            }
            for (var block : cce.getContraption().getBlocks().entrySet()) {
                var state = block.getValue().state();
                if (!(state.getBlock() instanceof SlidingDoorBlock)
                    || state.getValue(DoorBlock.HALF) != DoubleBlockHalf.LOWER || !state.getValue(DoorBlock.OPEN)) {
                    continue;
                }
                Vec3 c = cce.toGlobalVector(Vec3.atCenterOf(block.getKey()), 1);
                AABB doorway = new AABB(c.x - .5, c.y - .5, c.z - .5, c.x + .5, c.y + 1.5, c.z + .5);
                for (LivingEntity entity : cce.level().getEntitiesOfClass(LivingEntity.class, doorway, e -> e != except)) {
                    return entity;
                }
            }
        }
        return null;
    }

    /**
     * "ピンポン、ピンポン" from every carriage (so the platform hears it too), pitched above the announcement
     * chime so the two are easy to tell apart.
     */
    private static void playDoorChime(Train train, int tick) {
        float pitch;
        switch (tick) {
            case 0, 10 -> pitch = 1.5f;
            case 4, 14 -> pitch = 1.19f;
            default -> {
                return;
            }
        }
        for (Carriage carriage : train.carriages) {
            CarriageContraptionEntity cce = carriage.anyAvailableEntity();
            if (cce != null) {
                cce.level().playSound(null, cce.getX(), cce.getY(), cce.getZ(), SoundEvents.NOTE_BLOCK_CHIME.value(),
                    SoundSource.BLOCKS, 1f, pitch);
            }
        }
    }

    /** "○○、○○です" at a station, "次は ○○ です" when the next station is known, else a generic line. */
    private static String announcementText(Train train) {
        GlobalStation station = train.getCurrentStation();
        if (station != null) {
            return station.name + "、" + station.name + "です";
        }
        StationInfo next = Mascon.stationInfoOf(train);
        if (next != null && !next.name().isEmpty()) {
            return "次は " + next.name() + " です";
        }
        return "ご乗車ありがとうございます";
    }

    /** Right-clicking a 車掌ブロック on {@code train} (see ConductorPanelInteraction). */
    static void toggleDuty(ServerPlayer player, Train train) {
        Crew crew = CREWS.get(train.id);
        Train current = conductingTrain(player);
        if (crew != null && player.getUUID().equals(crew.conductor)) {
            CREWS.remove(train.id);
            notify(player, "車掌の乗務を終了しました");
        } else if (crew != null) {
            notify(player, "この列車には既に車掌が乗務しています");
        } else if (current != null) {
            notify(player, "他の列車で車掌として乗務中です");
        } else if (player.equals(Mascon.driverOf(train))) {
            notify(player, "運転士は車掌を兼務できません");
        } else {
            crew = new Crew();
            crew.conductor = player.getUUID();
            CREWS.put(train.id, crew);
            notify(player, "車掌として乗務しました");
        }
    }

    /** One-man trains may always depart; with a conductor the doors must have been opened and closed again. */
    static boolean departureAllowed(Train train) {
        Crew crew = CREWS.get(train.id);
        return crew == null || !crew.doorsOpen && crew.departureReady;
    }

    /**
     * Checked by SlidingDoorMixin. Without a conductor, Create decides (open at a station, on the sides the
     * station allows). With one, the doors follow the conductor: at a station still only on the station's
     * sides, anywhere else (stopped between stations) on both sides, as there is no platform to pick from.
     */
    public static boolean doorsShouldOpen(Train train, boolean createWouldOpen) {
        Crew crew = CREWS.get(train.id);
        if (crew == null) {
            return createWouldOpen;
        }
        return crew.doorsOpen && (train.getCurrentStation() != null ? createWouldOpen : train.speed == 0);
    }

    /** The mascon holds a train still while its doors are open. */
    static boolean doorsOpen(Train train) {
        Crew crew = CREWS.get(train.id);
        return crew != null && crew.doorsOpen;
    }

    static boolean emergency(Train train) {
        Crew crew = CREWS.get(train.id);
        return crew != null && crew.emergency;
    }

    /** The driver resets the conductor's emergency brake by moving the handle to EB. */
    static void acknowledgeEmergency(Train train) {
        Crew crew = CREWS.get(train.id);
        if (crew != null) {
            crew.emergency = false;
        }
    }

    static void handle(ServerPlayer player, int action) {
        Train train = conductingTrain(player);
        if (train == null) {
            train = Mascon.trainDrivenBy(player);
        }
        if (train == null) {
            return;
        }
        Crew crew = CREWS.get(train.id);
        boolean isConductor = crew != null && player.getUUID().equals(crew.conductor);

        switch (action) {
            case TOGGLE_DOORS -> {
                if (!isConductor) {
                    return;
                }
                if (train.getCurrentStation() == null && train.speed != 0) {
                    notify(player, "戸開扱いは停車中のみできます");
                    return;
                }
                if (crew.doorsOpen) {
                    LivingEntity trapped = trappedInDoor(train, player);
                    if (trapped != null) {
                        notify(player, "戸挟み検知: " + trapped.getName().getString() + "（戸閉できません）");
                        player.playNotifySound(SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1f, 0.5f);
                        return;
                    }
                }
                crew.doorsOpen = !crew.doorsOpen;
                crew.departureReady = !crew.doorsOpen;
                if (!crew.doorsOpen) {
                    crew.doorChimeTick = 0;
                }
            }
            case EMERGENCY -> {
                // The conductor's key toggles: pull the 車掌弁, or release it again.
                if (isConductor) {
                    crew.emergency = !crew.emergency;
                    notify(player, crew.emergency ? "非常ブレーキを動作させました" : "非常ブレーキを解除しました");
                }
            }
            case BELL -> {
                // The whole crew hears it, whoever pressed it (driver or conductor), the presser included.
                ServerPlayer driver = Mascon.driverOf(train);
                if (!isConductor && !player.equals(driver)) {
                    return;
                }
                ring(player);
                if (driver != null && !driver.equals(player)) {
                    ring(driver);
                }
                ServerPlayer conductor = crew == null ? null : player.server.getPlayerList().getPlayer(crew.conductor);
                if (conductor != null && !conductor.equals(player)) {
                    ring(conductor);
                }
            }
            case HORN -> {
                if (player.equals(Mascon.driverOf(train))) {
                    SoundEvent whistle = BuiltInRegistries.SOUND_EVENT.getOptional(WHISTLE_SOUND)
                        .orElse(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value());
                    player.level().playSound(null, player.getX(), player.getY(), player.getZ(), whistle,
                        SoundSource.BLOCKS, HORN_VOLUME, 1f);
                }
            }
            case MUSIC_HORN -> {
                if (player.equals(Mascon.driverOf(train))) {
                    MUSIC_HORNS.putIfAbsent(player, 0);
                }
            }
            case ANNOUNCE -> {
                // The conductor announces; on a one-man train (no conductor) the driver does.
                if (!isConductor && !(crew == null && player.equals(Mascon.driverOf(train)))) {
                    return;
                }
                // Someone in an open doorway: the announcement becomes a door warning, and they hear it too.
                LivingEntity trapped = crew != null && crew.doorsOpen ? trappedInDoor(train, player) : null;
                Announcement announcement = new Announcement(trapped != null
                    ? "ドアが閉まります。ドアから離れてください" : announcementText(train));
                Set<ServerPlayer> listeners = new HashSet<>(onBoard(train));
                listeners.add(player);
                if (trapped instanceof ServerPlayer trappedPlayer) {
                    listeners.add(trappedPlayer);
                }
                ServerPlayer driver = Mascon.driverOf(train);
                if (driver != null) {
                    listeners.add(driver);
                }
                for (ServerPlayer listener : listeners) {
                    PacketDistributor.sendToPlayer(listener, announcement);
                }
            }
            default -> {
            }
        }
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        for (Iterator<Map.Entry<ServerPlayer, Integer>> it = MUSIC_HORNS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<ServerPlayer, Integer> entry = it.next();
            int tick = entry.getValue();
            playMusicHorn(entry.getKey(), tick);
            if (entry.getKey().isRemoved() || tick >= MUSIC_HORN_NOTES[MUSIC_HORN_NOTES.length - 1][0]) {
                it.remove();
            } else {
                entry.setValue(tick + 1);
            }
        }

        for (Iterator<Map.Entry<UUID, Crew>> it = CREWS.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<UUID, Crew> entry = it.next();
            Crew crew = entry.getValue();
            Train train = Create.RAILWAYS.trains.get(entry.getKey());
            ServerPlayer conductor = event.getServer().getPlayerList().getPlayer(crew.conductor);

            // Duty ends when the conductor gets further than conductorRange from the train, logs off, or the
            // train is gone.
            if (train == null || conductor == null || !nearTrain(conductor, train, TrainCabServerConfig.conductorRange)) {
                it.remove();
                if (conductor != null) {
                    notify(conductor, "車掌の乗務を終了しました");
                }
                continue;
            }

            boolean atStation = train.getCurrentStation() != null;
            if (atStation && !crew.wasAtStation) {
                crew.doorsOpen = false;
                crew.departureReady = false;
            }
            crew.wasAtStation = atStation;

            if (crew.doorChimeTick >= 0) {
                playDoorChime(train, crew.doorChimeTick);
                crew.doorChimeTick = crew.doorChimeTick < DOOR_CHIME_LENGTH ? crew.doorChimeTick + 1 : -1;
            }

            PacketDistributor.sendToPlayer(conductor, status(crew, train, true));
            StationInfo station = Mascon.stationInfoOf(train);
            if (station != null) {
                PacketDistributor.sendToPlayer(conductor, station);
            }
            ServerPlayer driver = Mascon.driverOf(train);
            if (driver != null) {
                PacketDistributor.sendToPlayer(driver, status(crew, train, false));
            }
        }
    }

    private static CrewStatus status(Crew crew, Train train, boolean forConductor) {
        return new CrewStatus(forConductor, crew.doorsOpen, crew.departureReady, crew.emergency,
            train.getCurrentStation() != null, (float) train.speed);
    }

    /**
     * A single "チン", heard only by that player. Volume is capped at 1, so loudness comes from layering the
     * (loud) village bell pitched right up over the note-block bell.
     */
    private static void ring(ServerPlayer player) {
        player.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1f, 2f);
        player.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1f, 1.5f);
    }

    /** Plays this tick's MH note, if any: flute for the tone, a quieter square wave on top for the electronic edge. */
    private static void playMusicHorn(ServerPlayer driver, int tick) {
        for (int[] note : MUSIC_HORN_NOTES) {
            if (note[0] == tick) {
                float pitch = (float) Math.pow(2, (note[1] - 12) / 12.0);
                driver.level().playSound(null, driver.getX(), driver.getY(), driver.getZ(),
                    SoundEvents.NOTE_BLOCK_FLUTE.value(), SoundSource.BLOCKS, HORN_VOLUME, pitch);
                driver.level().playSound(null, driver.getX(), driver.getY(), driver.getZ(),
                    SoundEvents.NOTE_BLOCK_BIT.value(), SoundSource.BLOCKS, HORN_VOLUME / 2, pitch);
            }
        }
    }

    private static void notify(ServerPlayer player, String message) {
        player.displayClientMessage(Component.literal(message), true);
    }
}
