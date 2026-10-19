package uk.iwaservice.traincab.block;

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

    /** The スピーカー playing along with it. */
    SpeakerLinks speakers();
}
