package io.rong.callkit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import io.rong.callkit.util.CallRingingUtil;
import io.rong.push.notification.RongNotificationInterface;
import io.rong.push.notification.RongNotificationInterface.SoundType;

public class CallForegroundService extends Service {

    private static final String TAG = "CallForegroundService";

    @Override
    public void onCreate() {
        super.onCreate();
    }

    private void showNotification(
            String title, String content, PendingIntent pendingIntent, int notificationId) {
        Log.d(TAG, "showNotification: ");
        NotificationManager notificationManager =
                (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        String channelId = CallRingingUtil.getInstance().getNotificationChannelId();
        Notification notification =
                RongNotificationInterface.createNotification(
                        getApplicationContext(),
                        title,
                        pendingIntent,
                        content,
                        SoundType.SILENT,
                        channelId);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW;
            String channelName = CallRingingUtil.getInstance().getNotificationChannelName(this);
            NotificationChannel notificationChannel =
                    new NotificationChannel(channelId, channelName, importance);
            notificationChannel.enableLights(false);
            notificationChannel.setLightColor(Color.GREEN);
            notificationChannel.enableVibration(false);
            notificationChannel.setSound(null, null);
            notificationManager.createNotificationChannel(notificationChannel);
        }
        notification.defaults = Notification.DEFAULT_ALL;
        if (VERSION.SDK_INT >= VERSION_CODES.Q) {
            startForeground(
                    notificationId,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                            | ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } else {
            startForeground(notificationId, notification);
        }
        Log.d(TAG, "showNotification: startForeground");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopForeground(true);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getStringExtra("action");
        String title = intent.getStringExtra("title");
        String content = intent.getStringExtra("content");
        ResolveInfo info =
                getPackageManager()
                        .resolveActivity(new Intent(action), PackageManager.MATCH_DEFAULT_ONLY);
        ActivityInfo activityInfo;
        if (info == null || (activityInfo = info.activityInfo) == null) {
            Log.e(TAG, "onStartCommand: ResolveInfo is null! action=" + action);
            return super.onStartCommand(intent, flags, startId);
        }
        Log.d(TAG, "onStartCommand: " + activityInfo.name);
        Intent launched = new Intent(action);
        launched.setClassName(activityInfo.packageName, activityInfo.name);
        launched.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        launched.putExtra("floatbox", intent.getBundleExtra("floatbox"));
        launched.putExtra("callAction", RongCallAction.ACTION_RESUME_CALL.getName());
        PendingIntent pendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            1000,
                            launched,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent =
                    PendingIntent.getActivity(
                            this, 1000, launched, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        try {
            showNotification(title, content, pendingIntent, BaseCallActivity.CALL_NOTIFICATION_ID);
        } catch (Exception e) {
            Log.e(TAG, "showNotification: ", e);
            e.printStackTrace();
        }
        return super.onStartCommand(intent, flags, startId);
    }
}
