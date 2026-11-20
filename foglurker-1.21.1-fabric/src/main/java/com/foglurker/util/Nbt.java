package com.foglurker.util;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NumericTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.Optional;

/**
 * Reading saved data the forgiving way on Minecraft 1.21.1 (newer versions have these on CompoundTag itself): a value that
 * is missing or of the wrong type gives the default instead, so an old or damaged save never stops the mod.
 */
public final class Nbt {
    private Nbt() {
    }

    public static float getFloatOr(CompoundTag t, String key, float def) {
        return t.get(key) instanceof NumericTag n ? n.getAsFloat() : def;
    }

    public static double getDoubleOr(CompoundTag t, String key, double def) {
        return t.get(key) instanceof NumericTag n ? n.getAsDouble() : def;
    }

    public static int getIntOr(CompoundTag t, String key, int def) {
        return t.get(key) instanceof NumericTag n ? n.getAsInt() : def;
    }

    public static long getLongOr(CompoundTag t, String key, long def) {
        return t.get(key) instanceof NumericTag n ? n.getAsLong() : def;
    }

    public static short getShortOr(CompoundTag t, String key, short def) {
        return t.get(key) instanceof NumericTag n ? n.getAsShort() : def;
    }

    public static byte getByteOr(CompoundTag t, String key, byte def) {
        return t.get(key) instanceof NumericTag n ? n.getAsByte() : def;
    }

    public static boolean getBooleanOr(CompoundTag t, String key, boolean def) {
        return t.get(key) instanceof NumericTag n ? n.getAsByte() != 0 : def;
    }

    public static String getStringOr(CompoundTag t, String key, String def) {
        return t.get(key) instanceof StringTag s ? s.getAsString() : def;
    }

    public static CompoundTag getCompoundOrEmpty(CompoundTag t, String key) {
        return t.get(key) instanceof CompoundTag c ? c : new CompoundTag();
    }

    /** The list under this key, whatever it holds (empty if missing). */
    public static ListTag getListOrEmpty(CompoundTag t, String key) {
        return t.get(key) instanceof ListTag l ? l : new ListTag();
    }

    public static CompoundTag getCompoundOrEmpty(ListTag l, int i) {
        return i >= 0 && i < l.size() && l.get(i) instanceof CompoundTag c ? c : new CompoundTag();
    }

    public static String getStringOr(ListTag l, int i, String def) {
        return i >= 0 && i < l.size() && l.get(i) instanceof StringTag s ? s.getAsString() : def;
    }

    public static long getLongOr(ListTag l, int i, long def) {
        return i >= 0 && i < l.size() && l.get(i) instanceof NumericTag n ? n.getAsLong() : def;
    }

    public static int getIntOr(ListTag l, int i, int def) {
        return i >= 0 && i < l.size() && l.get(i) instanceof NumericTag n ? n.getAsInt() : def;
    }

    public static <T> Optional<T> read(CompoundTag t, String key, Codec<T> codec) {
        Tag v = t.get(key);
        return v == null ? Optional.empty() : codec.parse(NbtOps.INSTANCE, v).result();
    }

    public static <T> void store(CompoundTag t, String key, Codec<T> codec, T value) {
        codec.encodeStart(NbtOps.INSTANCE, value).result().ifPresent(v -> t.put(key, v));
    }
}
