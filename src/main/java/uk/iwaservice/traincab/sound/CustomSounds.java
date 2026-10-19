package uk.iwaservice.traincab.sound;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.slf4j.Logger;
import uk.iwaservice.traincab.TrainCabServerConfig;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Departure bell sounds uploaded by players, so nobody needs a resource pack: SAMPLE_RATE mono 16-bit little-endian
 * PCM, stored by SHA-1 under {@code <world>/traincab_sounds/} and sent to clients on request (they cache it).
 */
public final class CustomSounds {

    public static final int SAMPLE_RATE = 22050;
    /** Bytes per packet: serverbound custom payloads are capped at 32 KiB. */
    public static final int CHUNK_SIZE = 30000;
    /** Players this close to a ringing bell are told to play it (it is heard up to ~32 blocks away). */
    private static final double PLAY_RANGE = 64;
    /** Same for an MH, heard up to ~64 blocks away like the horn. */
    private static final double HORN_RANGE = 96;
    private static final double EDIT_DISTANCE_SQR = 8 * 8;
    private static final int UPLOAD_COOLDOWN_TICKS = 200;
    private static final Pattern HASH = Pattern.compile("[0-9a-f]{40}");
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final class Upload {
        BlockPos pos;
        String name;
        int target;
        byte[] data;
        int received;
    }

    // Server thread only.
    private static final Map<UUID, Upload> UPLOADS = new HashMap<>();
    private static final Map<UUID, Integer> LAST_UPLOAD = new HashMap<>();

    private CustomSounds() {
    }

