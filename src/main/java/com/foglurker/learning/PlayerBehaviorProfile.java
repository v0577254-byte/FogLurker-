package com.foglurker.learning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.util.RandomSource;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Everything the creature has learned about how one player behaves. Values are exponential moving
 * averages so the profile slowly follows the player's habits as they change.
 */
public final class PlayerBehaviorProfile {
    public static final int MAX_CHAT_SAMPLES = 40;
    private static final int MAX_ITEMS = 12;

    public String lastKnownName = "";

    /** Fraction of moving time spent sprinting. */
    public float sprintRatio = 0.45F;
    /** Fraction of all time spent crouching. */
    public float sneakRatio = 0.04F;
    /** Jumps per second while moving (bunny hoppers score high). */
    public float jumpRate = 0.15F;
    /** Crouch toggles per second while standing near another player ("crouch spam" greeting). */
    public float crouchSpamRate = 0.2F;
    /** Average head-turn speed (degrees per tick) while standing still. */
    public float lookJitter = 3.0F;
    /** Arm swings per second while not doing anything in particular. */
    public float idleSwingRate = 0.05F;
    /** Distance this player usually keeps from friends when hanging out. */
    public float friendDistance = 3.5F;
    /** Chat messages per minute while online. */
    public float chatRate = 0.6F;
    /** Fraction of time the player stands still (AFK-ish / looking around). */
    public float idleRatio = 0.35F;
    /** Total samples folded in, used to judge how much we really know. */
    public int windows;

    /** Held item id -> decayed score. */
    public final Map<String, Float> heldItems = new HashMap<>();
    public final Deque<String> chatSamples = new ArrayDeque<>();

    /** The player's dog (tamed wolf) as last seen. */
    public UUID dogUuid;
    public String dogName;
    public int dogCollar = -1;
    public float dogSitRatio = 0.3F;

    public void addChat(String msg) {
        if (msg == null) {
            return;
        }
        String m = msg.strip();
        if (m.isEmpty() || m.length() > 200) {
            return;
        }
        chatSamples.addLast(m);
        while (chatSamples.size() > MAX_CHAT_SAMPLES) {
            chatSamples.removeFirst();
        }
    }

    public void bumpHeldItem(String itemId, float weight) {
        heldItems.replaceAll((k, v) -> v * 0.995F);
        heldItems.merge(itemId, weight, Float::sum);
        if (heldItems.size() > MAX_ITEMS) {
            String weakest = null;
            float min = Float.MAX_VALUE;
            for (Map.Entry<String, Float> e : heldItems.entrySet()) {
                if (e.getValue() < min) {
                    min = e.getValue();
                    weakest = e.getKey();
                }
            }
            heldItems.remove(weakest);
        }
    }

    public String favoriteItem() {
        String best = null;
        float max = 0;
        for (Map.Entry<String, Float> e : heldItems.entrySet()) {
            if (e.getValue() > max) {
                max = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    public List<String> recentChat(int max) {
        List<String> all = new ArrayList<>(chatSamples);
        return all.subList(Math.max(0, all.size() - max), all.size());
    }

    public String randomChat(RandomSource random) {
        if (chatSamples.isEmpty()) {
            return null;
        }
        List<String> all = new ArrayList<>(chatSamples);
        return all.get(random.nextInt(all.size()));
    }

    public boolean likesCrouchSpam() {
        return crouchSpamRate > 0.35F;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", lastKnownName);
        t.putFloat("sprint", sprintRatio);
        t.putFloat("sneak", sneakRatio);
        t.putFloat("jump", jumpRate);
        t.putFloat("crouchSpam", crouchSpamRate);
        t.putFloat("look", lookJitter);
        t.putFloat("swing", idleSwingRate);
        t.putFloat("friendDist", friendDistance);
        t.putFloat("chatRate", chatRate);
        t.putFloat("idle", idleRatio);
        t.putInt("windows", windows);
        CompoundTag items = new CompoundTag();
        heldItems.forEach(items::putFloat);
        t.put("items", items);
        ListTag chat = new ListTag();
        for (String s : chatSamples) {
            chat.add(StringTag.valueOf(s));
        }
        t.put("chat", chat);
        if (dogUuid != null) {
            t.putUUID("dog", dogUuid);
        }
        if (dogName != null) {
            t.putString("dogName", dogName);
        }
        t.putInt("dogCollar", dogCollar);
        t.putFloat("dogSit", dogSitRatio);
        return t;
    }

    public static PlayerBehaviorProfile load(CompoundTag t) {
        PlayerBehaviorProfile p = new PlayerBehaviorProfile();
        p.lastKnownName = t.getString("name");
        p.sprintRatio = t.getFloat("sprint");
        p.sneakRatio = t.getFloat("sneak");
        p.jumpRate = t.getFloat("jump");
        p.crouchSpamRate = t.getFloat("crouchSpam");
        p.lookJitter = t.getFloat("look");
        p.idleSwingRate = t.getFloat("swing");
        p.friendDistance = t.contains("friendDist") ? t.getFloat("friendDist") : 3.5F;
        p.chatRate = t.getFloat("chatRate");
        p.idleRatio = t.getFloat("idle");
        p.windows = t.getInt("windows");
        CompoundTag items = t.getCompound("items");
        for (String k : items.getAllKeys()) {
            p.heldItems.put(k, items.getFloat(k));
        }
        ListTag chat = t.getList("chat", Tag.TAG_STRING);
        for (int i = 0; i < chat.size(); i++) {
            p.chatSamples.add(chat.getString(i));
        }
        if (t.hasUUID("dog")) {
            p.dogUuid = t.getUUID("dog");
        }
        if (t.contains("dogName")) {
            p.dogName = t.getString("dogName");
        }
        p.dogCollar = t.contains("dogCollar") ? t.getInt("dogCollar") : -1;
        p.dogSitRatio = t.contains("dogSit") ? t.getFloat("dogSit") : 0.3F;
        return p;
    }
}
