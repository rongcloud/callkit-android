package io.rong.callkit;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.view.View;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import io.rong.callkit.util.CallRingingUtil;
import io.rong.callkit.util.IncomingCallExtraHandleUtil;
import io.rong.callkit.util.RingingMode;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallSession;
import io.rong.common.RLog;
import io.rong.push.notification.PushNotificationMessage;
import java.util.concurrent.atomic.AtomicBoolean;

/** @author gusd @Date 2021/09/01 */
public class RongIncomingCallService {
    private static final String TAG = "IncomingCallService";
    public static final int ACCEPT_REQUEST_CODE = 145679;
    public static final int HANGUP_REQUEST_CODE = 145678;
    private static int notificationId = 4000;

    public static final String KEY_MESSAGE = "message";
    public static final String KEY_CALL_SESSION = "callsession";
    public static final String KEY_CHECK_PERMISSIONS = "checkPermissions";
    public static final String KEY_NEED_AUTO_ANSWER = "needAutoAnswer";

    /** 该服务最长存活时间，60 秒 */
    private static final long SERVICE_MAX_ALIVE_TIME = 60 * 1000L;

    private static final String TAG_KILL_INCOMING_SERVICE = "TAG_KILL_INCOMING_SERVICE";

    public static final String ACTION_CALLINVITEMESSAGE_CLICKED =
            "action.push.CallInviteMessage.CLICKED";

    private Handler mHandler;
    private AtomicBoolean isRinging = new AtomicBoolean(false);

    private RongIncomingCallService() {}

    public static RongIncomingCallService getInstance() {
        return RongIncomingCallHolder.instance;
    }

    private static class RongIncomingCallHolder {
        static RongIncomingCallService instance = new RongIncomingCallService();
    }

