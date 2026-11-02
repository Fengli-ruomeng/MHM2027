package com.mhm.mhm2027.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.gateway.ModAttachments;
import com.mhm.mhm2027.hud.HudTargets;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The target callout: brackets lock onto the gateway under the crosshair, and a label beside
 * them grows as you learn more. Seen: name, level and distance. Inspected: its flaws, and how
 * old that is. Then your attempt on it and your session. The server sends every gateway in
 * scan range; this picks one each frame, so the brackets lock on with no delay.
 */
final class TargetCallout {
    /** After the crosshair slips off, the lock holds this long, so a twitch doesn't drop it. */
    private static final long HOLD_MILLIS = 250;
    private static final long LOCK_MILLIS = 150;
    private static final int LINE = 10;
    private static final int PANEL = 0xD00B0F17;

    private static HudTargets.@Nullable Seen locked;
    private static long lockedAt;
    private static long lastSeenAt;

    private TargetCallout() {
    }

    /** The gateway the callout is on now, for the quick wheel's panel. */
    static HudTargets.@Nullable Seen current() {
        return locked;
    }

    static void draw(GuiGraphicsExtractor graphics, Font font, Minecraft minecraft, int width, int height, float partialTick, long now) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        HudTargets.Seen picked = pick(minecraft, player, partialTick);
        if (picked != null) {
            if (locked == null || locked.entityId() != picked.entityId()) {
                lockedAt = now;
            }
            locked = picked;
            lastSeenAt = now;
        } else if (locked != null) {
            // Hold on briefly, with the latest news about it.
            HudTargets.Seen latest = find(locked.entityId());
            locked = now - lastSeenAt < HOLD_MILLIS && latest != null ? latest : null;
        }
        if (locked == null) {
            return;
        }
        Entity entity = minecraft.level.getEntity(locked.entityId());
        if (entity == null) {
            locked = null;
            return;
        }
        float[] box = screenBox(minecraft.gameRenderer.getMainCamera(), entity, partialTick, width, height);
        if (box == null) {
            return;
        }
        double age = HudFeed.targetsAge(now);
        int accent = !locked.attempt().isEmpty() || !locked.session().isEmpty() ? EdgeShell.MAGENTA
                : locked.player() ? EdgeShell.ORANGE : EdgeShell.CYAN;
        brackets(graphics, box, accent, Math.clamp((now - lockedAt) / (float) LOCK_MILLIS, 0, 1));
        label(graphics, font, box, locked, entity.distanceTo(player), accent, age, width, height);
    }

    /** The gateway under the crosshair, within scan range and not behind a wall. */
    private static HudTargets.@Nullable Seen pick(Minecraft minecraft, LocalPlayer player, float partialTick) {
        double range = player.getData(ModAttachments.GATEWAY).tier().scanRange();
        Vec3 eye = player.getEyePosition(partialTick);
        Vec3 end = eye.add(player.getViewVector(partialTick).scale(range));
        BlockHitResult wall = minecraft.level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double nearest = wall.getType() == HitResult.Type.MISS ? range * range : eye.distanceToSqr(wall.getLocation());
        HudTargets.Seen best = null;
        for (HudTargets.Seen seen : HudFeed.targets().seen()) {
            Entity entity = minecraft.level.getEntity(seen.entityId());
            if (entity == null) {
                continue;
            }
            AABB box = entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position())).inflate(0.2);
            Optional<Vec3> hit = box.clip(eye, end);
            if (box.contains(eye)) {
                hit = Optional.of(eye);
            }
            if (hit.isPresent() && eye.distanceToSqr(hit.get()) < nearest) {
                nearest = eye.distanceToSqr(hit.get());
                best = seen;
            }
        }
        return best;
    }

    private static HudTargets.@Nullable Seen find(int entityId) {
        for (HudTargets.Seen seen : HudFeed.targets().seen()) {
            if (seen.entityId() == entityId) {
                return seen;
            }
        }
        return null;
    }

    /** The entity's box on the GUI as left, top, right, bottom; null if any of it is behind the camera. */
    private static float @Nullable [] screenBox(Camera camera, Entity entity, float partialTick, int width, int height) {
        AABB box = entity.getBoundingBox().move(entity.getPosition(partialTick).subtract(entity.position()));
        float left = Float.MAX_VALUE;
        float top = Float.MAX_VALUE;
        float right = -Float.MAX_VALUE;
        float bottom = -Float.MAX_VALUE;
        for (int corner = 0; corner < 8; corner++) {
            Vec3 point = new Vec3((corner & 1) == 0 ? box.minX : box.maxX, (corner & 2) == 0 ? box.minY : box.maxY,
                    (corner & 4) == 0 ? box.minZ : box.maxZ);
            float[] at = project(camera, point, width, height);
            if (at == null) {
                return null;
            }
            left = Math.min(left, at[0]);
            top = Math.min(top, at[1]);
            right = Math.max(right, at[0]);
            bottom = Math.max(bottom, at[1]);
        }
        return new float[]{left, top, right, bottom};
    }

    /** A world point on the GUI, or null if it's behind the camera. */
    private static float @Nullable [] project(Camera camera, Vec3 point, int width, int height) {
        Vec3 relative = point.subtract(camera.position());
        Vector3fc forward = camera.forwardVector();
        Vector3fc up = camera.upVector();
        Vector3fc left = camera.leftVector();
        double depth = relative.x * forward.x() + relative.y * forward.y() + relative.z * forward.z();
        if (depth < 0.05) {
            return null;
        }
        double across = -(relative.x * left.x() + relative.y * left.y() + relative.z * left.z());
        double upward = relative.x * up.x() + relative.y * up.y() + relative.z * up.z();
        float fov = camera.getFov() > 1 ? camera.getFov() : Minecraft.getInstance().options.fov().get();
        double scale = height / 2.0 / Math.tan(Math.toRadians(fov) / 2);
        return new float[]{(float) (width / 2.0 + across / depth * scale), (float) (height / 2.0 - upward / depth * scale)};
    }

    /** Four corners, closing in from a little wider as they lock on. */
    private static void brackets(GuiGraphicsExtractor graphics, float[] box, int color, float lock) {
        int spread = Math.round((1 - lock) * 8);
        int left = Math.round(box[0]) - 2 - spread;
        int top = Math.round(box[1]) - 2 - spread;
        int right = Math.round(box[2]) + 2 + spread;
        int bottom = Math.round(box[3]) + 2 + spread;
        int arm = Math.max(3, Math.min(7, Math.min(right - left, bottom - top) / 4));
        int faded = LensHud.fade(color, 0.4f + 0.6f * lock);
        // Top left, top right, bottom left, bottom right.
        graphics.fill(left, top, left + arm, top + 1, faded);
        graphics.fill(left, top, left + 1, top + arm, faded);
        graphics.fill(right - arm, top, right, top + 1, faded);
        graphics.fill(right - 1, top, right, top + arm, faded);
        graphics.fill(left, bottom - 1, left + arm, bottom, faded);
        graphics.fill(left, bottom - arm, left + 1, bottom, faded);
        graphics.fill(right - arm, bottom - 1, right, bottom, faded);
        graphics.fill(right - 1, bottom - arm, right, bottom, faded);
    }

    private record Row(String text, int color, int cells, float filled) {
        Row(String text, int color) {
            this(text, color, 0, 0);
        }
    }

    /** The label beside the brackets: what's known, growing with each thing you learn. */
    private static void label(GuiGraphicsExtractor graphics, Font font, float[] box, HudTargets.Seen seen, float distance, int accent, double age,
                              int width, int height) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row("◆ " + seen.name().toUpperCase(Locale.ROOT) + " // " + seen.address(), accent));
        rows.add(new Row((seen.level() < 0 ? "LV?" : "LV" + seen.level()) + " · " + Math.round(distance) + " m" + (seen.player() ? " · player" : ""),
                EdgeShell.DIM));
        if (!seen.attempt().isEmpty()) {
            double left = Math.max(0, seen.attemptLeft() - age);
            float done = seen.attemptTotal() <= 0 ? 0 : (float) Math.clamp(1 - left / seen.attemptTotal(), 0, 1);
            rows.add(new Row(seen.attempt().toUpperCase(Locale.ROOT) + String.format(Locale.ROOT, " %.1f s", left / 20),
                    seen.attempt().equals("scanning") ? EdgeShell.CYAN : EdgeShell.MAGENTA, 8, done));
        }
        if (!seen.session().isEmpty()) {
            double left = Math.max(0, seen.sessionLeft() - age);
            rows.add(new Row("SESSION · " + seen.session().toUpperCase(Locale.ROOT) + String.format(Locale.ROOT, " · %.1f s", left / 20),
                    EdgeShell.MAGENTA));
        }
        if (seen.inspected()) {
            if (seen.flaws().isEmpty()) {
                rows.add(new Row("no flaws", EdgeShell.TEXT));
            }
            for (HudTargets.Flaw flaw : seen.flaws()) {
                rows.add(new Row(flaw.className() + " · " + flaw.severity(), severityColor(flaw.severity())));
            }
            rows.add(new Row("inspected " + Math.round((seen.inspectedAgo() + age) / 20) + "s ago", EdgeShell.GREY));
        } else {
            rows.add(new Row("flaws unknown · inspect to read", EdgeShell.GREY));
        }

        int textWidth = 0;
        for (Row row : rows) {
            textWidth = Math.max(textWidth, font.width(row.text()) + (row.cells() > 0 ? row.cells() * 4 + 6 : 0));
        }
        int panelWidth = textWidth + 10;
        int panelHeight = rows.size() * LINE + 6;
        int x = Math.round(box[2]) + 10;
        if (x + panelWidth > width - 4) {
            x = Math.round(box[0]) - 10 - panelWidth;
        }
        x = Math.clamp(x, 4, Math.max(4, width - 4 - panelWidth));
        int y = Math.clamp(Math.round(box[1]), 4, Math.max(4, height - 4 - panelHeight));
        LensHud.panel(graphics, x, y, panelWidth, panelHeight, LensHud.fade(accent, 0.55f), PANEL);
        int rowY = y + 4;
        for (Row row : rows) {
            int end = x + 5 + font.width(row.text());
            graphics.text(font, row.text(), x + 5, rowY, row.color(), false);
            if (row.cells() > 0) {
                int lit = Math.round(row.filled() * row.cells());
                for (int i = 0; i < row.cells(); i++) {
                    int cellX = end + 6 + i * 4;
                    graphics.fill(cellX, rowY + 1, cellX + 3, rowY + 7, i < lit ? row.color() : 0x40F2F6FF);
                }
            }
            rowY += LINE;
        }
    }

    private static int severityColor(String severity) {
        return switch (severity) {
            case "critical" -> EdgeShell.MAGENTA;
            case "major" -> EdgeShell.ORANGE;
            default -> EdgeShell.GOLD;
        };
    }
}
