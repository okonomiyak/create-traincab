package uk.iwaservice.traincab.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.sounds.AudioStream;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.BufferUtils;
import org.slf4j.Logger;
import uk.iwaservice.traincab.TrainCabMod;
import uk.iwaservice.traincab.sound.CustomSounds;
import uk.iwaservice.traincab.sound.SoundChunk;
import uk.iwaservice.traincab.sound.SoundPlay;
import uk.iwaservice.traincab.sound.SoundRequest;

import javax.sound.sampled.AudioFormat;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Client side of uploaded departure bell sounds: downloads them from the server into a cache
 * ({@code .minecraft/traincab/sound_cache/}, by hash) and plays them: at the bell (keyed by its BlockPos),
 * following the driver for an MH (keyed by their entity id), or unpositioned for a 車内放送 (keyed by null).
 */
public final class CustomSoundClient {

    private static final AudioFormat FORMAT = new AudioFormat(CustomSounds.SAMPLE_RATE, 16, 1, true, false);
    private static final ResourceLocation LOCATION = ResourceLocation.fromNamespaceAndPath(TrainCabMod.MODID, "departure_bell");
    /** Above 1 only widens the range: heard up to ~32 blocks away, like the built-in bell. */
    private static final float VOLUME = 2f;
    /** An MH carries like the horn: ~64 blocks. */
    private static final float HORN_VOLUME = 4f;
    /** Refuse bigger downloads (the server allows at most 300 s = 13 MB). */
    private static final int MAX_BYTES = 16 << 20;
    private static final Logger LOGGER = LogUtils.getLogger();

    // Client thread only.
    // ponytail: sounds stay in memory for the session; evict if players collect many long ones.
    private static final Map<String, byte[]> CACHE = new HashMap<>();
    private static final Map<String, byte[]> DOWNLOADS = new HashMap<>();
    private static final Set<String> REQUESTED = new HashSet<>();
    /** Bells told to play a sound still downloading. */
    private static final Map<Object, String> WAITING = new HashMap<>();
    private static final Map<Object, SoundInstance> PLAYING = new HashMap<>();
    private static SoundInstance preview;

    private CustomSoundClient() {
    }

