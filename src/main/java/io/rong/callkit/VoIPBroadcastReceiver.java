package io.rong.callkit;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import io.rong.callkit.util.CallRingingUtil;
import io.rong.callkit.util.IncomingCallExtraHandleUtil;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
import io.rong.common.fwlog.FwLog;
import io.rong.imkit.userinfo.RongUserInfoManager;
import io.rong.imlib.model.AndroidConfig;
import io.rong.imlib.model.Conversation.ConversationType;
import io.rong.imlib.model.Group;
import io.rong.imlib.model.MessagePushConfig;
import io.rong.imlib.model.UserInfo;
import io.rong.push.common.PushConst;
import io.rong.push.common.RLog;
import io.rong.push.notification.PushNotificationMessage;
import io.rong.push.notification.RongNotificationInterface;
import java.util.HashMap;
import java.util.Map;

/**
 * 为解决在 Android 10 以上版本不再允许后台运行 Activity，音视频的离线推送呼叫消息将由通知栏的形式展示给用户 Created by wangw on 2019-12-09.
 */
public class VoIPBroadcastReceiver extends BroadcastReceiver {

    private static final String HANGUP = "RC:VCHangup";
    private static final String INVITE = "RC:VCInvite";
    public static final String ACTION_CALLINVITEMESSAGE = "action.push.CallInviteMessage";
    public static final String ACTION_CALLINVITEMESSAGE_CLICKED =
            "action.push.CallInviteMessage.CLICKED";
    public static final String ACTION_CALL_HANGUP_CLICKED = "action.push.voip.hangup.click";
    private static final String TAG = "VoIPBroadcastReceiver";
    public static final String ACTION_CLEAR_VOIP_NOTIFICATION = "action.voip.notification.clear";
    private static Map<String, Integer> notificationCache = new HashMap<>();

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        RLog.d(TAG, "onReceive.action:" + action);

        // 通知栏挂断按钮事件响应
        if (ACTION_CALL_HANGUP_CLICKED.equals(action)) {
            RongCallSession session =
                    intent.getParcelableExtra(RongIncomingCallService.KEY_CALL_SESSION);
            stopIncomingService(context);
            if (session == null) {
                RongCallClient.getInstance().hangUpCall();
            } else {
                RongCallClient.getInstance().hangUpCall(session.getCallId());
            }
            return;
        } else if (ACTION_CLEAR_VOIP_NOTIFICATION.equals(action)) {
            // 针对 Android 12 的业务逻辑
            IncomingCallExtraHandleUtil.removeNotification(context);
            IncomingCallExtraHandleUtil.clear();
            clearNotificationCache();
            return;
        }

        PushNotificationMessage message = intent.getParcelableExtra(PushConst.MESSAGE);
        // bug fixed : https://rc-jira.rongcloud.net/browse/AC-903
        if (message == null) {
            return;
        }
        RongCallSession callSession = null;
        boolean checkPermissions = false;
        if (intent.hasExtra("callsession")) {
            callSession = intent.getParcelableExtra("callsession");
            checkPermissions = intent.getBooleanExtra("checkPermissions", false);
        }

        if (!needShowNotification(context, message)) {
            return;
        }

