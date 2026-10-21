package cat.narezany.studio;

import android.app.Application;

import androidx.appcompat.app.AppCompatDelegate;

import cat.narezany.studio.core.StudioPrefs;

public class StudioApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        applyNightMode(this);
    }

    public static void applyNightMode(android.content.Context context) {
        String mode = StudioPrefs.get(context).getString(StudioPrefs.THEME_MODE, "system");
        switch (mode) {
            case "light":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
            case "dark":
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            default:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        }
    }
}
