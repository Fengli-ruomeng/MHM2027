package com.mhm.mhm2027.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

import com.mhm.mhm2027.editor.LuaHighlighter;
import com.mhm.mhm2027.terminal.ApiDocs;
import com.mhm.mhm2027.terminal.ApiSpec;
import com.mhm.mhm2027.terminal.ScriptEvents;

/**
 * The Edge terminal's shell: it parses what the player types, prints the answer, and queues
 * requests for the server (run, stop, socket, ...). It reads a plain {@link Data} snapshot and
 * knows nothing of the game, so it can be unit-tested; the desktop screen draws it.
 *
 * Output lines are runs of colored text. A clickable run either runs its command at once (ps's
 * [stop]) or puts it in the prompt for the player to confirm (a file name in ls).
 */
public final class EdgeShell {
    public static final int TEXT = 0xFFF2F6FF;
    public static final int DIM = 0xFFB9C3D6;
    public static final int GREY = 0xFF5B6478;
    public static final int CYAN = 0xFF3FE8FF;
    public static final int ORANGE = 0xFFFFB347;
    public static final int MAGENTA = 0xFFFF2E7E;
    public static final int PINK = 0xFFFF6FA6;
    public static final int GOLD = 0xFFFFC877;
    public static final int VIOLET = 0xFFB9A4FF;
    public static final int GREEN = 0xFF98E3A0;

    private static final int MAX_LINES = 400;
    private static final int MAX_HISTORY = 50;
    /** Lines printed together appear one after another, this far apart. */
    private static final long STAGGER_MILLIS = 25;
    /** Prose wraps at this many characters until the screen says how wide the terminal is. */
    private static final int DEFAULT_COLUMNS = 60;
    private static final List<String> NAMESPACES = List.of("me", "sys", "remote");
    /** What a new file may be called: the same rule as the editor's name prompt and the server. */
    private static final Pattern NEW_NAME = Pattern.compile("[A-Za-z0-9_\\-]{1,32}");

    /** The commands, with a line of help each, in the order help lists them. */
    static final List<String[]> COMMANDS = List.of(
            new String[]{"ls", "what's in your storage"},
            new String[]{"cat", "<file>  print a script"},
            new String[]{"run", "<program>  start a program"},
            new String[]{"stop", "<program>  stop one"},
            new String[]{"ps", "running programs"},
            new String[]{"top", "live processes, in their own window"},
            new String[]{"log", "[program]  open a program's window, or kernel.log"},
            new String[]{"status", "your gateway at a glance"},
            new String[]{"flaws", "your flaws, and the ones you're close to"},
            new String[]{"scan", "[radius]  gateways around you"},
            new String[]{"socket", "[ls | add | rm]  sockets (change them at a Flash Station)"},
            new String[]{"kernel", "[ls | install]  your kernel (install at a Flash Station)"},
            new String[]{"edit", "[file]  open the editor"},
            new String[]{"burn", "<file>  copy a script onto a blank chip"},
            new String[]{"man", "<api | event | hook | command>  how it works"},
            new String[]{"folder", "open your storage folder (singleplayer)"},
            new String[]{"reboot", "restart the gateway"},
            new String[]{"clear", "clear the screen"},
            new String[]{"help", "this list"},
            new String[]{"exit", "close Edge (or press G)"});

    /** A run of text. With a command it's clickable: it either runs it, or puts it in the prompt. */
    public record Seg(String text, int color, String command, boolean runs) {
        static Seg of(String text, int color) {
            return new Seg(text, color, "", false);
        }

        static Seg run(String text, int color, String command) {
            return new Seg(text, color, command, true);
        }

        static Seg fill(String text, int color, String command) {
            return new Seg(text, color, command, false);
        }

        public boolean clickable() {
            return !command.isEmpty();
        }
    }

    /** One line of output, and when it was printed (it types itself in from then). */
    public record Line(List<Seg> segs, long at) {
        public String text() {
            StringBuilder out = new StringBuilder();
            segs.forEach(seg -> out.append(seg.text()));
            return out.toString();
        }
    }

    /**
     * Something for the screen to do: a panel request by its action name (START, SCAN, ...), or
     * one the screen handles itself: EDIT, CAT, WINDOW (with a window id), FOLDER or EXIT.
     */
    public record Request(String action, String argument) {
    }

    // region What the shell reads

    public record Script(String id, String name, boolean kernel, boolean preset) {
    }

    public record Proc(String id, String name, String type, String state, int ramMb, int tops) {
    }

    public record Flaw(String className, String severity, String cause, boolean approaching) {
    }

