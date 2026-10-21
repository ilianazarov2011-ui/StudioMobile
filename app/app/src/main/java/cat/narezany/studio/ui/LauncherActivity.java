package cat.narezany.studio.ui;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.Formatter;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.winlator.R;
import com.winlator.container.Container;

import cat.narezany.studio.core.InstallManager;
import cat.narezany.studio.core.RuntimeSetup;
import cat.narezany.studio.core.StudioContainer;
import cat.narezany.studio.core.StudioInstaller;
import cat.narezany.studio.core.StudioLauncher;
import cat.narezany.studio.core.StudioPrefs;

public class LauncherActivity extends AppCompatActivity implements InstallManager.Listener {
    private View root;
    private MaterialButton btMain, btSecondary, btCancel;
    private TextView tvStatusTitle, tvStatusText, tvProgress;
    private ImageView ivStatus;
    private View progressGroup;
    private LinearProgressIndicator progressBar;
    private CircularProgressIndicator logoRing;
    private View logo;
    private boolean setupTried = false;
    private boolean checkedUpdates = false;
    private boolean updateChecking = false;
    private InstallManager.State lastState = new InstallManager.State();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (StudioPrefs.get(this).getBoolean(StudioPrefs.DYNAMIC_COLOR, true)) DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_launcher);

        root = findViewById(R.id.Root);
        btMain = findViewById(R.id.BTMain);
        btSecondary = findViewById(R.id.BTSecondary);
        btCancel = findViewById(R.id.BTCancel);
        tvStatusTitle = findViewById(R.id.TVStatusTitle);
        tvStatusText = findViewById(R.id.TVStatusText);
        tvProgress = findViewById(R.id.TVProgress);
        ivStatus = findViewById(R.id.IVStatus);
        progressGroup = findViewById(R.id.ProgressGroup);
        progressBar = findViewById(R.id.ProgressBar);
        logoRing = findViewById(R.id.LogoRing);
        logo = findViewById(R.id.Logo);

        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        updateBarIcons();

        findViewById(R.id.BTSettings).setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        findViewById(R.id.BTAbout).setOnClickListener(v -> AboutDialog.show(this));
        btCancel.setOnClickListener(v -> InstallManager.cancel());
    }

    private void updateBarIcons() {
        boolean night = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);
    }

    @Override
    protected void onStart() {
        super.onStart();
        InstallManager.addListener(this);
    }

    @Override
    protected void onStop() {
        InstallManager.removeListener(this);
        super.onStop();
    }

    @Override
    public void onInstallState(InstallManager.State state) {
        lastState = state;
        refresh();
    }

    private void refresh() {
        InstallManager.State state = lastState;
        boolean ready = RuntimeSetup.isReady(this);
        Container container = ready ? StudioContainer.find(this) : null;
        boolean installed = container != null && StudioInstaller.isInstalled(this, container);

        boolean busy = state.phase == InstallManager.Phase.RUNTIME || state.phase == InstallManager.Phase.DOWNLOAD;
        progressGroup.setVisibility(busy ? View.VISIBLE : View.GONE);
        logoRing.setVisibility(busy ? View.VISIBLE : View.INVISIBLE);
        btCancel.setVisibility(state.phase == InstallManager.Phase.DOWNLOAD ? View.VISIBLE : View.GONE);

        if (state.phase == InstallManager.Phase.RUNTIME) {
            showStatus(R.drawable.ic_phone, getString(state.statusResId == 0 ? R.string.studio_runtime_title : state.statusResId), getString(R.string.studio_runtime_text));
            boolean indeterminate = state.statusResId != R.string.studio_runtime_title || state.percent <= 0;
            setProgress(indeterminate, state.percent);
            tvProgress.setText(indeterminate ? "" : getString(R.string.studio_runtime_progress, state.percent));
            setButtons(false, 0, null, false, 0, null);
            return;
        }

        if (state.phase == InstallManager.Phase.DOWNLOAD) {
            String detail = state.total > 0
                ? getString(R.string.studio_download_progress, Formatter.formatShortFileSize(this, state.done), Formatter.formatShortFileSize(this, state.total), Formatter.formatShortFileSize(this, state.speed))
                : "";
            showStatus(R.drawable.ic_cloud_download, getString(R.string.studio_downloading_title), state.statusResId != 0 ? getString(state.statusResId) : "");
            setProgress(state.total <= 0, state.percent);
            tvProgress.setText(detail);
            setButtons(false, 0, null, false, 0, null);
            return;
        }

        if (state.phase == InstallManager.Phase.FAILED) {
            String message = state.neededBytes > 0
                ? getString(R.string.studio_error_space, Formatter.formatShortFileSize(this, state.neededBytes))
                : getString(R.string.studio_error_generic, state.error);
            showStatus(R.drawable.ic_error, getString(R.string.studio_error_title), message);
            setButtons(true, R.string.studio_retry, v -> {
                InstallManager.acknowledge();
                setupTried = false;
                if (!ready) InstallManager.startRuntime(this);
                else startDownload();
            }, false, 0, null);
            return;
        }

        if (!ready || container == null) {
            if (!setupTried && state.phase == InstallManager.Phase.IDLE) {
                setupTried = true;
                InstallManager.startRuntime(this);
            }
            else if (state.phase == InstallManager.Phase.DONE) {
                InstallManager.acknowledge();
            }
            showStatus(R.drawable.ic_phone, getString(R.string.studio_runtime_title), getString(R.string.studio_runtime_text));
            setButtons(false, 0, null, false, 0, null);
            return;
        }

        if (state.phase == InstallManager.Phase.DONE) InstallManager.acknowledge();

        if (!installed) {
            long size = state.latestDownloadSize > 0 ? state.latestDownloadSize : 550L * 1024 * 1024;
            showStatus(R.drawable.ic_cloud_download, getString(R.string.studio_not_installed_title),
                getString(R.string.studio_not_installed_text, Formatter.formatShortFileSize(this, size * 3)));
            setButtons(true, R.string.studio_download, v -> startDownload(), false, 0, null);
            if (!checkedUpdates) {
                checkedUpdates = true;
                InstallManager.checkLatest(this);
            }
            return;
        }

        String version = StudioPrefs.get(this).getString(StudioPrefs.INSTALLED_VERSION, "");
        String guid = StudioPrefs.get(this).getString(StudioPrefs.INSTALLED_GUID, "");
        long size = StudioInstaller.getInstalledSize(this, container);
        boolean updateAvailable = state.latest != null && !state.latest.guid.equals(guid);

        if (!checkedUpdates) {
            checkedUpdates = true;
            updateChecking = true;
            InstallManager.checkLatest(this);
        }
        else updateChecking = false;

        if (updateAvailable) {
            showStatus(R.drawable.ic_cloud_download, getString(R.string.studio_update_title),
                getString(R.string.studio_update_text, state.latest.version, Formatter.formatShortFileSize(this, state.latestDownloadSize)));
            setButtons(true, R.string.studio_update, v -> startDownload(), true, R.string.studio_launch, v -> launch());
        }
        else {
            showStatus(R.drawable.ic_check_circle, getString(R.string.studio_installed_title, version.isEmpty() ? "" : version),
                getString(R.string.studio_installed_text, Formatter.formatShortFileSize(this, size)));
            setButtons(true, R.string.studio_launch, v -> launch(), true, R.string.studio_check_updates, v -> {
                checkedUpdates = true;
                InstallManager.checkLatest(this);
                tvStatusText.setText(R.string.studio_checking);
                v.postDelayed(this::refresh, 2500);
            });
        }
    }

    private void showStatus(int iconRes, String title, String text) {
        ivStatus.setImageResource(iconRes);
        tvStatusTitle.setText(title);
        tvStatusText.setText(text);
        tvStatusText.setVisibility(text == null || text.isEmpty() ? View.GONE : View.VISIBLE);
    }

    private void setProgress(boolean indeterminate, int percent) {
        if (progressBar.isIndeterminate() != indeterminate) {
            progressBar.setVisibility(View.INVISIBLE);
            progressBar.setIndeterminate(indeterminate);
            progressBar.setVisibility(View.VISIBLE);
        }
        if (logoRing.isIndeterminate() != indeterminate) {
            logoRing.setVisibility(View.INVISIBLE);
            logoRing.setIndeterminate(indeterminate);
            logoRing.setVisibility(View.VISIBLE);
        }
        if (!indeterminate) {
            progressBar.setProgressCompat(percent, true);
            logoRing.setProgressCompat(percent, true);
        }
    }

    private void setButtons(boolean mainEnabled, int mainText, View.OnClickListener mainClick, boolean secondaryVisible, int secondaryText, View.OnClickListener secondaryClick) {
        btMain.setVisibility(mainEnabled ? View.VISIBLE : View.INVISIBLE);
        if (mainEnabled) {
            btMain.setText(mainText);
            btMain.setIcon(androidx.core.content.ContextCompat.getDrawable(this, mainText == R.string.studio_launch ? R.drawable.ic_play : mainText == R.string.studio_retry ? R.drawable.ic_refresh : R.drawable.ic_download));
            btMain.setOnClickListener(mainClick);
        }

        btSecondary.setVisibility(secondaryVisible ? View.VISIBLE : View.GONE);
        if (secondaryVisible) {
            btSecondary.setText(secondaryText);
            btSecondary.setIcon(androidx.core.content.ContextCompat.getDrawable(this, secondaryText == R.string.studio_launch ? R.drawable.ic_play : R.drawable.ic_refresh));
            btSecondary.setOnClickListener(secondaryClick);
        }
    }

    private void startDownload() {
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        ((ActivityManager)getSystemService(Context.ACTIVITY_SERVICE)).getMemoryInfo(info);
        if (info.totalMem < 5800L * 1024 * 1024 && !StudioPrefs.get(this).getBoolean("low_ram_warned", false)) {
            StudioPrefs.get(this).edit().putBoolean("low_ram_warned", true).apply();
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.studio_low_ram_title)
                .setMessage(getString(R.string.studio_low_ram_text, Formatter.formatShortFileSize(this, info.totalMem)))
                .setPositiveButton(R.string.studio_download, (d, w) -> InstallManager.startStudio(this))
                .setNegativeButton(R.string.studio_cancel, null)
                .show();
            return;
        }
        InstallManager.startStudio(this);
    }

    private void launch() {
        if (!StudioLauncher.start(this, null)) {
            android.widget.Toast.makeText(this, R.string.studio_launch_failed, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}
