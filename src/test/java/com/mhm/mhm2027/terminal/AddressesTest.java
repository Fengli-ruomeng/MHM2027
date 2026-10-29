package com.mhm.mhm2027.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class AddressesTest {
    @Test
    void addressesLookLikeIpsAndNeverChange() {
        UUID uuid = UUID.fromString("3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90");
        String address = Addresses.of(uuid);
        assertTrue(address.matches("10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"), address);
        assertEquals(address, Addresses.of(UUID.fromString(uuid.toString())));
        for (String part : address.split("\\.")) {
            assertTrue(Integer.parseInt(part) <= 255);
        }
    }

    @Test
    void differentEntitiesRarelyShareAnAddress() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            seen.add(Addresses.of(UUID.randomUUID()));
        }
        assertTrue(seen.size() > 990, "1000 random entities gave only " + seen.size() + " addresses");
    }
}
