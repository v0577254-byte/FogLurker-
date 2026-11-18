package com.foglurker.network;

/**
 * What the creature is doing to the player it visits, sent with the fog so the player's own game can show it in
 * their Discord status. Only ever about the player who receives it.
 */
public final class Mood {
    public static final byte NONE = 0;
    /** Something is following you around in disguise (stalking, lurking in a herd, creeping closer). */
    public static final byte WATCHED = 1;
    /** It just dropped flat in the bushes because you looked its way. */
    public static final byte BUSHES = 2;
    /** A "friend" (or your dog) is leading you somewhere. */
    public static final byte FOLLOWING = 3;
    /** Its real shape, standing in the fog: the night meeting, or the watcher. */
    public static final byte MEETING = 4;
    /** It is shedding its disguise. */
    public static final byte TRANSFORM = 5;
    /** It is coming for you. */
    public static final byte HUNTED = 6;
    /** It is running away from you. */
    public static final byte FLEEING = 7;

    private Mood() {
    }
}
