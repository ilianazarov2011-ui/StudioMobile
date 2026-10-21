package cat.narezany.studio.core;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.StatFs;

import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.core.FileUtils;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Downloads the Roblox Studio packages straight from Roblox's CDN and unpacks them into the Wine prefix. */
public class StudioInstaller {
    public interface Listener {
        void onStatus(int stringResId);
        void onProgress(long done, long total, long bytesPerSecond);
    }

    public static class Cancelled extends IOException {}

    /** Packages that are not needed to run Studio under Wine. */
    private static final String[] SKIPPED = {"RobloxStudioInstaller.exe", "WebView2RuntimeInstaller.zip", "redist.zip"};
    private static final int PARALLEL_DOWNLOADS = 3;
    private static final long UNPACKED_FACTOR_PERCENT = 280;

    private final Context context;
    private final Container container;
    private volatile boolean cancelled = false;

    public StudioInstaller(Context context, Container container) {
        this.context = context.getApplicationContext();
        this.container = container;
    }

    public void cancel() {
        cancelled = true;
    }

    public static File getVersionsDir(Container container) {
        return new File(container.getUserDir(), "AppData/Local/Roblox/Versions");
    }

    public static File getVersionDir(Container container, String guid) {
        return new File(getVersionsDir(container), guid);
    }

    public static File getStudioExe(Container container, String guid) {
        return new File(getVersionDir(container, guid), "RobloxStudioBeta.exe");
    }

    public static boolean isInstalled(Context context, Container container) {
        String guid = StudioPrefs.get(context).getString(StudioPrefs.INSTALLED_GUID, null);
        return guid != null && new File(getVersionDir(container, guid), ".installed").isFile() && getStudioExe(container, guid).isFile();
    }

    public static long getInstalledSize(Context context, Container container) {
        String guid = StudioPrefs.get(context).getString(StudioPrefs.INSTALLED_GUID, null);
        return guid != null ? dirSize(getVersionDir(container, guid)) : 0;
    }

    private static long dirSize(File file) {
        File[] children = file.listFiles();
        if (children == null) return file.length();
        long size = 0;
        for (File child : children) size += dirSize(child);
        return size;
    }

    private File getDownloadsDir() {
        File dir = new File(context.getFilesDir(), "downloads");
        dir.mkdirs();
        return dir;
    }

    public static boolean isSkipped(String name) {
        for (String skipped : SKIPPED) if (skipped.equals(name)) return true;
        return false;
    }

    public static class Plan {
        public final RobloxApi.Deployment deployment;
        public final List<RobloxApi.Pkg> packages;
        public final long downloadSize;
        public final long unpackedSize;

        Plan(RobloxApi.Deployment deployment, List<RobloxApi.Pkg> packages) {
            this.deployment = deployment;
            this.packages = packages;
            long zip = 0, size = 0;
            for (RobloxApi.Pkg pkg : packages) {
                zip += pkg.zipSize;
                size += pkg.size;
            }
            this.downloadSize = zip;
            this.unpackedSize = size;
        }
    }

    public static Plan plan(RobloxApi.Deployment deployment) throws IOException {
        List<RobloxApi.Pkg> packages = RobloxApi.parseManifest(RobloxApi.getPackageFile(deployment.guid, "rbxPkgManifest.txt"));
        List<RobloxApi.Pkg> wanted = new ArrayList<>();
        for (RobloxApi.Pkg pkg : packages) if (!isSkipped(pkg.name)) wanted.add(pkg);
        return new Plan(deployment, wanted);
    }

    private Map<String, String> loadPackageDirs(String guid) {
        try {
            Map<String, String> dirs = PackageDirs.scan(RobloxApi.getPackageFile(guid, "RobloxStudioInstaller.exe"));
            if (dirs != null) return dirs;
        }
        catch (IOException e) {}

        try (InputStream in = context.getResources().openRawResource(R.raw.package_dirs)) {
            byte[] data = new byte[in.available()];
            int offset = 0;
            while (offset < data.length) {
                int n = in.read(data, offset, data.length - offset);
                if (n < 0) break;
                offset += n;
            }
            return PackageDirs.parse(new String(data, 0, offset));
        }
        catch (IOException e) {
            return null;
        }
    }

