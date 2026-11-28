package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.entity.Disguise;
import com.foglurker.entity.FogLurkerEntity;
import com.foglurker.registry.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * TEMPORARY (part of /foglurker debugeverything): checks the client side and prints the results in
 * your own chat. Remove together with DebugCommand and DebugPacket before release.
 */
public final class ClientDebug {
    private static int passed;
    private static int failed;

    private ClientDebug() {
    }

    public static void run() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        passed = 0;
        failed = 0;
        say(Component.literal("— Your game —").withStyle(ChatFormatting.GOLD));

        FogLurkerEntity probe = ModEntities.FOG_LURKER.get().create(mc.level);
        try {
            EntityRenderer<?> renderer = mc.getEntityRenderDispatcher().getRenderer(probe);
            check("Creature renderer", renderer instanceof FogLurkerRenderer, renderer.getClass().getSimpleName());
        } catch (Throwable t) {
            check("Creature renderer", false, t.toString());
        }
        try {
            mc.getEntityModels().bakeLayer(CrawlerModel.LAYER);
            check("Fog Lurker model", true, "long limbs, jaw and teeth baked");
        } catch (Throwable t) {
            check("Fog Lurker model", false, t.toString());
        }
        List<String> missingTex = new ArrayList<>();
        for (ResourceLocation tex : List.of(CrawlerRenderer.TEXTURE, CrawlerRenderer.EYES_TEXTURE)) {
            if (mc.getResourceManager().getResource(tex).isEmpty()) {
                missingTex.add(tex.getPath());
            }
        }
        check("Textures", missingTex.isEmpty(), missingTex.isEmpty() ? "the Fog Lurker and its glowing eyes" : "missing " + missingTex);
        List<String> missingSounds = new ArrayList<>();
        for (String s : List.of("lurker.scream", "lurker.reveal", "lurker.whisper", "lurker.vanish", "lurker.heartbeat",
                "lurker.hurt", "lurker.attack")) {
            if (mc.getSoundManager().getSoundEvent(FogLurkerMod.id(s)) == null) {
                missingSounds.add(s);
            }
        }
        check("Sounds", missingSounds.isEmpty(), missingSounds.isEmpty() ? "7 sounds defined" : "missing " + missingSounds);
        check("Language file", I18n.exists("entity.foglurker.fog_lurker"), I18n.get("entity.foglurker.fog_lurker"));

        List<String> broken = new ArrayList<>();
        int ok = 0;
        for (Disguise d : Disguise.values()) {
            if (d == Disguise.CRAWLER) {
                continue;
            }
            FogLurkerEntity e = ModEntities.FOG_LURKER.get().create(mc.level);
            try {
                e.setDisguise(d);
                if (d == Disguise.PLAYER) {
                    e.setMimic(mc.player.getUUID(), mc.player.getGameProfile().getName());
                }
                LivingEntity puppet = DisguiseCache.get(e);
                if (puppet != null && mc.getEntityRenderDispatcher().getRenderer(puppet) != null) {
                    ok++;
                } else {
                    broken.add(d.id());
                }
            } catch (Throwable t) {
                broken.add(d.id() + " (" + t.getClass().getSimpleName() + ")");
            } finally {
                DisguiseCache.forget(e.getId());
                e.discard();
            }
        }
        check("Disguise puppets", broken.isEmpty(), ok + " shapes ready" + (broken.isEmpty() ? "" : ", broken: " + broken));
        // Your skin, and the copy of you: they must be the same picture.
        try {
            var conn = mc.getConnection();
            var mine = mc.player.getSkin();
            boolean defaultSkin = mine.texture().equals(
                    net.minecraft.client.resources.DefaultPlayerSkin.get(mc.player.getUUID()).texture());
            check("Your skin", !defaultSkin, defaultSkin
                    ? "Minecraft is showing the default skin for you, so it did not get your skin from Mojang this session "
                    + "(not something the mod changes). Restart the game; if it stays, check your skin at minecraft.net"
                    : "loaded");
            FogLurkerEntity copy = ModEntities.FOG_LURKER.get().create(mc.level);
            try {
                copy.setDisguise(Disguise.PLAYER);
                copy.setMimic(mc.player.getUUID(), mc.player.getGameProfile().getName());
                LivingEntity puppet = DisguiseCache.get(copy);
                boolean same = puppet instanceof net.minecraft.client.player.AbstractClientPlayer ap
                        && ap.getSkin().texture().equals(mine.texture()) && ap.getSkin().model() == mine.model();
                check("Copies your skin", same, same ? "a copy of you wears your skin and arm type"
                        : "the copy of you would wear a different skin" + (conn == null ? " (no connection)" : ""));
            } finally {
                DisguiseCache.forget(copy.getId());
                copy.discard();
            }
        } catch (Throwable t) {
            check("Copies your skin", false, t.getClass().getSimpleName());
        }
        check("Swim animation", DisguiseCache.swimReflectionWorks(), "a stolen player lies flat when it sprint-swims");
        check("Fog & screen effects", true, "fog " + (FogLurkerConfig.CLIENT.fogEffects.get() ? "on" : "off")
                + ", shake " + (FogLurkerConfig.CLIENT.screenEffects.get() ? "on" : "off"));
        probe.discard();

