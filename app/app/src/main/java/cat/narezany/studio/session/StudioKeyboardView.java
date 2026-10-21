package cat.narezany.studio.session;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import com.winlator.xserver.XKeycode;
import com.winlator.xserver.XServer;

import java.util.ArrayList;
import java.util.List;

import static com.winlator.xserver.XKeycode.*;

/** On-screen keyboard with the keys Roblox Studio actually needs. */
public class StudioKeyboardView extends View {
    public interface Host {
        void onHideKeyboard();
        void onSystemKeyboard();
    }

    private static final int K_KEY = 0, K_MOD = 1, K_MACRO = 2, K_PAGE = 3, K_HIDE = 4, K_SYSTEM = 5;
    private static final int LATCH_OFF = 0, LATCH_ONCE = 1, LATCH_LOCK = 2;
    private static final int COLOR_PANEL = 0xF2101418;
    private static final int COLOR_KEY = 0xFF2B3138;
    private static final int COLOR_KEY_ALT = 0xFF222830;
    private static final int COLOR_KEY_DOWN = 0xFF0B84FF;
    private static final int COLOR_LATCH = 0xFF00497D;
    private static final int COLOR_LOCK = 0xFF0B84FF;
    private static final int COLOR_TEXT = 0xFFE6EAF0;
    private static final int COLOR_SUB = 0xFF9AA7B5;
    private static final int COLOR_TAB_ON = 0xFF00497D;

    private static class Key {
        final String label;
        final String sub;
        final float weight;
        final int kind;
        final XKeycode[] codes;
        final int page;
        boolean repeat;
        boolean alt;
        final RectF rect = new RectF();
        int pointerId = -1;
        ModState state;
        boolean letter = false;

        Key(String label, String sub, float weight, int kind, int page, XKeycode... codes) {
            this.label = label;
            this.sub = sub;
            this.weight = weight;
            this.kind = kind;
            this.page = page;
            this.codes = codes;
        }
    }

    /** Ctrl, Shift and Alt are shared by every page. */
    private static class ModState {
        final XKeycode code;
        int latch = LATCH_OFF;
        int held = 0;
        boolean chordUsed = false;

        ModState(XKeycode code) {
            this.code = code;
        }
    }

    private final XServer xServer;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF panelRect = new RectF();
    private final List<List<List<Key>>> pages = new ArrayList<>();
    private final List<Key> topBar = new ArrayList<>();
    private final List<ModState> mods = new ArrayList<>();
    private int pageIndex = 0;
    private Host host;
    private boolean haptics = true;
    private final float density;
    private Key repeatKey;
    private final Runnable repeatRunnable = new Runnable() {
        @Override
        public void run() {
            if (repeatKey == null) return;
            tapCodes(repeatKey.codes);
            handler.postDelayed(this, 55);
        }
    };

    public StudioKeyboardView(Context context, XServer xServer) {
        super(context);
        this.xServer = xServer;
        this.density = context.getResources().getDisplayMetrics().density;
        buildLayouts();
        setClickable(true);
        paint.setTextAlign(Paint.Align.CENTER);
    }

    public void setHost(Host host) {
        this.host = host;
    }

    public void setHaptics(boolean haptics) {
        this.haptics = haptics;
    }

    private Key key(String label, XKeycode code) {
        return new Key(label, null, 1f, K_KEY, 0, code);
    }

    private Key key(String label, float weight, XKeycode code) {
        return new Key(label, null, weight, K_KEY, 0, code);
    }

    private Key letter(String label, XKeycode code) {
        Key key = key(label, code);
        key.letter = true;
        return key;
    }

    private Key arrow(String label, XKeycode code) {
        Key key = key(label, code);
        key.repeat = true;
        key.alt = true;
        return key;
    }

    private Key mod(String label, float weight, XKeycode code) {
        Key key = new Key(label, null, weight, K_MOD, 0, code);
        key.alt = true;
        key.state = modState(code);
        return key;
    }

    private ModState modState(XKeycode code) {
        for (ModState state : mods) if (state.code == code) return state;
        ModState state = new ModState(code);
        mods.add(state);
        return state;
    }

    private Key macro(String label, String sub, XKeycode... codes) {
        return new Key(label, sub, 1f, K_MACRO, 0, codes);
    }

