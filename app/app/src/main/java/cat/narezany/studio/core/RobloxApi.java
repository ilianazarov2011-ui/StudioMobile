package cat.narezany.studio.core;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** Talks to the public Roblox deployment endpoints. */
public final class RobloxApi {
    public static final String VERSION_URL = "https://clientsettingscdn.roblox.com/v2/client-version/WindowsStudio64";
    public static final String[] MIRRORS = {
        "https://setup.rbxcdn.com",
        "https://roblox-setup.cachefly.net",
        "https://s3.amazonaws.com/setup.roblox.com"
    };
    public static final String USER_AGENT = "RobloxStudioMobile/1.0";

    private RobloxApi() {}

    public static class Deployment {
        public final String guid;
        public final String version;

        Deployment(String guid, String version) {
            this.guid = guid;
            this.version = version;
        }
    }

    public static class Pkg {
        public final String name;
        public final String md5;
        public final long zipSize;
        public final long size;

        Pkg(String name, String md5, long zipSize, long size) {
            this.name = name;
            this.md5 = md5;
            this.zipSize = zipSize;
            this.size = size;
        }
    }

    public static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection)new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", USER_AGENT);
        return connection;
    }

    public static byte[] getBytes(String url) throws IOException {
        HttpURLConnection connection = open(url);
        try {
            int code = connection.getResponseCode();
            if (code != 200) throw new IOException("HTTP "+code+" for "+url);
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream(1 << 16);
                byte[] buffer = new byte[1 << 16];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                return out.toByteArray();
            }
        }
        finally {
            connection.disconnect();
        }
    }

    public static Deployment latest() throws IOException {
        try {
            JSONObject json = new JSONObject(new String(getBytes(VERSION_URL)));
            return new Deployment(json.getString("clientVersionUpload"), json.optString("version", ""));
        }
        catch (JSONException e) {
            throw new IOException("Bad version response", e);
        }
    }

    public static String packageUrl(String mirror, String guid, String name) {
        return mirror+"/channel/common/"+guid+"-"+name;
    }

    /** Tries each mirror until one has the file. */
    public static byte[] getPackageFile(String guid, String name) throws IOException {
        IOException last = null;
        for (String mirror : MIRRORS) {
            try {
                return getBytes(packageUrl(mirror, guid, name));
            }
            catch (IOException e) {
                last = e;
            }
        }
        throw last != null ? last : new IOException("No mirror");
    }

    public static List<Pkg> parseManifest(byte[] data) throws IOException {
        String[] lines = new String(data).split("\r?\n");
        if (lines.length < 5 || !lines[0].trim().equals("v0")) throw new IOException("Unsupported package manifest");

        List<Pkg> packages = new ArrayList<>();
        try {
            for (int i = 1; i + 3 < lines.length; i += 4) {
                packages.add(new Pkg(lines[i].trim(), lines[i + 1].trim(), Long.parseLong(lines[i + 2].trim()), Long.parseLong(lines[i + 3].trim())));
            }
        }
        catch (NumberFormatException e) {
            throw new IOException("Bad package manifest", e);
        }
        return packages;
    }
}
