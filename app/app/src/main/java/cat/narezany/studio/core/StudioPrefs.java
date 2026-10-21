package cat.narezany.studio.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

public final class StudioPrefs {
    public static final String INSTALLED_GUID = "studio_guid";
    public static final String INSTALLED_VERSION = "studio_version";
    public static final String CONTAINER_ID = "studio_container_id";
    public static final String RESOLUTION = "resolution";
    public static final String UI_SCALE = "ui_scale";
    public static final String GRAPHICS_DRIVER = "graphics_driver_choice";
    public static final String ROBLOX_RENDERER = "roblox_renderer";
    public static final String BOX64_PRESET = "box64_preset_choice";
    public static final String TOUCH_MODE = "touch_mode";
    public static final String CURSOR_SPEED = "cursor_speed_pct";
    public static final String KEYBOARD_OPACITY = "keyboard_opacity";
    public static final String KEYBOARD_HEIGHT = "keyboard_height";
    public static final String HAPTICS = "haptics";
    public static final String DYNAMIC_COLOR = "dynamic_color";
    public static final String THEME_MODE = "theme_mode";
    public static final String SHOW_FPS = "show_fps";
    public static final String WINE_LOGS = "enable_wine_debug";
    public static final String CUSTOM_FFLAGS = "custom_fflags";

    private StudioPrefs() {}

    public static SharedPreferences get(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context);
    }
}
