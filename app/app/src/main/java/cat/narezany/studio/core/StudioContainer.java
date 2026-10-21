package cat.narezany.studio.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.winlator.box64.Box64Preset;
import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.container.DXWrappers;
import com.winlator.container.GraphicsDrivers;
import com.winlator.core.Callback;
import com.winlator.core.WineRegistryEditor;
import com.winlator.core.WineThemeManager;
import com.winlator.core.WineUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/** The single Wine container Studio lives in. */
public final class StudioContainer {
    private StudioContainer() {}

    public static Container find(Context context) {
        ContainerManager manager = new ContainerManager(context);
        int id = StudioPrefs.get(context).getInt(StudioPrefs.CONTAINER_ID, 0);
        Container container = id > 0 ? manager.getContainerById(id) : null;
        if (container == null && !manager.getContainers().isEmpty()) container = manager.getContainers().get(0);
        return container;
    }

    /** Must be called on a thread with a Looper, the callback arrives there. */
    public static void ensure(Context context, Callback<Container> callback) {
        Container existing = find(context);
        if (existing != null) {
            callback.call(existing);
            return;
        }

        try {
            JSONObject data = new JSONObject();
            data.put("name", "Roblox Studio");
            data.put("screenSize", ScreenSizes.resolve(context));
            data.put("envVars", Container.DEFAULT_ENV_VARS);
            data.put("graphicsDriver", GraphicsDrivers.getDefaultDriver(context));
            data.put("dxwrapper", DXWrappers.DXVK);
            data.put("dxwrapperConfig", "");
            data.put("graphicsDriverConfig", "");
            data.put("audioDriver", Container.DEFAULT_AUDIO_DRIVER);
            data.put("audioDriverConfig", "");
            data.put("wincomponents", Container.DEFAULT_WINCOMPONENTS);
            data.put("drives", Container.DEFAULT_DRIVES);
            data.put("hudMode", 0);
            data.put("startupSelection", Container.STARTUP_SELECTION_ESSENTIAL);
            data.put("box64Preset", Box64Preset.INTERMEDIATE);
            data.put("desktopTheme", WineThemeManager.DEFAULT_DESKTOP_THEME);

            new ContainerManager(context).createContainerAsync(data, (container) -> {
                if (container != null) {
                    StudioPrefs.get(context).edit().putInt(StudioPrefs.CONTAINER_ID, container.id).apply();
                }
                callback.call(container);
            });
        }
        catch (JSONException e) {
            callback.call(null);
        }
    }

    /** Pushes the launcher settings into the container before a session starts. */
    public static void applySettings(Context context, Container container) {
        SharedPreferences prefs = StudioPrefs.get(context);

        container.setScreenSize(ScreenSizes.resolve(context));

        String driver = prefs.getString(StudioPrefs.GRAPHICS_DRIVER, "auto");
        if (driver.equals("auto")) container.setGraphicsDriver(GraphicsDrivers.getDefaultDriver(context));
        else container.setGraphicsDriver(driver+","+GraphicsDrivers.DEFAULT_OPENGL_DRIVER);

        container.setBox64Preset(prefs.getString(StudioPrefs.BOX64_PRESET, Box64Preset.INTERMEDIATE));
        container.setHUDMode((byte)(prefs.getBoolean(StudioPrefs.SHOW_FPS, false) ? 1 : 0));
        container.saveData();

        applyUiScale(container, parseInt(prefs.getString(StudioPrefs.UI_SCALE, "96"), 96));
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        }
        catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static void applyUiScale(Container container, int dpi) {
        File userReg = new File(container.getRootDir(), ".wine/user.reg");
        if (!userReg.isFile()) return;
        try (WineRegistryEditor editor = new WineRegistryEditor(userReg)) {
            editor.setDwordValue("Control Panel\\Desktop", "LogPixels", dpi);
        }
    }

    /** Moves the installed Studio out, deletes the container and returns the stash dir (or null if nothing to keep). */
    public static File stashStudio(Context context, Container container) {
        File versions = StudioInstaller.getVersionsDir(container);
        if (!versions.isDirectory()) return null;
        File stash = new File(context.getFilesDir(), "versions-stash");
        com.winlator.core.FileUtils.delete(stash);
        return versions.renameTo(stash) ? stash : null;
    }

    public static void restoreStudio(Container container, File stash) {
        if (stash == null || !stash.isDirectory()) return;
        File versions = StudioInstaller.getVersionsDir(container);
        versions.getParentFile().mkdirs();
        com.winlator.core.FileUtils.delete(versions);
        stash.renameTo(versions);

        File[] installed = versions.listFiles();
        if (installed != null && installed.length > 0) registerProtocols(container, new File(installed[0], "RobloxStudioBeta.exe"));
    }

    public static void registerProtocols(Container container, File studioExe) {
        File userReg = new File(container.getRootDir(), ".wine/user.reg");
        if (!userReg.isFile()) return;

        String dosPath = WineUtils.unixToDOSPath(studioExe.getPath(), container);
        String command = "\""+dosPath+"\" \"%1\"";
        try (WineRegistryEditor editor = new WineRegistryEditor(userReg)) {
            for (String scheme : new String[]{"roblox-studio", "roblox-studio-auth"}) {
                String key = "Software\\Classes\\"+scheme;
                editor.setStringValue(key, null, "URL: "+scheme+" Protocol");
                editor.setStringValue(key, "URL Protocol", "");
                editor.setStringValue(key+"\\shell\\open\\command", null, command);
            }
        }
    }
}
