package uk.iwaservice.traincab.block;

import org.jetbrains.annotations.Nullable;
import uk.iwaservice.traincab.block.DepartureBellBlockEntity.Clip;

/** What a departure melody plays: an uploaded sound (see CustomSounds), or else the built-in melody / bell. */
public interface BellSound {

    boolean hasSound();

    String soundHash();

    String soundName();

    int soundBytes();

    /** Built-in sound used without an uploaded one: the melody, or else the electric bell. */
    boolean melody();

    void setSound(String hash, String name, int bytes);

    /** Drops the uploaded sound and picks a built-in one. */
    void setBuiltin(boolean melody);

    /** 接近メロディ: an uploaded sound, else the built-in chime if approachChime, else none. */
    @Nullable
    Clip approach();

    boolean approachChime();

    void setApproach(@Nullable Clip clip, boolean chime);

    /** The スピーカー playing along with it. */
    SpeakerLinks speakers();
}
