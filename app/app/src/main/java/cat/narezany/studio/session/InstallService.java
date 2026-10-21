package cat.narezany.studio.session;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import com.winlator.R;

import cat.narezany.studio.core.InstallManager;
import cat.narezany.studio.ui.LauncherActivity;

/** Keeps the process alive while the system files or Studio are being installed. */
public class InstallService extends Service implements InstallManager.Listener {
    public static final String EXTRA_TITLE = "title";
    private static final String CHANNEL_ID = "install";
    private static final int NOTIFICATION_ID = 21;
    private int titleResId = R.string.studio_notification_download;
    private NotificationManager notificationManager;

    @Override
    public void onCreate() {
        super.onCreate();
        notificationManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, getString(R.string.studio_notification_channel), NotificationManager.IMPORTANCE_LOW);
        notificationManager.createNotificationChannel(channel);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) titleResId = intent.getIntExtra(EXTRA_TITLE, titleResId);
        startForeground(NOTIFICATION_ID, build(0, true));
        InstallManager.addListener(this);
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        InstallManager.removeListener(this);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private Notification build(int percent, boolean indeterminate) {
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, new Intent(this, LauncherActivity.class), PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.icon_notification)
            .setContentTitle(getString(titleResId))
            .setContentIntent(contentIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, percent, indeterminate)
            .build();
    }

    @Override
    public void onInstallState(InstallManager.State state) {
        switch (state.phase) {
            case RUNTIME:
            case DOWNLOAD:
            case CHECKING:
                notificationManager.notify(NOTIFICATION_ID, build(state.percent, state.percent <= 0));
                break;
            default:
                stopForeground(true);
                stopSelf();
                break;
        }
    }
}
