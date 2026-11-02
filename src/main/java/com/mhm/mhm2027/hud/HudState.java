package com.mhm.mhm2027.hud;

import java.util.List;

import com.mhm.mhm2027.Mhm2027;
import com.mhm.mhm2027.terminal.Terminal;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: what the HUD shows that the synced gateway data doesn't carry. Sent when
 * it changes, at most twice a second.
 *
 * @param flash how far a firmware flash has got, 0 to 1; -1 when there is none
 * @param power off, booting or on: the HUD hides itself while the gateway is off
 * @param tasks everything that takes time, most urgent first, for the task list
 */
public record HudState(List<Slot> slots, float flash, Terminal.Power power, List<Task> tasks) implements CustomPacketPayload {
    /**
     * One socket.
     *
     * @param label a short label for what sits in it, empty when the socket is empty
     * @param fill  0 to 1: an Overload socket's share of max Heat
     */
    public record Slot(boolean overload, String label, boolean running, float fill) {
        static final StreamCodec<RegistryFriendlyByteBuf, Slot> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, Slot::overload,
                ByteBufCodecs.STRING_UTF8, Slot::label,
                ByteBufCodecs.BOOL, Slot::running,
                ByteBufCodecs.FLOAT, Slot::fill,
                Slot::new);

        public boolean empty() {
            return label.isEmpty();
        }
    }

    /**
     * Something that takes time: a program waiting on a call, an attempt on you, a session, a
     * cooldown, Overload Heat. The client counts the ones that count down between updates.
     *
     * @param label  the program, the API or the other gateway's address
     * @param detail a few words on what's happening
     * @param total  the whole span in ticks, or 0 when there's no bar; for Overload, 100 (max Heat)
     * @param left   ticks still to go; for Overload, 100 minus the Heat
     */
    public record Task(Kind kind, String label, String detail, int total, int left) {
        /** In the order the list shows them: what you may need to act on first. */
        public enum Kind {
            INCOMING, WAITING, SESSION, OVERLOAD, LOCKED, FLASH, COOLDOWN, BUSY, IDLE;

            /** Whether {@code left} is a time the client can count down. */
            public boolean countsDown() {
                return this != OVERLOAD && this != FLASH && this != BUSY && this != IDLE;
            }
        }

        static final StreamCodec<RegistryFriendlyByteBuf, Task> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.idMapper(id -> Kind.values()[id], Kind::ordinal), Task::kind,
                ByteBufCodecs.STRING_UTF8, Task::label,
                ByteBufCodecs.STRING_UTF8, Task::detail,
                ByteBufCodecs.VAR_INT, Task::total,
                ByteBufCodecs.VAR_INT, Task::left,
                Task::new);
    }

    public static final HudState EMPTY = new HudState(List.of(), -1, Terminal.Power.ON, List.of());
    /** What the client assumes before the server says otherwise: a gateway starts off. */
    public static final HudState OFFLINE = new HudState(List.of(), -1, Terminal.Power.OFF, List.of());
    public static final Type<HudState> TYPE = new Type<>(Identifier.fromNamespaceAndPath(Mhm2027.MODID, "hud_state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, HudState> STREAM_CODEC = StreamCodec.composite(
            Slot.STREAM_CODEC.apply(ByteBufCodecs.list()), HudState::slots,
            ByteBufCodecs.FLOAT, HudState::flash,
            ByteBufCodecs.idMapper(id -> Terminal.Power.values()[id], Terminal.Power::ordinal), HudState::power,
            Task.STREAM_CODEC.apply(ByteBufCodecs.list()), HudState::tasks,
            HudState::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