    public record Socket(boolean overload, String id, String name) {
    }

    public record Kernel(String id, String name, String schedule, String gc, String verify, int rateLimit, List<String> hooks,
                         int ramMb, int tops) {
    }

    public record Status(String level, String band, double ramUsed, double ram, double leak, int topsFree, int tops,
                         double buffer, double bufferMax, int heat) {
    }

    public record Data(String user, String address, boolean atStation, Status status, Kernel kernel,
                       List<Script> scripts, List<Proc> programs, List<Flaw> flaws, List<Socket> sockets, List<String> outputs) {
    }

    /** One gateway a scan found. {@code level} is -1 when it can't be read. */
    public record Gateway(String address, String name, boolean player, int level, double distance) {
    }

    // endregion

    private final List<Line> lines = new ArrayList<>();
    private final List<String> history = new ArrayList<>();
    private final List<Request> requests = new ArrayList<>();
    private int historyIndex = -1;
    private long printAt;
    private int columns = DEFAULT_COLUMNS;

    public List<Line> lines() {
        return lines;
    }

    /** The requests queued since the last call. */
    public List<Request> drain() {
        List<Request> out = List.copyOf(requests);
        requests.clear();
        return out;
    }

    public static String prompt(Data data) {
        return data.user() + "@gateway:~$ ";
    }

    public void welcome(Data data, long now) {
        printAt = now;
        print(Seg.of("EDGE ", TEXT), Seg.of(data.status().level() + " " + data.status().band() + " · " + data.address(), DIM));
        print(Seg.of("Type a command, or click one. ", DIM), Seg.run("help", CYAN, "help"), Seg.of(" lists them all.", DIM));
    }

    /** How many characters fit across the terminal, for wrapping prose such as man pages. */
    public void setColumns(int columns) {
        this.columns = Math.max(24, columns);
    }

    /** A line from outside the shell, such as the server's answer to a request. */
    public void message(String text, boolean success, long now) {
        printAt = Math.max(printAt, now);
        print(Seg.of(text, success ? CYAN : PINK));
    }

    // region Running commands