    public void install(Plan plan, Listener listener) throws IOException {
        final String guid = plan.deployment.guid;
        File versionDir = getVersionDir(container, guid);
        File doneDir = new File(versionDir, ".done");

        StatFs stat = new StatFs(context.getFilesDir().getPath());
        long needed = plan.downloadSize + plan.unpackedSize * UNPACKED_FACTOR_PERCENT / 100 / 2;
        if (stat.getAvailableBytes() < needed) throw new NoSpaceException(needed);

        listener.onStatus(R.string.studio_status_preparing);
        Map<String, String> dirs = loadPackageDirs(guid);
        if (dirs == null) throw new IOException("Unable to read the package layout");
        checkCancelled();

        versionDir.mkdirs();
        doneDir.mkdirs();

        final long total = plan.downloadSize;
        final AtomicLong done = new AtomicLong();
        final long startTime = System.currentTimeMillis();
        final long[] lastReport = {0};

        List<RobloxApi.Pkg> pending = new ArrayList<>();
        for (RobloxApi.Pkg pkg : plan.packages) {
            if (new File(doneDir, pkg.name).isFile()) done.addAndGet(pkg.zipSize);
            else pending.add(pkg);
        }
        // RobloxStudio.zip holds the exe, so it goes last: a half-finished install has no exe
        Collections.sort(pending, (a, b) -> {
            boolean aMain = a.name.equals("RobloxStudio.zip"), bMain = b.name.equals("RobloxStudio.zip");
            if (aMain != bMain) return aMain ? 1 : -1;
            return Long.compare(a.zipSize, b.zipSize);
        });

        listener.onStatus(R.string.studio_status_downloading);
        final long alreadyDone = done.get();
        Runnable report = () -> {
            long now = System.currentTimeMillis();
            synchronized (lastReport) {
                if (now - lastReport[0] < 250) return;
                lastReport[0] = now;
            }
            long elapsed = Math.max(1, now - startTime);
            long speed = (done.get() - alreadyDone) * 1000 / elapsed;
            listener.onProgress(done.get(), total, speed);
        };
        report.run();

        ExecutorService pool = Executors.newFixedThreadPool(PARALLEL_DOWNLOADS);
        List<Future<?>> futures = new ArrayList<>();
        // The big exe package must wait for everything else
        List<RobloxApi.Pkg> mainPackages = new ArrayList<>();
        for (RobloxApi.Pkg pkg : pending) {
            if (pkg.name.equals("RobloxStudio.zip")) {
                mainPackages.add(pkg);
                continue;
            }
            futures.add(pool.submit(() -> {
                processPackage(guid, pkg, dirs, versionDir, doneDir, done, report);
                return null;
            }));
        }

        try {
            for (Future<?> future : futures) waitFor(future);
            for (RobloxApi.Pkg pkg : mainPackages) {
                Future<?> future = pool.submit(() -> {
                    processPackage(guid, pkg, dirs, versionDir, doneDir, done, report);
                    return null;
                });
                waitFor(future);
            }
        }
        finally {
            pool.shutdownNow();
        }

        listener.onProgress(total, total, 0);
        listener.onStatus(R.string.studio_status_finishing);
        finishInstall(plan, versionDir);
    }

    private void waitFor(Future<?> future) throws IOException {
        try {
            future.get();
        }
        catch (java.util.concurrent.ExecutionException e) {
            cancelled = true;
            Throwable cause = e.getCause();
            if (cause instanceof IOException) throw (IOException)cause;
            throw new IOException(cause);
        }
        catch (InterruptedException e) {
            cancelled = true;
            throw new Cancelled();
        }
    }

    private void checkCancelled() throws Cancelled {
        if (cancelled) throw new Cancelled();
    }

    private void processPackage(String guid, RobloxApi.Pkg pkg, Map<String, String> dirs, File versionDir, File doneDir, AtomicLong done, Runnable report) throws IOException {
        String dir = dirs.get(pkg.name);
        if (dir == null) {
            // unknown package: nothing to do with it
            done.addAndGet(pkg.zipSize);
            report.run();
            return;
        }

        File zip = new File(getDownloadsDir(), pkg.md5+".zip");
        if (!zip.isFile() || !md5(zip).equalsIgnoreCase(pkg.md5)) {
            zip.delete();
            download(guid, pkg, zip, done, report);
        }
        else done.addAndGet(pkg.zipSize);

        checkCancelled();
        unzip(zip, new File(versionDir, dir));
        zip.delete();
        new File(doneDir, pkg.name).createNewFile();
        report.run();
    }

