package com.mhm.mhm2027.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BowRulesTest {
    private static double angle(BowRules.Vec a, BowRules.Vec b) {
        return Math.acos(Math.clamp(a.normalize().dot(b.normalize()), -1, 1));
    }

    @Test
    void aFarTargetIsTurnedTowardsAtMostTheMaxTurn() {
        BowRules.Vec heading = new BowRules.Vec(0, 0, 1);
        BowRules.Vec toTarget = new BowRules.Vec(30, 0, 0);
        BowRules.Vec next = BowRules.steer(heading, toTarget, 3);
        assertEquals(BowRules.MAX_TURN, angle(heading, next), 1.0E-9);
        assertTrue(angle(next, toTarget) < angle(heading, toTarget), "it turned towards the target");
        assertEquals(1, next.length(), 1.0E-9);
    }

    @Test
    void aSmallCorrectionIsMadeInFull() {
        BowRules.Vec toTarget = new BowRules.Vec(1, 0, 20);
        assertEquals(0, angle(BowRules.steer(new BowRules.Vec(0, 0, 1), toTarget, 3), toTarget), 1.0E-6);
    }

    @Test
    void aCloseTargetIsAimedAtDirectlySoTheArrowNeverCircles() {
        BowRules.Vec toTarget = new BowRules.Vec(-4, 1, 0);
        BowRules.Vec next = BowRules.steer(new BowRules.Vec(0, 0, 1), toTarget, 3);
        assertEquals(0, angle(next, toTarget), 1.0E-6);
    }

    @Test
    void aTargetDeadBehindStillGetsATurn() {
        BowRules.Vec heading = new BowRules.Vec(0, 0, 1);
        BowRules.Vec next = BowRules.steer(heading, new BowRules.Vec(0, 0, -40), 3);
        assertEquals(BowRules.MAX_TURN, angle(heading, next), 1.0E-6);
    }

    @Test
    void wildFlightIsTheSameOnEverySideAndBounded() {
        BowRules.Vec heading = new BowRules.Vec(0.3, 0.1, 1);
        for (int tick = 0; tick < 40; tick++) {
            BowRules.Vec a = BowRules.wild(heading, 1234, tick);
            BowRules.Vec b = BowRules.wild(heading, 1234, tick);
            assertEquals(a, b, "server and client agree");
            assertEquals(1, a.length(), 1.0E-9);
            assertTrue(angle(heading, a) <= BowRules.WILD_TURN * 1.5 + 1.0E-9, "a swerve, not a reversal");
        }
        assertTrue(!BowRules.wild(heading, 1234, 5).equals(BowRules.wild(heading, 1235, 5)), "different arrows wander differently");
    }

    @Test
    void weakShotsStillTrackAtAUsefulSpeed() {
        assertEquals(BowRules.MIN_SPEED, BowRules.trackingSpeed(0.4));
        assertEquals(3.0, BowRules.trackingSpeed(3.0));
    }
}
