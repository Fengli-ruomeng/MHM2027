package com.mhm.mhm2027.terminal;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

/**
 * Flaw classes from datapack JSON, and the pure check that turns kernel settings plus what the
 * body is doing into the flaws a player currently has.
 *
 * <p>A file is {@code id}, {@code rules}, and an optional {@code dynamic} flag. Each rule has
 * {@code when}, {@code severity}, and optional {@code cause} and {@code close} templates
 * ({@code {heat}}, {@code {rate_limit}}, and the other reading keys). {@code when} is one
 * condition or {@code all} of them. A condition names a {@code setting} ({@code schedule},
 * {@code gc}, {@code verify}, {@code rate_limit}) or a {@code state} ({@code heat},
 * {@code enhancements}, {@code programs}, {@code hooks}, {@code hook_points}, {@code hooks_spare}
 * which is hook points minus hooks defined) and compares it with {@code equals}, {@code at_least}
 * or {@code at_most}. Numeric comparisons may set {@code near}: within that distance of the
 * threshold, but not yet over it, the rule is reported as close rather than active.
 * When several rules of one class match, the highest severity wins.
 */
public final class FlawRules {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z_]+)}");
    private static final Set<String> TEXT_SETTINGS = Set.of("schedule", "gc", "verify");
    private static final Set<String> NUMBER_SETTINGS = Set.of("rate_limit");
    private static final Set<String> STATES = Set.of("heat", "enhancements", "programs", "hooks", "hook_points", "hooks_spare");
    private static final Set<String> FILE_KEYS = Set.of("id", "rules", "dynamic");
    private static final Set<String> RULE_KEYS = Set.of("when", "severity", "cause", "close");

    /**
     * One class, in the order its rules were written. The rules stay inside this package;
     * callers use {@link #evaluate}.
     */
    public static final class ClassDef {
        private final String id;
        private final String className;
        private final boolean dynamic;
        private final List<Rule> rules;

        ClassDef(String id, String className, boolean dynamic, List<Rule> rules) {
            this.id = id;
            this.className = className;
            this.dynamic = dynamic;
            this.rules = List.copyOf(rules);
        }

        public String id() {
            return id;
        }

        public String className() {
            return className;
        }

        /** Dynamic flaws come and go with the fight. Static ones change only when the kernel changes. */
        public boolean dynamic() {
            return dynamic;
        }

        List<Rule> rules() {
            return rules;
        }
    }

    /** Settings and live numbers the rules read. Absent keys simply fail to match. */
    public record Reading(Map<String, String> text, Map<String, Double> numbers) {
        public Reading {
            text = Map.copyOf(text);
            numbers = Map.copyOf(numbers);
        }

        public static Reading of(KernelSettings settings, int programs, double heat, int enhancements, int hooks, int hookPoints) {
            return new Reading(Map.of(
                    "schedule", settings.schedule(),
                    "gc", settings.gc(),
                    "verify", settings.verify()),
                    Map.of(
                            "rate_limit", (double) settings.rateLimit(),
                            "heat", heat,
                            "enhancements", (double) enhancements,
                            "programs", (double) programs,
                            "hooks", (double) hooks,
                            "hook_points", (double) hookPoints,
                            "hooks_spare", (double) hookPoints - hooks));
        }
    }

    /** A flaw that is active right now. */
    public record Hit(String className, FlawSeverity severity, String cause, boolean dynamic) {
    }

    /** A flaw that has not fired, but is within a rule's {@code near} distance of doing so. */
    public record Approach(String className, FlawSeverity severity, String cause, boolean dynamic) {
    }

    /** What changed since the previous check. {@code severity} is {@code none} when a class cleared. */
    public record Change(String className, String severity) {
    }

    /** The active flaws and the ones the player is close to triggering, in class order. */
    public record Profile(List<Hit> active, List<Approach> approaching) {
        public static final Profile EMPTY = new Profile(List.of(), List.of());

        public Profile {
            active = List.copyOf(active);
            approaching = List.copyOf(approaching);
        }
    }

    /** A rule file that does not match the schema. The registry skips that file and keeps the rest. */
    public static class FlawFormatException extends Exception {
        public FlawFormatException(String message) {
            super(message);
        }
    }

    private sealed interface Condition permits Equals, AtLeast, AtMost, All {
    }

    private record Equals(String key, String value) implements Condition {
    }

    private record AtLeast(String key, double threshold, double near) implements Condition {
    }

    private record AtMost(String key, double threshold, double near) implements Condition {
    }

    private record All(List<Condition> parts) implements Condition {
    }

    private enum Match {
        HIT, CLOSE, MISS
    }

    record Rule(Condition when, FlawSeverity severity, @Nullable String cause, @Nullable String close) {
    }

    private FlawRules() {
    }

    /** Parses one class file. Throws when the JSON is not a rule file this mod can check. */
    public static ClassDef parse(String json) throws FlawFormatException {
        JsonElement root;
        try {
            root = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            throw new FlawFormatException(e.getMessage());
        }
        if (!root.isJsonObject()) {
            throw new FlawFormatException("a flaw file must be a JSON object");
        }
        JsonObject object = root.getAsJsonObject();
        allowOnly(object, FILE_KEYS, "flaw file");
        String id = requireString(object, "id", "flaw file");
        String className = className(id);
        if (!object.has("rules") || !object.get("rules").isJsonArray()) {
            throw new FlawFormatException(id + " needs a rules array");
        }
        JsonArray array = object.getAsJsonArray("rules");
        if (array.isEmpty()) {
            throw new FlawFormatException(id + " has no rules");
        }
        List<Rule> rules = new ArrayList<>();
        for (int i = 0; i < array.size(); i++) {
            try {
                rules.add(parseRule(array.get(i)));
            } catch (FlawFormatException e) {
                throw new FlawFormatException(id + " rule " + (i + 1) + ": " + e.getMessage());
            }
        }
        boolean dynamic = object.has("dynamic") ? requireBoolean(object, "dynamic", id) : usesState(rules);
        return new ClassDef(id, className, dynamic, rules);
    }

    /**
     * The flaws {@code reading} has under these classes. Classes stay in the given order, which
     * is the order the registry loads them.
     */
    public static Profile evaluate(List<ClassDef> classes, Reading reading) {
        List<Hit> active = new ArrayList<>();
        List<Approach> approaching = new ArrayList<>();
        for (ClassDef flawClass : classes) {
            Hit best = null;
            Approach close = null;
            for (Rule rule : flawClass.rules()) {
                switch (match(rule.when(), reading)) {
                    case HIT -> {
                        if (best == null || rule.severity().rank() >= best.severity().rank()) {
                            best = new Hit(flawClass.className(), rule.severity(), hitCause(rule, reading), flawClass.dynamic());
                        }
                    }
                    case CLOSE -> {
                        if (close == null || rule.severity().rank() > close.severity().rank()) {
                            close = new Approach(flawClass.className(), rule.severity(), closeCause(rule, reading), flawClass.dynamic());
                        }
                    }
                    case MISS -> {
                    }
                }
            }
            if (best != null) {
                active.add(best);
            }
            // A higher severity you have not reached yet is still worth showing. A lower one is not.
            if (close != null && (best == null || close.severity().rank() > best.severity().rank())) {
                approaching.add(close);
            }
        }
        return new Profile(active, approaching);
    }

    /**
     * Severity changes between two checks, in class order: new and changed classes first, then
     * classes that disappeared. Disappeared classes are reported as {@code none}.
     */
    public static List<Change> changes(List<Hit> before, List<Hit> after) {
        Map<String, FlawSeverity> previous = new LinkedHashMap<>();
        for (Hit hit : before) {
            previous.put(hit.className(), hit.severity());
        }
        Map<String, FlawSeverity> next = new LinkedHashMap<>();
        for (Hit hit : after) {
            next.put(hit.className(), hit.severity());
        }
        List<Change> changes = new ArrayList<>();
        for (Map.Entry<String, FlawSeverity> entry : next.entrySet()) {
            FlawSeverity old = previous.get(entry.getKey());
            if (old != entry.getValue()) {
                changes.add(new Change(entry.getKey(), entry.getValue().id()));
            }
        }
        for (Map.Entry<String, FlawSeverity> entry : previous.entrySet()) {
            if (!next.containsKey(entry.getKey())) {
                changes.add(new Change(entry.getKey(), FlawSeverity.NONE.id()));
            }
        }
        return changes;
    }

    /**
     * Lines for the terminal log when a kernel is installed. Only static flaws: dynamic ones
     * depend on the fight, not on the settings just chosen.
     */
    public static List<String> installLog(Profile profile) {
        List<String> lines = new ArrayList<>();
        for (Hit hit : profile.active()) {
            if (!hit.dynamic()) {
                lines.add("flaw " + hit.className() + " " + hit.severity().id() + ": " + hit.cause());
            }
        }
        return lines.isEmpty() ? List.of("no static flaws") : List.copyOf(lines);
    }

    /** How the editor lists one flaw. Same words as the install log, without the prefix. */
    public static String warning(Hit hit) {
        return hit.className() + " " + hit.severity().id() + ": " + hit.cause();
    }

    private static Rule parseRule(JsonElement element) throws FlawFormatException {
        if (!element.isJsonObject()) {
            throw new FlawFormatException("a rule must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        allowOnly(object, RULE_KEYS, "rule");
        if (!object.has("when")) {
            throw new FlawFormatException("needs when");
        }
        return new Rule(parseWhen(object.get("when")), FlawSeverity.parse(requireString(object, "severity", "rule")),
                optionalText(object, "cause"), optionalText(object, "close"));
    }

    private static Condition parseWhen(JsonElement element) throws FlawFormatException {
        if (!element.isJsonObject()) {
            throw new FlawFormatException("when must be an object");
        }
        JsonObject object = element.getAsJsonObject();
        if (object.has("all")) {
            allowOnly(object, Set.of("all"), "when");
            if (!object.get("all").isJsonArray() || object.getAsJsonArray("all").isEmpty()) {
                throw new FlawFormatException("all must be a non-empty array");
            }
            List<Condition> parts = new ArrayList<>();
            for (JsonElement child : object.getAsJsonArray("all")) {
                parts.add(parseWhen(child));
            }
            return new All(parts);
        }
        boolean setting = object.has("setting");
        boolean state = object.has("state");
        if (setting == state) {
            throw new FlawFormatException("when needs setting or state");
        }
        String key = requireString(object, setting ? "setting" : "state", "when");
        if (setting && !TEXT_SETTINGS.contains(key) && !NUMBER_SETTINGS.contains(key)) {
            throw new FlawFormatException("unknown setting " + key);
        }
        if (state && !STATES.contains(key)) {
            throw new FlawFormatException("unknown state " + key);
        }
        // Unknown keys are reported before the comparison check, so a typo like "equal" names itself.
        allowOnly(object, Set.of(setting ? "setting" : "state", "equals", "at_least", "at_most", "near"), "when");
        int comparisons = (object.has("equals") ? 1 : 0) + (object.has("at_least") ? 1 : 0) + (object.has("at_most") ? 1 : 0);
        if (comparisons != 1) {
            throw new FlawFormatException("when needs exactly one of equals, at_least, at_most");
        }
        if (object.has("equals")) {
            if (object.has("near")) {
                throw new FlawFormatException("equals does not take near");
            }
            if (state || NUMBER_SETTINGS.contains(key)) {
                throw new FlawFormatException(key + " is numeric; use at_least or at_most");
            }
            JsonElement expected = object.get("equals");
            if (!expected.isJsonPrimitive() || !expected.getAsJsonPrimitive().isString()) {
                throw new FlawFormatException("equals must be a string");
            }
            return new Equals(key, expected.getAsString());
        }
        String mode = object.has("at_least") ? "at_least" : "at_most";
        if (setting && !NUMBER_SETTINGS.contains(key)) {
            throw new FlawFormatException(key + " is text; use equals");
        }
        double threshold = requireNumber(object, mode, "when");
        double near = object.has("near") ? requireNumber(object, "near", "when") : 0;
        if (near < 0) {
            throw new FlawFormatException("near must be >= 0");
        }
        return mode.equals("at_least") ? new AtLeast(key, threshold, near) : new AtMost(key, threshold, near);
    }

    private static boolean usesState(List<Rule> rules) {
        for (Rule rule : rules) {
            if (usesState(rule.when())) {
                return true;
            }
        }
        return false;
    }

    private static boolean usesState(Condition condition) {
        return switch (condition) {
            case Equals ignored -> false;
            case AtLeast at -> STATES.contains(at.key());
            case AtMost at -> STATES.contains(at.key());
            case All all -> {
                for (Condition part : all.parts()) {
                    if (usesState(part)) {
                        yield true;
                    }
                }
                yield false;
            }
        };
    }

    private static Match match(Condition condition, Reading reading) {
        return switch (condition) {
            case Equals equals -> equals.value().equals(reading.text().get(equals.key())) ? Match.HIT : Match.MISS;
            case AtLeast atLeast -> matchThreshold(number(reading, atLeast.key()), atLeast.threshold(), atLeast.near(), true);
            case AtMost atMost -> matchThreshold(number(reading, atMost.key()), atMost.threshold(), atMost.near(), false);
            case All all -> {
                boolean anyClose = false;
                Match result = Match.HIT;
                for (Condition part : all.parts()) {
                    Match partMatch = match(part, reading);
                    if (partMatch == Match.MISS) {
                        result = Match.MISS;
                        break;
                    }
                    if (partMatch == Match.CLOSE) {
                        anyClose = true;
                    }
                }
                yield result == Match.MISS ? Match.MISS : anyClose ? Match.CLOSE : Match.HIT;
            }
        };
    }

    /** {@code atLeast} uses {@code >=}. Otherwise the comparison is {@code <=}. */
    private static Match matchThreshold(double value, double threshold, double near, boolean atLeast) {
        boolean hit = atLeast ? value >= threshold : value <= threshold;
        if (hit) {
            return Match.HIT;
        }
        if (near > 0) {
            boolean close = atLeast ? value >= threshold - near : value <= threshold + near;
            if (close) {
                return Match.CLOSE;
            }
        }
        return Match.MISS;
    }

    private static double number(Reading reading, String key) {
        Double value = reading.numbers().get(key);
        if (value == null) {
            throw new IllegalStateException("flaw reading has no " + key);
        }
        return value;
    }

    private static String hitCause(Rule rule, Reading reading) {
        return rule.cause() != null ? fill(rule.cause(), reading) : describe(rule.when(), reading, false);
    }

    private static String closeCause(Rule rule, Reading reading) {
        if (rule.close() != null) {
            return fill(rule.close(), reading);
        }
        String described = describe(rule.when(), reading, true);
        return described.isEmpty() && rule.cause() != null ? fill(rule.cause(), reading) : described;
    }

    /** {@code closeOnly} describes the numeric parts that are near, not the ones that already match. */
    private static String describe(Condition condition, Reading reading, boolean closeOnly) {
        if (closeOnly && !(condition instanceof All) && match(condition, reading) != Match.CLOSE) {
            return "";
        }
        return switch (condition) {
            case Equals equals -> closeOnly ? "" : equals.key() + " = " + equals.value();
            case AtLeast atLeast -> describeNumber(atLeast.key(), atLeast.threshold(), "at least", reading, closeOnly);
            case AtMost atMost -> describeNumber(atMost.key(), atMost.threshold(), "at most", reading, closeOnly);
            case All all -> {
                List<String> parts = new ArrayList<>();
                for (Condition part : all.parts()) {
                    String text = describe(part, reading, closeOnly);
                    if (!text.isEmpty()) {
                        parts.add(text);
                    }
                }
                yield String.join(", ", parts);
            }
        };
    }

    private static String describeNumber(String key, double threshold, String relation, Reading reading, boolean closeOnly) {
        if (closeOnly) {
            return key + " " + formatNumber(number(reading, key)) + ", needs " + relation + " " + formatNumber(threshold);
        }
        return key + " " + formatNumber(number(reading, key));
    }

    private static String fill(String template, Reading reading) {
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String key = matcher.group(1);
            String value = reading.text().containsKey(key) ? reading.text().get(key)
                    : reading.numbers().containsKey(key) ? formatNumber(reading.numbers().get(key)) : null;
            matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    static String formatNumber(double value) {
        if (Double.isFinite(value) && value == Math.rint(value) && Math.abs(value) < 1_000_000_000d) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String className(String id) throws FlawFormatException {
        int colon = id.lastIndexOf(':');
        String name = colon >= 0 ? id.substring(colon + 1) : id;
        if (colon == 0 || name.isEmpty() || !name.chars().allMatch(c -> c >= 'a' && c <= 'z' || c == '_' || c >= '0' && c <= '9')
                || name.charAt(0) < 'a' || name.charAt(0) > 'z') {
            throw new FlawFormatException("bad flaw id " + id);
        }
        return name;
    }

    private static void allowOnly(JsonObject object, Set<String> keys, String what) throws FlawFormatException {
        for (String key : object.keySet()) {
            if (!keys.contains(key)) {
                throw new FlawFormatException(what + " has unknown field " + key);
            }
        }
    }

    private static String requireString(JsonObject object, String key, String what) throws FlawFormatException {
        JsonElement element = require(object, key, what);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new FlawFormatException(what + " field " + key + " must be a string");
        }
        return element.getAsString();
    }

    private static boolean requireBoolean(JsonObject object, String key, String what) throws FlawFormatException {
        JsonElement element = require(object, key, what);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new FlawFormatException(what + " field " + key + " must be true or false");
        }
        return element.getAsBoolean();
    }

    private static double requireNumber(JsonObject object, String key, String what) throws FlawFormatException {
        JsonElement element = require(object, key, what);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new FlawFormatException(what + " field " + key + " must be a number");
        }
        return element.getAsDouble();
    }

    private static @Nullable String optionalText(JsonObject object, String key) throws FlawFormatException {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            return null;
        }
        String text = requireString(object, key, "rule").trim();
        return text.isEmpty() ? null : text;
    }

    private static JsonElement require(JsonObject object, String key, String what) throws FlawFormatException {
        if (!object.has(key) || object.get(key).isJsonNull()) {
            throw new FlawFormatException(what + " needs " + key);
        }
        return object.get(key);
    }
}
