package com.mhm.mhm2027.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Splits Lua source into coloured spans for the editor. Line by line, carrying state across
 * lines so long comments and long strings ({@code --[[ ]]}, {@code [[ ]]}) colour correctly.
 */
public final class LuaHighlighter {
    public enum Kind {
        PLAIN, KEYWORD, STRING, COMMENT, NUMBER, API, EVENT, HEADER, BUILTIN
    }

    /** Columns [start, end) on one line. */
    public record Span(int start, int end, Kind kind) {
    }

    private static final Set<String> KEYWORDS = Set.of("and", "break", "do", "else", "elseif", "end", "false", "for",
            "function", "goto", "if", "in", "local", "nil", "not", "or", "repeat", "return", "then", "true", "until", "while");
    private static final Set<String> BUILTINS = Set.of("print", "pairs", "ipairs", "next", "select", "type", "tostring",
            "tonumber", "error", "assert", "pcall", "xpcall", "rawget", "rawset", "rawequal", "rawlen", "setmetatable",
            "getmetatable", "unpack", "math", "string", "table", "chance");
    private static final Set<String> NAMESPACES = Set.of("me", "sys", "remote");
    private static final Set<String> HEADERS = Set.of("program", "kernel");

    private LuaHighlighter() {
    }

    public static List<List<Span>> highlight(List<String> lines) {
        List<List<Span>> result = new ArrayList<>(lines.size());
        // Inside a long bracket from an earlier line: its '=' level and whether it is a comment.
        int openLevel = -1;
        boolean openIsComment = false;
        for (String line : lines) {
            List<Span> spans = new ArrayList<>();
            int i = 0;
            if (openLevel >= 0) {
                int close = findClose(line, 0, openLevel);
                Kind kind = openIsComment ? Kind.COMMENT : Kind.STRING;
                if (close < 0) {
                    spans.add(new Span(0, line.length(), kind));
                    result.add(spans);
                    continue;
                }
                spans.add(new Span(0, close, kind));
                i = close;
                openLevel = -1;
            }
            while (i < line.length()) {
                char c = line.charAt(i);
                if (c == '-' && line.startsWith("--", i)) {
                    int level = longBracketLevel(line, i + 2);
                    if (level >= 0) {
                        int close = findClose(line, i + 2 + level + 2, level);
                        if (close < 0) {
                            spans.add(new Span(i, line.length(), Kind.COMMENT));
                            openLevel = level;
                            openIsComment = true;
                            break;
                        }
                        spans.add(new Span(i, close, Kind.COMMENT));
                        i = close;
                    } else {
                        spans.add(new Span(i, line.length(), Kind.COMMENT));
                        break;
                    }
                } else if (c == '"' || c == '\'') {
                    int end = i + 1;
                    while (end < line.length() && line.charAt(end) != c) {
                        end += line.charAt(end) == '\\' ? 2 : 1;
                    }
                    end = Math.min(end + 1, line.length());
                    spans.add(new Span(i, end, Kind.STRING));
                    i = end;
                } else if (c == '[' && longBracketLevel(line, i) >= 0) {
                    int level = longBracketLevel(line, i);
                    int close = findClose(line, i + level + 2, level);
                    if (close < 0) {
                        spans.add(new Span(i, line.length(), Kind.STRING));
                        openLevel = level;
                        openIsComment = false;
                        break;
                    }
                    spans.add(new Span(i, close, Kind.STRING));
                    i = close;
                } else if (Character.isDigit(c) || (c == '.' && i + 1 < line.length() && Character.isDigit(line.charAt(i + 1)))) {
                    int end = i + 1;
                    while (end < line.length() && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '.'
                            || ((line.charAt(end) == '-' || line.charAt(end) == '+') && "eEpP".indexOf(line.charAt(end - 1)) >= 0))) {
                        end++;
                    }
                    spans.add(new Span(i, end, Kind.NUMBER));
                    i = end;
                } else if (Character.isLetter(c) || c == '_') {
                    int end = i + 1;
                    while (end < line.length() && CodeDocument.isWordChar(line.charAt(end))) {
                        end++;
                    }
                    String word = line.substring(i, end);
                    if (NAMESPACES.contains(word) && end < line.length() && line.charAt(end) == '.') {
                        // me.speed_boost: colour the namespace, the dot and the member together.
                        int member = end + 1;
                        while (member < line.length() && CodeDocument.isWordChar(line.charAt(member))) {
                            member++;
                        }
                        spans.add(new Span(i, member, Kind.API));
                        i = member;
                        continue;
                    }
                    spans.add(new Span(i, end, kindOf(word, i > 0 && line.charAt(i - 1) == '.')));
                    i = end;
                } else {
                    int end = i + 1;
                    while (end < line.length() && isPlainSymbol(line, end)) {
                        end++;
                    }
                    spans.add(new Span(i, end, Kind.PLAIN));
                    i = end;
                }
            }
            result.add(spans);
        }
        return result;
    }

    private static Kind kindOf(String word, boolean afterDot) {
        if (afterDot) {
            return Kind.PLAIN;
        }
        if (KEYWORDS.contains(word)) {
            return Kind.KEYWORD;
        }
        if (HEADERS.contains(word)) {
            return Kind.HEADER;
        }
        if (word.startsWith("on_")) {
            return Kind.EVENT;
        }
        return BUILTINS.contains(word) ? Kind.BUILTIN : Kind.PLAIN;
    }

    /** Symbols and spaces that can be lumped into one plain span. */
    private static boolean isPlainSymbol(String line, int at) {
        char c = line.charAt(at);
        if (CodeDocument.isWordChar(c) || c == '"' || c == '\'' || c == '[') {
            return false;
        }
        return !(c == '-' && line.startsWith("--", at)) && !(c == '.' && at + 1 < line.length() && Character.isDigit(line.charAt(at + 1)));
    }

    /** If a long bracket ([[, [=[, ...) opens at {@code at}, its level (the number of '='); otherwise -1. */
    private static int longBracketLevel(String line, int at) {
        if (at >= line.length() || line.charAt(at) != '[') {
            return -1;
        }
        int level = 0;
        int i = at + 1;
        while (i < line.length() && line.charAt(i) == '=') {
            level++;
            i++;
        }
        return i < line.length() && line.charAt(i) == '[' ? level : -1;
    }

    /** The column just after the closing long bracket of this level, or -1 if it isn't on this line. */
    private static int findClose(String line, int from, int level) {
        String close = "]" + "=".repeat(level) + "]";
        int at = line.indexOf(close, Math.min(from, line.length()));
        return at < 0 ? -1 : at + close.length();
    }
}