    private Key shifted(String label, XKeycode code) {
        return new Key(label, null, 1f, K_MACRO, 0, KEY_SHIFT_L, code);
    }

    private Key alt(Key key) {
        key.alt = true;
        return key;
    }

    private Key repeating(Key key) {
        key.repeat = true;
        return key;
    }

    private List<Key> row(Key... keys) {
        List<Key> row = new ArrayList<>();
        for (Key key : keys) row.add(key);
        return row;
    }

    private void buildLayouts() {
        // Studio shortcuts
        List<List<Key>> studio = new ArrayList<>();
        studio.add(row(
            alt(key("Esc", KEY_ESC)), alt(macro("Rename", "F2", KEY_F2)), macro("Play", "F5", KEY_F5), macro("Stop", "⇧F5", KEY_SHIFT_L, KEY_F5),
            macro("Run", "F8", KEY_F8), macro("Here", "F6", KEY_F6), macro("Save", "^S", KEY_CTRL_L, KEY_S),
            macro("Undo", "^Z", KEY_CTRL_L, KEY_Z), macro("Redo", "^Y", KEY_CTRL_L, KEY_Y),
            macro("Cut", "^X", KEY_CTRL_L, KEY_X), macro("Copy", "^C", KEY_CTRL_L, KEY_C), macro("Paste", "^V", KEY_CTRL_L, KEY_V),
            alt(key("Del", KEY_DEL))));
        studio.add(row(
            macro("Select", "^1", KEY_CTRL_L, KEY_1), macro("Move", "^2", KEY_CTRL_L, KEY_2), macro("Scale", "^3", KEY_CTRL_L, KEY_3),
            macro("Rotate", "^4", KEY_CTRL_L, KEY_4), macro("Trans.", "^5", KEY_CTRL_L, KEY_5),
            macro("Duplic.", "^D", KEY_CTRL_L, KEY_D), macro("Group", "^G", KEY_CTRL_L, KEY_G), macro("Ungrp", "^⇧G", KEY_CTRL_L, KEY_SHIFT_L, KEY_G),
            macro("All", "^A", KEY_CTRL_L, KEY_A), macro("Find", "^F", KEY_CTRL_L, KEY_F), letter("F", KEY_F), repeating(alt(key("⌫", 1.4f, KEY_BKSP)))));
        studio.add(row(
            mod("Ctrl", 1.3f, KEY_CTRL_L), mod("Shift", 1.3f, KEY_SHIFT_L), mod("Alt", 1.3f, KEY_ALT_L), alt(key("Tab", KEY_TAB)),
            letter("Q", KEY_Q), letter("W", KEY_W), letter("E", KEY_E), letter("R", KEY_R), letter("T", KEY_T),
            arrow("↑", KEY_UP), alt(key("Enter", 1.6f, KEY_ENTER))));
        studio.add(row(
            key("Space", 3f, KEY_SPACE), letter("A", KEY_A), letter("S", KEY_S), letter("D", KEY_D),
            arrow("←", KEY_LEFT), arrow("↓", KEY_DOWN), arrow("→", KEY_RIGHT), alt(key("Home", KEY_HOME)), alt(key("End", KEY_END))));
        studio.add(row(
            key("1", KEY_1), key("2", KEY_2), key("3", KEY_3), key("4", KEY_4), key("5", KEY_5),
            key("6", KEY_6), key("7", KEY_7), key("8", KEY_8), key("9", KEY_9), key("0", KEY_0), key(".", KEY_PERIOD), key("-", KEY_MINUS)));
        pages.add(studio);

        // Full keys
        List<List<Key>> keys = new ArrayList<>();
        keys.add(row(key("`", KEY_GRAVE), key("1", KEY_1), key("2", KEY_2), key("3", KEY_3), key("4", KEY_4), key("5", KEY_5), key("6", KEY_6),
            key("7", KEY_7), key("8", KEY_8), key("9", KEY_9), key("0", KEY_0), key("-", KEY_MINUS), key("=", KEY_EQUAL), repeating(alt(key("⌫", 1.6f, KEY_BKSP)))));
        keys.add(row(alt(key("Tab", 1.4f, KEY_TAB)), letter("q", KEY_Q), letter("w", KEY_W), letter("e", KEY_E), letter("r", KEY_R), letter("t", KEY_T),
            letter("y", KEY_Y), letter("u", KEY_U), letter("i", KEY_I), letter("o", KEY_O), letter("p", KEY_P), key("[", KEY_BRACKET_LEFT),
            key("]", KEY_BRACKET_RIGHT), key("\\", KEY_BACKSLASH)));
        keys.add(row(alt(key("Esc", 1.6f, KEY_ESC)), letter("a", KEY_A), letter("s", KEY_S), letter("d", KEY_D), letter("f", KEY_F), letter("g", KEY_G),
            letter("h", KEY_H), letter("j", KEY_J), letter("k", KEY_K), letter("l", KEY_L), key(";", KEY_SEMICOLON), key("'", KEY_APOSTROPHE), alt(key("Enter", 1.8f, KEY_ENTER))));
        keys.add(row(mod("Shift", 2.1f, KEY_SHIFT_L), letter("z", KEY_Z), letter("x", KEY_X), letter("c", KEY_C), letter("v", KEY_V), letter("b", KEY_B),
            letter("n", KEY_N), letter("m", KEY_M), key(",", KEY_COMMA), key(".", KEY_PERIOD), key("/", KEY_SLASH), arrow("↑", KEY_UP)));
        keys.add(row(mod("Ctrl", 1.4f, KEY_CTRL_L), mod("Alt", 1.4f, KEY_ALT_L), key("Space", 5f, KEY_SPACE), arrow("←", KEY_LEFT), arrow("↓", KEY_DOWN),
            arrow("→", KEY_RIGHT), alt(key("Del", 1.2f, KEY_DEL))));
        pages.add(keys);

        // Symbols and function keys
        List<List<Key>> symbols = new ArrayList<>();
        symbols.add(row(alt(key("F1", KEY_F1)), alt(key("F2", KEY_F2)), alt(key("F3", KEY_F3)), alt(key("F4", KEY_F4)), alt(key("F5", KEY_F5)), alt(key("F6", KEY_F6)),
            alt(key("F7", KEY_F7)), alt(key("F8", KEY_F8)), alt(key("F9", KEY_F9)), alt(key("F10", KEY_F10)), alt(key("F11", KEY_F11)), alt(key("F12", KEY_F12))));
        symbols.add(row(shifted("!", KEY_1), shifted("@", KEY_2), shifted("#", KEY_3), shifted("$", KEY_4), shifted("%", KEY_5), shifted("^", KEY_6),
            shifted("&", KEY_7), shifted("*", KEY_8), shifted("(", KEY_9), shifted(")", KEY_0), shifted("_", KEY_MINUS), shifted("+", KEY_EQUAL)));
        symbols.add(row(shifted("{", KEY_BRACKET_LEFT), shifted("}", KEY_BRACKET_RIGHT), shifted("|", KEY_BACKSLASH), shifted(":", KEY_SEMICOLON), shifted("\"", KEY_APOSTROPHE),
            shifted("<", KEY_COMMA), shifted(">", KEY_PERIOD), shifted("?", KEY_SLASH), shifted("~", KEY_GRAVE), key("[", KEY_BRACKET_LEFT), key("]", KEY_BRACKET_RIGHT), key("\\", KEY_BACKSLASH)));
        symbols.add(row(alt(key("Home", KEY_HOME)), alt(key("End", KEY_END)), arrow("PgUp", KEY_PRIOR), arrow("PgDn", KEY_NEXT), alt(key("Ins", KEY_INSERT)), alt(key("Del", KEY_DEL)),
            arrow("↑", KEY_UP), arrow("←", KEY_LEFT), arrow("↓", KEY_DOWN), arrow("→", KEY_RIGHT), alt(key("Enter", 1.6f, KEY_ENTER))));
        symbols.add(row(mod("Ctrl", 1.4f, KEY_CTRL_L), mod("Shift", 1.4f, KEY_SHIFT_L), mod("Alt", 1.4f, KEY_ALT_L), key("Space", 5f, KEY_SPACE), repeating(alt(key("⌫", 1.6f, KEY_BKSP))), alt(key("Esc", 1.2f, KEY_ESC))));
        pages.add(symbols);

        String[] names = {"Studio", "Keys", "F / #"};
        for (int i = 0; i < names.length; i++) topBar.add(new Key(names[i], null, 1.2f, K_PAGE, i));
        topBar.add(new Key("", null, 6f, K_PAGE, -1));
        topBar.add(new Key("Aa", null, 1.1f, K_SYSTEM, 0));
        topBar.add(new Key("⌄", null, 1.1f, K_HIDE, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = (int)((30 + 5 * 35 + 7 * 3) * density);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutKeys();
    }

    private void layoutRow(List<Key> row, float top, float bottom, float left, float right, float gap) {
        float totalWeight = 0;
        for (Key key : row) totalWeight += key.weight;
        float available = (right - left) - gap * (row.size() - 1);
        float x = left;
        for (Key key : row) {
            float width = available * key.weight / totalWeight;
            key.rect.set(x, top, x + width, bottom);
            x += width + gap;
        }
    }

    private void layoutKeys() {
        float gap = 3 * density;
        float pad = 5 * density;
        float topBarHeight = 30 * density;
        panelRect.set(0, 0, getWidth(), getHeight());

        layoutRow(topBar, pad, pad + topBarHeight, pad, getWidth() - pad, gap);

        float rowsTop = pad + topBarHeight + gap;
        float rowHeight = (getHeight() - rowsTop - pad - gap * 4) / 5f;
        for (List<List<Key>> page : pages) {
            for (int r = 0; r < page.size(); r++) {
                float top = rowsTop + r * (rowHeight + gap);
                layoutRow(page.get(r), top, top + rowHeight, pad, getWidth() - pad, gap);
            }
        }
    }

    private boolean shiftActive() {
        for (ModState state : mods) if (state.code == KEY_SHIFT_L && (state.latch != LATCH_OFF || state.held > 0)) return true;
        return false;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(COLOR_PANEL);
        canvas.drawRoundRect(panelRect, 14 * density, 14 * density, paint);

        for (Key key : topBar) {
            if (key.page == -1) continue;
            drawKey(canvas, key, key.kind == K_PAGE && key.page == pageIndex, false);
        }
        boolean shift = shiftActive();
        for (List<Key> row : pages.get(pageIndex)) for (Key key : row) drawKey(canvas, key, false, shift);
    }

    private void drawKey(Canvas canvas, Key key, boolean selected, boolean shift) {
        boolean down = key.pointerId != -1;
        int color = key.alt ? COLOR_KEY_ALT : COLOR_KEY;
        if (selected) color = COLOR_TAB_ON;
        if (key.kind == K_MOD) {
            if (key.state.latch == LATCH_ONCE) color = COLOR_LATCH;
            else if (key.state.latch == LATCH_LOCK) color = COLOR_LOCK;
        }
        if (down) color = COLOR_KEY_DOWN;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        float radius = 7 * density;
        canvas.drawRoundRect(key.rect, radius, radius, paint);

        String label = key.label;
        if (key.letter && shift && label.length() == 1) label = label.toUpperCase();

        float cx = key.rect.centerX();
        float height = key.rect.height();
        paint.setColor(COLOR_TEXT);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        float textSize = Math.min(height * 0.42f, 17 * density);
        if (label.length() > 4) textSize = Math.min(textSize, key.rect.width() / (label.length() * 0.62f));
        if (key.sub != null) {
            textSize = Math.min(Math.min(height * 0.30f, 12 * density), key.rect.width() / (Math.max(label.length(), 3) * 0.66f));
            paint.setTextSize(textSize);
            canvas.drawText(label, cx, key.rect.top + height * 0.46f, paint);
            paint.setColor(COLOR_SUB);
            paint.setTypeface(Typeface.DEFAULT);
            paint.setTextSize(textSize * 0.9f);
            canvas.drawText(key.sub, cx, key.rect.top + height * 0.84f, paint);
        }
        else {
            paint.setTextSize(textSize);
            Paint.FontMetrics fm = paint.getFontMetrics();
            canvas.drawText(label, cx, key.rect.centerY() - (fm.ascent + fm.descent) / 2f, paint);
        }
    }

    private Key hitTest(float x, float y) {
        for (Key key : topBar) if (key.page != -1 && key.rect.contains(x, y)) return key;
        for (List<Key> row : pages.get(pageIndex)) for (Key key : row) if (key.rect.contains(x, y)) return key;
        return null;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                Key key = hitTest(event.getX(index), event.getY(index));
                if (key != null && key.pointerId == -1) {
                    key.pointerId = event.getPointerId(index);
                    pressKey(key);
                    invalidate();
                }
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                releasePointer(event.getPointerId(index));
                invalidate();
                return true;
            case MotionEvent.ACTION_CANCEL:
                releaseAll();
                invalidate();
                return true;
        }
        return true;
    }

    private Key findByPointer(int pointerId) {
        for (Key key : topBar) if (key.pointerId == pointerId) return key;
        for (List<List<Key>> page : pages) for (List<Key> row : page) for (Key key : row) if (key.pointerId == pointerId) return key;
        return null;
    }

    private void releasePointer(int pointerId) {
        Key key = findByPointer(pointerId);
        if (key == null) return;
        key.pointerId = -1;
        releaseKey(key);
    }

    private void pressKey(Key key) {
        if (haptics) performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);

        switch (key.kind) {
            case K_MOD:
                if (key.state.latch == LATCH_OFF && key.state.held == 0) xServer.injectKeyPress(key.state.code);
                if (key.state.held == 0) key.state.chordUsed = false;
                key.state.held++;
                break;
            case K_KEY:
            case K_MACRO:
                markChord();
                for (XKeycode code : key.codes) xServer.injectKeyPress(code);
                if (key.repeat) {
                    repeatKey = key;
                    handler.removeCallbacks(repeatRunnable);
                    handler.postDelayed(repeatRunnable, 380);
                }
                break;
        }
    }

