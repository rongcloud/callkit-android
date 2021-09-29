package io.rong.callkit;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;

import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;

import java.util.concurrent.atomic.AtomicBoolean;

import io.rong.callkit.util.CallRingingUtil;
import io.rong.callkit.util.IncomingCallExtraHandleUtil;
import io.rong.callkit.util.RingingMode;
import io.rong.calllib.RongCallSession;
import io.rong.common.RLog;
import io.rong.push.common.PushConst;
import io.rong.push.notification.PushNotificationMessage;

/**
 * @author gusd
 * @Date 2021/09/01
 */
public class RongIncomingCallService extends Service {
    private static final String TAG = "IncomingCallService";
    public static final int ACCEPT_REQUEST_CODE = 145679;
    public static final int HANGUP_REQUEST_CODE = 145678;
    private static int notificationId = 4000;

    public static final String KEY_MESSAGE = "message";
    public static final String KEY_CALL_SESSION = "callsession";
    public static final String KEY_CHECK_PERMISSIONS = "checkPermissions";
    public static final String KEY_NEED_AUTO_ANSWER = "needAutoAnswer";

    /**
     * 该服务最长存活时间，60 秒
     */
    private static final long SERVICE_MAX_ALIVE_TIME = 60L * 1000;
    private static final String TAG_KILL_INCOMING_SERVICE = "TAG_KILL_INCOMING_SERVICE";

    public final static String ACTION_CALLINVITEMESSAGE_CLICKED = "action.push.CallInviteMessage.CLICKED";