    private void download(String guid, RobloxApi.Pkg pkg, File target, AtomicLong done, Runnable report) throws IOException {
        File part = new File(target.getPath()+".part");
        IOException lastError = null;

        for (int attempt = 0; attempt < 6; attempt++) {
            String mirror = RobloxApi.MIRRORS[attempt % RobloxApi.MIRRORS.length];
            long offset = part.isFile() ? part.length() : 0;
            if (attempt == 0) done.addAndGet(offset);

            try {
                HttpURLConnection connection = RobloxApi.open(RobloxApi.packageUrl(mirror, guid, pkg.name));
                if (offset > 0) connection.setRequestProperty("Range", "bytes="+offset+"-");
                int code = connection.getResponseCode();
                if (code == 200 && offset > 0) {
                    // server ignored the range
                    done.addAndGet(-offset);
                    offset = 0;
                    part.delete();
                }
                else if (code != 200 && code != 206) throw new IOException("HTTP "+code+" for "+pkg.name);

                try (InputStream in = connection.getInputStream();
                     OutputStream out = new FileOutputStream(part, offset > 0)) {
                    byte[] buffer = new byte[1 << 17];
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        checkCancelled();
                        out.write(buffer, 0, n);
                        done.addAndGet(n);
                        report.run();
                    }
                }
                finally {
                    connection.disconnect();
                }

                if (md5(part).equalsIgnoreCase(pkg.md5) && part.renameTo(target)) return;

                done.addAndGet(-(part.length()));
                part.delete();
                lastError = new IOException("Checksum mismatch for "+pkg.name);
            }
            catch (Cancelled e) {
                throw e;
            }
            catch (IOException e) {
                lastError = e;
                // keep what we have for resuming; it's already counted in done
                try {
                    Thread.sleep(1000L * (attempt + 1));
                }
                catch (InterruptedException ie) {
                    throw new Cancelled();
                }
            }
        }

        throw lastError != null ? lastError : new IOException("Download failed: "+pkg.name);
    }

    private static String md5(File file) throws IOException {
        try (InputStream in = new FileInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[1 << 17];
            int n;
            while ((n = in.read(buffer)) != -1) digest.update(buffer, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) sb.append(String.format(Locale.ENGLISH, "%02x", b));
            return sb.toString();
        }
        catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    private void unzip(File zip, File destination) throws IOException {
        destination.mkdirs();
        String basePath = destination.getCanonicalPath()+File.separator;

        try (ZipFile zipFile = new ZipFile(zip)) {
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            byte[] buffer = new byte[1 << 17];
            while (entries.hasMoreElements()) {
                checkCancelled();
                ZipEntry entry = entries.nextElement();
                // Roblox zips use backslashes
                String name = entry.getName().replace('\\', '/');
                File file = new File(destination, name);
                if (!file.getCanonicalPath().startsWith(basePath) && !file.getCanonicalPath().equals(destination.getCanonicalPath())) {
                    throw new IOException("Illegal path in package: "+name);
                }

                if (entry.isDirectory() || name.endsWith("/")) {
                    file.mkdirs();
                    continue;
                }

                File parent = file.getParentFile();
                if (parent != null) parent.mkdirs();
                try (InputStream in = zipFile.getInputStream(entry); OutputStream out = new FileOutputStream(file)) {
                    int n;
                    while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                }
                file.setExecutable(true, false);
            }
        }
    }

    private void finishInstall(Plan plan, File versionDir) throws IOException {
        if (!new File(versionDir, "RobloxStudioBeta.exe").isFile()) throw new IOException("RobloxStudioBeta.exe is missing after install");

        FileUtils.writeString(new File(versionDir, "AppSettings.xml"),
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n<Settings>\r\n        <ContentFolder>content</ContentFolder>\r\n        <BaseUrl>http://www.roblox.com</BaseUrl>\r\n</Settings>\r\n");

        StudioFFlags.write(context, versionDir);

        try {
            JSONObject marker = new JSONObject();
            marker.put("guid", plan.deployment.guid);
            marker.put("version", plan.deployment.version);
            marker.put("time", System.currentTimeMillis());
            FileUtils.writeString(new File(versionDir, ".installed"), marker.toString());
        }
        catch (org.json.JSONException e) {
            throw new IOException(e);
        }
        FileUtils.delete(new File(versionDir, ".done"));

        SharedPreferences.Editor editor = StudioPrefs.get(context).edit();
        editor.putString(StudioPrefs.INSTALLED_GUID, plan.deployment.guid);
        editor.putString(StudioPrefs.INSTALLED_VERSION, plan.deployment.version);
        editor.apply();

        removeOldVersions(plan.deployment.guid);
        StudioContainer.registerProtocols(container, getStudioExe(container, plan.deployment.guid));
    }

    private void removeOldVersions(String keepGuid) {
        File[] versions = getVersionsDir(container).listFiles();
        if (versions == null) return;
        for (File version : versions) if (!version.getName().equals(keepGuid)) FileUtils.delete(version);
    }

    public static void uninstall(Context context, Container container) {
        FileUtils.delete(getVersionsDir(container));
        FileUtils.delete(new File(context.getFilesDir(), "downloads"));
        StudioPrefs.get(context).edit().remove(StudioPrefs.INSTALLED_GUID).remove(StudioPrefs.INSTALLED_VERSION).apply();
    }

    public static class NoSpaceException extends IOException {
        public final long needed;

        NoSpaceException(long needed) {
            super("Not enough free space");
            this.needed = needed;
        }
    }
}
