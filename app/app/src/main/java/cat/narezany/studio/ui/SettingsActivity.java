package cat.narezany.studio.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.net.Uri;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.preference.EditTextPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.color.DynamicColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.winlator.R;
import com.winlator.container.Container;
import com.winlator.xenvironment.RootFS;

import cat.narezany.studio.StudioApp;
import cat.narezany.studio.core.InstallManager;
import cat.narezany.studio.core.StudioContainer;
import cat.narezany.studio.core.StudioFFlags;
import cat.narezany.studio.core.StudioInstaller;
import cat.narezany.studio.core.StudioPrefs;

public class SettingsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (StudioPrefs.get(this).getBoolean(StudioPrefs.DYNAMIC_COLOR, true)) DynamicColors.applyToActivityIfAvailable(this);
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_studio_settings);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.Root), (view, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        boolean night = (getResources().getConfiguration().uiMode & android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        androidx.core.view.WindowInsetsControllerCompat controller = WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        controller.setAppearanceLightStatusBars(!night);
        controller.setAppearanceLightNavigationBars(!night);

        findViewById(R.id.Toolbar).setOnClickListener(null);
        ((com.google.android.material.appbar.MaterialToolbar)findViewById(R.id.Toolbar)).setNavigationOnClickListener(v -> finish());

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.FLSettings, new Fragment()).commit();
        }
    }

    public static class Fragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.studio_preferences, rootKey);

            Preference theme = findPreference(StudioPrefs.THEME_MODE);
            theme.setOnPreferenceChangeListener((p, value) -> {
                StudioPrefs.get(requireContext()).edit().putString(StudioPrefs.THEME_MODE, (String)value).commit();
                StudioApp.applyNightMode(requireContext());
                return true;
            });

            Preference dynamic = findPreference(StudioPrefs.DYNAMIC_COLOR);
            dynamic.setOnPreferenceChangeListener((p, value) -> {
                requireActivity().recreate();
                return true;
            });
            dynamic.setEnabled(DynamicColors.isDynamicColorAvailable());

            EditTextPreference fflags = findPreference(StudioPrefs.CUSTOM_FFLAGS);
            fflags.setSummaryProvider(null);
            fflags.setOnPreferenceChangeListener((p, value) -> {
                if (!StudioFFlags.isValidCustom((String)value)) {
                    Toast.makeText(requireContext(), R.string.studio_pref_fflags_invalid, Toast.LENGTH_SHORT).show();
                    return false;
                }
                return true;
            });

            findPreference("storage_access").setOnPreferenceClickListener(p -> {
                requestStorage();
                return true;
            });

            findPreference("reset_prefix").setOnPreferenceClickListener(p -> {
                confirm(() -> {
                    InstallManager.startRuntime(requireContext(), true);
                    requireActivity().finish();
                });
                return true;
            });

            findPreference("reinstall_system").setOnPreferenceClickListener(p -> {
                confirm(() -> {
                    RootFS rootFS = RootFS.find(requireContext());
                    rootFS.getRFSVersionFile().delete();
                    InstallManager.startRuntime(requireContext());
                    requireActivity().finish();
                });
                return true;
            });

            findPreference("delete_studio").setOnPreferenceClickListener(p -> {
                confirm(() -> {
                    Container container = StudioContainer.find(requireContext());
                    if (container != null) StudioInstaller.uninstall(requireContext(), container);
                    requireActivity().finish();
                });
                return true;
            });
        }

        private void confirm(Runnable action) {
            new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.studio_pref_confirm_title)
                .setPositiveButton(R.string.studio_pref_done, (d, w) -> action.run())
                .setNegativeButton(R.string.studio_cancel, null)
                .show();
        }

        private void requestStorage() {
            if (Build.VERSION.SDK_INT >= 30) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:"+requireContext().getPackageName())));
                    return;
                }
                catch (Exception e) {}
            }
            if (requireContext().checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, 1);
            }
        }
    }
}
