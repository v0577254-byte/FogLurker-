package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.util.FogCurve;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Real fog in the world: big, soft wisps of mist drifting around you while it hunts you, more and closer the
 * thicker the fog. Drawn by Fog Lurker itself after the world (and after its own fog), so they show the same with
 * Distant Horizons, Embeddium or any render distance, keep their soft edges, fade into the fog with distance like
 * the world does, and stay a pale moonlit grey at night instead of turning black.
 */
final class FogWisps {
    private static final ResourceLocation TEXTURE = FogLurkerMod.id("textures/misc/fog_wisps.png");

    private static final class Wisp {
        double x;
        double y;
        double z;
        double ox;
        double oy;
        double oz;
        double dx;
        double dy;
        double dz;
        float size;
        float roll;
        float oRoll;
        float spin;
        float peak;
        float bright = 1F;
        int variant;
        int age;
        int life;
        double distSq;
    }

    private static final List<Wisp> WISPS = new ArrayList<>();
    @Nullable
    private static ShaderInstance shader;
    private static boolean broken;

    private FogWisps() {
    }

    static void setShader(ShaderInstance s) {
        shader = s;
    }

    static void clear() {
        WISPS.clear();
    }

    static int count() {
        return WISPS.size();
    }

    /** Client tick: move, age and (while the fog is up) add wisps around the player. */
    static void tick(Minecraft mc, float fog) {
        if (mc.level == null || mc.player == null) {
            WISPS.clear();
            return;
        }
        var p = mc.player;
        for (var it = WISPS.iterator(); it.hasNext(); ) {
            Wisp w = it.next();
            w.ox = w.x;
            w.oy = w.y;
            w.oz = w.z;
            w.oRoll = w.roll;
            w.x += w.dx;
            w.y += w.dy;
            w.z += w.dz;
            w.roll += w.spin;
            if (fog < 0.03F && w.life - w.age > 30) {
                w.life = w.age + 30; // the fog is lifting: every wisp fades out within 1.5 s
            }
            if (++w.age >= w.life || p.distanceToSqr(w.x, w.y, w.z) > 48 * 48) {
                it.remove();
            } else if (w.age % 10 == 0) {
                w.bright = brightness(mc, w);
            }
        }
        if (!FogLurkerConfig.CLIENT.fogEffects.get() || !FogLurkerConfig.CLIENT.fogParticles.get() || fog < 0.05F) {
            return;
        }
        float scale = switch (mc.options.particles().get()) {
            case ALL -> 1F;
            case DECREASED -> 0.5F;
            case MINIMAL -> 0.2F;
        };
        int maxAlive = (int) ((40 + 200 * fog) * scale);
        int perTick = Math.max(1, Math.round(fog * 6 * scale));
        RandomSource r = mc.level.getRandom();
        // Thick fog: the wisps crowd in close (you cannot see far anyway). Light fog: spread wide around you.
        float spread = 6F + 22F * (1F - fog);
        for (int i = 0; i < perTick && WISPS.size() < maxAlive; i++) {
            double angle = r.nextDouble() * Math.PI * 2;
            double dist = 2.5 + r.nextDouble() * spread;
            double x = p.getX() + Math.cos(angle) * dist;
            double z = p.getZ() + Math.sin(angle) * dist;
            BlockPos column = BlockPos.containing(x, p.getY(), z);
            if (!mc.level.isLoaded(column)) {
                continue;
            }
            int ground = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
            // On open ground it hugs the ground; in a cave, under a roof or on a tower it stays at your height.
            double y = Math.abs(ground - p.getY()) <= 8 ? ground + 0.3 + r.nextDouble() * 2.2 : p.getY() - 0.5 + r.nextDouble() * 2.5;
            Wisp w = new Wisp();
            w.x = w.ox = x;
            w.y = w.oy = y;
            w.z = w.oz = z;
            w.dx = (r.nextDouble() - 0.5) * 0.03;
            w.dy = 0.001 + r.nextDouble() * 0.003;
            w.dz = (r.nextDouble() - 0.5) * 0.03;
            w.size = 1.6F + r.nextFloat() * 2.4F;
            w.roll = w.oRoll = r.nextFloat() * Mth.TWO_PI;
            w.spin = (r.nextFloat() - 0.5F) * 0.012F;
            w.peak = 0.16F + 0.34F * fog;
            w.variant = r.nextInt(4);
            w.life = 110 + r.nextInt(90);
            w.bright = brightness(mc, w);
            WISPS.add(w);
        }
    }

