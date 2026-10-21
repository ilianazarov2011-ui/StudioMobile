package cat.narezany.studio.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.DisplayMetrics;

public final class ScreenSizes {
    public static final String AUTO = "auto";

    private ScreenSizes() {}

    /** Height in Wine pixels for the given setting, width follows the phone's aspect ratio. */
    public static String resolve(Context context) {
        SharedPreferences prefs = StudioPrefs.get(context);
        String value = prefs.getString(StudioPrefs.RESOLUTION, "720");
        if (value.contains("x")) return value;

        int height = 720;
        try {
            height = Integer.parseInt(value);
        }
        catch (NumberFormatException e) {}

        DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        float aspect = (float)Math.max(metrics.widthPixels, metrics.heightPixels) / Math.min(metrics.widthPixels, metrics.heightPixels);
        aspect = Math.min(aspect, 2.4f);
        int width = Math.round(height * aspect / 8f) * 8;
        width = Math.max(width, 960);
        return width+"x"+height;
    }
}