    private void markChord() {
        for (ModState mod : mods) if (mod.held > 0) mod.chordUsed = true;
    }

    private void releaseKey(Key key) {
        switch (key.kind) {
            case K_MOD: {
                ModState state = key.state;
                state.held = Math.max(0, state.held - 1);
                if (state.held > 0) break;
                if (state.chordUsed) {
                    if (state.latch == LATCH_OFF) xServer.injectKeyRelease(state.code);
                }
                else if (state.latch == LATCH_OFF) state.latch = LATCH_ONCE;
                else if (state.latch == LATCH_ONCE) state.latch = LATCH_LOCK;
                else {
                    state.latch = LATCH_OFF;
                    xServer.injectKeyRelease(state.code);
                }
                break;
            }
            case K_KEY:
            case K_MACRO:
                if (repeatKey == key) {
                    repeatKey = null;
                    handler.removeCallbacks(repeatRunnable);
                }
                for (int i = key.codes.length - 1; i >= 0; i--) xServer.injectKeyRelease(key.codes[i]);
                releaseOneShots();
                break;
            case K_PAGE:
                if (key.page >= 0) pageIndex = key.page;
                break;
            case K_HIDE:
                if (host != null) host.onHideKeyboard();
                break;
            case K_SYSTEM:
                if (host != null) host.onSystemKeyboard();
                break;
        }
    }

