package cat.narezany.studio.core;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;

import com.winlator.container.Container;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import cat.narezany.studio.session.InstallService;

/** Process-wide owner of the setup and download jobs, so they survive screen rotation. */
public final class InstallManager {
    public enum Phase {IDLE, RUNTIME, CHECKING, DOWNLOAD, DONE, FAILED}

    public static final class State {
        public Phase phase = Phase.IDLE;
        public int statusResId;
        public int percent;
        public long done, total, speed;
        public String error;
        public long neededBytes;
        public RobloxApi.Deployment latest;
        public long latestDownloadSize;

        State copy() {
            State s = new State();
            s.phase = phase;
            s.statusResId = statusResId;
            s.percent = percent;
            s.done = done;
            s.total = total;
            s.speed = speed;
            s.error = error;
            s.neededBytes = neededBytes;
            s.latest = latest;
            s.latestDownloadSize = latestDownloadSize;
            return s;
        }
    }

    public interface Listener {
        void onInstallState(State state);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Listener> LISTENERS = new ArrayList<>();
    private static State state = new State();
    private static StudioInstaller installer;
    private static Thread worker;

    private InstallManager() {}

    public static synchronized State get() {
        return state.copy();
    }

    public static synchronized boolean isBusy() {
        return state.phase == Phase.RUNTIME || state.phase == Phase.DOWNLOAD || state.phase == Phase.CHECKING;
    }

    public static void addListener(Listener listener) {
        synchronized (LISTENERS) {
            if (!LISTENERS.contains(listener)) LISTENERS.add(listener);
        }
        listener.onInstallState(get());
    }

    public static void removeListener(Listener listener) {
        synchronized (LISTENERS) {
            LISTENERS.remove(listener);
        }
    }

    private static void publish() {
        final State snapshot = get();
        MAIN.post(() -> {
            List<Listener> copy;
            synchronized (LISTENERS) {
                copy = new ArrayList<>(LISTENERS);
            }
            for (Listener listener : copy) listener.onInstallState(snapshot);
        });
    }

    private static synchronized void update(java.util.function.Consumer<State> change) {
        change.accept(state);
        publish();
    }

    /** Clears DONE or FAILED once the UI has looked at it. */
    public static synchronized void acknowledge() {
        if (state.phase == Phase.DONE || state.phase == Phase.FAILED) {
            RobloxApi.Deployment latest = state.latest;
            long latestSize = state.latestDownloadSize;
            state = new State();
            state.latest = latest;
            state.latestDownloadSize = latestSize;
            publish();
        }
    }

    private static void startService(Context context, int titleResId) {
        Intent intent = new Intent(context, InstallService.class);
        intent.putExtra(InstallService.EXTRA_TITLE, titleResId);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent);
        else context.startService(intent);
    }

    /** Unpacks the runtime if needed and makes sure the Wine container exists. */
    public static synchronized void startRuntime(Context context) {
        startRuntime(context, false);
    }

    /** @param reset throw the Wine container away first, keeping the installed Studio */
    public static synchronized void startRuntime(Context context, boolean reset) {
        if (isBusy()) return;
        final Context app = context.getApplicationContext();
        state = new State();
        state.phase = Phase.RUNTIME;
        state.statusResId = com.winlator.R.string.studio_runtime_title;
        publish();
        startService(app, com.winlator.R.string.studio_notification_runtime);

        worker = new Thread(() -> {
            try {
                java.io.File stash = null;
                if (reset) {
                    Container old = StudioContainer.find(app);
                    if (old != null) {
                        stash = StudioContainer.stashStudio(app, old);
                        com.winlator.core.FileUtils.delete(old.getRootDir());
                    }
                    StudioPrefs.get(app).edit().remove(StudioPrefs.CONTAINER_ID).apply();
                }

                if (!RuntimeSetup.isReady(app)) {
                    CountDownLatch latch = new CountDownLatch(1);
                    boolean[] ok = {false};
                    RuntimeSetup.install(app, new RuntimeSetup.Listener() {
                        @Override
                        public void onProgress(int percent) {
                            update(s -> s.percent = percent);
                        }

                        @Override
                        public void onFinished(boolean success) {
                            ok[0] = success;
                            latch.countDown();
                        }
                    });
                    latch.await();
                    if (!ok[0]) throw new IOException("Could not unpack the system files");
                }

                update(s -> {
                    s.statusResId = com.winlator.R.string.studio_container_creating;
                    s.percent = 100;
                });

                CountDownLatch latch = new CountDownLatch(1);
                Container[] created = {null};
                MAIN.post(() -> StudioContainer.ensure(app, (container) -> {
                    created[0] = container;
                    latch.countDown();
                }));
                latch.await();
                if (created[0] == null) throw new IOException("Could not create the Windows environment");
                StudioContainer.restoreStudio(created[0], stash);

                update(s -> s.phase = Phase.DONE);
            }
            catch (Exception e) {
                fail(e);
            }
        }, "studio-setup");
        worker.start();
    }

    /** Looks up the newest Studio build without downloading it. */
    public static synchronized void checkLatest(Context context) {
        if (isBusy()) return;
        final Context app = context.getApplicationContext();
        new Thread(() -> {
            try {
                RobloxApi.Deployment latest = RobloxApi.latest();
                StudioInstaller.Plan plan = StudioInstaller.plan(latest);
                update(s -> {
                    s.latest = latest;
                    s.latestDownloadSize = plan.downloadSize;
                });
            }
            catch (IOException e) {
                update(s -> s.latest = null);
            }
        }, "studio-check").start();
    }

    public static synchronized void startStudio(Context context) {
        if (isBusy()) return;
        final Context app = context.getApplicationContext();
        RobloxApi.Deployment previousLatest = state.latest;
        state = new State();
        state.latest = previousLatest;
        state.phase = Phase.DOWNLOAD;
        state.statusResId = com.winlator.R.string.studio_status_preparing;
        publish();
        startService(app, com.winlator.R.string.studio_notification_download);

        worker = new Thread(() -> {
            try {
                Container container = StudioContainer.find(app);
                if (container == null) throw new IOException("No container");

                RobloxApi.Deployment latest = RobloxApi.latest();
                StudioInstaller.Plan plan = StudioInstaller.plan(latest);
                update(s -> {
                    s.latest = latest;
                    s.latestDownloadSize = plan.downloadSize;
                    s.total = plan.downloadSize;
                });

                StudioInstaller job = new StudioInstaller(app, container);
                synchronized (InstallManager.class) {
                    installer = job;
                }
                job.install(plan, new StudioInstaller.Listener() {
                    @Override
                    public void onStatus(int stringResId) {
                        update(s -> s.statusResId = stringResId);
                    }

                    @Override
                    public void onProgress(long done, long total, long speed) {
                        update(s -> {
                            s.done = done;
                            s.total = total;
                            s.speed = speed;
                            s.percent = total > 0 ? (int)(done * 100 / total) : 0;
                        });
                    }
                });

                update(s -> s.phase = Phase.DONE);
            }
            catch (StudioInstaller.Cancelled e) {
                update(s -> s.phase = Phase.IDLE);
            }
            catch (Exception e) {
                fail(e);
            }
            finally {
                synchronized (InstallManager.class) {
                    installer = null;
                }
            }
        }, "studio-install");
        worker.start();
    }

    public static synchronized void cancel() {
        if (installer != null) installer.cancel();
    }

    private static void fail(Exception e) {
        update(s -> {
            s.phase = Phase.FAILED;
            s.error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (e instanceof StudioInstaller.NoSpaceException) s.neededBytes = ((StudioInstaller.NoSpaceException)e).needed;
        });
    }
}
