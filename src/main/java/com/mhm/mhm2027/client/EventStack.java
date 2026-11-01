package com.mhm.mhm2027.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The HUD's event cards, newest first. A card with a key replaces the last card with that key
 * in place, keeping its age, so a countdown updates instead of stacking. Kept free of game
 * classes so it can be unit-tested.
 */
public final class EventStack<T> {
    /** One card: when it arrived (for the slide-in) and when it goes. */
    public record Entry<T>(T value, String key, long bornAt, long expiresAt) {
    }

    private final int max;
    private final List<Entry<T>> entries = new ArrayList<>();

    public EventStack(int max) {
        this.max = max;
    }

    public void push(T value, String key, long now, long lifeMillis) {
        if (!key.isEmpty()) {
            for (int i = 0; i < entries.size(); i++) {
                Entry<T> old = entries.get(i);
                if (old.key().equals(key)) {
                    entries.set(i, new Entry<>(value, key, old.bornAt(), now + lifeMillis));
                    return;
                }
            }
        }
        entries.addFirst(new Entry<>(value, key, now, now + lifeMillis));
        while (entries.size() > max) {
            entries.removeLast();
        }
    }

    /** The cards still showing, newest first. Expired ones are dropped. */
    public List<Entry<T>> visible(long now) {
        entries.removeIf(entry -> now >= entry.expiresAt());
        return List.copyOf(entries);
    }

    public void clear() {
        entries.clear();
    }
}
