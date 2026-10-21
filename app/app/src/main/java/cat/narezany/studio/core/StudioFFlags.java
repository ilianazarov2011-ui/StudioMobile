package cat.narezany.studio.core;

import android.content.Context;
import android.content.SharedPreferences;

import com.winlator.core.FileUtils;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/** Writes ClientSettings/ClientAppSettings.json for the installed Studio. */
public final class StudioFFlags {
    public static final String RENDERER_D3D11 = "D3D11";
    public static final String RENDERER_VULKAN = "Vulkan";
    public static final String RENDERER_OPENGL = "OpenGL";
    private static final String[] RENDERERS = {"D3D11", "Vulkan", "D3D11FL10", "OpenGL"};

    private StudioFFlags() {}

    public static void write(Context context, File versionDir) {
        SharedPreferences prefs = StudioPrefs.get(context);
        String renderer = prefs.getString(StudioPrefs.ROBLOX_RENDERER, RENDERER_D3D11);

        try {
            JSONObject flags = new JSONObject();
            for (String name : RENDERERS) {
                boolean selected = name.equals(renderer);
                flags.put("FFlagDebugGraphicsPrefer"+name, selected);
                flags.put("FFlagDebugGraphicsDisable"+name, !selected);
            }

            String custom = prefs.getString(StudioPrefs.CUSTOM_FFLAGS, "");
            if (!custom.trim().isEmpty()) {
                JSONObject extra = new JSONObject(custom);
                for (java.util.Iterator<String> it = extra.keys(); it.hasNext(); ) {
                    String key = it.next();
                    flags.put(key, extra.get(key));
                }
            }

            File dir = new File(versionDir, "ClientSettings");
            dir.mkdirs();
            FileUtils.writeString(new File(dir, "ClientAppSettings.json"), flags.toString(2));
        }
        catch (JSONException e) {}
    }

    public static boolean isValidCustom(String text) {
        if (text == null || text.trim().isEmpty()) return true;
        try {
            new JSONObject(text);
            return true;
        }
        catch (JSONException e) {
            return false;
        }
    }
}
