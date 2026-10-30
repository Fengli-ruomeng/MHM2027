package com.mhm.mhm2027.terminal;

import static org.squiddev.cobalt.ValueFactory.valueOf;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** me.looking_at(): what the player's crosshair is on, within the reach they can interact at. */
final class Sight {
    private Sight() {
    }

    /**
     * An entity ({@code type = "entity"}, its id and a handle) or a block ({@code type = "block"},
     * its id and position), whichever is nearer; nil when looking at nothing in reach.
     */
    static LuaValue lookingAt(ServerPlayer player) {
        double reach = player.blockInteractionRange();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);
        Vec3 end = eye.add(look.scale(reach));
        BlockHitResult block = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
        AABB area = player.getBoundingBox().expandTowards(look.scale(reach)).inflate(1.0);
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, eye, limit, area,
                each -> !each.isSpectator() && each.isPickable(), eye.distanceToSqr(limit));

        LuaTable result = new LuaTable();
        if (entity != null) {
            Entity hit = entity.getEntity();
            result.rawset("type", valueOf("entity"));
            result.rawset("id", valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(hit.getType()).toString()));
            result.rawset("entity", EntityHandles.of(hit, player));
            return result;
        }
        if (block.getType() == HitResult.Type.MISS) {
            return Constants.NIL;
        }
        BlockPos pos = block.getBlockPos();
        result.rawset("type", valueOf("block"));
        result.rawset("id", valueOf(BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock()).toString()));
        result.rawset("x", valueOf(pos.getX()));
        result.rawset("y", valueOf(pos.getY()));
        result.rawset("z", valueOf(pos.getZ()));
        return result;
    }
}
