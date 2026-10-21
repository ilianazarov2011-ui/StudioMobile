package cat.narezany.studio.ui;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.winlator.R;

public final class AboutDialog {
    private AboutDialog() {}

    public static void show(Context context) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_studio_about, null);
        String version = "";
        try {
            version = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        }
        catch (Exception e) {}
        ((TextView)view.findViewById(R.id.TVAboutVersion)).setText(context.getString(R.string.studio_about_version, version));
        view.findViewById(R.id.BTAboutSite).setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://narez.xyz"));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        });

        new MaterialAlertDialogBuilder(context)
            .setView(view)
            .setPositiveButton(R.string.studio_ok, null)
            .show();
    }
}
