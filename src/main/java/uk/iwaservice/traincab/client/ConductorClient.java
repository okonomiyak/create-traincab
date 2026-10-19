package uk.iwaservice.traincab.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;
import uk.iwaservice.traincab.mascon.Announcement;
import uk.iwaservice.traincab.mascon.Conductor;
import uk.iwaservice.traincab.mascon.ConductorAction;
import uk.iwaservice.traincab.mascon.CrewStatus;
import uk.iwaservice.traincab.mascon.TrainCabNetwork;

/**
 * The conductor's keys while on duty (duty itself is toggled by right-clicking a 車掌ブロック on the train):
 * close/open doors, pull EB, make an announcement. The bell key works for both crew members. Also plays the
 * announcement chime for anyone who received one.
 */
public final class ConductorClient {

    public static final KeyMapping DOORS =
        new KeyMapping("key.traincab.doors", GLFW.GLFW_KEY_K, "key.categories.traincab");
    /** Shared by conductor and driver: signals the other one. */
    public static final KeyMapping BELL =
        new KeyMapping("key.traincab.bell", GLFW.GLFW_KEY_B, "key.categories.traincab");
    public static final KeyMapping ANNOUNCE =
        new KeyMapping("key.traincab.announce", GLFW.GLFW_KEY_N, "key.categories.traincab");
    public static final KeyMapping DEPARTURE_BELL =
        new KeyMapping("key.traincab.departure_bell", GLFW.GLFW_KEY_J, "key.categories.traincab");
    public static final KeyMapping HORN =
        new KeyMapping("key.traincab.horn", GLFW.GLFW_KEY_H, "key.categories.traincab");
    public static final KeyMapping MUSIC_HORN =
        new KeyMapping("key.traincab.music_horn", GLFW.GLFW_KEY_M, "key.categories.traincab");

    private ConductorClient() {
    }

    /** Called from TrainDrivingState.tick, which owns the shared emergency key. */
    static void tick(boolean driving, boolean emergencyPressed) {
        CrewStatus.age++;
        // 車内放送 chime, "ピンポーン": two notes a few ticks apart, for whoever received the announcement.
        if (Announcement.latest != null) {
            if (Announcement.age == 0) {
                chime(1.26f);
            } else if (Announcement.age == 6) {
                chime(1f);
            }
        }
        Announcement.age++;
        boolean departureBell = false;
        while (DEPARTURE_BELL.consumeClick()) {
            departureBell = true;
        }
        boolean doors = false;
        while (DOORS.consumeClick()) {
            doors = true;
        }
        boolean announce = false;
        while (ANNOUNCE.consumeClick()) {
            announce = true;
        }
        boolean horn = false;
        while (HORN.consumeClick()) {
            horn = true;
        }
        boolean musicHorn = false;
        while (MUSIC_HORN.consumeClick()) {
            musicHorn = true;
        }
        if (driving && horn) {
            send(Conductor.HORN);
        }
        if (driving && musicHorn) {
            send(Conductor.MUSIC_HORN);
        }
        boolean bell = false;
        while (BELL.consumeClick()) {
            bell = true;
        }
        if (bell && (driving || isConductor())) {
            send(Conductor.BELL);
        }
        // The server only accepts a driver's announcement on a one-man train.
        if (announce && (driving || isConductor())) {
            send(Conductor.ANNOUNCE);
        }

        if (driving || !isConductor()) {
            return;
        }
        if (doors) {
            send(Conductor.TOGGLE_DOORS);
        }
        if (departureBell) {
            send(Conductor.DEPARTURE_BELL);
        }
        if (emergencyPressed) {
            send(Conductor.EMERGENCY);
        }
    }

    public static boolean isConductor() {
        CrewStatus status = CrewStatus.current();
        return status != null && status.youAreConductor();
    }

    private static void chime(float pitch) {
        Minecraft.getInstance().getSoundManager()
            .play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), pitch, 1f));
    }

    private static void send(int action) {
        TrainCabNetwork.CHANNEL.sendToServer(new ConductorAction(action));
    }
}
