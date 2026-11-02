package com.mhm.mhm2027.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Edge's windows: where each one is, which is on top, and when it last opened, closed or
 * minimised, for the animations. Pure state and layout, so it can be tested; the screen draws it.
 */
public final class EdgeWindows {
    public static final String TERMINAL = "terminal";
    public static final String KERNEL = "kernel.log";
    public static final String TOP = "top";
    public static final String EDITOR = "editor";
    private static final String PROGRAM = "program:";

    /** Opening, closing and minimising each take this long. */
    public static final long ANIMATION_MILLIS = 160;
    public static final int TITLE_HEIGHT = 14;
    public static final int MIN_WIDTH = 120;
    /** Room for the title, a line, and a crashed program's footer. */
    public static final int MIN_HEIGHT = 60;
    private static final int GAP = 6;
    private static final int CASCADE = 12;
    private static final int PROGRAM_HEIGHT = 92;
    private static final int KERNEL_HEIGHT = 82;
    private static final int TOP_HEIGHT = 112;

    public record Rect(int x, int y, int width, int height) {
        public int right() {
            return x + width;
        }

        public int bottom() {
            return y + height;
        }

        public boolean contains(double px, double py) {
            return px >= x && px < right() && py >= y && py < bottom();
        }

        boolean overlaps(Rect other) {
            return x < other.right() && other.x < right() && y < other.bottom() && other.y < bottom();
        }

        /** Part way from this to {@code target}, {@code t} from 0 to 1. */
        public Rect toward(Rect target, float t) {
            return new Rect(Math.round(x + (target.x - x) * t), Math.round(y + (target.y - y) * t),
                    Math.round(width + (target.width - width) * t), Math.round(height + (target.height - height) * t));
        }
    }

    public static final class Window {
        private final String id;
        private Rect rect;
        private boolean minimised;
        private boolean maximised;
        private boolean closing;
        private long changedAt;
        private int scroll;

        private Window(String id, Rect rect, long now) {
            this.id = id;
            this.rect = rect;
            this.changedAt = now;
        }

        public String id() {
            return id;
        }

        public boolean minimised() {
            return minimised;
        }

        public boolean maximised() {
            return maximised;
        }

        public boolean closing() {
            return closing;
        }

        /** Lines scrolled back from the newest. */
        public int scroll() {
            return scroll;
        }

        public void setScroll(int scroll) {
            this.scroll = Math.max(0, scroll);
        }

        /** On screen, or on its way: not closing and not minimised. */
        public boolean shown() {
            return !closing && !minimised;
        }
    }

    // Bottom to top.
    private final List<Window> order = new ArrayList<>();
    private Rect area = new Rect(0, 0, 640, 360);

    public static String program(String name) {
        return PROGRAM + name;
    }

    public static boolean isProgram(String id) {
        return id.startsWith(PROGRAM);
    }

    public static String programName(String id) {
        return id.substring(PROGRAM.length());
    }

    /** The desktop between the top bar and the taskbar. Windows are kept inside it. */
    public void setArea(Rect area) {
        if (area.equals(this.area)) {
            return;
        }
        this.area = area;
        for (Window window : order) {
            window.rect = clamp(window.rect);
        }
    }

    public Rect area() {
        return area;
    }

    /** Every window, bottom to top, including ones still animating closed. */
    public List<Window> windows() {
        return List.copyOf(order);
    }

    /** A window that is open or minimised; one that is closing doesn't count. */
    public Optional<Window> get(String id) {
        return order.stream().filter(window -> window.id.equals(id) && !window.closing).findFirst();
    }

    /** The window on top, if any is shown. */
    public Optional<Window> focused() {
        for (int i = order.size() - 1; i >= 0; i--) {
            if (order.get(i).shown()) {
                return Optional.of(order.get(i));
            }
        }
        return Optional.empty();
    }

    /** Opens a window, or brings it back if it's minimised, and puts it on top. */
    public Window open(String id, long now) {
        Optional<Window> existing = get(id);
        if (existing.isPresent()) {
            Window window = existing.get();
            if (window.minimised) {
                window.minimised = false;
                window.changedAt = now;
            }
            focus(id);
            return window;
        }
        order.removeIf(window -> window.id.equals(id));
        Window window = new Window(id, place(id), now);
        order.add(window);
        return window;
    }

    public void close(String id, long now) {
        get(id).ifPresent(window -> {
            window.closing = true;
            window.changedAt = now;
        });
    }

    public void minimise(String id, long now) {
        get(id).filter(window -> !window.minimised).ifPresent(window -> {
            window.minimised = true;
            window.changedAt = now;
        });
    }

