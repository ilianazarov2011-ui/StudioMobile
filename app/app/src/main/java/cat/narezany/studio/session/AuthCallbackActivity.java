package cat.narezany.studio.session;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import com.winlator.R;
import com.winlator.XServerDisplayActivity;

import cat.narezany.studio.core.StudioLauncher;

/** Receives roblox-studio-auth: links from the browser and hands them to the running Studio. */
public class AuthCallbackActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        handle(getIntent());
        finish();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handle(intent);
        finish();
    }

    private void handle(Intent intent) {
        Uri data = intent != null ? intent.getData() : null;
        if (data == null) return;
        String url = data.toString();

        if (XServerDisplayActivity.isSessionRunning()) {
            Intent forward = new Intent(this, XServerDisplayActivity.class);
            forward.putExtra(StudioLauncher.EXTRA_EXEC_ARGS, url);
            forward.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(forward);
        }
        else if (!StudioLauncher.start(this, url)) {
            Toast.makeText(this, R.string.studio_launch_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