    public static String sha1(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static boolean isHash(String s) {
        return HASH.matcher(s).matches();
    }

    /** How long a sound of {@code bytes} plays, in ticks. */
    public static int ticks(int bytes) {
        return (int) Math.ceil(bytes / 2.0 * 20 / SAMPLE_RATE);
    }

    private static Path file(MinecraftServer server, String hash) {
        return server.getWorldPath(LevelResource.ROOT).resolve("traincab_sounds").resolve(hash + ".pcm");
    }

    public static boolean exists(MinecraftServer server, String hash) {
        return isHash(hash) && Files.isRegularFile(file(server, hash));
    }

    /** Players near {@code pos} start playing the sound {@code hash} there, or stop whatever plays there if empty. */
    public static void play(ServerLevel level, BlockPos pos, String hash) {
        Vec3 c = Vec3.atCenterOf(pos);
        PacketDistributor.sendToPlayersNear(level, null, c.x, c.y, c.z, PLAY_RANGE, new SoundPlay(pos, -1, hash));
    }

    /** Players near {@code driver} hear the sound {@code hash} follow them (the train's MH). */
    public static void playFollowing(ServerPlayer driver, String hash) {
        PacketDistributor.sendToPlayersNear(driver.serverLevel(), null, driver.getX(), driver.getY(), driver.getZ(),
            HORN_RANGE, new SoundPlay(BlockPos.ZERO, driver.getId(), hash));
    }

    /** The departure bell at {@code pos} if {@code player} may change it, else null. */
    static DepartureBellBlockEntity editable(ServerPlayer player, BlockPos pos) {
        if (TrainCabServerConfig.uploadOpOnly && !player.hasPermissions(2)) {
            player.displayClientMessage(Component.literal("発車ベルの音を変更できるのは OP のみです"), true);
            return null;
        }
        Level level = player.level();
        if (!level.isLoaded(pos) || player.distanceToSqr(Vec3.atCenterOf(pos)) > EDIT_DISTANCE_SQR
            || !player.mayBuild() || !level.mayInteract(player, pos)) {
            return null;
        }
        return level.getBlockEntity(pos) instanceof DepartureBellBlockEntity be ? be : null;
    }

    static void receive(ServerPlayer player, SoundChunk chunk) {
        UUID id = player.getUUID();
        int now = player.server.getTickCount();
        if (chunk.offset() == 0) {
            UPLOADS.remove(id);
            DepartureBellBlockEntity be = editable(player, chunk.pos());
            if (be == null) {
                return;
            }
            if (chunk.target() == SoundChunk.ANNOUNCEMENT && be.announcements().size() >= DepartureBellBlockEntity.MAX_ANNOUNCEMENTS) {
                player.displayClientMessage(Component.literal(
                    "車内放送は " + DepartureBellBlockEntity.MAX_ANNOUNCEMENTS + " 本までです"), true);
                return;
            }
            if (chunk.total() <= 0 || chunk.total() % 2 != 0
                || chunk.total() > TrainCabServerConfig.maxSoundSeconds * SAMPLE_RATE * 2) {
                player.displayClientMessage(Component.literal(
                    "音声が長すぎます（最大 " + TrainCabServerConfig.maxSoundSeconds + " 秒）"), true);
                return;
            }
            Integer last = LAST_UPLOAD.get(id);
            if (last != null && now - last < UPLOAD_COOLDOWN_TICKS) {
                player.displayClientMessage(Component.literal("少し待ってからアップロードしてください"), true);
                return;
            }
            Upload upload = new Upload();
            upload.pos = chunk.pos();
            upload.name = chunk.id();
            upload.target = chunk.target();
            upload.data = new byte[chunk.total()];
            UPLOADS.put(id, upload);
        }
        Upload u = UPLOADS.get(id);
        if (u == null) {
            return;
        }
        if (!chunk.pos().equals(u.pos) || chunk.total() != u.data.length || chunk.offset() != u.received
            || chunk.data().length > u.data.length - u.received) {
            UPLOADS.remove(id);
            return;
        }
        System.arraycopy(chunk.data(), 0, u.data, u.received, chunk.data().length);
        u.received += chunk.data().length;
        if (u.received < u.data.length) {
            return;
        }
        UPLOADS.remove(id);
        DepartureBellBlockEntity be = editable(player, u.pos);
        if (be == null) {
            return;
        }
        String hash = sha1(u.data);
        Path file = file(player.server, hash);
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.write(file, u.data);
            }
        } catch (IOException e) {
            LOGGER.error("Could not save departure bell sound {}", file, e);
            player.displayClientMessage(Component.literal("音声を保存できませんでした"), true);
            return;
        }
        LAST_UPLOAD.put(id, now);
        DepartureBellBlockEntity.Clip clip = new DepartureBellBlockEntity.Clip(hash, u.name, u.data.length);
        if (u.target == SoundChunk.MUSIC_HORN) {
            be.setMusicHorn(clip);
            player.displayClientMessage(Component.literal("ミュージックホーンを「" + u.name + "」にしました"), true);
        } else if (u.target != SoundChunk.ANNOUNCEMENT) {
            be.setSound(hash, u.name, u.data.length);
            player.displayClientMessage(Component.literal("発車ベルを「" + u.name + "」にしました"), true);
        } else if (be.announcements().size() < DepartureBellBlockEntity.MAX_ANNOUNCEMENTS) {
            be.addAnnouncement(clip);
            player.displayClientMessage(Component.literal("車内放送「" + u.name + "」を追加しました"), true);
        }
    }

    // ponytail: no rate limit, a client re-requesting only wastes its own bandwidth; add one if servers see abuse.
    static void send(ServerPlayer player, String hash) {
        if (!exists(player.server, hash)) {
            return;
        }
        byte[] data;
        try {
            data = Files.readAllBytes(file(player.server, hash));
        } catch (IOException e) {
            LOGGER.error("Could not read departure bell sound {}", hash, e);
            return;
        }
        for (int off = 0; off < data.length; off += CHUNK_SIZE) {
            PacketDistributor.sendToPlayer(player, new SoundChunk(BlockPos.ZERO, hash, SoundChunk.BELL, data.length, off,
                Arrays.copyOfRange(data, off, Math.min(data.length, off + CHUNK_SIZE))));
        }
    }
}
