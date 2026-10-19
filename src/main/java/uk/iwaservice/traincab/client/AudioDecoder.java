package uk.iwaservice.traincab.client;

import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;
import uk.iwaservice.traincab.sound.CustomSounds;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Locale;

/** Reads mp3 / wav / ogg into the departure bell format: CustomSounds.SAMPLE_RATE mono 16-bit little-endian PCM. */
final class AudioDecoder {

    /** {@code clipped}: the file was longer than the limit and has been cut. */
    record Result(byte[] pcm, boolean clipped) {
    }

    /** Interleaved 16-bit samples as decoded. */
    private record Samples(short[] data, int length, int channels, int rate) {
    }

    private AudioDecoder() {
    }

    static Result decode(Path path, int maxSeconds) throws Exception {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        Samples samples = name.endsWith(".mp3") ? mp3(path, maxSeconds)
            : name.endsWith(".ogg") ? ogg(path)
            : javaSound(path, maxSeconds);
        return toMono(samples, maxSeconds);
    }

    private static Samples mp3(Path path, int maxSeconds) throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(path))) {
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();
            short[] data = new short[1 << 16];
            int length = 0;
            for (Header header; (header = bitstream.readFrame()) != null; ) {
                SampleBuffer out = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                if (length + out.getBufferLength() > data.length) {
                    data = Arrays.copyOf(data, Math.max(data.length * 2, length + out.getBufferLength()));
                }
                System.arraycopy(out.getBuffer(), 0, data, length, out.getBufferLength());
                length += out.getBufferLength();
                bitstream.closeFrame();
                // A bit past the limit, so toMono can tell it was cut.
                if (length > (maxSeconds + 1L) * decoder.getOutputFrequency() * decoder.getOutputChannels()) {
                    break;
                }
            }
            return new Samples(data, length, decoder.getOutputChannels(), decoder.getOutputFrequency());
        }
    }

    // ponytail: decodes the whole file at once; stb_vorbis's pull API if huge ogg files show up.
    private static Samples ogg(Path path) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer file = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer rate = stack.mallocInt(1);
            ShortBuffer out = STBVorbis.stb_vorbis_decode_memory(file, channels, rate);
            if (out == null) {
                throw new IOException("Ogg Vorbis として読めません");
            }
            try {
                short[] data = new short[out.remaining()];
                out.get(data);
                return new Samples(data, data.length, channels.get(0), rate.get(0));
            } finally {
                LibCStdlib.free(out);
            }
        } finally {
            MemoryUtil.memFree(file);
        }
    }

    /** wav / aiff / au, 8 or 16 bit. */
    private static Samples javaSound(Path path, int maxSeconds) throws Exception {
        try (AudioInputStream in = AudioSystem.getAudioInputStream(path.toFile())) {
            AudioFormat src = in.getFormat();
            int channels = src.getChannels();
            AudioFormat target = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16,
                channels, channels * 2, src.getSampleRate(), false);
            try (AudioInputStream pcm = AudioSystem.getAudioInputStream(target, in)) {
                byte[] bytes = pcm.readNBytes((int) Math.min(Integer.MAX_VALUE - 8,
                    (maxSeconds + 1L) * (long) src.getSampleRate() * channels * 2));
                short[] data = new short[bytes.length / 2];
                ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(data);
                return new Samples(data, data.length, channels, (int) src.getSampleRate());
            }
        }
    }

    /** Mixes down to mono and resamples to SAMPLE_RATE, averaging the source samples behind each output sample. */
    private static Result toMono(Samples s, int maxSeconds) throws IOException {
        int frames = s.channels > 0 ? s.length / s.channels : 0;
        if (frames == 0 || s.rate <= 0) {
            throw new IOException("音声が入っていません");
        }
        double step = (double) s.rate / CustomSounds.SAMPLE_RATE;
        long outFrames = (long) (frames / step);
        int max = maxSeconds * CustomSounds.SAMPLE_RATE;
        int n = (int) Math.min(outFrames, max);
        byte[] pcm = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            int from = (int) (i * step);
            int to = Math.min(frames, Math.max(from + 1, (int) ((i + 1) * step)));
            long sum = 0;
            for (int f = from; f < to; f++) {
                for (int c = 0; c < s.channels; c++) {
                    sum += s.data[f * s.channels + c];
                }
            }
            int sample = (int) (sum / ((long) (to - from) * s.channels));
            pcm[2 * i] = (byte) sample;
            pcm[2 * i + 1] = (byte) (sample >> 8);
        }
        return new Result(pcm, outFrames > max);
    }
}
