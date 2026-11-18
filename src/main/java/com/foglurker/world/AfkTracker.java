package com.foglurker.world;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Who is away from the keyboard. Nothing comes for an AFK player and it never attacks one.
 *
 * <ul>
 *   <li><b>Automatic:</b> no activity for {@code afkMinutes} (5 by default) and you are AFK.</li>
 *   <li><b>/afk:</b> AFK right now, without the wait. Not while it is already hunting you.</li>
 *   <li><b>Back:</b> any real activity ends it: looking around, walking, sneaking, chatting, breaking,
 *       placing, using or attacking. Being pushed (knockback, water, a vehicle) does not count.</li>
 * </ul>
 * AFK players show "[AFK]" after their name in the player list.
 */
@Mod.EventBusSubscriber(modid = FogLurkerMod.MOD_ID)
public final class AfkTracker {
    /** After /afk, ignore this many ticks of activity (closing the chat screen jiggles the mouse). */
    static final int MANUAL_SETTLE_TICKS = 40;
    /** How often looking and walking are compared. */
    private static final int CHECK_EVERY = 20;

    private static final Map<UUID, State> STATES = new ConcurrentHashMap<>();

    private static final class State {
        long lastActive;
        boolean afk;
        boolean manual;
        long afkSince;
        float yaw;
        float pitch;
        double x;
        double y;
        double z;
        boolean sneaking;
    }

    private AfkTracker() {
    }

    public static boolean enabled() {
        return FogLurkerConfig.COMMON.afkProtection.get();
    }

    public static long afkTicks() {
        return FogLurkerConfig.COMMON.afkMinutes.get() * 60L * 20L;
    }

    /** True while this player is AFK (and AFK protection is on). */
    public static boolean isAfk(@Nullable Player p) {
        if (p == null || !enabled()) {
            return false;
        }
        State s = STATES.get(p.getUUID());
        return s != null && s.afk;
    }

    /** True if they went AFK with /afk (rather than by standing still). */
    public static boolean isManual(Player p) {
        State s = STATES.get(p.getUUID());
        return s != null && s.afk && s.manual;
    }

    private static State state(ServerPlayer p, long now) {
        return STATES.computeIfAbsent(p.getUUID(), id -> {
            State s = new State();
            s.lastActive = now;
            s.yaw = p.getYRot();
            s.pitch = p.getXRot();
            s.x = p.getX();
            s.y = p.getY();
            s.z = p.getZ();
            s.sneaking = p.isShiftKeyDown();
            return s;
        });
    }

    /** Respawned, teleported or changed dimension: where they are now is the new starting point, not "they walked". */
    public static void rebase(ServerPlayer p) {
        State s = STATES.get(p.getUUID());
        if (s != null) {
            s.yaw = p.getYRot();
            s.pitch = p.getXRot();
            s.x = p.getX();
            s.y = p.getY();
            s.z = p.getZ();
        }
    }

    private static long now(ServerPlayer p) {
        return p.server.getTickCount();
    }

    /** The player did something: they are here. */
    public static void markActive(ServerPlayer p) {
        markActive(p, now(p));
    }

    static void markActive(ServerPlayer p, long now) {
        if (p instanceof FakePlayer) {
            return;
        }
        State s = state(p, now);
        s.lastActive = now;
        if (s.afk && (!s.manual || now - s.afkSince >= MANUAL_SETTLE_TICKS)) {
            setAfk(p, s, false, false, now);
        }
    }

    /**
     * /afk: AFK right now.
     *
     * @return null if it worked, otherwise why not
     */
    @Nullable
    public static String goAfk(ServerPlayer p) {
        if (!enabled()) {
            return "AFK protection is switched off on this server.";
        }
        if (com.foglurker.brain.LurkerDirector.isHunted(p.getUUID())) {
            return "Not while it is hunting you. Get away from it first.";
        }
        State s = state(p, now(p));
        if (s.afk) {
            return null;
        }
        setAfk(p, s, true, true, now(p));
        return null;
    }

    /** /afk again: back. */
    public static void comeBack(ServerPlayer p) {
        State s = state(p, now(p));
        s.lastActive = now(p);
        if (s.afk) {
            setAfk(p, s, false, false, now(p));
        }
    }

    private static void setAfk(ServerPlayer p, State s, boolean afk, boolean manual, long now) {
        s.afk = afk;
        s.manual = afk && manual;
        s.afkSince = now;
        p.refreshTabListName();
        if (afk) {
            // Everyone on the server sees it, like a join message.
            p.server.getPlayerList().broadcastSystemMessage(Component.literal(p.getGameProfile().getName() + " went afk")
                    .withStyle(ChatFormatting.GRAY), false);
            p.sendSystemMessage(Component.literal(manual
                    ? "It will leave you alone until you move or look around. (/afk again to come back)"
                    : "You did nothing for " + FogLurkerConfig.COMMON.afkMinutes.get()
                    + " minutes. It will leave you alone until you move or look around.").withStyle(ChatFormatting.DARK_GRAY));
            showAfk(p);
        } else {
            p.displayClientMessage(Component.empty(), true); // clear "AFK" above the hotbar
            p.sendSystemMessage(Component.literal("Welcome back. You are no longer AFK.").withStyle(ChatFormatting.GRAY));
        }
    }

