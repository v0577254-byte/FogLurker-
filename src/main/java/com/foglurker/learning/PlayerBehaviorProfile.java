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

    /** Where this player lives: their bed, or wherever they open chests the most. */
    public boolean homeKnown;
    public int homeX;
    public int homeY;
    public int homeZ;
    public String homeDim = "";
    /** The home is where they really sleep (false: worked out from the chests they open). */
    public boolean homeFromBed;

    /** Times this player got away from an attack (water, soul fire, a bell) since it last landed a hit. */
    public int escapes;
    /** The player asked it not to learn from their chat (/fglurk chatlearning off). */
    public boolean chatLearningOff;
    private int homeHits;
    private int awayHits;
    /** Game time of the last book it left for them (saved, so the 20-minute limit survives a restart). */
    public long lastBookAt = Long.MIN_VALUE / 2;
    /** Game time the remembered dog was last seen with them (a dog unseen for a whole day may be gone). */
    public long dogSeenAt;
    /** When they were last seen playing (System.currentTimeMillis), 0 if not known (older saves). */
    public long lastSeenMillis;

    /** Seen in the last 30 days (or not known): someone a fake "friend" line can name. */
    public boolean recentlySeen(long nowMillis) {
        return lastSeenMillis == 0 || nowMillis - lastSeenMillis < 30L * 24 * 60 * 60 * 1000;
    }

    public net.minecraft.core.BlockPos home() {
        return new net.minecraft.core.BlockPos(homeX, homeY, homeZ);
    }

    public boolean isNearHome(String dimension, net.minecraft.core.BlockPos pos, int range) {
        return homeKnown && homeDim.equals(dimension) && home().closerThan(pos, range);
    }

    /** A bed is a strong signal: this is home. */
    public void sleptAt(String dimension, net.minecraft.core.BlockPos pos) {
        setHome(dimension, pos);
        homeFromBed = true;
        homeHits += 10;
        awayHits = 0;
    }

    /** Opening containers is a weaker signal; home moves when most of it happens somewhere else. */
    public void usedContainerAt(String dimension, net.minecraft.core.BlockPos pos) {
        if (!homeKnown) {
            setHome(dimension, pos);
            homeFromBed = false;
            homeHits = 1;
        } else if (isNearHome(dimension, pos, 32)) {
            homeHits++;
        } else if (++awayHits > homeHits) {
            setHome(dimension, pos);
            homeFromBed = false;
            homeHits = 1;
            awayHits = 0;
        }
    }

    private void setHome(String dimension, net.minecraft.core.BlockPos pos) {
        homeKnown = true;
        homeDim = dimension;
        homeX = pos.getX();
        homeY = pos.getY();
        homeZ = pos.getZ();
    }

    public void addChat(String msg) {
        if (msg == null || chatLearningOff) {
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
                if (!e.getKey().equals(itemId) && e.getValue() < min) { // never the one just used
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
        t.putLong("dogSeen", dogSeenAt);
        t.putInt("escapes", escapes);
        t.putLong("lastBook", lastBookAt);
        t.putLong("lastSeen", lastSeenMillis);
        t.putBoolean("noChatLearn", chatLearningOff);
        if (homeKnown) {
            t.putIntArray("home", new int[]{homeX, homeY, homeZ, homeHits, awayHits});
            t.putString("homeDim", homeDim);
            t.putBoolean("homeBed", homeFromBed);
        }
        return t;
    }

    /** A damaged value (NaN, infinite) would stick forever through the averaging: back to the default instead. */
    private void sanitize() {
        PlayerBehaviorProfile d = new PlayerBehaviorProfile();
        sprintRatio = Float.isFinite(sprintRatio) ? sprintRatio : d.sprintRatio;
        sneakRatio = Float.isFinite(sneakRatio) ? sneakRatio : d.sneakRatio;
        jumpRate = Float.isFinite(jumpRate) ? jumpRate : d.jumpRate;
        crouchSpamRate = Float.isFinite(crouchSpamRate) ? crouchSpamRate : d.crouchSpamRate;
        lookJitter = Float.isFinite(lookJitter) ? lookJitter : d.lookJitter;
        idleSwingRate = Float.isFinite(idleSwingRate) ? idleSwingRate : d.idleSwingRate;
        friendDistance = Float.isFinite(friendDistance) ? friendDistance : d.friendDistance;
        chatRate = Float.isFinite(chatRate) ? chatRate : d.chatRate;
        idleRatio = Float.isFinite(idleRatio) ? idleRatio : d.idleRatio;
        dogSitRatio = Float.isFinite(dogSitRatio) ? dogSitRatio : d.dogSitRatio;
    }

    public static PlayerBehaviorProfile load(CompoundTag t) {
        PlayerBehaviorProfile p = new PlayerBehaviorProfile();
        p.lastKnownName = t.getString("name");
        p.sprintRatio = t.contains("sprint") ? t.getFloat("sprint") : p.sprintRatio;
        p.sneakRatio = t.contains("sneak") ? t.getFloat("sneak") : p.sneakRatio;
        p.jumpRate = t.contains("jump") ? t.getFloat("jump") : p.jumpRate;
        p.crouchSpamRate = t.contains("crouchSpam") ? t.getFloat("crouchSpam") : p.crouchSpamRate;
        p.lookJitter = t.contains("look") ? t.getFloat("look") : p.lookJitter;
        p.idleSwingRate = t.contains("swing") ? t.getFloat("swing") : p.idleSwingRate;
        p.friendDistance = t.contains("friendDist") ? t.getFloat("friendDist") : 3.5F;
        p.chatRate = t.contains("chatRate") ? t.getFloat("chatRate") : p.chatRate;
        p.idleRatio = t.contains("idle") ? t.getFloat("idle") : p.idleRatio;
        p.windows = t.getInt("windows");
        CompoundTag items = t.getCompound("items");
        for (String k : items.getAllKeys()) {
            p.heldItems.put(k, items.getFloat(k));
        }
        ListTag chat = t.getList("chat", Tag.TAG_STRING);
        for (int i = 0; i < chat.size(); i++) {
            String line = chat.getString(i).strip();
            if (!line.isEmpty() && line.length() <= 200) { // the same rules as addChat, for a hand-edited file
                p.chatSamples.addLast(line);
            }
        }
        while (p.chatSamples.size() > MAX_CHAT_SAMPLES) {
            p.chatSamples.removeFirst();
        }
        p.heldItems.values().removeIf(v -> !Float.isFinite(v));
        while (p.heldItems.size() > MAX_ITEMS) {
            p.heldItems.remove(p.heldItems.keySet().iterator().next());
        }
        p.lastSeenMillis = t.getLong("lastSeen");
        p.sanitize();
        if (t.hasUUID("dog")) {
            p.dogUuid = t.getUUID("dog");
        }
        if (t.contains("dogName")) {
            p.dogName = t.getString("dogName");
        }
        p.dogCollar = t.contains("dogCollar") ? t.getInt("dogCollar") : -1;
        p.dogSitRatio = t.contains("dogSit") ? t.getFloat("dogSit") : 0.3F;
        p.dogSeenAt = t.getLong("dogSeen");
        p.escapes = t.getInt("escapes");
        if (t.contains("lastBook")) {
            p.lastBookAt = t.getLong("lastBook");
        }
        p.chatLearningOff = t.getBoolean("noChatLearn");
        int[] home = t.getIntArray("home");
        if (home.length == 5) {
            p.homeKnown = true;
            p.homeX = home[0];
            p.homeY = home[1];
            p.homeZ = home[2];
            p.homeHits = home[3];
            p.awayHits = home[4];
            p.homeDim = t.getString("homeDim");
            p.homeFromBed = t.getBoolean("homeBed"); // (older saves: not known, so it never claims a bed)
        }
        return p;
    }
}
