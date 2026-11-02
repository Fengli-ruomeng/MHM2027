package com.mhm.mhm2027.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.mhm.mhm2027.editor.LuaHighlighter.Kind;
import com.mhm.mhm2027.editor.LuaHighlighter.Span;

class EditorSupportTest {
    private static Kind kindAt(List<Span> spans, int col) {
        return spans.stream().filter(span -> span.start() <= col && col < span.end()).findFirst().orElseThrow().kind();
    }

    @Test
    void highlightsTheMainTokenKinds() {
        String line = "function on_hit_taken(a, amount) if amount >= 4 then me.speed_boost(0.3, 2) end -- go";
        List<Span> spans = LuaHighlighter.highlight(List.of(line)).getFirst();
        assertEquals(Kind.KEYWORD, kindAt(spans, line.indexOf("function")));
        assertEquals(Kind.EVENT, kindAt(spans, line.indexOf("on_hit_taken")));
        assertEquals(Kind.NUMBER, kindAt(spans, line.indexOf("0.3")));
        assertEquals(Kind.API, kindAt(spans, line.indexOf("speed_boost")));
        assertEquals(Kind.COMMENT, kindAt(spans, line.indexOf("go")));
    }

    @Test
    void highlightsWorldCallsAsApis() {
        String line = "if world.block(0, -1, 0) == \"minecraft:lava\" then";
        List<Span> spans = LuaHighlighter.highlight(List.of(line)).getFirst();
        assertEquals(Kind.API, kindAt(spans, line.indexOf("world")));
        assertEquals(Kind.API, kindAt(spans, line.indexOf("block")));
    }

    @Test
    void stringsHideKeywordsInside() {
        String line = "sys.log(\"if then end\")";
        List<Span> spans = LuaHighlighter.highlight(List.of(line)).getFirst();
        assertEquals(Kind.STRING, kindAt(spans, line.indexOf("then")));
    }

    @Test
    void longCommentsSpanLines() {
        List<List<Span>> lines = LuaHighlighter.highlight(List.of("x = 1 --[[ start", "still comment", "end ]] y = 2"));
        assertEquals(Kind.COMMENT, kindAt(lines.get(1), 0));
        assertEquals(Kind.COMMENT, kindAt(lines.get(2), 0));
        assertEquals(Kind.NUMBER, kindAt(lines.get(2), "end ]] y = 2".indexOf("2")));
    }

    @Test
    void spansCoverEveryCharacter() {
        String line = "  local t = { a = [[x]], b = 'q' } -- done";
        List<Span> spans = LuaHighlighter.highlight(List.of(line)).getFirst();
        int covered = 0;
        for (Span span : spans) {
            assertEquals(covered, span.start(), "no gaps or overlaps");
            covered = span.end();
        }
        assertEquals(line.length(), covered);
    }

    @Test
    void completesApisFromTheRegistry() {
        List<Completions.Suggestion> me = Completions.suggest("  me.sp", false);
        assertEquals(1, me.size());
        assertEquals("eed_boost(", me.getFirst().insert());
        assertEquals(1, me.getFirst().level());

        assertTrue(Completions.suggest("x = sys.", false).size() >= 10);
        assertEquals(List.of("remote.inspect(target)"),
                Completions.suggest("local r = remote.ins", false).stream().map(Completions.Suggestion::label).toList());
        assertTrue(Completions.suggest("remote.", true).isEmpty(), "remote.* is program-only");
        assertEquals(List.of("world.block(dx, dy, dz)"),
                Completions.suggest("local b = world.bl", false).stream().map(Completions.Suggestion::label).toList());
        assertEquals(List.of("me.inventory()"),
                Completions.suggest("me.inv", false).stream().map(Completions.Suggestion::label).toList());
    }

    @Test
    void kernelsOnlySeeKernelApisAndHooks() {
        assertTrue(Completions.suggest("me.", true).isEmpty(), "me.* is program-only");
        assertEquals(List.of("world.time()", "world.day()", "world.weather()"),
                Completions.suggest("world.", true).stream().map(Completions.Suggestion::label).toList(),
                "kernels may read the clock and the sky, nothing else around you");
        assertEquals(List.of("on_panic()"), Completions.suggest("function on_pa", true).stream().map(Completions.Suggestion::label).toList());
        assertTrue(Completions.suggest("function on_", false).stream().anyMatch(s -> s.label().equals("on_hit_taken()")));
    }
}
