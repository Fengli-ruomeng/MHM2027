package com.mhm.mhm2027.terminal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What each program has printed, for its window in Edge. The output outlives the program, so a
 * crashed program's window still shows what happened; only the last few ended ones are kept.
 * Kernel and session lines go to kernel.log instead.
 */
public final class ProgramOutputs {
    /** Lines kept for each program, and for kernel.log. */
    public static final int LINES = 40;
    /** Ended programs whose output is kept. */
    public static final int ENDED_KEPT = 8;

    // "Watcher.lua:12: attempt to index a nil value": the chunk, the line, then the message.
    private static final Pattern LOCATED = Pattern.compile("^(.*?):(\\d{1,6}): (.*)$", Pattern.DOTALL);

    /**
     * @param minute the in-game time of day it was printed, in minutes
     */
    public record Line(int minute, String text) {
    }

    /**
     * One program's window.
     *
     * @param id        what started it: a file name, or {@code preset:<id>}
     * @param serial    goes up with every start, so a new run can be told from the last one
     * @param total     lines printed since it started, including the ones no longer kept
     * @param ended     empty while it runs; otherwise "finished", "stopped" or "crashed"
     * @param reason    why it ended: the error, or what stopped it; may be empty
     * @param errorLine the script line a crash points at, or 0
     */
    public record Output(String name, String id, int serial, int total, String ended, String reason, int errorLine,
                         List<Line> lines) {
    }

    private static final class Entry {
        private final String id;
        private final int serial;
        private final Deque<Line> lines = new ArrayDeque<>();
        private int total;
        private String ended = "";
        private String reason = "";
        private int errorLine;

        private Entry(String id, int serial) {
            this.id = id;
            this.serial = serial;
        }
    }

    // In the order the programs started.
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final Deque<Line> kernel = new ArrayDeque<>();
    private int serial;

    /** A program starts: a fresh window, in place of whatever an earlier run left. */
    void started(String name, String id) {
        entries.remove(name);
        entries.put(name, new Entry(id, ++serial));
    }

    /** A log line goes to its program's window, or to kernel.log for the kernel and sessions. */
    void line(String source, int minute, String text) {
        if (source.equals("kernel") || source.equals("session")) {
            add(kernel, new Line(minute, source.equals("session") ? "session " + text : text));
            return;
        }
        Entry entry = entries.get(source);
        if (entry != null) {
            add(entry.lines, new Line(minute, text));
            entry.total++;
        }
    }

    /** A program ended. A crash that names a script line keeps the line for "edit line N". */
    void ended(String name, String how, String reason) {
        Entry entry = entries.get(name);
        if (entry == null || !entry.ended.isEmpty()) {
            return;
        }
        entry.ended = how;
        Matcher located = LOCATED.matcher(reason);
        if (located.matches()) {
            entry.errorLine = Integer.parseInt(located.group(2));
            entry.reason = located.group(3);
        } else {
            entry.reason = reason;
        }
        List<String> ended = entries.entrySet().stream().filter(each -> !each.getValue().ended.isEmpty()).map(Map.Entry::getKey).toList();
        for (int i = 0; i < ended.size() - ENDED_KEPT; i++) {
            entries.remove(ended.get(i));
        }
    }

    public List<Output> outputs() {
        List<Output> outputs = new ArrayList<>();
        entries.forEach((name, entry) -> outputs.add(new Output(name, entry.id, entry.serial, entry.total, entry.ended, entry.reason,
                entry.errorLine, List.copyOf(entry.lines))));
        return outputs;
    }

    public List<Line> kernel() {
        return List.copyOf(kernel);
    }

    private static void add(Deque<Line> lines, Line line) {
        lines.addLast(line);
        while (lines.size() > LINES) {
            lines.removeFirst();
        }
    }
}