    /** Lit like the world around it, but never darker than faint moonlight (mist at night is pale, not black). */
    private static float brightness(Minecraft mc, Wisp w) {
        int light = mc.level.getMaxLocalRawBrightness(BlockPos.containing(w.x, w.y, w.z));
        return 0.34F + 0.66F * (light / 15F);
    }

    /** After the world and Fog Lurker's own fog: the wisps on top, sorted far to near, hidden behind blocks. */
    static void render(Camera camera, Matrix4f pose, float partialTick, float fog) {
        if (WISPS.isEmpty() || shader == null || broken) {
            return;
        }
        try {
            draw(camera, pose, partialTick, fog);
        } catch (RuntimeException e) {
            broken = true;
            FogLurkerMod.LOGGER.warn("Fog Lurker: could not draw the fog wisps ({}); the rest of the fog still shows", e.toString());
        }
    }

    private static void draw(Camera camera, Matrix4f pose, float partialTick, float fog) {
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = camera.getPosition();
        for (Wisp w : WISPS) {
            double x = Mth.lerp(partialTick, w.ox, w.x) - cam.x;
            double y = Mth.lerp(partialTick, w.oy, w.y) - cam.y;
            double z = Mth.lerp(partialTick, w.oz, w.z) - cam.z;
            w.distSq = x * x + y * y + z * z;
        }
        WISPS.sort(Comparator.comparingDouble((Wisp w) -> w.distSq).reversed()); // far ones first, for blending
        // They thin out into the fog with distance like everything else does.
        float vanillaFar = Math.max(mc.gameRenderer.getRenderDistance(), 32F);
        float vanillaNear = vanillaFar - Mth.clamp(vanillaFar / 10F, 4F, 64F);
        float far = FogCurve.far(Math.max(fog, 0.05F), vanillaFar);
        float near = FogCurve.near(Math.max(fog, 0.05F), far, vanillaNear);
        Vector3f left = camera.getLeftVector();
        Vector3f up = camera.getUpVector();

        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = 0;
        for (Wisp w : WISPS) {
            float d = (float) Math.sqrt(w.distSq);
            float fade = Math.min(1F, w.age / 25F) * Math.min(1F, (w.life - w.age) / 30F);
            float close = Mth.clamp((d - 1.0F) / 2.5F, 0F, 1F); // never a wall of mist pressed against your eyes
            float faded = 1F - Mth.clamp((d - near) / Math.max(far - near, 0.5F), 0F, 1F);
            float a = w.peak * fade * close * faded;
            if (a < 0.004F) {
                continue;
            }
            float rr = Mth.lerp(partialTick, w.oRoll, w.roll);
            float cos = Mth.cos(rr) * w.size;
            float sin = Mth.sin(rr) * w.size;
            // The quad's two half-axes, turned by the wisp's slow roll, always facing the camera.
            float ax = left.x() * cos + up.x() * sin;
            float ay = left.y() * cos + up.y() * sin;
            float az = left.z() * cos + up.z() * sin;
            float bx = up.x() * cos - left.x() * sin;
            float by = up.y() * cos - left.y() * sin;
            float bz = up.z() * cos - left.z() * sin;
            float x = (float) (Mth.lerp(partialTick, w.ox, w.x) - cam.x);
            float y = (float) (Mth.lerp(partialTick, w.oy, w.y) - cam.y);
            float z = (float) (Mth.lerp(partialTick, w.oz, w.z) - cam.z);
            float u0 = (w.variant % 2) * 0.5F;
            float v0 = (w.variant / 2) * 0.5F;
            float c = w.bright;
            float red = 0.84F * c;
            float green = 0.86F * c;
            float blue = 0.90F * c;
            bb.vertex(pose, x - ax - bx, y - ay - by, z - az - bz).uv(u0, v0 + 0.5F).color(red, green, blue, a).endVertex();
            bb.vertex(pose, x - ax + bx, y - ay + by, z - az + bz).uv(u0, v0).color(red, green, blue, a).endVertex();
            bb.vertex(pose, x + ax + bx, y + ay + by, z + az + bz).uv(u0 + 0.5F, v0).color(red, green, blue, a).endVertex();
            bb.vertex(pose, x + ax - bx, y + ay - by, z + az - bz).uv(u0 + 0.5F, v0 + 0.5F).color(red, green, blue, a).endVertex();
            quads++;
        }
        var built = bb.end();
        if (quads == 0) {
            built.release();
            return;
        }
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity(); // the vertices already carry the camera's turn (pose)
        RenderSystem.applyModelViewMatrix();
        ShaderInstance s = shader;
        RenderSystem.setShader(() -> s);
        RenderSystem.setShaderTexture(0, TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(built);
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        modelView.popPose();
        RenderSystem.applyModelViewMatrix();
    }
}