    public boolean isRinging() {
        return isRinging.get();
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    public void startRing(final Context context, Intent intent) {
        RLog.d(TAG, "onStartCommand: ");
        if (isRinging.get()) {
            return;
        }

        PushNotificationMessage message = intent.getParcelableExtra(KEY_MESSAGE);
        RongCallSession callSession = intent.getParcelableExtra(KEY_CALL_SESSION);
        boolean checkPermission = intent.getBooleanExtra(KEY_CHECK_PERMISSIONS, false);
        if (message == null || callSession == null) {
            return;
        }
        RLog.d(TAG, "onStartCommand : " + "callId = " + callSession.getCallId());

        mHandler = new Handler(Looper.myLooper());
        mHandler.postAtTime(
                new Runnable() {
                    @Override
                    public void run() {
                        stopRinging(context);
                    }
                },
                TAG_KILL_INCOMING_SERVICE,
                SystemClock.uptimeMillis() + SERVICE_MAX_ALIVE_TIME);

        wakeUpAndUnlock(context);

        try {
            PendingIntent answerPendingIntent =
                    createAnswerIntent(
                            context,
                            message,
                            callSession,
                            checkPermission,
                            IncomingCallExtraHandleUtil.VOIP_REQUEST_CODE,
                            false);
            PendingIntent hangupPendingIntent = createHangupIntent(context, callSession);
            PendingIntent openAppIntent =
                    createOpenAppPendingIntent(
                            context,
                            message,
                            callSession,
                            checkPermission,
                            IncomingCallExtraHandleUtil.VOIP_REQUEST_CODE,
                            false);
            CallRingingUtil.getInstance().createNotificationChannel(context);

            int smallIcon =
                    context.getResources()
                            .getIdentifier(
                                    "notification_small_icon",
                                    "drawable",
                                    context.getPackageName());
            if (smallIcon <= 0) {
                smallIcon = context.getApplicationInfo().icon;
            }
            androidx.media.app.NotificationCompat.MediaStyle mediaStyle =
                    new androidx.media.app.NotificationCompat.MediaStyle();
            mediaStyle.setShowCancelButton(false);
            mediaStyle.setCancelButtonIntent(hangupPendingIntent);
            mediaStyle.setShowActionsInCompactView(0, 1);

            NotificationCompat.Builder notificationBuilder =
                    new NotificationCompat.Builder(
                                    context,
                                    CallRingingUtil.getInstance().getNotificationChannelId())
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
                notificationBuilder.addAction(
                        R.drawable.rc_voip_notification_hangup,
                        context.getString(R.string.rc_voip_hangup),
                        hangupPendingIntent);
            }

            int notificationAnswerIcon = CallRingingUtil.getInstance().getNotificationAnswerIcon();
            if (notificationAnswerIcon != View.NO_ID && notificationAnswerIcon != 0) {
                notificationBuilder.addAction(
                        R.drawable.rc_voip_notification_answer,
                        context.getString(R.string.rc_voip_answer),
                        answerPendingIntent);
            }

            Notification notification = notificationBuilder.build();

            NotificationManager notificationManager =
                    context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.notify(++notificationId, notification);
            }
            CallRingingUtil.getInstance().startRinging(context, RingingMode.Incoming);
            isRinging.set(true);
        } catch (Exception e) {
            RLog.e(TAG, "onStartCommand = " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stopRinging(Context context) {
        isRinging.set(false);
        CallRingingUtil.getInstance().stopRinging();
        try {
            NotificationManager notificationManager = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                notificationManager = context.getSystemService(NotificationManager.class);
            }
            if (notificationManager != null) {
                notificationManager.cancel(notificationId);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        Handler handler = mHandler;
        if (handler != null) {
            handler.removeCallbacksAndMessages(TAG_KILL_INCOMING_SERVICE);
        }
    }

    private PendingIntent createOpenAppPendingIntent(
            Context context,
            PushNotificationMessage message,
            RongCallSession callSession,
            boolean checkPermissions,
            int requestCode,
            boolean isMulti) {
        Intent intent =
                createOpenAppIntent(
                        context, message, callSession, checkPermissions, requestCode, isMulti);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getActivity(
                    context,
                    2314412,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            return PendingIntent.getBroadcast(
                    context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    private Intent createOpenAppIntent(
            Context context,
            PushNotificationMessage message,
            RongCallSession callSession,
            boolean checkPermissions,
            int requestCode,
            boolean isMulti) {
        return createIntentForAndroidS(context, callSession, checkPermissions);
    }

    private PendingIntent createAnswerIntent(
            Context context,
            PushNotificationMessage message,
            RongCallSession callSession,
            boolean checkPermissions,
            int requestCode,
            boolean isMulti) {
        Intent intent =
                createOpenAppIntent(
                        context, message, callSession, checkPermissions, requestCode, isMulti);
        intent.putExtra(KEY_NEED_AUTO_ANSWER, callSession.getCallId());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getActivity(
                    context,
                    12345664,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        } else {
            intent.setClass(context, VoIPBroadcastReceiver.class);
            return PendingIntent.getBroadcast(
                    context, ACCEPT_REQUEST_CODE, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    private Intent createIntentForAndroidS(
            Context context, RongCallSession callSession, boolean checkPermissions) {
        Intent intent;
        // 如果进程被杀 RongCallClient.getInstance() 返回Null
        if (RongCallClient.getInstance() != null
                && RongCallClient.getInstance().getCallSession() != null
                && callSession != null) {
            intent = RongCallModule.createVoIPIntent(context, callSession, checkPermissions);
            io.rong.push.common.RLog.d(TAG, "handleNotificationClickEvent: start call activity");
        } else {
            intent = createConversationListIntent(context);
            io.rong.push.common.RLog.d(
                    TAG, "handleNotificationClickEvent: start conversation activity");
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage(context.getPackageName());
        return intent;
    }

    private static Intent createConversationListIntent(Context context) {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri =
                Uri.parse("rong://" + context.getPackageName())
                        .buildUpon()
                        .appendPath("conversationlist")
                        .build();
        intent.setData(uri);
        intent.setPackage(context.getPackageName());
        return intent;
    }

    private PendingIntent createHangupIntent(Context context, RongCallSession callSession) {
        Intent hangupIntent = new Intent();
        hangupIntent.setAction(VoIPBroadcastReceiver.ACTION_CALL_HANGUP_CLICKED);
        hangupIntent.putExtra(KEY_CALL_SESSION, callSession);
        hangupIntent.setPackage(context.getPackageName());
        hangupIntent.setClass(context, VoIPBroadcastReceiver.class);
        // KNOTE: 2021/9/29 PendingIntent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getBroadcast(
                    context,
                    HANGUP_REQUEST_CODE,
                    hangupIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            return PendingIntent.getBroadcast(
                    context, HANGUP_REQUEST_CODE, hangupIntent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
    }

    // 唤醒屏幕并解锁
    public void wakeUpAndUnlock(Context context) {
        try {
            KeyguardManager km =
                    (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            KeyguardManager.KeyguardLock kl = km.newKeyguardLock("unLock");
            // 获取电源管理器对象
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            // 获取PowerManager.WakeLock对象,后面的参数|表示同时传入两个值,最后的是LogCat里用的Tag
            @SuppressLint("InvalidWakeLockTag")
            PowerManager.WakeLock wl =
                    pm.newWakeLock(
                            PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK,
                            "bright");
            // 点亮屏幕
            wl.acquire(5000);
            // 释放
            wl.release();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