    private Handler mHandler;
    private AtomicBoolean isRinging = new AtomicBoolean(false);


    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        RLog.d(TAG, "onCreate: ");
        super.onCreate();

    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    @Override
    public int onStartCommand(Intent intent, int flags, final int startId) {
        RLog.d(TAG, "onStartCommand: ");
        if (isRinging.get()) {
            return START_NOT_STICKY;
        }
        if (intent == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        PushNotificationMessage message = intent.getParcelableExtra(KEY_MESSAGE);
        RongCallSession callSession = intent.getParcelableExtra(KEY_CALL_SESSION);
        boolean checkPermission = intent.getBooleanExtra(KEY_CHECK_PERMISSIONS, false);
        if (message == null || callSession == null) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        RLog.d(TAG, "onStartCommand : " + "callId = " + callSession.getCallId());

        mHandler = new Handler(Looper.myLooper());
        mHandler.postAtTime(new Runnable() {
            @Override
            public void run() {
                stopSelf(startId);
            }
        }, TAG_KILL_INCOMING_SERVICE, SystemClock.uptimeMillis() + SERVICE_MAX_ALIVE_TIME);

        wakeUpAndUnlock(this);

        try {
            PendingIntent answerPendingIntent = createAnswerIntent(message, callSession, checkPermission, IncomingCallExtraHandleUtil.VOIP_REQUEST_CODE, false);
            PendingIntent hangupPendingIntent = createHangupIntent(callSession);
            PendingIntent openAppIntent = createOpenAppPendingIntent(message, callSession, checkPermission, IncomingCallExtraHandleUtil.VOIP_REQUEST_CODE, false);
            CallRingingUtil.getInstance().createNotificationChannel(this);

            int smallIcon = getResources().getIdentifier("notification_small_icon", "drawable", getPackageName());
            if (smallIcon <= 0) {
                smallIcon = getApplicationInfo().icon;
            }
            androidx.media.app.NotificationCompat.MediaStyle mediaStyle = new androidx.media.app.NotificationCompat.MediaStyle();
            mediaStyle.setShowCancelButton(false);
            mediaStyle.setCancelButtonIntent(hangupPendingIntent);
            mediaStyle.setShowActionsInCompactView(0, 1);


            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(this, CallRingingUtil.getInstance().getNotificationChannelId())
                    .setContentText(message.getPushContent())
                    .setContentTitle(message.getPushTitle())
                    .setSmallIcon(smallIcon)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setCategory(NotificationCompat.CATEGORY_CALL)
                    .setAutoCancel(true)
                    .setOngoing(true)
                    .setStyle(mediaStyle)
                    .setContentIntent(openAppIntent)
                    .setFullScreenIntent(openAppIntent, true);

            int notificationHangupIcon = CallRingingUtil.getInstance().getNotificationHangupIcon();
            if (notificationHangupIcon != View.NO_ID && notificationHangupIcon != 0) {
                notificationBuilder.addAction(R.drawable.rc_voip_notification_hangup, getString(R.string.rc_voip_hangup), hangupPendingIntent);
            }

            int notificationAnswerIcon = CallRingingUtil.getInstance().getNotificationAnswerIcon();
            if (notificationAnswerIcon != View.NO_ID && notificationAnswerIcon != 0) {
                notificationBuilder.addAction(R.drawable.rc_voip_notification_answer, getString(R.string.rc_voip_answer), answerPendingIntent);
            }

            Notification notification = notificationBuilder.build();
            startForeground(notificationId++, notification);
            CallRingingUtil.getInstance().startRinging(this, RingingMode.Incoming);
            isRinging.set(true);
        } catch (Exception e) {
            RLog.e(TAG, "onStartCommand = " + e.getMessage());
            e.printStackTrace();
        }

        return START_NOT_STICKY;
    }

    private PendingIntent createOpenAppPendingIntent(PushNotificationMessage message, RongCallSession callSession, boolean checkPermissions, int requestCode, boolean isMulti) {
        Intent intent = createOpenAppIntent(message, callSession, checkPermissions, requestCode, isMulti);
        return PendingIntent.getBroadcast(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private Intent createOpenAppIntent(PushNotificationMessage message, RongCallSession callSession, boolean checkPermissions, int requestCode, boolean isMulti) {
        Intent intent = new Intent();
        intent.setAction(ACTION_CALLINVITEMESSAGE_CLICKED);
        intent.putExtra(PushConst.MESSAGE, message);
        intent.putExtra(KEY_CALL_SESSION, callSession);
        intent.putExtra(KEY_CHECK_PERMISSIONS, checkPermissions);
        intent.putExtra(PushConst.IS_MULTI, isMulti);
        intent.setPackage(getPackageName());
        return intent;
    }


    private PendingIntent createAnswerIntent(PushNotificationMessage message, RongCallSession callSession, boolean checkPermissions, int requestCode, boolean isMulti) {
        Intent intent = createOpenAppIntent(message, callSession, checkPermissions, requestCode, isMulti);
        intent.putExtra(KEY_NEED_AUTO_ANSWER, true);
        return PendingIntent.getBroadcast(this, ACCEPT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private PendingIntent createHangupIntent(RongCallSession callSession) {
        Intent hangupIntent = new Intent();
        hangupIntent.setAction(VoIPBroadcastReceiver.ACTION_CALL_HANGUP_CLICKED);
        hangupIntent.putExtra(KEY_CALL_SESSION, callSession);
        hangupIntent.setPackage(getPackageName());
        return PendingIntent.getBroadcast(this, HANGUP_REQUEST_CODE, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    @Override
    public void onDestroy() {
        RLog.d(TAG, "onDestroy: ");
        super.onDestroy();
        isRinging.set(false);
        CallRingingUtil.getInstance().stopRinging();
        Handler handler = mHandler;
        if (handler != null) {
            handler.removeCallbacksAndMessages(TAG_KILL_INCOMING_SERVICE);
        }
    }

    //唤醒屏幕并解锁
    public void wakeUpAndUnlock(Context context) {
        try {
            KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            KeyguardManager.KeyguardLock kl = km.newKeyguardLock("unLock");
            //获取电源管理器对象
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            //获取PowerManager.WakeLock对象,后面的参数|表示同时传入两个值,最后的是LogCat里用的Tag
            @SuppressLint("InvalidWakeLockTag") PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK, "bright");
            //点亮屏幕
            wl.acquire(5000);
            //释放
            wl.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