    private static Path cacheFile(String hash) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("traincab/sound_cache/" + hash + ".pcm");
    }

    /** The sound from memory or the disk cache, or null if it has to be downloaded. */
    @Nullable
    static byte[] get(String hash) {
        byte[] pcm = CACHE.get(hash);
        if (pcm == null && CustomSounds.isHash(hash)) {
            try {
                byte[] read = Files.readAllBytes(cacheFile(hash));
                if (CustomSounds.sha1(read).equals(hash)) {
                    pcm = read;
                    CACHE.put(hash, pcm);
                }
            } catch (IOException ignored) {
                // Not cached yet.
            }
        }
        return pcm;
    }

    private static void store(String hash, byte[] pcm) {
        CACHE.put(hash, pcm);
        Path file = cacheFile(hash);
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, pcm);
        } catch (IOException e) {
            LOGGER.warn("Could not cache departure bell sound {}", file, e);
        }
    }

    /** Starts downloading the sound unless it is already here. */
    public static void prefetch(String hash) {
        if (CustomSounds.isHash(hash) && !CACHE.containsKey(hash) && !REQUESTED.contains(hash)
            && !Files.isRegularFile(cacheFile(hash))) {
            REQUESTED.add(hash);
            PacketDistributor.sendToServer(new SoundRequest(hash));
        }
    }

    public static void onChunk(SoundChunk chunk) {
        String hash = chunk.id();
        if (!REQUESTED.contains(hash) || chunk.total() <= 0 || chunk.total() > MAX_BYTES) {
            return;
        }
        byte[] buf = chunk.offset() == 0 ? new byte[chunk.total()] : DOWNLOADS.get(hash);
        if (buf == null || buf.length != chunk.total() || chunk.data().length > buf.length - chunk.offset()) {
            return;
        }
        System.arraycopy(chunk.data(), 0, buf, chunk.offset(), chunk.data().length);
        DOWNLOADS.put(hash, buf);
        if (chunk.offset() + chunk.data().length < buf.length) {
            return;
        }
        DOWNLOADS.remove(hash);
        REQUESTED.remove(hash);
        if (!CustomSounds.sha1(buf).equals(hash)) {
            return;
        }
        store(hash, buf);
        WAITING.entrySet().removeIf(e -> {
            if (e.getValue().equals(hash)) {
                start(e.getKey(), buf);
                return true;
            }
            return false;
        });
    }

    public static void onPlay(SoundPlay play) {
        play(play.entityId() >= 0 ? (Object) play.entityId() : play.pos(), play.hash());
    }

    /** A 車内放送: heard the same anywhere on board, replacing the previous one. */
    public static void playOnBoard(String hash) {
        play(null, hash);
    }

    private static void play(@Nullable Object at, String hash) {
        WAITING.remove(at);
        SoundInstance playing = PLAYING.remove(at);
        if (playing != null) {
            Minecraft.getInstance().getSoundManager().stop(playing);
        }
        if (hash.isEmpty()) {
            return;
        }
        byte[] pcm = get(hash);
        if (pcm != null) {
            start(at, pcm);
        } else if (CustomSounds.isHash(hash)) {
            // ponytail: starts from the beginning once downloaded, i.e. late; bells prefetch on load and the
            // conductor's menu prefetches, so only other passengers' first play is late.
            WAITING.put(at, hash);
            prefetch(hash);
        }
    }

    private static void start(@Nullable Object at, byte[] pcm) {
        SoundInstance sound = new PcmSound(pcm, at);
        PLAYING.put(at, sound);
        Minecraft.getInstance().getSoundManager().play(sound);
    }

    /** Plays {@code pcm} to this player only, unpositioned (null just stops the previous preview). */
    static void preview(@Nullable byte[] pcm) {
        if (preview != null) {
            Minecraft.getInstance().getSoundManager().stop(preview);
            preview = null;
        }
        if (pcm != null) {
            preview = new PcmSound(pcm, null);
            Minecraft.getInstance().getSoundManager().play(preview);
        }
    }

    /** {@code target}: one of the SoundChunk targets. */
    static void upload(BlockPos pos, String name, int target, byte[] pcm) {
        store(CustomSounds.sha1(pcm), pcm);
        for (int off = 0; off < pcm.length; off += CustomSounds.CHUNK_SIZE) {
            PacketDistributor.sendToServer(new SoundChunk(pos, name, target, pcm.length, off,
                Arrays.copyOfRange(pcm, off, Math.min(pcm.length, off + CustomSounds.CHUNK_SIZE))));
        }
    }

    /**
     * Streams raw PCM; it needs no sounds.json entry since it resolves to its own Sound. Played at a BlockPos,
     * following the entity with an Integer id, or unpositioned for null.
     */
    private static final class PcmSound extends AbstractSoundInstance implements TickableSoundInstance {

        private final byte[] pcm;
        @Nullable
        private final Object at;

        PcmSound(byte[] pcm, @Nullable Object at) {
            super(LOCATION, SoundSource.BLOCKS, SoundInstance.createUnseededRandom());
            this.pcm = pcm;
            this.at = at;
            if (at == null) {
                relative = true;
                attenuation = Attenuation.NONE;
            } else {
                volume = at instanceof BlockPos ? VOLUME : HORN_VOLUME;
                tick();
            }
        }

        @Override
        public void tick() {
            if (at instanceof BlockPos pos) {
                x = pos.getX() + 0.5;
                y = pos.getY() + 0.5;
                z = pos.getZ() + 0.5;
            } else if (at instanceof Integer id && Minecraft.getInstance().level != null) {
                Entity entity = Minecraft.getInstance().level.getEntity(id);
                if (entity != null) {
                    x = entity.getX();
                    y = entity.getY();
                    z = entity.getZ();
                }
            }
        }

        @Override
        public boolean isStopped() {
            return false;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager manager) {
            sound = new Sound(LOCATION, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, true, false, 16);
            return new WeighedSoundEvents(LOCATION, null);
        }

        @Override
        public CompletableFuture<AudioStream> getStream(SoundBufferLibrary buffers, Sound sound, boolean looping) {
            return CompletableFuture.completedFuture(new AudioStream() {
                private int offset;

                @Override
                public AudioFormat getFormat() {
                    return FORMAT;
                }

                @Override
                public ByteBuffer read(int size) {
                    if (offset >= pcm.length) {
                        return null;
                    }
                    int n = Math.min(size & ~1, pcm.length - offset);
                    ByteBuffer buf = BufferUtils.createByteBuffer(n).put(pcm, offset, n).flip();
                    offset += n;
                    return buf;
                }

                @Override
                public void close() {
                }
            });
        }
    }
}