        say(Component.literal("Your game: " + passed + " passed, " + failed + " failed")
                .withStyle(failed == 0 ? ChatFormatting.GREEN : ChatFormatting.RED, ChatFormatting.BOLD));
        say(Component.literal("Now playing a 2 second fog + heartbeat test...").withStyle(ChatFormatting.GRAY));
        ClientEffects.setFogTarget(0.6F);
        ClientEffects.scare(40);
        // Two seconds in, check that Minecraft is really using the creature's fog.
        fogCheckIn = 40;
    }

    private static int fogCheckIn;

    /** End of every client tick (ClientSetup). */
    public static void onClientTick() {
        if (fogCheckIn <= 0 || --fogCheckIn > 0) {
            return;
        }
        int asked = ClientEffects.fogEventsLastSecond();
        int used = ClientEffects.fogAppliedLastSecond();
        int own = DepthFog.framesLastSecond();
        int shown = DepthFog.worldFogFramesLastSecond();
        boolean ok = shown > 0 || own > 0;
        String detail = shown > 0 ? "the world fog is working (" + shown + " frames in the last second)"
                : own > 0 ? "another mod switches Minecraft's fog off, so Fog Lurker draws its own fog over the world ("
                + own + " frames in the last second)"
                : asked == 0 ? "Minecraft never asked for the world fog and Fog Lurker could not draw its own: a shader pack "
                + "replaces the picture. The fog veil over the screen still shows it" + (FogLurkerConfig.CLIENT.fogOverlay.get() ? "." : " (turn fogOverlay on).")
                : !FogLurkerConfig.CLIENT.fogEffects.get() ? "fogEffects is off in config/foglurker-client.toml"
                : "the fog was asked for but not used (" + asked + " frames); tell the mod author";
        passed = 0;
        failed = 0;
        check("World fog", ok, detail);
        check("Fog wisps", !FogLurkerConfig.CLIENT.fogParticles.get() || DepthFog.wispCount() > 0,
                !FogLurkerConfig.CLIENT.fogParticles.get() ? "off (fogParticles in config/foglurker-client.toml)"
                : DepthFog.wispCount() + " wisps of mist drifting around you");
        check("Fog veil", FogLurkerConfig.CLIENT.fogOverlay.get(), FogLurkerConfig.CLIENT.fogOverlay.get()
                ? "on: the fog also shows as a layer over the screen" : "off (fogOverlay in config/foglurker-client.toml)");
    }

    private static void check(String what, boolean ok, String detail) {
        if (ok) {
            passed++;
        } else {
            failed++;
        }
        MutableComponent c = Component.literal(ok ? "✔ " : "✘ ").withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED)
                .append(Component.literal(what).withStyle(ChatFormatting.WHITE));
        if (detail != null && !detail.isEmpty()) {
            c.append(Component.literal(" — " + detail).withStyle(ChatFormatting.GRAY));
        }
        say(c);
    }

    private static void say(Component c) {
        Minecraft.getInstance().gui.getChat().addMessage(c);
    }
}
