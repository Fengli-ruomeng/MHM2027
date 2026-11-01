package com.mhm.mhm2027.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class EventStackTest {
    @Test
    void newestCardsComeFirstAndTheOldestFallOff() {
        EventStack<String> stack = new EventStack<>(3);
        for (String card : List.of("a", "b", "c", "d")) {
            stack.push(card, "", 0, 1000);
        }
        assertEquals(List.of("d", "c", "b"), stack.visible(0).stream().map(EventStack.Entry::value).toList());
    }

    @Test
    void aKeyedCardUpdatesInPlaceAndKeepsItsAge() {
        EventStack<String> stack = new EventStack<>(5);
        stack.push("flash 9 s", "flash", 0, 2000);
        stack.push("scan", "", 100, 2000);
        stack.push("flash 8 s", "flash", 1000, 2000);
        List<EventStack.Entry<String>> cards = stack.visible(1000);
        assertEquals(2, cards.size());
        assertEquals("flash 8 s", cards.get(1).value(), "stays where it was");
        assertEquals(0, cards.get(1).bornAt(), "doesn't slide in again");
        assertEquals(3000, cards.get(1).expiresAt(), "but lasts from the update");
    }

    @Test
    void cardsExpire() {
        EventStack<String> stack = new EventStack<>(5);
        stack.push("short", "", 0, 500);
        stack.push("long", "", 0, 5000);
        assertEquals(List.of("long"), stack.visible(600).stream().map(EventStack.Entry::value).toList());
        assertTrue(stack.visible(6000).isEmpty());
    }
}
