package com.foglurker.client;

import com.foglurker.FogLurkerMod;
import com.foglurker.config.FogLurkerConfig;
import com.foglurker.util.FogCurve;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import org.jetbrains.annotations.Nullable;
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
    private static final Comparator<Wisp> FAR_FIRST = Comparator.comparingDouble((Wisp w) -> w.distSq).reversed();
    private static boolean broken;

    private FogWisps() {
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
            // Even over the area (not bunched toward the middle), and never right on top of another wisp: an even
            // layer of mist, not clumps.
            double dist = 2.5 + Math.sqrt(r.nextDouble()) * spread;
            double x = p.getX() + Math.cos(angle) * dist;
            double z = p.getZ() + Math.sin(angle) * dist;
            BlockPos column = BlockPos.containing(x, p.getY(), z);
            if (!mc.level.isLoaded(column)) {
                continue;
            }
            Double floor = floorNear(mc, column, p.getY());
            if (floor == null) {
                continue; // over a cliff edge, a ravine or open air: mist lies on the ground, it does not float
            }
            double y = floor + 0.2 + r.nextDouble() * 1.1; // low, lying on the ground
            if (tooClose(x, y, z)) {
                continue;
            }
            Wisp w = new Wisp();
            w.x = w.ox = x;
            w.y = w.oy = y;
            w.z = w.oz = z;
            w.dx = (r.nextDouble() - 0.5) * 0.03;
            w.dy = 0; // it drifts along the ground; it never rises up into the air
            w.dz = (r.nextDouble() - 0.5) * 0.03;
            w.size = 2.4F + r.nextFloat() * 2.2F;
            w.roll = w.oRoll = (r.nextFloat() - 0.5F) * 0.3F; // nearly level: a low band, not a tilted blob
            w.spin = (r.nextFloat() - 0.5F) * 0.0015F;
            w.peak = 0.12F + 0.26F * fog; // bigger and fainter: they blend into one layer
            w.variant = r.nextInt(4);
            w.life = 110 + r.nextInt(90);
            w.bright = brightness(mc, w);
            WISPS.add(w);
        }
    }

    /**
     * The floor near this column at about the player's height: the ground outside, the floor of a cave, a roof
     * you stand on. Null if there is none within a few blocks (a cliff edge, a ravine, open air).
     */
    @Nullable
    private static Double floorNear(Minecraft mc, BlockPos column, double playerY) {
        BlockPos.MutableBlockPos pos = column.mutable();
        int top = (int) Math.floor(playerY) + 3;
        for (int y = top; y >= top - 9; y--) {
            pos.setY(y);
            var below = mc.level.getBlockState(pos.below());
            if (mc.level.getBlockState(pos).getCollisionShape(mc.level, pos).isEmpty()
                    && !below.getCollisionShape(mc.level, pos.below()).isEmpty()
                    && below.getFluidState().isEmpty()) {
                return (double) y;
            }
        }
        return null;
    }

    /** Another wisp already this close: it would only thicken one spot into a clump. */
    private static boolean tooClose(double x, double y, double z) {
        for (Wisp o : WISPS) {
            double dx = o.x - x;
            double dy = (o.y - y) * 2; // (height counts double: they are a low layer)
            double dz = o.z - z;
            if (dx * dx + dy * dy + dz * dz < 2.6 * 2.6) {
                return true;
            }
        }
        return false;
    }

    /** Lit like the world around it, but never darker than faint moonlight (mist at night is pale, not black). */
    private static float brightness(Minecraft mc, Wisp w) {
        int light = mc.level.getMaxLocalRawBrightness(BlockPos.containing(w.x, w.y, w.z));
        return 0.24F + 0.76F * (light / 15F);
    }

    /** After the world and Fog Lurker's own fog: the wisps on top, sorted far to near, hidden behind blocks. */
    private static boolean textureLoaded;

    /** Loads the mist texture before the first wisp is drawn (else the first frames show the "missing" texture). */
    static void preload(Minecraft mc) {
        if (!textureLoaded) {
            textureLoaded = true;
            mc.getTextureManager().getTexture(TEXTURE);
        }
    }

    /** At the end of the world pass (ClientSetup, WorldRenderEvents.LAST); {@code pose} carries the camera's turn. */
    static void render(Camera camera, Matrix4f pose, float partialTick, float fog) {
        if (WISPS.isEmpty() || broken || !textureLoaded) {
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
        WISPS.sort(FAR_FIRST); // far ones first, for blending
        // They thin out into the fog with distance like everything else does.
        float vanillaFar = Math.max(mc.gameRenderer.getRenderDistance(), 32F);
        float vanillaNear = vanillaFar - Mth.clamp(vanillaFar / 10F, 4F, 64F);
        float far = FogCurve.far(Math.max(fog, 0.05F), vanillaFar);
        float near = FogCurve.near(Math.max(fog, 0.05F), far, vanillaNear);
        org.joml.Vector3fc left = camera.getLeftVector();
        org.joml.Vector3fc up = camera.getUpVector();

        // Minecraft's own textured, coloured shader (like the 1.20.1 version's: soft edges, no lighting).
        com.mojang.blaze3d.vertex.BufferBuilder bb = com.mojang.blaze3d.vertex.Tesselator.getInstance().begin(
                com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS, com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR);
        for (Wisp w : WISPS) {
            float d = (float) Math.sqrt(w.distSq);
            float fade = Math.min(1F, w.age / 25F) * Math.min(1F, (w.life - w.age) / 30F);
            float close = Mth.clamp((d - 1.0F) / 2.5F, 0F, 1F); // never a wall of mist pressed against your eyes
            // Mist is something around you: it thins out well before the fog's far edge (no row of puffs on the horizon).
            float fadeFrom = Math.min(6F, far * 0.25F);
            float fadeTo = Math.max(fadeFrom + 2F, far * 0.6F);
            float faded = 1F - Mth.clamp((d - fadeFrom) / (fadeTo - fadeFrom), 0F, 1F);
            float a = w.peak * fade * close * faded;
            if (a < 0.004F) {
                continue;
            }
            float rr = Mth.clamp(Mth.lerp(partialTick, w.oRoll, w.roll), -0.2F, 0.2F);
            float cos = Mth.cos(rr);
            float sin = Mth.sin(rr);
            // Facing the camera, wide and low (2.5 times wider than tall): mist lies along the ground in bands.
            float sx = w.size;
            float sy = w.size * 0.4F;
            float ax = (left.x() * cos + up.x() * sin) * sx;
            float ay = (left.y() * cos + up.y() * sin) * sx;
            float az = (left.z() * cos + up.z() * sin) * sx;
            float bx = (up.x() * cos - left.x() * sin) * sy;
            float by = (up.y() * cos - left.y() * sin) * sy;
            float bz = (up.z() * cos - left.z() * sin) * sy;
            float x = (float) (Mth.lerp(partialTick, w.ox, w.x) - cam.x);
            float y = (float) (Mth.lerp(partialTick, w.oy, w.y) - cam.y);
            float z = (float) (Mth.lerp(partialTick, w.oz, w.z) - cam.z);
            float u0 = (w.variant % 2) * 0.5F;
            float v0 = (w.variant / 2) * 0.5F;
            float c = w.bright;
            float red = 0.84F * c;
            float green = 0.86F * c;
            float blue = 0.90F * c;
            bb.addVertex(pose, x - ax - bx, y - ay - by, z - az - bz).setUv(u0, v0 + 0.5F).setColor(red, green, blue, a);
            bb.addVertex(pose, x - ax + bx, y - ay + by, z - az + bz).setUv(u0, v0).setColor(red, green, blue, a);
            bb.addVertex(pose, x + ax + bx, y + ay + by, z + az + bz).setUv(u0 + 0.5F, v0).setColor(red, green, blue, a);
            bb.addVertex(pose, x + ax - bx, y + ay - by, z + az - bz).setUv(u0 + 0.5F, v0 + 0.5F).setColor(red, green, blue, a);
        }
        com.mojang.blaze3d.vertex.MeshData mesh = bb.build();
        if (mesh == null) {
            return; // nothing near enough to see
        }
        org.joml.Matrix4fStack modelView = com.mojang.blaze3d.systems.RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity(); // the vertices already carry the camera's turn (pose)
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
        com.mojang.blaze3d.systems.RenderSystem.setShader(net.minecraft.client.renderer.GameRenderer::getPositionTexColorShader);
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, TEXTURE);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        com.mojang.blaze3d.systems.RenderSystem.defaultBlendFunc();
        com.mojang.blaze3d.systems.RenderSystem.enableDepthTest();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(false);
        com.mojang.blaze3d.systems.RenderSystem.disableCull();
        com.mojang.blaze3d.vertex.BufferUploader.drawWithShader(mesh);
        com.mojang.blaze3d.systems.RenderSystem.enableCull();
        com.mojang.blaze3d.systems.RenderSystem.depthMask(true);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        modelView.popMatrix();
        com.mojang.blaze3d.systems.RenderSystem.applyModelViewMatrix();
    }
}