        if (TextUtils.equals(ACTION_CALLINVITEMESSAGE, action)) {
            if (callSession == null) {
                RLog.e(TAG, "push:: callsession is null !!");
                return;
            }
            String objName = message.getObjectName();
            if (TextUtils.equals(objName, INVITE)) {
                IncomingCallExtraHandleUtil.cacheCallSession(callSession, checkPermissions);
                UserInfo userInfo =
                        RongUserInfoManager.getInstance()
                                .getUserInfo(callSession.getCallerUserId());
                sendNotification(context, message, callSession, checkPermissions, userInfo);
            } else {
                IncomingCallExtraHandleUtil.clear();
                UserInfo userInfo =
                        RongUserInfoManager.getInstance()
                                .getUserInfo(callSession.getCallerUserId());
                sendNotification(context, message, callSession, checkPermissions, userInfo);
            }
        } else if (TextUtils.equals(ACTION_CALLINVITEMESSAGE_CLICKED, action)) {
            IncomingCallExtraHandleUtil.removeNotification(context);
            IncomingCallExtraHandleUtil.clear();
            clearNotificationCache();
            handleNotificationClickEvent(
                    context,
                    message,
                    callSession,
                    checkPermissions,
                    intent.getStringExtra(RongIncomingCallService.KEY_NEED_AUTO_ANSWER));
        }
    }

    private boolean needShowNotification(Context context, PushNotificationMessage message) {
        if (message == null || context == null) {
            return false;
        }
        if (INVITE.equals(message.getObjectName())
                && Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            // Android 10 以下允许后台运行，直接交由会话列表界面拉取消息
            RLog.d(TAG, "handle VoIP event.");
            Intent newIntent = new Intent();
            newIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            Uri uri =
                    Uri.parse("rong://" + context.getPackageName())
                            .buildUpon()
                            .appendPath("conversationlist")
                            .appendQueryParameter("isFromPush", "false")
                            .build();
            newIntent.setData(uri);
            newIntent.setPackage(context.getPackageName());
            context.startActivity(newIntent);
            return false;
        }
        return true;
    }

    @SuppressLint("QueryPermissionsNeeded")
    private void handleNotificationClickEvent(
            Context context,
            PushNotificationMessage message,
            RongCallSession callSession,
            boolean checkPermissions,
            String needAutoAnswerCallId) {
        Intent intent;
        // 如果进程被杀 RongCallClient.getInstance() 返回Null
        if (RongCallClient.getInstance() != null
                && RongCallClient.getInstance().getCallSession() != null
                && callSession != null) {
            intent = RongCallModule.createVoIPIntent(context, callSession, checkPermissions);
            RLog.d(TAG, "handleNotificationClickEvent: start call activity");
        } else {
            intent = createConversationListIntent(context);
            RLog.d(TAG, "handleNotificationClickEvent: start conversation activity");
        }
        if (callSession != null && !TextUtils.isEmpty(needAutoAnswerCallId)) {
            intent.putExtra(RongIncomingCallService.KEY_NEED_AUTO_ANSWER, callSession.getCallId());
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage(context.getPackageName());
        // bug fixed : https://rc-jira.rongcloud.net/browse/AC-864
        if (intent.resolveActivity(context.getPackageManager()) != null) {
            context.startActivity(intent);
        } else {
            StringBuilder builder = new StringBuilder("start activity with scheme : ");
            if (intent.getData() != null) {
                builder.append(intent.getData().toString());
            }
            FwLog.write(FwLog.I, FwLog.IM, "L-VoIP_notify_scheme", "scheme", builder.toString());
        }
    }

    private void sendNotification(
            Context context,
            PushNotificationMessage message,
            RongCallSession callSession,
            boolean checkPermissions,
            UserInfo userInfo) {
        String pushContent;
        boolean isAudio = callSession.getMediaType() == RongCallCommon.CallMediaType.AUDIO;
        if (HANGUP.equals(message.getObjectName())) {
            pushContent =
                    context.getResources().getString(R.string.rc_voip_call_terminalted_notify);
            if (callSession.getConversationType().equals(ConversationType.GROUP)
                    && RongCallClient.getInstance().getCallSession() != null) {
                return; // 群组消息，getCallSession不为空，说明收到的hangup并不是最后一个人发出的，此时不需要生成通知
            }
        } else {
            pushContent =
                    context.getResources()
                            .getString(
                                    isAudio
                                            ? R.string.rc_voip_audio_call_inviting
                                            : R.string.rc_voip_video_call_inviting);
        }
        message.setPushContent(pushContent);
        if (callSession.getConversationType().equals(ConversationType.PRIVATE)) {
            if (userInfo != null && !TextUtils.isEmpty(userInfo.getName())) {
                message.setPushTitle(userInfo.getName());
            }
        } else if (callSession.getConversationType().equals(ConversationType.GROUP)) {
            Group group = RongUserInfoManager.getInstance().getGroupInfo(callSession.getTargetId());
            if (group != null && !TextUtils.isEmpty(group.getName())) {
                message.setPushTitle(group.getName());
            }
        }
        if (callSession != null && callSession.getPushConfig() != null) {
            MessagePushConfig messagePushConfig = callSession.getPushConfig();
            if (!TextUtils.isEmpty(messagePushConfig.getPushTitle())) {
                message.setPushTitle(messagePushConfig.getPushTitle());
            }
            if (!TextUtils.isEmpty(messagePushConfig.getPushContent())
                    && !messagePushConfig.getPushContent().equals("voip")) {
                // message.setPushContent(messagePushConfig.getPushContent());
            }
            if (messagePushConfig.isForceShowDetailContent()) {
                message.setShowDetail(messagePushConfig.isForceShowDetailContent());
            }
            AndroidConfig androidConfig = messagePushConfig.getAndroidConfig();
            if (androidConfig != null) {
                message.setChannelIdHW(androidConfig.getChannelIdHW());
                message.setChannelIdMi(androidConfig.getChannelIdMi());
                message.setChannelIdOPPO(androidConfig.getChannelIdOPPO());
                message.setNotificationId(androidConfig.getNotificationId());
            }
        }
        sendNotification(context, message, callSession, checkPermissions);
    }

    private void startIncomingService(
            Context context,
            PushNotificationMessage message,
            RongCallSession callSession,
            boolean checkPermissions) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Bundle bundle = new Bundle();
            bundle.putParcelable(RongIncomingCallService.KEY_MESSAGE, message);
            bundle.putParcelable(RongIncomingCallService.KEY_CALL_SESSION, callSession);
            bundle.putBoolean(RongIncomingCallService.KEY_CHECK_PERMISSIONS, checkPermissions);
            CallRingingUtil.getInstance().startRingingService(context, bundle);
        }
    }

    private void stopIncomingService(Context context) {
        CallRingingUtil.getInstance().stopService(context);
    }

    private void sendNotification(
            Context context,
            PushNotificationMessage message,
            RongCallSession callSession,
            boolean checkPermissions) {
        String objName = message.getObjectName();
        if (TextUtils.isEmpty(objName)) {
            return;
        }

        String title;
        String content;
        int notificationId = IncomingCallExtraHandleUtil.VOIP_NOTIFICATION_ID;
        RLog.i(
                TAG,
                "sendNotification() messageType: "
                        + message.getConversationType()
                        + " messagePushContent: "
                        + message.getPushContent()
                        + " messageObjectName: "
                        + message.getObjectName()
                        + " notificationId: "
                        + message.getNotificationId());

        // Android 10 以上走新逻辑，通过服务启动
        if (Build.VERSION.SDK_INT >= 29) {
            if (INVITE.equals(objName)) {
                startIncomingService(context, message, callSession, checkPermissions);
            } else if (HANGUP.equals(objName)) {
                stopIncomingService(context);
                sendHangupNotification(context, message, callSession, checkPermissions);
            }
            return;
        }

        if (objName.equals(INVITE) || objName.equals(HANGUP)) {
            content = message.getPushContent();
            title = message.getPushTitle();
        } else {
            return;
        }

        Notification notification =
                RongNotificationInterface.createNotification(
                        context,
                        title,
                        createPendingIntent(
                                context,
                                message,
                                callSession,
                                checkPermissions,
                                IncomingCallExtraHandleUtil.VOIP_REQUEST_CODE,
                                false),
                        content,
                        RongNotificationInterface.SoundType.VOIP,
                        message.isShowDetail());
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_HIGH;
            String channelName = CallRingingUtil.getInstance().getNotificationChannelName(context);
            NotificationChannel notificationChannel =
                    new NotificationChannel(
                            CallRingingUtil.getInstance().getNotificationChannelId(),
                            channelName,
                            importance);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.GREEN);
            if (notification != null && notification.sound != null) {
                notificationChannel.setSound(notification.sound, null);
            }
            nm.createNotificationChannel(notificationChannel);
        }
        if (notification != null) {
            RLog.i(
                    TAG,
                    "sendNotification() real notify! notificationId: "
                            + notificationId
                            + " notification: "
                            + notification.toString());
            if (INVITE.equals(message.getObjectName())) {
                notificationCache.put(callSession.getCallId(), notificationId);
                nm.notify(notificationId, notification);
                IncomingCallExtraHandleUtil.VOIP_NOTIFICATION_ID++;
            } else if (notificationCache.containsKey(callSession.getCallId())) {
                notificationId = notificationCache.get(callSession.getCallId());
                nm.notify(notificationId, notification);
            }
        }
    }

    private void sendHangupNotification(
            Context context,
            PushNotificationMessage message,
            RongCallSession callSession,
            boolean checkPermissions) {
        try {
            String content =
                    context.getResources().getString(R.string.rc_voip_call_terminalted_notify);
            String title = message.getPushTitle();
            int notificationId = IncomingCallExtraHandleUtil.VOIP_NOTIFICATION_ID;
            Notification notification =
                    RongNotificationInterface.createNotification(
                            context,
                            title,
                            createPendingIntent(
                                    context,
                                    message,
                                    callSession,
                                    checkPermissions,
                                    IncomingCallExtraHandleUtil.VOIP_REQUEST_CODE,
                                    false),
                            content,
                            RongNotificationInterface.SoundType.VOIP,
                            message.isShowDetail());
            if (notification == null) {
                return;
            }
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notification.flags |= Notification.FLAG_AUTO_CANCEL;
            CallRingingUtil.getInstance().createNotificationChannel(context);
            RLog.i(
                    TAG,
                    "sendNotification() real notify! notificationId: "
                            + notificationId
                            + " notification: "
                            + notification.toString());
            notification.defaults = Notification.DEFAULT_ALL;
            notification.sound = null;
            nm.notify(notificationId, notification);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static PendingIntent createPendingIntent(
            Context context,
            PushNotificationMessage message,
            RongCallSession callSession,
            boolean checkPermissions,
            int requestCode,
            boolean isMulti) {
        Intent intent = new Intent();
        intent.setAction(ACTION_CALLINVITEMESSAGE_CLICKED);
        intent.putExtra(PushConst.MESSAGE, message);
        intent.putExtra("callsession", callSession);
        intent.putExtra("checkPermissions", checkPermissions);
        intent.putExtra(PushConst.IS_MULTI, isMulti);
        intent.setPackage(context.getPackageName());
        // KNOTE: 2021/9/29 PendingIntent
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return PendingIntent.getBroadcast(
                    context,
                    requestCode,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            return PendingIntent.getBroadcast(
                    context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
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

    public static void clearNotificationCache() {
        notificationCache.clear();
    }
}