    public void submit(String input, Data data, long now) {
        printAt = now;
        String line = input.strip();
        print(Seg.of(prompt(data), CYAN), Seg.of(line, TEXT));
        if (line.isEmpty()) {
            return;
        }
        if (history.isEmpty() || !history.getLast().equals(line)) {
            history.add(line);
            if (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
        historyIndex = -1;

        int space = line.indexOf(' ');
        String command = (space < 0 ? line : line.substring(0, space)).toLowerCase(Locale.ROOT);
        String rest = space < 0 ? "" : line.substring(space + 1).strip();
        switch (command) {
            case "help" -> help();
            case "ls" -> ls(data);
            case "cat" -> cat(data, rest);
            case "run" -> run(data, rest);
            case "stop" -> stop(data, rest);
            case "ps" -> ps(data);
            case "top" -> {
                requests.add(new Request("WINDOW", EdgeWindows.TOP));
                print(Seg.of("opened top", DIM));
            }
            case "log" -> log(data, rest);
            case "status" -> status(data);
            case "flaws" -> flaws(data);
            case "scan" -> scan(rest);
            case "socket" -> socket(data, rest);
            case "kernel" -> kernel(data, rest);
            case "man" -> man(data, rest);
            case "edit" -> {
                if (rest.isEmpty()) {
                    requests.add(new Request("EDIT", ""));
                } else {
                    file(data, rest, "edit", "EDIT");
                }
            }
            case "burn" -> file(data, rest, "burn", "BURN");
            case "folder" -> requests.add(new Request("FOLDER", ""));
            case "reboot" -> {
                requests.add(new Request("REBOOT", ""));
                print(Seg.of("rebooting...", DIM));
            }
            case "clear" -> lines.clear();
            case "exit" -> requests.add(new Request("EXIT", ""));
            default -> print(Seg.of("edge: " + command + ": command not found · try ", PINK), Seg.run("help", CYAN, "help"));
        }
    }

    private void help() {
        print(Seg.of("EDGE SHELL", TEXT), Seg.of(" · Tab completes, Up and Down go through history", DIM));
        for (String[] command : COMMANDS) {
            print(Seg.fill(pad("  " + command[0], 10), CYAN, command[0] + " "), Seg.of(command[1], DIM));
        }
    }

    private void ls(Data data) {
        List<Seg> programs = new ArrayList<>(List.of(Seg.of(pad("programs", 10), DIM)));
        List<Seg> kernels = new ArrayList<>(List.of(Seg.of(pad("kernels", 10), DIM), Seg.fill("Baseline", VIOLET, "kernel install Baseline"), Seg.of("  ", TEXT)));
        List<Seg> presets = new ArrayList<>(List.of(Seg.of(pad("presets", 10), DIM)));
        for (Script script : data.scripts()) {
            if (script.kernel()) {
                kernels.add(Seg.fill(script.name(), VIOLET, "kernel install " + script.name()));
                kernels.add(Seg.of("  ", TEXT));
            } else if (script.preset()) {
                presets.add(Seg.fill(script.name(), GOLD, "run " + script.name()));
                presets.add(Seg.of("  ", TEXT));
            } else {
                programs.add(Seg.fill(script.name() + ".lua", TEXT, "run " + script.name()));
                programs.add(Seg.of("  ", TEXT));
            }
        }
        print(programs.size() > 1 ? programs : List.of(Seg.of(pad("programs", 10), DIM), Seg.of("none", GREY)));
        print(kernels);
        print(presets.size() > 1 ? presets : List.of(Seg.of(pad("presets", 10), DIM), Seg.of("none", GREY)));
    }

    private void run(Data data, String name) {
        if (name.isEmpty()) {
            error("run: which program? Try ls.");
            return;
        }
        Optional<Script> script = find(data.scripts().stream().filter(each -> !each.kernel()).toList(), Script::name, Script::id, name);
        if (script.isEmpty()) {
            error("run: no program called " + name + " · try ls");
            return;
        }
        requests.add(new Request("START", script.get().id()));
        print(Seg.of("starting " + script.get().name() + "... its window opens when it runs", DIM));
    }

    private void stop(Data data, String name) {
        if (name.isEmpty()) {
            error("stop: which program? Try ps.");
            return;
        }
        Optional<Proc> program = find(data.programs(), Proc::name, Proc::id, name);
        if (program.isEmpty()) {
            error("stop: " + name + " isn't running · try ps");
            return;
        }
        requests.add(new Request("STOP", program.get().name()));
    }

    private void ps(Data data) {
        if (data.programs().isEmpty()) {
            print(Seg.of("nothing running · ", GREY), Seg.fill("run", CYAN, "run "), Seg.of(" one", GREY));
            return;
        }
        print(Seg.of(pad("PID", 5) + pad("NAME", 18) + pad("STATE", 13) + pad("TOPS", 6) + pad("RAM", 7), GREY));
        for (int i = 0; i < data.programs().size(); i++) {
            Proc program = data.programs().get(i);
            print(Seg.of(pad(String.valueOf(i + 1), 5), GREY), Seg.of(pad(program.name(), 18), TEXT),
                    Seg.of(pad(program.state(), 13), stateColor(program.state())), Seg.of(pad(String.valueOf(program.tops()), 6), DIM),
                    Seg.of(pad(program.ramMb() + "M", 7), DIM),
                    Seg.run("[log]", CYAN, "log " + program.name()), Seg.of(" ", TEXT), Seg.run("[stop]", PINK, "stop " + program.name()));
        }
    }

    /** Opens kernel.log, or the window of a program that has printed something. */
    private void log(Data data, String name) {
        if (name.isEmpty()) {
            requests.add(new Request("WINDOW", EdgeWindows.KERNEL));
            print(Seg.of("opened " + EdgeWindows.KERNEL, DIM));
            return;
        }
        Optional<String> output = find(data.outputs(), each -> each, each -> each, name);
        if (output.isEmpty()) {
            error("log: nothing from " + name + " yet · try ps");
            return;
        }
        requests.add(new Request("WINDOW", EdgeWindows.program(output.get())));
        print(Seg.of("opened " + output.get(), DIM));
    }

    private void status(Data data) {
        Status status = data.status();
        print(Seg.of(status.level() + " " + status.band(), TEXT), Seg.of(" · kernel ", DIM), Seg.of(data.kernel().name(), VIOLET));
        print(Seg.of("BUFFER ", DIM), Seg.of(size(status.buffer(), status.bufferMax()), CYAN),
                Seg.of("   RAM ", DIM), Seg.of(size(status.ramUsed(), status.ram()), status.ramUsed() > status.ram() ? PINK : TEXT),
                Seg.of(status.leak() >= 1 ? " (leak " + Math.round(status.leak()) + ")" : "", VIOLET),
                Seg.of("   TOPS ", DIM), Seg.of(status.topsFree() + "/" + status.tops(), status.topsFree() <= 2 ? PINK : TEXT),
                Seg.of("   HEAT ", DIM), Seg.of(String.valueOf(status.heat()), status.heat() >= 70 ? ORANGE : TEXT));
        socketLine(data);
    }

    private void flaws(Data data) {
        if (data.flaws().isEmpty()) {
            print(Seg.of("no flaws", GREY));
            return;
        }
        for (Flaw flaw : data.flaws()) {
            int color = flaw.approaching() ? GREY : flaw.severity().equals("minor") ? ORANGE : PINK;
            print(Seg.of(pad(flaw.className(), 11), color), Seg.of(pad((flaw.approaching() ? "near " : "") + flaw.severity(), 15), color),
                    Seg.of(flaw.cause(), DIM));
        }
    }

    private void socket(Data data, String rest) {
        int space = rest.indexOf(' ');
        String sub = (space < 0 ? rest : rest.substring(0, space)).toLowerCase(Locale.ROOT);
        String name = space < 0 ? "" : rest.substring(space + 1).strip();
        switch (sub) {
            case "", "ls" -> {
                int standard = 0;
                int overload = 0;
                for (Socket socket : data.sockets()) {
                    String label = socket.overload() ? "O" + ++overload : "S" + ++standard;
                    if (socket.id().isEmpty()) {
                        print(Seg.of(pad(label, 4), socket.overload() ? ORANGE : DIM), Seg.of("empty", GREY));
                    } else {
                        print(Seg.of(pad(label, 4), socket.overload() ? ORANGE : DIM), Seg.of(pad(socket.name(), 22), TEXT),
                                Seg.fill("[rm]", PINK, "socket rm " + socket.name()));
                    }
                }
                if (data.sockets().isEmpty()) {
                    print(Seg.of("no sockets", GREY));
                }
                if (!data.atStation()) {
                    print(Seg.of("change sockets at a Flash Station", GREY));
                }
            }
            case "add" -> {
                Optional<Script> script = find(data.scripts().stream().filter(each -> !each.kernel()).toList(), Script::name, Script::id, name);
                if (script.isEmpty()) {
                    error("socket add: no program called " + name);
                } else {
                    requests.add(new Request("SOCKET", script.get().id()));
                }
            }
            case "rm" -> {
                Optional<Socket> socket = find(data.sockets().stream().filter(each -> !each.id().isEmpty()).toList(), Socket::name, Socket::id, name);
                if (socket.isEmpty()) {
                    error("socket rm: nothing called " + name + " is in a socket");
                } else {
                    requests.add(new Request("UNSOCKET", socket.get().id()));
                }
            }
            default -> error("socket: use socket ls, socket add <program> or socket rm <program>");
        }
    }

    private void kernel(Data data, String rest) {
        int space = rest.indexOf(' ');
        String sub = (space < 0 ? rest : rest.substring(0, space)).toLowerCase(Locale.ROOT);
        String name = space < 0 ? "" : rest.substring(space + 1).strip();
        Kernel kernel = data.kernel();
        switch (sub) {
            case "" -> {
                print(Seg.of("kernel ", DIM), Seg.of(kernel.name(), VIOLET), Seg.of(" · " + kernel.ramMb() + " MB · " + kernel.tops() + " TOPS", DIM));
                print(Seg.of("schedule=" + kernel.schedule() + " gc=" + kernel.gc() + " verify=" + kernel.verify() + " rate_limit=" + kernel.rateLimit(), TEXT));
                print(Seg.of("hooks ", DIM), Seg.of(kernel.hooks().isEmpty() ? "none" : String.join(" ", kernel.hooks()), TEXT));
            }
            case "ls" -> {
                print(Seg.fill("Baseline", VIOLET, "kernel install Baseline"), Seg.of(kernel.id().equals("Baseline") ? "  installed" : "", CYAN));
                for (Script script : data.scripts()) {
                    if (script.kernel()) {
                        print(Seg.fill(script.name(), VIOLET, "kernel install " + script.name()),
                                Seg.of(script.id().equals(kernel.id()) ? "  installed" : "", CYAN));
                    }
                }
                if (!data.atStation()) {
                    print(Seg.of("install kernels at a Flash Station", GREY));
                }
            }
            case "install" -> {
                if (name.equalsIgnoreCase("Baseline")) {
                    requests.add(new Request("INSTALL_KERNEL", "Baseline"));
                    return;
                }
                Optional<Script> script = find(data.scripts().stream().filter(Script::kernel).toList(), Script::name, Script::id, name);
                if (script.isEmpty()) {
                    error("kernel install: no kernel called " + name + " · try kernel ls");
                } else {
                    requests.add(new Request("INSTALL_KERNEL", script.get().id()));
                }
            }
            default -> error("kernel: use kernel, kernel ls or kernel install <name>");
        }
    }

    /** cat: asks the server for the file; it prints when it arrives (see {@link #printFile}). */
    private void cat(Data data, String name) {
        if (name.isEmpty()) {
            error("cat: which file? Try ls.");
            return;
        }
        Optional<Script> script = find(data.scripts(), Script::name, Script::id, name);
        if (script.isEmpty()) {
            error("cat: no file called " + name + " · try ls");
        } else if (script.get().preset()) {
            error("cat: " + script.get().name() + " is a preset; its code is locked");
        } else {
            requests.add(new Request("CAT", script.get().id()));
        }
    }

    /** A file the server sent for cat: numbered and highlighted, like the editor shows it. */
    public void printFile(String name, String content, long now) {
        printAt = Math.max(printAt, now);
        List<String> lines = List.of(content.replace("\r", "").replace("\t", "  ").split("\n", -1));
        if (lines.size() > 1 && lines.getLast().isEmpty()) {
            lines = lines.subList(0, lines.size() - 1);
        }
        print(Seg.of("── " + name + ".lua", TEXT), Seg.of(" · " + lines.size() + (lines.size() == 1 ? " line" : " lines"), GREY),
                Seg.of("  ", TEXT), Seg.run("[edit]", CYAN, "edit " + name));
        int width = String.valueOf(lines.size()).length();
        List<List<LuaHighlighter.Span>> spans = LuaHighlighter.highlight(lines);
        for (int i = 0; i < lines.size(); i++) {
            List<Seg> segs = new ArrayList<>();
            segs.add(Seg.of(" ".repeat(width - String.valueOf(i + 1).length()) + (i + 1) + "  ", GREY));
            String line = lines.get(i);
            int at = 0;
            for (LuaHighlighter.Span span : spans.get(i)) {
                if (span.start() > at) {
                    segs.add(Seg.of(line.substring(at, span.start()), TEXT));
                }
                segs.add(Seg.of(line.substring(Math.max(at, span.start()), span.end()), codeColor(span.kind())));
                at = Math.max(at, span.end());
            }
            if (at < line.length()) {
                segs.add(Seg.of(line.substring(at), TEXT));
            }
            print(segs);
        }
    }

    private void scan(String rest) {
        String radius = "";
        if (!rest.isEmpty()) {
            try {
                int blocks = Integer.parseInt(rest.strip());
                if (blocks < 1) {
                    throw new NumberFormatException();
                }
                radius = String.valueOf(blocks);
            } catch (NumberFormatException e) {
                error("scan: the radius is a number of blocks, such as scan 8");
                return;
            }
        }
        requests.add(new Request("SCAN", radius));
        print(Seg.of("scanning...", DIM));
    }

    /** The server's answer to scan: nearest first. */
    public void scanned(int radius, List<Gateway> found, long now) {
        printAt = Math.max(printAt, now);
        if (found.isEmpty()) {
            print(Seg.of("no gateways within " + radius + " blocks", GREY));
            return;
        }
        print(Seg.of(pad("ADDRESS", 16) + pad("NAME", 18) + pad("LV", 5) + "DIST", GREY));
        for (Gateway gateway : found) {
            print(Seg.of(pad(gateway.address(), 16), CYAN), Seg.of(pad(gateway.name(), 18), gateway.player() ? ORANGE : TEXT),
                    Seg.of(pad(gateway.level() < 0 ? "?" : "LV" + gateway.level(), 5), DIM),
                    Seg.of(String.format(Locale.ROOT, "%.1fm", gateway.distance()), DIM));
        }
        print(Seg.of(found.size() + (found.size() == 1 ? " gateway" : " gateways") + " within " + radius + " blocks · ", GREY),
                Seg.run("man remote.inspect", CYAN, "man remote.inspect"));
    }

    /**
     * man: an API's page (signature, what it does, level and costs), a namespace's list, an
     * event or hook, or a shell command. With no topic, the index.
     */
    private void man(Data data, String topic) {
        String wanted = topic.strip().toLowerCase(Locale.ROOT);
        if (wanted.endsWith("()")) {
            wanted = wanted.substring(0, wanted.length() - 2);
        }
        if (wanted.isEmpty()) {
            print(Seg.of("man <topic>", TEXT), Seg.of(" · what an API, event, hook or command does", DIM));
            List<Seg> apis = new ArrayList<>(List.of(Seg.of(pad("  apis", 10), DIM)));
            for (String namespace : NAMESPACES) {
                apis.add(Seg.run(namespace + ".*", CYAN, "man " + namespace));
                apis.add(Seg.of("  ", TEXT));
            }
            print(apis);
            print(Seg.of(pad("  scripts", 10), DIM), Seg.run("events", GOLD, "man events"), Seg.of("  ", TEXT),
                    Seg.run("hooks", VIOLET, "man hooks"), Seg.of("  ", TEXT), Seg.run("commands", TEXT, "man commands"));
            return;
        }
        Optional<ApiSpec> spec = ApiSpec.byId(wanted);
        if (spec.isPresent()) {
            apiPage(data, spec.get());
            return;
        }
        // "speed_boost" on its own finds me.speed_boost, unless a command has that name.
        String member = wanted;
        List<ApiSpec> byMember = ApiSpec.all().stream().filter(each -> each.member().equals(member)).toList();
        if (NAMESPACES.contains(wanted)) {
            String namespace = wanted;
            for (ApiSpec each : ApiSpec.all()) {
                if (each.namespace().equals(namespace)) {
                    print(Seg.fill(pad(each.id() + "(" + each.params() + ")", 34), CYAN, "man " + each.id()),
                            Seg.of(pad("LV" + each.minLevel(), 5), each.minLevel() > level(data) ? PINK : DIM),
                            Seg.of(ApiDocs.plain(ApiDocs.api(each.id()).orElse("")), DIM));
                }
            }
            return;
        }
        switch (wanted) {
            case "events" -> {
                ApiDocs.events().forEach((event, text) -> print(Seg.fill(pad(event, 20), GOLD, "man " + event), Seg.of(ApiDocs.plain(text), DIM)));
                return;
            }
            case "hooks" -> {
                ApiDocs.hooks().keySet().forEach(hook -> print(Seg.fill(pad(hook, 20), VIOLET, "man " + hook),
                        Seg.of("kernel hook", DIM)));
                return;
            }
            case "commands" -> {
                help();
                return;
            }
            default -> {
            }
        }
        boolean found = false;
        if (ApiDocs.events().containsKey(wanted)) {
            print(Seg.of("function " + wanted + "(...)", GOLD), Seg.of("  program event", GREY));
            wrap(ApiDocs.plain(ApiDocs.events().get(wanted)), TEXT);
            found = true;
        }
        if (ApiDocs.hooks().containsKey(wanted)) {
            print(Seg.of("function " + wanted + "(...)", VIOLET), Seg.of("  kernel hook", GREY));
            wrap(ApiDocs.plain(ApiDocs.hooks().get(wanted)), TEXT);
            found = true;
        }
        for (String[] command : COMMANDS) {
            if (command[0].equals(wanted)) {
                print(Seg.fill(command[0], CYAN, command[0] + " "), Seg.of("  shell command", GREY));
                wrap(command[1], TEXT);
                found = true;
            }
        }
        if (byMember.size() == 1) {
            if (found) {
                print(Seg.of("  see also ", GREY), Seg.run("man " + byMember.getFirst().id(), CYAN, "man " + byMember.getFirst().id()));
            } else {
                apiPage(data, byMember.getFirst());
            }
        } else if (!found) {
            print(Seg.of("man: nothing on " + topic.strip() + " · try ", PINK), Seg.run("man", CYAN, "man"));
        }
    }

    private void apiPage(Data data, ApiSpec spec) {
        print(Seg.of(spec.id() + "(" + spec.params() + ")", CYAN), Seg.of(spec.kernel() ? "  programs and kernel hooks" : "  programs", GREY));
        wrap(ApiDocs.plain(ApiDocs.api(spec.id()).orElse("No description yet.")), TEXT);
        int level = level(data);
        List<Seg> facts = new ArrayList<>();
        facts.add(Seg.of("  LV" + spec.minLevel(), spec.minLevel() > level ? PINK : TEXT));
        if (spec.minLevel() > level && level >= 0) {
            facts.add(Seg.of(" (you're LV" + level + ")", PINK));
        }
        facts.add(Seg.of(" · " + spec.steps() + (spec.steps() == 1 ? " step" : " steps") + " a call ("
                + spec.steps() * ApiSpec.STEP_INSTRUCTIONS + " instructions)", DIM));
        facts.add(Seg.of(" · +" + spec.ramMb() + " MB RAM", DIM));
        print(facts);
        List<Seg> rules = new ArrayList<>();
        if (spec.needsSlot()) {
            rules.add(Seg.of("  runs from a socket", ORANGE));
        }
        if (spec.cooldownSeconds() > 0) {
            String seconds = spec.cooldownSeconds() == Math.rint(spec.cooldownSeconds())
                    ? String.valueOf((long) spec.cooldownSeconds()) : String.valueOf(spec.cooldownSeconds());
            rules.add(Seg.of((rules.isEmpty() ? "  " : " · ") + seconds + " s cooldown, shared by your programs", DIM));
        }
        if (!rules.isEmpty()) {
            print(rules);
        }
    }

    /** Prose, wrapped to the terminal's width and indented. */
    private void wrap(String text, int color) {
        int width = Math.max(20, columns - 2);
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > width) {
                print(Seg.of("  " + line, color));
                line.setLength(0);
            }
            if (!line.isEmpty()) {
                line.append(' ');
            }
            line.append(word);
        }
        if (!line.isEmpty()) {
            print(Seg.of("  " + line, color));
        }
    }

