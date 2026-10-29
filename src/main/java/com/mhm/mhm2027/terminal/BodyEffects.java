package com.mhm.mhm2027.terminal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import org.squiddev.cobalt.LuaError;

import com.mhm.mhm2027.Mhm2027;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundPlayerRotationPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * What a session can do to a body, for players and mobs alike, through vanilla effects and
 * attributes. Timed changes are tracked here and taken off again when they run out, so they
 * work on mobs too, which have no terminal of their own.
 */
@EventBusSubscriber(modid = Mhm2027.MODID)
final class BodyEffects {
    private record Timed(ServerLevel level, UUID entity, Holder<Attribute> attribute, Identifier id, long expiresAt) {
    }

    private static final Identifier NO_JUMP = Identifier.fromNamespaceAndPath(Mhm2027.MODID, "session/neutralize");
    private static final List<Timed> ACTIVE = new ArrayList<>();
    private static final float JUMP_VELOCITY = 0.42f;

    private BodyEffects() {
    }

    /** Heavy slowness and no jumping for a while. */
    static void neutralize(LivingEntity target, int ticks) {
        target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, ticks, 5, false, true, true));
        addTimed(target, Attributes.JUMP_STRENGTH, NO_JUMP, -1.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL, ticks);
    }

    /** Turns the target's view: up, down, left, right or back. */
    static void look(LivingEntity target, String direction) throws LuaError {
        float yaw = target.getYRot();
        float pitch = target.getXRot();
        switch (direction) {
            case "up" -> pitch = -90;
            case "down" -> pitch = 90;
            case "left" -> yaw -= 90;
            case "right" -> yaw += 90;
            case "back" -> yaw += 180;
            default -> throw new LuaError("look direction must be up, down, left, right or back");
        }
        yaw = Mth.wrapDegrees(yaw);
        target.setYRot(yaw);
        target.setXRot(pitch);
        target.setYHeadRot(yaw);
        if (target instanceof ServerPlayer player) {
            player.connection.send(new ClientboundPlayerRotationPacket(yaw, false, pitch, false));
        }
    }

    /** One forced jump, if the target is standing on something. */
    static boolean jump(LivingEntity target) {
        if (!target.onGround()) {
            return false;
        }
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(motion.x, JUMP_VELOCITY, motion.z);
        target.hurtMarked = true;
        return true;
    }

    /** Throws the held item on the ground, as a player pressing Ctrl+Q would. */
    static boolean dropHeld(LivingEntity target) {
        if (target.getMainHandItem().isEmpty()) {
            return false;
        }
        if (target instanceof ServerPlayer player) {
            player.drop(true);
        } else if (target.level() instanceof ServerLevel level) {
            ItemStack held = target.getMainHandItem().copy();
            target.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            target.spawnAtLocation(level, held);
        }
        return true;
    }

    private static void addTimed(LivingEntity target, Holder<Attribute> attribute, Identifier id, double amount,
                                 AttributeModifier.Operation operation, int ticks) {
        AttributeInstance instance = target.getAttribute(attribute);
        if (instance == null || !(target.level() instanceof ServerLevel level)) {
            return;
        }
        instance.removeModifier(id);
        instance.addTransientModifier(new AttributeModifier(id, amount, operation));
        ACTIVE.removeIf(timed -> timed.entity().equals(target.getUUID()) && timed.id().equals(id));
        ACTIVE.add(new Timed(level, target.getUUID(), attribute, id, level.getServer().getTickCount() + ticks));
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        if (ACTIVE.isEmpty()) {
            return;
        }
        long now = event.getServer().getTickCount();
        for (Iterator<Timed> it = ACTIVE.iterator(); it.hasNext(); ) {
            Timed timed = it.next();
            if (now < timed.expiresAt()) {
                continue;
            }
            Entity entity = timed.level().getEntity(timed.entity());
            if (entity instanceof LivingEntity living) {
                AttributeInstance instance = living.getAttribute(timed.attribute());
                if (instance != null) {
                    instance.removeModifier(timed.id());
                }
            }
            it.remove();
        }
    }

    @SubscribeEvent
    static void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }
}