    private void releaseOneShots() {
        for (ModState mod : mods) {
            if (mod.latch == LATCH_ONCE && mod.held == 0) {
                mod.latch = LATCH_OFF;
                xServer.injectKeyRelease(mod.code);
            }
        }
    }

    private void tapCodes(XKeycode[] codes) {
        for (XKeycode code : codes) xServer.injectKeyPress(code);
        for (int i = codes.length - 1; i >= 0; i--) xServer.injectKeyRelease(codes[i]);
    }

    public void releaseAll() {
        handler.removeCallbacks(repeatRunnable);
        repeatKey = null;
        for (ModState mod : mods) {
            if (mod.latch != LATCH_OFF || mod.held > 0) xServer.injectKeyRelease(mod.code);
            mod.latch = LATCH_OFF;
            mod.held = 0;
        }
        for (List<List<Key>> page : pages) {
            for (List<Key> row : page) {
                for (Key key : row) {
                    if (key.pointerId != -1 && key.kind != K_MOD) for (XKeycode code : key.codes) xServer.injectKeyRelease(code);
                    key.pointerId = -1;
                }
            }
        }
        for (Key key : topBar) key.pointerId = -1;
    }

    @Override
    protected void onDetachedFromWindow() {
        releaseAll();
        super.onDetachedFromWindow();
    }
}
