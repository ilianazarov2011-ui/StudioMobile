package cat.narezany.studio.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Where each Roblox package goes inside a version folder. Roblox keeps the map inside
 * RobloxStudioInstaller.exe as a JSON blob between two zero bytes.
 */
public final class PackageDirs {
    private PackageDirs() {}

    public static Map<String, String> scan(byte[] exe) {
        int start = 0;
        for (int i = 1; i < exe.length - 1; i++) {
            if (exe[i] == '{' && exe[i + 1] == '"' && exe[i - 1] == 0) start = i;

            if (start > 0 && exe[i] == 0 && exe[i - 1] == '}' && exe[i - 2] == '"') {
                Map<String, String> result = parse(new String(exe, start, i - start));
                if (result != null) return result;
                start = 0;
            }
        }
        return null;
    }

    public static Map<String, String> parse(String json) {
        try {
            JSONObject object = new JSONObject(json);
            Map<String, String> map = new HashMap<>();
            for (Iterator<String> it = object.keys(); it.hasNext(); ) {
                String name = it.next();
                String dir = object.getString(name).replace('\\', '/');
                while (dir.endsWith("/")) dir = dir.substring(0, dir.length() - 1);
                map.put(name, dir);
            }
            return map.isEmpty() ? null : map;
        }
        catch (JSONException e) {
            return null;
        }
    }
}