    /** "AFK" just above the hotbar. Minecraft fades those after ~3 s, so it is shown again every second. */
    private static void showAfk(ServerPlayer p) {
        p.displayClientMessage(Component.literal("AFK").withStyle(ChatFormatting.YELLOW, ChatFormatting.BOLD), true);
    }

    /**
     * Compares where this player looks and stands with last time, and makes them AFK after the
     * configured time with no activity. Public for the game tests.
     */
    public static void check(ServerPlayer p, long now) {
        if (p instanceof FakePlayer || !enabled() || !p.isAlive()) {
            return; // on the death screen nothing is checked (no "went afk" while dead)
        }
        State s = state(p, now);
        // Looking around is always the player: nothing else turns your head.
        boolean looked = Math.abs(net.minecraft.util.Mth.wrapDegrees(p.getYRot() - s.yaw)) > 0.5F
                || Math.abs(p.getXRot() - s.pitch) > 0.5F;
        // Moving yourself counts. A jump of more than 12 blocks in a second is a respawn or a teleport, never
        // walking. Knockback (hurtTime) and water currents do not count. (The server cannot see keys, so a
        // shove along the ground by a piston or a mob still wakes you.)
        double moved = Math.hypot(p.getX() - s.x, p.getZ() - s.z);
        double climbedUp = Math.abs(p.getY() - s.y);
        // Elytra flight can cover far more than 12 blocks a second; it is still the player flying.
        // invulnerableTime is above 0 for a second after any hit, so knockback earlier in the window does not count.
        // So can a horse at full gallop (about 14 blocks a second) or a boat on ice; a teleport throws you off it.
        net.minecraft.world.entity.Entity vehicle = p.getVehicle();
        double cap = vehicle != null && vehicle.getControllingPassenger() == p ? 100.0 : 12.0;
        boolean ownPace = (p.isFallFlying() || (moved <= cap && climbedUp <= 12.0)) && p.hurtTime == 0 && p.invulnerableTime == 0;
        boolean walked = ownPace && moved > 0.3 && !p.isPassenger()
                && (p.onGround() || p.isFallFlying() || p.isSwimming() || p.getAbilities().flying)
                && (!p.isInWater() || p.isSwimming());
        boolean climbed = ownPace && climbedUp > 0.5 && p.onClimbable() && !p.isPassenger();
        // Steering a horse, boat or pig counts (a minecart, which nobody steers, does not).
        boolean steered = ownPace && moved > 1.0 && vehicle != null && vehicle.getControllingPassenger() == p;
        boolean sneaked = p.isShiftKeyDown() != s.sneaking;
        s.yaw = p.getYRot();
        s.pitch = p.getXRot();
        s.x = p.getX();
        s.y = p.getY();
        s.z = p.getZ();
        s.sneaking = p.isShiftKeyDown();
        walked = walked || climbed || steered;
        if (looked || walked || sneaked) {
            markActive(p, now);
        } else if (!s.afk && now - s.lastActive >= afkTicks()) {
            setAfk(p, s, true, false, now);
        }
        if (s.afk) {
            showAfk(p); // keep "AFK" above the hotbar for as long as it lasts
        }
    }

    // ------------------------------------------------------------------ events

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % CHECK_EVERY != 0) {
            return;
        }
        if (!enabled()) {
            // Switched off while someone was AFK: take the [AFK] tag down.
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                State s = STATES.get(p.getUUID());
                if (s != null && s.afk) {
                    s.afk = false;
                    s.manual = false;
                    p.refreshTabListName();
                    p.displayClientMessage(Component.empty(), true);
                }
            }
            return;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            check(p, now);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            rebase(p); // respawning happens to you: it does not end AFK (README)
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            rebase(p);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            STATES.remove(p.getUUID());
            state(p, now(p)); // a fresh start: here, not AFK
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }

    @SubscribeEvent
    public static void onTabListName(PlayerEvent.TabListNameFormat event) {
        if (isAfk(event.getEntity())) {
            Component base = event.getDisplayName() != null ? event.getDisplayName() : event.getEntity().getDisplayName();
            event.setDisplayName(base.copy().append(Component.literal(" [AFK]").withStyle(ChatFormatting.GRAY)));
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer p = event.getPlayer();
        p.server.execute(() -> {
            if (!p.hasDisconnected()) { // (sent, then left in the same moment: nothing to mark)
                markActive(p);
            }
        });
    }

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            markActive(p);
        }
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer p) {
            markActive(p);
        }
    }

    @SubscribeEvent
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            markActive(p);
        }
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            markActive(p);
        }
    }

    @SubscribeEvent
    public static void onUseItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            markActive(p);
        }
    }

    @SubscribeEvent
    public static void onHitBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            markActive(p);
        }
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            markActive(p);
        }
    }
}
