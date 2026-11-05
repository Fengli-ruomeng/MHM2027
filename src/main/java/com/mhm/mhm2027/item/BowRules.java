package com.mhm.mhm2027.item;

/**
 * The Programmable Bow's flight rules, kept apart from the game so they can be tested: how far
 * an arrow will chase, how hard it turns, and the wild flight of an arrow with nothing to lock
 * onto. Server and client both steer with these, so an arrow's flight looks the same on both.
 */
public final class BowRules {
    /** An arrow stops chasing a target farther than this from it, in blocks. */
    public static final double TRACK_RANGE = 50;
    /** The sharpest turn a tracking arrow makes in one tick. */
    public static final double MAX_TURN = Math.toRadians(24);
    /** Closer than this many ticks of flight, it aims straight at the target, so it never circles. */
    public static final double DIRECT_TICKS = 3;
    /** Tracking arrows never fly slower than this, in blocks per tick: a weak draw still arrives. */
    public static final double MIN_SPEED = 1.6;
    /** How long an arrow may track before it gives up and falls. */
    public static final int MAX_TRACK_TICKS = 200;
    /** Ticks a tracking arrow flies straight while its target is behind a wall, before it gives up. */
    public static final int BLOCKED_TICKS = 10;
    /** A wild arrow wanders this long, then falls. */
    public static final int WILD_TICKS = 40;
    /** The biggest swerve a wild arrow makes in one tick, sideways or up and down. */
    public static final double WILD_TURN = Math.toRadians(40);

    private BowRules() {
    }

    public record Vec(double x, double y, double z) {
        public double length() {
            return Math.sqrt(x * x + y * y + z * z);
        }

        public Vec normalize() {
            double length = length();
            return length < 1.0E-9 ? new Vec(0, 0, 1) : new Vec(x / length, y / length, z / length);
        }

        public Vec scale(double by) {
            return new Vec(x * by, y * by, z * by);
        }

        public Vec plus(Vec other) {
            return new Vec(x + other.x, y + other.y, z + other.z);
        }

        public double dot(Vec other) {
            return x * other.x + y * other.y + z * other.z;
        }

        public Vec cross(Vec other) {
            return new Vec(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
        }
    }

    /** The speed a tracking arrow keeps: what the bow gave it, but never a crawl. */
    public static double trackingSpeed(double launchSpeed) {
        return Math.max(MIN_SPEED, launchSpeed);
    }

    /**
     * A tracking arrow's new heading (a unit vector): towards the target, turning at most
     * {@link #MAX_TURN}, or straight at it once it's a few ticks away.
     */
    public static Vec steer(Vec heading, Vec toTarget, double speed) {
        Vec from = heading.normalize();
        Vec to = toTarget.normalize();
        if (toTarget.length() <= speed * DIRECT_TICKS) {
            return to;
        }
        double angle = Math.acos(Math.clamp(from.dot(to), -1, 1));
        if (angle <= MAX_TURN) {
            return to;
        }
        if (Math.sin(angle) < 1.0E-6) {
            // Dead behind: any way round will do, so swing up and over.
            Vec side = from.cross(new Vec(0, 1, 0));
            Vec up = (side.length() < 1.0E-6 ? new Vec(1, 0, 0) : side.cross(from)).normalize();
            return from.scale(Math.cos(MAX_TURN)).plus(up.scale(Math.sin(MAX_TURN))).normalize();
        }
        // Turn MAX_TURN of the way along the arc between the two headings.
        double sin = Math.sin(angle);
        double a = Math.sin(angle - MAX_TURN) / sin;
        double b = Math.sin(MAX_TURN) / sin;
        return from.scale(a).plus(to.scale(b)).normalize();
    }

    /**
     * A wild arrow's next heading: a random swerve. The same arrow on the same tick swerves the
     * same way on the server and the client, so they don't drift apart.
     */
    public static Vec wild(Vec heading, int arrowId, int tick) {
        Vec from = heading.normalize();
        long seed = mix(arrowId * 0x9E3779B97F4A7C15L + tick);
        double yawSwerve = unit(seed) * WILD_TURN;
        double pitchSwerve = unit(mix(seed)) * WILD_TURN;
        double yaw = Math.atan2(from.x(), from.z()) + yawSwerve;
        double pitch = Math.clamp(Math.asin(Math.clamp(from.y(), -1, 1)) + pitchSwerve, -1.3, 1.3);
        return new Vec(Math.sin(yaw) * Math.cos(pitch), Math.sin(pitch), Math.cos(yaw) * Math.cos(pitch));
    }

    /** SplitMix64: a well-scrambled number from a seed. */
    private static long mix(long seed) {
        long z = seed + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** -1 to 1 from a scrambled number. */
    private static double unit(long bits) {
        return (bits >>> 11) * 0x1.0p-53 * 2 - 1;
    }
}
