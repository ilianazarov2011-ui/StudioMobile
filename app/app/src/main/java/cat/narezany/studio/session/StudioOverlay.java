package cat.narezany.studio.session;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.TextView;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;
import com.winlator.core.AppUtils;
import com.winlator.widget.TouchpadView;
import com.winlator.xserver.Pointer;
import com.winlator.xserver.XServer;

import cat.narezany.studio.core.StudioPrefs;

/** Floating dock, mouse buttons and the Studio keyboard drawn over the X server view. */
public class StudioOverlay implements StudioKeyboardView.Host {
    private static final String PREF_DOCK_X = "dock_x";
    private static final String PREF_DOCK_Y = "dock_y";

    private final XServerDisplayActivity activity;
    private final FrameLayout root;
    private final TouchpadView touchpad;
    private final SharedPreferences prefs;
    private final float density;
    private final StudioKeyboardView keyboard;
    private final LinearLayout dock;
    private final LinearLayout mouseBar;
    private final ImageView btKeyboard;
    private final ImageView btMouse;
    private final TextView[] mouseButtons = new TextView[3];
    private Pointer.Button sticky = null;

    public StudioOverlay(XServerDisplayActivity activity, FrameLayout root, XServer xServer, TouchpadView touchpad) {
        this.activity = activity;
        this.root = root;
        this.touchpad = touchpad;
        this.prefs = StudioPrefs.get(activity);
        this.density = activity.getResources().getDisplayMetrics().density;

        keyboard = new StudioKeyboardView(activity, xServer);
        keyboard.setHost(this);
        keyboard.setHaptics(prefs.getBoolean(StudioPrefs.HAPTICS, true));
        keyboard.setAlpha(prefs.getInt(StudioPrefs.KEYBOARD_OPACITY, 85) / 100f);
        keyboard.setVisibility(View.GONE);
        float heightScale = prefs.getInt(StudioPrefs.KEYBOARD_HEIGHT, 100) / 100f;
        int keyboardHeight = (int)((30 + 5 * 35 + 7 * 3) * density * heightScale);
        FrameLayout.LayoutParams keyboardParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, keyboardHeight, Gravity.BOTTOM);
        int margin = dp(6);
        keyboardParams.setMargins(margin, 0, margin, margin);
        root.addView(keyboard, keyboardParams);