    /** The player's gateway level from the status label ("LV4"), or -1. */
    private static int level(Data data) {
        String label = data.status().level();
        try {
            return label.startsWith("LV") ? Integer.parseInt(label.substring(2)) : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /**
     * edit and burn: your own files only; presets are locked. edit with a new name offers to
     * create the file.
     */
    private void file(Data data, String name, String command, String action) {
        Optional<Script> script = find(data.scripts().stream().filter(each -> !each.preset()).toList(), Script::name, Script::id, name);
        if (script.isPresent()) {
            requests.add(new Request(action, script.get().id()));
            return;
        }
        if (find(data.scripts(), Script::name, Script::id, name).isPresent()) {
            error(command + ": " + name + " is a preset; its code is locked");
            return;
        }
        String fileName = name.toLowerCase(Locale.ROOT).endsWith(".lua") ? name.substring(0, name.length() - 4) : name;
        if (action.equals("EDIT") && NEW_NAME.matcher(fileName).matches()) {
            requests.add(new Request("EDIT_NEW", fileName));
            print(Seg.of("no " + fileName + ".lua yet · the editor will create it", DIM));
            return;
        }
        error(command + ": no file called " + name + " · try ls");
    }

    // endregion

    // region Completion and history

    /**
     * Tab: completes the command or the name being typed. With one match it fills it in; with
     * several it fills in what they share and prints them.
     */
    public String complete(String input, Data data, long now) {
        printAt = now;
        int space = input.indexOf(' ');
        if (space < 0) {
            List<String> matches = COMMANDS.stream().map(command -> command[0]).filter(command -> command.startsWith(input.toLowerCase(Locale.ROOT))).toList();
            return completeWith(input, "", input, matches, " ");
        }
        String command = input.substring(0, space).toLowerCase(Locale.ROOT);
        String rest = input.substring(space + 1);
        String head = input.substring(0, space + 1);
        List<String> pool = switch (command) {
            case "run" -> data.scripts().stream().filter(each -> !each.kernel()).map(Script::name).toList();
            case "stop" -> data.programs().stream().map(Proc::name).toList();
            case "log" -> data.outputs();
            case "edit", "burn", "cat" -> data.scripts().stream().filter(each -> !each.preset()).map(Script::name).toList();
            case "man" -> manTopics();
            case "socket", "kernel" -> null;
            default -> List.of();
        };
        if (pool == null) {
            int second = rest.indexOf(' ');
            if (second < 0) {
                List<String> subs = command.equals("socket") ? List.of("ls", "add", "rm") : List.of("ls", "install");
                return completeWith(input, head, rest, subs.stream().filter(sub -> sub.startsWith(rest)).toList(), " ");
            }
            String sub = rest.substring(0, second);
            String name = rest.substring(second + 1);
            List<String> names = switch (command + " " + sub) {
                case "socket add" -> data.scripts().stream().filter(each -> !each.kernel()).map(Script::name).toList();
                case "socket rm" -> data.sockets().stream().filter(each -> !each.id().isEmpty()).map(Socket::name).toList();
                case "kernel install" -> {
                    List<String> kernels = new ArrayList<>(List.of("Baseline"));
                    data.scripts().stream().filter(Script::kernel).map(Script::name).forEach(kernels::add);
                    yield kernels;
                }
                default -> List.of();
            };
            return completeWith(input, head + sub + " ", name, startingWith(names, name), "");
        }
        return completeWith(input, head, rest, startingWith(pool, rest), "");
    }

    private String completeWith(String input, String head, String partial, List<String> matches, String after) {
        if (matches.isEmpty()) {
            return input;
        }
        if (matches.size() == 1) {
            return head + matches.getFirst() + after;
        }
        print(Seg.of(String.join("   ", matches), GREY));
        String shared = matches.getFirst();
        for (String match : matches) {
            int i = 0;
            while (i < shared.length() && i < match.length() && Character.toLowerCase(shared.charAt(i)) == Character.toLowerCase(match.charAt(i))) {
                i++;
            }
            shared = shared.substring(0, i);
        }
        return shared.length() > partial.length() ? head + shared : input;
    }

    /** Everything man has a page for. */
    static List<String> manTopics() {
        List<String> topics = new ArrayList<>(NAMESPACES);
        topics.addAll(List.of("events", "hooks", "commands"));
        ApiSpec.all().forEach(spec -> topics.add(spec.id()));
        topics.addAll(ScriptEvents.PROGRAM_EVENTS);
        topics.addAll(ScriptEvents.KERNEL_HOOKS);
        COMMANDS.forEach(command -> topics.add(command[0]));
        return topics.stream().distinct().toList();
    }

    private static List<String> startingWith(List<String> names, String partial) {
        String lower = partial.toLowerCase(Locale.ROOT);
        return names.stream().filter(name -> name.toLowerCase(Locale.ROOT).startsWith(lower)).distinct().toList();
    }

    /** Up: an older command, or the input unchanged when there's none. */
    public String historyUp(String current) {
        if (history.isEmpty()) {
            return current;
        }
        historyIndex = historyIndex < 0 ? history.size() - 1 : Math.max(0, historyIndex - 1);
        return history.get(historyIndex);
    }

    /** Down: a newer command, or an empty prompt past the newest. */
    public String historyDown() {
        if (historyIndex < 0) {
            return "";
        }
        historyIndex++;
        if (historyIndex >= history.size()) {
            historyIndex = -1;
            return "";
        }
        return history.get(historyIndex);
    }

    // endregion

    // region Helpers

    private void error(String text) {
        print(Seg.of(text, PINK));
    }

    private void print(Seg... segs) {
        print(List.of(segs));
    }

    private void print(List<Seg> segs) {
        lines.add(new Line(List.copyOf(segs), printAt));
        printAt += STAGGER_MILLIS;
        while (lines.size() > MAX_LINES) {
            lines.removeFirst();
        }
    }

    private void socketLine(Data data) {
        List<Seg> segs = new ArrayList<>(List.of(Seg.of("sockets ", DIM)));
        int standard = 0;
        int overload = 0;
        for (Socket socket : data.sockets()) {
            String label = socket.overload() ? "O" + ++overload : "S" + ++standard;
            if (!socket.id().isEmpty()) {
                segs.add(Seg.of(label + " " + socket.name() + "  ", socket.overload() ? ORANGE : TEXT));
            }
        }
        if (segs.size() == 1) {
            segs.add(Seg.of("empty", GREY));
        }
        print(segs);
    }

    /** By name or id, ignoring case and a .lua ending. */
    static <T> Optional<T> find(List<T> items, Function<T, String> name, Function<T, String> id, String query) {
        String wanted = query.strip().toLowerCase(Locale.ROOT);
        if (wanted.endsWith(".lua")) {
            wanted = wanted.substring(0, wanted.length() - 4);
        }
        for (T item : items) {
            if (name.apply(item).toLowerCase(Locale.ROOT).equals(wanted) || id.apply(item).toLowerCase(Locale.ROOT).equals(wanted)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private static int codeColor(LuaHighlighter.Kind kind) {
        return switch (kind) {
            case KEYWORD -> VIOLET;
            case STRING -> GREEN;
            case COMMENT -> GREY;
            case NUMBER -> ORANGE;
            case API -> CYAN;
            case EVENT -> GOLD;
            case HEADER -> PINK;
            case BUILTIN -> DIM;
            case PLAIN -> TEXT;
        };
    }

    private static int stateColor(String state) {
        return switch (state) {
            case "running" -> TEXT;
            case "idle", "sleeping" -> DIM;
            case "scanning", "accessing", "escalating" -> CYAN;
            default -> state.startsWith("paused") ? ORANGE : DIM;
        };
    }

    static String size(double mb, double totalMb) {
        if (totalMb >= 1024) {
            return String.format(Locale.ROOT, "%.1f/%.1fG", mb / 1024, totalMb / 1024);
        }
        return Math.round(mb) + "/" + Math.round(totalMb);
    }

    private static String pad(String text, int width) {
        return text.length() >= width ? text + " " : text + " ".repeat(width - text.length());
    }

    // endregion
}
