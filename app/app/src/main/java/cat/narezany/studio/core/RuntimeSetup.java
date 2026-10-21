package cat.narezany.studio.core;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.preference.PreferenceManager;

import com.winlator.container.Container;
import com.winlator.container.ContainerManager;
import com.winlator.core.DefaultVersion;
import com.winlator.core.FileUtils;
import com.winlator.core.TarCompressorUtils;
import com.winlator.core.WineInfo;
import com.winlator.xenvironment.RootFS;
import com.winlator.xenvironment.RootFSInstaller;

import java.io.File;
import java.util.concurrent.atomic.AtomicLong;

/** Unpacks the bundled Linux userland (glibc, Wine, Box64) on first launch. */
public final class RuntimeSetup {
    public interface Listener {
        void onProgress(int percent);
        void onFinished(boolean success);
    }

    private RuntimeSetup() {}

    public static boolean isReady(Context context) {
        RootFS rootFS = RootFS.find(context);
        return rootFS.isValid() && rootFS.getVersion() >= RootFSInstaller.LATEST_VERSION;
    }

    public static void install(Context context, Listener listener) {
        new Thread(() -> {
            RootFS rootFS = RootFS.find(context);
            File rootDir = rootFS.getRootDir();

            SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(context).edit();
            editor.putString("box64_version", DefaultVersion.BOX64);
            editor.remove("current_box64_version");
            editor.remove("current_graphics_driver");
            editor.apply();

            clearRootDir(rootDir);
            final long contentLength = TarCompressorUtils.getContentLength(TarCompressorUtils.Type.ZSTD, context, RootFSInstaller.FILENAME, rootDir);
            AtomicLong totalSize = new AtomicLong();
            int[] lastPercent = {-1};

            boolean success = TarCompressorUtils.extract(TarCompressorUtils.Type.ZSTD, context, RootFSInstaller.FILENAME, rootDir, (file, size) -> {
                if (size > 0) {
                    int percent = (int)(totalSize.addAndGet(size) * 100 / Math.max(1, contentLength));
                    if (percent != lastPercent[0]) {
                        lastPercent[0] = percent;
                        listener.onProgress(Math.min(percent, 100));
                    }
                }
                return file;
            });

            if (success) {
                rootFS.createRFSVersionFile(RootFSInstaller.LATEST_VERSION);
                resetContainerVersions(context);
            }
            listener.onFinished(success);
        }, "runtime-setup").start();
    }

    private static void resetContainerVersions(Context context) {
        ContainerManager manager = new ContainerManager(context);
        for (Container container : manager.getContainers()) {
            String rfsVersion = container.getExtra("rfsVersion");
            String wineVersion = container.getWineVersion();
            if (!rfsVersion.isEmpty() && WineInfo.isMainWineVersion(wineVersion) && Short.parseShort(rfsVersion) <= RootFSInstaller.UPDATE_WINEPREFIX_VERSION) {
                container.putExtra("wineprefixNeedsUpdate", "t");
            }
            container.putExtra("rfsVersion", null);
            container.saveData();
        }
    }

    private static void clearRootDir(File rootDir) {
        if (!rootDir.isDirectory()) {
            rootDir.mkdirs();
            return;
        }

        File[] files = rootDir.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (file.isDirectory() && (name.equals("home") || name.equals("opt"))) {
                if (name.equals("opt")) {
                    File[] optFiles = file.listFiles();
                    if (optFiles != null) for (File opt : optFiles) if (!opt.getName().equals("installed-wine")) FileUtils.delete(opt);
                }
                continue;
            }
            FileUtils.delete(file);
        }
    }
}
