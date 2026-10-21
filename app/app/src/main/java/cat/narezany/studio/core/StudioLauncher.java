package cat.narezany.studio.core;

import android.content.Context;
import android.content.Intent;

import com.winlator.XServerDisplayActivity;
import com.winlator.container.Container;

import java.io.File;

public final class StudioLauncher {
    public static final String EXTRA_STUDIO_SESSION = "studio_session";
    public static final String EXTRA_EXEC_ARGS = "exec_args";

    private StudioLauncher() {}

    /** @return false when Studio is not installed yet */
    public static boolean start(Context context, String url) {
        Container container = StudioContainer.find(context);
        if (container == null || !StudioInstaller.isInstalled(context, container)) return false;

        String guid = StudioPrefs.get(context).getString(StudioPrefs.INSTALLED_GUID, "");
        File exe = StudioInstaller.getStudioExe(container, guid);

        StudioContainer.applySettings(context, container);
        StudioFFlags.write(context, StudioInstaller.getVersionDir(container, guid));
        StudioContainer.registerProtocols(container, exe);

        Intent intent = new Intent(context, XServerDisplayActivity.class);
        intent.putExtra("container_id", container.id);
        intent.putExtra("exec_path", exe.getPath());
        intent.putExtra(EXTRA_STUDIO_SESSION, true);
        if (url != null) intent.putExtra(EXTRA_EXEC_ARGS, url);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
        return true;
    }
}
