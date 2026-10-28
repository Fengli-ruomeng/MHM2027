package com.mhm.mhm2027.terminal;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.squiddev.cobalt.Constants;
import org.squiddev.cobalt.LuaError;
import org.squiddev.cobalt.LuaTable;
import org.squiddev.cobalt.LuaValue;
import org.squiddev.cobalt.compiler.CompileException;
import org.squiddev.cobalt.compiler.LoadState;
import org.squiddev.cobalt.function.LuaFunction;

/**
 * The install step shared by programs and kernels: static checks on the source, compiling,
 * running the top-level code once to read the header, and collecting event handlers.
 * Most mistakes are reported here, before anything runs in the world.
 */
final class ScriptCompiler {
    /** Top-level code only defines things; real work belongs in event handlers. */
    private static final int SETUP_INSTRUCTIONS = 20_000;

    private static final Pattern API_REFERENCE = Pattern.compile("\\b(me|sys|remote)\\s*\\.\\s*([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern LONG_COMMENT = Pattern.compile("--\\[(=*)\\[.*?]\\1]", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("--[^\\n]*");

    private ScriptCompiler() {
    }

    /**
     * Finds every API the source refers to and checks it exists, is allowed in this kind of
     * script, and is within the gateway level. The result decides the script's code RAM.
     */
    static Set<ApiSpec> scanApis(String source, boolean kernel, ProgramType type, int level) throws InstallException {
        String code = LINE_COMMENT.matcher(LONG_COMMENT.matcher(source).replaceAll("")).replaceAll("");
        Set<ApiSpec> apis = new LinkedHashSet<>();
        Matcher matcher = API_REFERENCE.matcher(code);
        while (matcher.find()) {
            String id = matcher.group(1) + "." + matcher.group(2);
            if (matcher.group(1).equals("remote")) {
                throw new InstallException(id + ": remote APIs arrive with cyber combat and aren't available yet");
            }
            ApiSpec spec = ApiSpec.byId(id).orElseThrow(() -> new InstallException("Unknown API " + id));
            if (kernel && !spec.kernel()) {
                throw new InstallException(id + " can't be used in a kernel");
            }
            if (type == ProgramType.QUERY && spec.needsSlot()) {
                throw new InstallException(id + " changes your body, which a query program may not do");
            }
            if (spec.minLevel() > level) {
                throw new InstallException(id + " needs gateway LV" + spec.minLevel() + " (you are LV" + level + ")");
            }
            apis.add(spec);
        }
        return apis;
    }

    /**
     * Compiles the source and runs its top-level code once. The header function
     * ({@code program} or {@code kernel}) is defined only for this run and must be called
     * exactly once with a table.
     */
    static LuaTable load(LuaSandbox sandbox, String name, String source, String headerFunction, String wrongHeaderFunction) throws InstallException {
        LuaTable globals = sandbox.globals();
        LuaTable[] header = new LuaTable[1];
        globals.rawset(headerFunction, LuaSandbox.function(headerFunction, (state, args) -> {
            if (header[0] != null) {
                throw new LuaError(headerFunction + " { ... } may only appear once");
            }
            if (!(args.arg(1) instanceof LuaTable table)) {
                throw new LuaError(headerFunction + " expects a table, like " + headerFunction + " { name = \"" + name + "\" }");
            }
            header[0] = table;
            return Constants.NONE;
        }));
        globals.rawset(wrongHeaderFunction, LuaSandbox.function(wrongHeaderFunction, (state, args) -> {
            throw new LuaError("this is a " + wrongHeaderFunction + " script; it can't be used as a " + headerFunction);
        }));

        LuaFunction chunk;
        try {
            chunk = LoadState.load(sandbox.state(), new ByteArrayInputStream(source.getBytes(StandardCharsets.UTF_8)), "@" + name + ".lua", globals);
        } catch (CompileException | LuaError e) {
            throw new InstallException("Syntax error: " + firstLine(e.getMessage()));
        }

        try {
            LuaSandbox.Slice slice = sandbox.run(sandbox.newThread(chunk), Constants.NONE, SETUP_INSTRUCTIONS);
            if (slice.kind() != LuaSandbox.Slice.Kind.FINISHED) {
                throw new InstallException("Top-level code ran too long; put the work inside event handlers");
            }
        } catch (LuaError e) {
            throw new InstallException(firstLine(e.getMessage()));
        } finally {
            globals.rawset(headerFunction, Constants.NIL);
            globals.rawset(wrongHeaderFunction, Constants.NIL);
        }

        if (header[0] == null) {
            throw new InstallException("Missing header: start the file with " + headerFunction + " { name = \"" + name + "\", ... }");
        }
        return header[0];
    }

    /** Collects the handler functions the script defines, from a fixed list of names. */
    static Map<String, LuaFunction> handlers(LuaSandbox sandbox, List<String> allowed) throws InstallException {
        Map<String, LuaFunction> handlers = new LinkedHashMap<>();
        for (String name : allowed) {
            LuaValue value = sandbox.globals().rawget(name);
            if (value instanceof LuaFunction function) {
                handlers.put(name, function);
            } else if (!value.isNil()) {
                throw new InstallException(name + " must be a function");
            }
        }
        return handlers;
    }

    static String stringField(LuaTable header, String key, String fallback) throws InstallException {
        LuaValue value = header.rawget(key);
        if (value.isNil()) {
            return fallback;
        }
        if (!value.isString()) {
            throw new InstallException("Header field " + key + " must be a string");
        }
        return value.toString();
    }

    static int intField(LuaTable header, String key, int fallback, int min, int max) throws InstallException {
        LuaValue value = header.rawget(key);
        if (value.isNil()) {
            return fallback;
        }
        if (!value.isNumber()) {
            throw new InstallException("Header field " + key + " must be a number");
        }
        double number = value.toDouble();
        if (number != Math.rint(number) || number < min || number > max) {
            throw new InstallException("Header field " + key + " must be a whole number from " + min + " to " + max);
        }
        return (int) number;
    }

    /** Rejects header keys that aren't in the allowed list, to catch typos like {@code top = 4}. */
    static void checkKeys(LuaTable header, Set<String> allowed) throws InstallException {
        try {
            LuaValue key = Constants.NIL;
            while (true) {
                var next = header.next(key);
                key = next.first();
                if (key.isNil()) {
                    return;
                }
                if (!key.isString() || !allowed.contains(key.toString())) {
                    throw new InstallException("Unknown header field " + key + " (expected one of " + String.join(", ", allowed) + ")");
                }
            }
        } catch (LuaError e) {
            throw new InstallException(e.getMessage());
        }
    }

    static String firstLine(String message) {
        if (message == null) {
            return "unknown error";
        }
        int newline = message.indexOf('\n');
        return newline < 0 ? message : message.substring(0, newline);
    }
}