    public void toggleMaximise(String id) {
        get(id).ifPresent(window -> window.maximised = !window.maximised);
    }

    public void focus(String id) {
        get(id).ifPresent(window -> {
            order.remove(window);
            order.add(window);
        });
    }

    /** A taskbar click: open it, bring it back, put it on top, or minimise it if it's already on top. */
    public void pill(String id, long now) {
        Optional<Window> window = get(id);
        if (window.isPresent() && window.get().shown() && focused().equals(window)) {
            minimise(id, now);
        } else {
            open(id, now);
        }
    }

    /** Drops windows that have finished closing. */
    public void prune(long now) {
        order.removeIf(window -> window.closing && now - window.changedAt >= ANIMATION_MILLIS);
    }

    /** Where the window is drawn: the whole desktop when maximised. */
    public Rect bounds(Window window) {
        return window.maximised ? area : window.rect;
    }

    /** Moves a window by its top-left corner. Dragging a maximised one brings it back to size first. */
    public void move(String id, int x, int y) {
        get(id).ifPresent(window -> {
            window.maximised = false;
            window.rect = clamp(new Rect(x, y, window.rect.width, window.rect.height));
        });
    }

    public void resize(String id, int width, int height) {
        get(id).ifPresent(window -> {
            window.maximised = false;
            int fitWidth = Math.clamp(width, MIN_WIDTH, Math.max(MIN_WIDTH, area.right() - window.rect.x));
            int fitHeight = Math.clamp(height, MIN_HEIGHT, Math.max(MIN_HEIGHT, area.bottom() - window.rect.y));
            window.rect = clamp(new Rect(window.rect.x, window.rect.y, fitWidth, fitHeight));
        });
    }

    /** The shown window on top at a point. */
    public Optional<Window> at(double x, double y) {
        for (int i = order.size() - 1; i >= 0; i--) {
            Window window = order.get(i);
            if (window.shown() && bounds(window).contains(x, y)) {
                return Optional.of(window);
            }
        }
        return Optional.empty();
    }

    /** 0 to 1 while a window opens, closes, minimises or comes back; 1 once it has settled. */
    public static float progress(Window window, long now) {
        return Math.clamp((now - window.changedAt) / (float) ANIMATION_MILLIS, 0, 1);
    }

    /**
     * The terminal takes the left side and the editor most of the desktop. Everything else goes
     * down the right side, in the first gap nothing else covers, or cascades from the top when
     * the column is full.
     */
    private Rect place(String id) {
        int terminalWidth = Math.max(MIN_WIDTH, area.width * 11 / 20);
        if (id.equals(TERMINAL)) {
            return clamp(new Rect(area.x, area.y, terminalWidth, area.height));
        }
        if (id.equals(EDITOR)) {
            // Leaves a strip of the terminal showing, so it's one click away.
            int x = area.x + area.width / 6;
            return clamp(new Rect(x, area.y, Math.max(MIN_WIDTH, area.right() - x), area.height));
        }
        int x = area.x + terminalWidth + GAP;
        int width = Math.max(MIN_WIDTH, area.right() - x);
        int height = Math.min(area.height, switch (id) {
            case KERNEL -> KERNEL_HEIGHT;
            case TOP -> TOP_HEIGHT;
            default -> PROGRAM_HEIGHT;
        });
        List<Integer> tops = new ArrayList<>(List.of(area.y));
        for (Window window : order) {
            if (window.shown()) {
                tops.add(bounds(window).bottom() + GAP);
            }
        }
        tops.sort(null);
        for (int y : tops) {
            Rect candidate = new Rect(x, y, width, height);
            if (candidate.bottom() <= area.bottom() && order.stream().noneMatch(window -> window.shown()
                    && !window.id.equals(TERMINAL) && !window.id.equals(EDITOR) && bounds(window).overlaps(candidate))) {
                return clamp(candidate);
            }
        }
        int step = (int) order.stream().filter(window -> !window.id.equals(TERMINAL) && !window.id.equals(EDITOR)).count() % 5;
        return clamp(new Rect(x - step * CASCADE, area.y + step * CASCADE, width, height));
    }

    /** Fits a rectangle inside the desktop, shrinking it only if it's bigger than the desktop. */
    private Rect clamp(Rect rect) {
        int width = Math.min(rect.width, area.width);
        int height = Math.min(rect.height, area.height);
        int x = Math.clamp(rect.x, area.x, area.right() - width);
        int y = Math.clamp(rect.y, area.y, area.bottom() - height);
        return new Rect(x, y, width, height);
    }
}
