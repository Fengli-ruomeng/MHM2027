package com.mhm.mhm2027.terminal;

import static org.squiddev.cobalt.ValueFactory.valueOf;

import java.util.UUID;

import org.jspecify.annotations.Nullable;
import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaUserdata;
import org.squiddev.cobalt.LuaValue;

import com.mhm.mhm2027.gateway.GatewayData;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Entity handles: how scripts refer to other entities. A handle is sealed userdata, so a
 * script can read its fields but can't build one or change one.
 *
 * Fixed fields (set when the handle is made): {@code address}, {@code kind}, {@code name},
 * {@code is_player}. Live fields (read each time): {@code distance}, {@code level}. A handle goes
 * stale when its entity is gone or far away; live fields are then nil, and calls that take the
 * handle return nil.
 */
final class EntityHandles {
    /** Past this, a handle is stale even if the entity still exists. */
    private static final double STALE_RANGE = 64;

    record Handle(UUID target, UUID viewer, String address, String kind, String name, boolean isPlayer, MinecraftServer server) {
    }

    private static final LuaTable METATABLE = metatable();

    private EntityHandles() {
    }

    /** A handle on {@code entity}, as seen by {@code viewer}. Nil for no entity. */
    static LuaValue of(@Nullable Entity entity, ServerPlayer viewer) {
        if (entity == null) {
            return Constants.NIL;
        }
        Handle handle = new Handle(entity.getUUID(), viewer.getUUID(), Addresses.of(entity.getUUID()),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(), entity.getName().getString(),
                entity instanceof Player, viewer.level().getServer());
        return new LuaUserdata(handle, METATABLE);
    }

    /** The live entity behind a handle, if it still exists, is alive, and is within {@code range} of the viewer. */
    static @Nullable LivingEntity resolve(LuaValue value, ServerPlayer viewer, double range) {
        Handle handle = handleOf(value);
        if (handle == null) {
            return null;
        }
        Entity entity = viewer.level().getEntity(handle.target());
        if (!(entity instanceof LivingEntity living) || !living.isAlive() || living.distanceTo(viewer) > range) {
            return null;
        }
        return living;
    }

    /** How a handle prints: in tostring(), sys.log and print. */
    static String describe(@Nullable Handle handle) {
        return handle == null ? "gateway ?" : "gateway " + handle.address() + " (" + handle.kind() + ")";
    }

    static @Nullable Handle handleOf(LuaValue value) {
        return value instanceof LuaUserdata userdata && userdata.toUserdata() instanceof Handle handle ? handle : null;
    }

    /** Checks an API argument is a handle; a mistake in the code, so it raises an error. */
    static Handle check(LuaValue value, String api) throws LuaError {
        Handle handle = handleOf(value);
        if (handle == null) {
            throw new LuaError(api + " expects an entity handle (from remote.target, remote.scan or an event)");
        }
        return handle;
    }

    private static LuaTable metatable() {
        LuaTable meta = new LuaTable();
        meta.rawset("__index", LuaSandbox.function("__index", (state, args) -> {
            Handle handle = handleOf(args.arg(1));
            return handle == null ? Constants.NIL : field(handle, args.arg(2).toString());
        }));
        meta.rawset("__tostring", LuaSandbox.function("__tostring", (state, args) -> valueOf(describe(handleOf(args.arg(1))))));
        meta.rawset("__eq", LuaSandbox.function("__eq", (state, args) -> {
            Handle a = handleOf(args.arg(1));
            Handle b = handleOf(args.arg(2));
            return valueOf(a != null && b != null && a.target().equals(b.target()));
        }));
        // getmetatable(handle) returns this string, so scripts can't reach or change the shared table.
        meta.rawset("__metatable", valueOf("locked"));
        return meta;
    }

    private static LuaValue field(Handle handle, String key) {
        return switch (key) {
            case "address" -> valueOf(handle.address());
            case "kind" -> valueOf(handle.kind());
            case "name" -> valueOf(handle.name());
            case "is_player" -> valueOf(handle.isPlayer());
            case "distance", "level" -> liveField(handle, key);
            default -> Constants.NIL;
        };
    }

    private static LuaValue liveField(Handle handle, String key) {
        ServerPlayer viewer = handle.server().getPlayerList().getPlayer(handle.viewer());
        if (viewer == null) {
            return Constants.NIL;
        }
        Entity entity = viewer.level().getEntity(handle.target());
        if (entity == null || !entity.isAlive() || entity.distanceTo(viewer) > STALE_RANGE) {
            return Constants.NIL;
        }
        if (key.equals("distance")) {
            return valueOf(entity.distanceTo(viewer));
        }
        GatewayData data = Gateways.of(entity);
        return valueOf(data == null ? -1 : data.level());
    }
}