        mouseBar = new LinearLayout(activity);
        mouseBar.setOrientation(LinearLayout.HORIZONTAL);
        mouseBar.setVisibility(View.GONE);
        mouseBar.setBackground(pill(0xCC101418));
        mouseBar.setPadding(dp(4), dp(4), dp(4), dp(4));
        String[] labels = {"L", "M", "R"};
        Pointer.Button[] buttons = {Pointer.Button.BUTTON_LEFT, Pointer.Button.BUTTON_MIDDLE, Pointer.Button.BUTTON_RIGHT};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView chip = new TextView(activity);
            chip.setText(labels[i]);
            chip.setGravity(Gravity.CENTER);
            chip.setTextColor(Color.WHITE);
            chip.setTextSize(16);
            chip.setTypeface(chip.getTypeface(), android.graphics.Typeface.BOLD);
            chip.setOnClickListener(v -> toggleSticky(index, buttons[index]));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(42), dp(42));
            params.setMargins(dp(2), 0, dp(2), 0);
            mouseBar.addView(chip, params);
            mouseButtons[i] = chip;
        }
        root.addView(mouseBar, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END));

        dock = new LinearLayout(activity);
        dock.setOrientation(LinearLayout.VERTICAL);
        dock.setBackground(pill(0xB3101418));
        dock.setPadding(dp(3), dp(3), dp(3), dp(3));
        ImageView btMenu = dockButton(R.drawable.ic_more_vert, R.string.studio_session_menu);
        btKeyboard = dockButton(R.drawable.ic_keyboard, R.string.studio_session_keyboard);
        btMouse = dockButton(R.drawable.ic_mouse, R.string.studio_session_mouse);
        btMenu.setOnClickListener(v -> showMenu(v));
        btKeyboard.setOnClickListener(v -> toggleKeyboard());
        btMouse.setOnClickListener(v -> toggleMouseBar());
        dock.addView(btMenu);
        dock.addView(btKeyboard);
        dock.addView(btMouse);
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        root.addView(dock, dockParams);
        makeDraggable(dock, new View[]{btMenu, btKeyboard, btMouse});
        updateChips();

        dock.post(() -> {
            float x = prefs.getFloat(PREF_DOCK_X, -1);
            float y = prefs.getFloat(PREF_DOCK_Y, -1);
            if (x < 0 || y < 0) {
                x = root.getWidth() - dock.getWidth() - dp(10);
                y = dp(10);
            }
            placeDock(x, y);
        });
    }

    private int dp(int value) {
        return (int)(value * density);
    }

    private GradientDrawable pill(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(22));
        return drawable;
    }

    private ImageView dockButton(int icon, int description) {
        ImageView view = new ImageView(activity);
        view.setImageResource(icon);
        view.setColorFilter(Color.WHITE);
        view.setPadding(dp(10), dp(10), dp(10), dp(10));
        view.setContentDescription(activity.getString(description));
        view.setLayoutParams(new LinearLayout.LayoutParams(dp(46), dp(46)));
        return view;
    }

    private void placeDock(float x, float y) {
        float maxX = Math.max(0, root.getWidth() - dock.getWidth());
        float maxY = Math.max(0, root.getHeight() - dock.getHeight());
        x = Math.max(0, Math.min(x, maxX));
        y = Math.max(0, Math.min(y, maxY));
        dock.setX(x);
        dock.setY(y);
        updateMouseBarPosition();
    }

    private void updateMouseBarPosition() {
        mouseBar.post(() -> {
            float x = dock.getX() - mouseBar.getWidth() - dp(6);
            if (x < 0) x = dock.getX() + dock.getWidth() + dp(6);
            mouseBar.setX(x);
            mouseBar.setY(dock.getY() + dock.getHeight() - mouseBar.getHeight());
        });
    }

    /** Drag the dock by any of its buttons, a short tap still clicks. */
    private void makeDraggable(View dock, View[] handles) {
        final float slop = dp(8);
        for (View handle : handles) {
            handle.setOnTouchListener(new View.OnTouchListener() {
                float downX, downY, startX, startY;
                boolean dragging;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            downX = event.getRawX();
                            downY = event.getRawY();
                            startX = dock.getX();
                            startY = dock.getY();
                            dragging = false;
                            return false;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - downX;
                            float dy = event.getRawY() - downY;
                            if (!dragging && Math.hypot(dx, dy) > slop) dragging = true;
                            if (dragging) {
                                placeDock(startX + dx, startY + dy);
                                return true;
                            }
                            return false;
                        case MotionEvent.ACTION_UP:
                            if (dragging) {
                                prefs.edit().putFloat(PREF_DOCK_X, dock.getX()).putFloat(PREF_DOCK_Y, dock.getY()).apply();
                                dragging = false;
                                return true;
                            }
                            return false;
                    }
                    return false;
                }
            });
        }
    }

    private void toggleSticky(int index, Pointer.Button button) {
        sticky = sticky == button ? null : button;
        touchpad.setStickyButton(sticky);
        updateChips();
    }

    private void updateChips() {
        Pointer.Button[] buttons = {Pointer.Button.BUTTON_LEFT, Pointer.Button.BUTTON_MIDDLE, Pointer.Button.BUTTON_RIGHT};
        for (int i = 0; i < 3; i++) {
            GradientDrawable background = new GradientDrawable();
            background.setCornerRadius(dp(21));
            background.setColor(buttons[i] == sticky ? 0xFF0B84FF : 0xFF2B3138);
            mouseButtons[i].setBackground(background);
        }
        btMouse.setColorFilter(sticky != null ? 0xFF4DB0FF : Color.WHITE);
    }

    private void toggleMouseBar() {
        boolean show = mouseBar.getVisibility() != View.VISIBLE;
        mouseBar.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) updateMouseBarPosition();
        if (!show && sticky != null) {
            sticky = null;
            touchpad.setStickyButton(null);
            updateChips();
        }
    }

    public boolean isKeyboardVisible() {
        return keyboard.getVisibility() == View.VISIBLE;
    }

    public void toggleKeyboard() {
        if (isKeyboardVisible()) hideKeyboard();
        else showKeyboard();
    }

    public void showKeyboard() {
        keyboard.setVisibility(View.VISIBLE);
        keyboard.setTranslationY(keyboard.getHeight() > 0 ? keyboard.getHeight() : dp(200));
        keyboard.animate().translationY(0).setDuration(160).start();
        btKeyboard.setImageResource(R.drawable.ic_keyboard_hide);
    }

    public void hideKeyboard() {
        keyboard.releaseAll();
        keyboard.animate().translationY(keyboard.getHeight()).setDuration(140).withEndAction(() -> keyboard.setVisibility(View.GONE)).start();
        btKeyboard.setImageResource(R.drawable.ic_keyboard);
    }

    public void showMenu(View anchor) {
        PopupMenu menu = new PopupMenu(activity, anchor);
        menu.getMenu().add(0, 1, 0, R.string.studio_session_system_keyboard);
        boolean direct = touchpad.getTouchMode() == TouchpadView.MODE_DIRECT;
        menu.getMenu().add(0, 2, 1, activity.getString(R.string.studio_session_touch_mode)+": "+activity.getString(direct ? R.string.studio_session_direct : R.string.studio_session_touchpad));
        menu.getMenu().add(0, 3, 2, R.string.studio_session_fullscreen);
        menu.getMenu().add(0, 4, 3, R.string.studio_session_task_manager);
        menu.getMenu().add(0, 6, 4, R.string.studio_session_login_hint_title);
        menu.getMenu().add(0, 5, 5, R.string.studio_session_exit);
        menu.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    onSystemKeyboard();
                    break;
                case 2:
                    byte mode = direct ? TouchpadView.MODE_TOUCHPAD : TouchpadView.MODE_DIRECT;
                    touchpad.setTouchMode(mode);
                    prefs.edit().putString(StudioPrefs.TOUCH_MODE, mode == TouchpadView.MODE_DIRECT ? "direct" : "touchpad").apply();
                    break;
                case 3:
                    activity.toggleFullscreen();
                    break;
                case 4:
                    activity.showTaskManager();
                    break;
                case 5:
                    activity.confirmExit();
                    break;
                case 6:
                    AppUtils.showToast(activity, R.string.studio_session_login_hint);
                    break;
            }
            return true;
        });
        menu.show();
    }

    @Override
    public void onHideKeyboard() {
        hideKeyboard();
    }

    @Override
    public void onSystemKeyboard() {
        AppUtils.showKeyboard(activity);
    }
}
