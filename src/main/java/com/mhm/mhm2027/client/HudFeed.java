package com.mhm.mhm2027.client;

import java.util.ArrayDeque;
import java.util.Deque;

import org.jspecify.annotations.Nullable;

import com.mhm.mhm2027.hud.HudBoot;
import com.mhm.mhm2027.hud.HudEvent;
import com.mhm.mhm2027.hud.HudState;
import com.mhm.mhm2027.hud.HudTargets;
import com.mhm.mhm2027.station.FlashStationBlockEntity;
import com.mhm.mhm2027.terminal.Terminal;

import net.minecraft.util.Util;

/** What the server has told the HUD (cards, banners, socket slots), plus the glitch level. Client only. */
public final class HudFeed {
    private static final int MAX_CARDS = 5;
    private static final int MAX_QUEUED_BANNERS = 3;
    private static final long GLITCH_HALF_LIFE = 220;
    private static final long FLASH_MILLIS = FlashStationBlockEntity.FLASH_TICKS * 50L;

    /** The banner showing now, and when it started and ends. */
    record Banner(HudEvent event, long start, long end) {
    }

    private static final EventStack<HudEvent> CARDS = new EventStack<>(MAX_CARDS);
    private static final Deque<HudEvent> BANNERS = new ArrayDeque<>();
    private static @Nullable Banner banner;
    private static HudState state = HudState.OFFLINE;
    private static long stateAt;
    private static HudTargets targets = HudTargets.NONE;
    private static long targetsAt;
    private static float glitchLevel;
    private static long glitchAt;
    private static @Nullable HudBoot boot;
    private static long bootStart;
    private static long bootEnd;

    private HudFeed() {
    }

    static void accept(HudEvent event) {
        if (event.kind() == HudEvent.Kind.ALERT) {
            glitch(1f);
        }
        if (event.banner()) {
            if (BANNERS.size() < MAX_QUEUED_BANNERS) {
                BANNERS.add(event);
            }
            if (event.kind() == HudEvent.Kind.WARN) {
                glitch(0.4f);
            }
            return;
        }
        long life = event.ticks() > 0 ? event.ticks() * 50L : defaultLife(event.kind());
        CARDS.push(event, event.key(), Util.getMillis(), life);
    }

    static void accept(HudState next) {
        state = next;
        stateAt = Util.getMillis();
    }

    static void accept(HudTargets next) {
        targets = next;
        targetsAt = Util.getMillis();
    }

    /** The gateways in scan range, for the target callout. */
    static HudTargets targets() {
        return targets;
    }

    /** Ticks since the targets arrived, so their timers count down in between. */
    static double targetsAge(long now) {
        return (now - targetsAt) / 50.0;
    }

    /** Ticks since the state arrived, so the task list counts down in between. */
    static double stateAge(long now) {
        return (now - stateAt) / 50.0;
    }

    /** The gateway is booting: the boot screen runs for as long as the server said, and starts with a glitch. */
    static void accept(HudBoot next) {
        long now = Util.getMillis();
        boot = next;
        bootStart = now;
        bootEnd = now + Math.max(20, next.ticks()) * 50L;
        glitch(1f);
    }

    /** How far the boot has got, 0 to 1; -1 when the gateway isn't booting. */
    static float boot(long now) {
        if (boot == null || now >= bootEnd) {
            return -1;
        }
        return (now - bootStart) / (float) (bootEnd - bootStart);
    }

    static @Nullable HudBoot bootEvent() {
        return boot;
    }

    static EventStack<HudEvent> cards() {
        return CARDS;
    }

    static HudState state() {
        return state;
    }

    public static Terminal.Power power() {
        return state.power();
    }

    /** How far a firmware flash has got, moving smoothly between updates; -1 when there is none. */
    static float flash(long now) {
        if (state.flash() < 0) {
            return -1;
        }
        return Math.min(1f, state.flash() + (now - stateAt) / (float) FLASH_MILLIS);
    }

    /** The banner to show now, taking the next from the queue when the last one has run its time. */
    static @Nullable Banner banner(long now) {
        if (banner == null || now >= banner.end()) {
            HudEvent next = BANNERS.poll();
            banner = next == null ? null : new Banner(next, now, now + bannerLife(next.kind()));
        }
        return banner;
    }

    /** Kicks off a glitch; a stronger one replaces a weaker one already fading. */
    static void glitch(float power) {
        long now = Util.getMillis();
        glitchLevel = Math.max(glitch(now), power);
        glitchAt = now;
    }

    static float glitch(long now) {
        return TextFx.decay(glitchLevel, now - glitchAt, GLITCH_HALF_LIFE);
    }

    static void reset() {
        CARDS.clear();
        BANNERS.clear();
        banner = null;
        state = HudState.OFFLINE;
        targets = HudTargets.NONE;
        glitchLevel = 0;
        boot = null;
    }

    /** Attacks stay longest; background noise goes first. */
    private static long defaultLife(HudEvent.Kind kind) {
        return switch (kind) {
            case ALERT -> 8000;
            case WARN -> 7000;
            case INFO -> 5000;
            case QUIET -> 4000;
        };
    }

    private static long bannerLife(HudEvent.Kind kind) {
        return kind == HudEvent.Kind.ALERT ? 4500 : 3500;
    }
}
