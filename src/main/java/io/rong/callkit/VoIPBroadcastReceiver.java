package io.rong.callkit;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import io.rong.calllib.CallSessionImp;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
import io.rong.eventbus.EventBus;
import io.rong.imkit.RongContext;
import io.rong.imkit.RongIM;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.UserInfo;
import io.rong.push.common.PushConst;
import io.rong.push.common.RLog;
import io.rong.push.notification.PushNotificationMessage;
import io.rong.push.notification.RongNotificationInterface;

/**
 * 为解决在 Android 10 以上版本不再允许后台运行 Activity，音视频的离线推送呼叫消息将由通知栏的形式展示给用户
 * Created by wangw on 2019-12-09.
 */
public class VoIPBroadcastReceiver extends BroadcastReceiver {

    private static final String HANGUP = "RC:VCHangup";
    private static final String INVITE = "RC:VCInvite";
    public static final String ACTION_CALLINVITEMESSAGE = "action.push.CallInviteMessage";
    public final static String ACTION_CALLINVITEMESSAGE_CLICKED = "action.push.CallInviteMessage.CLICKED";
    private static final String TAG = "VoIPBroadcastReceiver";
    private static int VOIP_NOTIFICATION_ID = 3000; //VoIP类型的通知消息。
    private static int VOIP_REQUEST_CODE = 30001;

//    public IntentFilter getFilter(){
//        IntentFilter filter = new IntentFilter();
//        filter.addAction(ACTION_CALLINVITEMESSAGE);
//        return filter;
//    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        RLog.d(TAG, "onReceive.action:" + action);

//        String name = intent.getStringExtra(PushConst.PUSH_TYPE);
//        PushType pushType = PushType.getType(name);
//        int left = intent.getIntExtra(PushConst.LEFT, 0);
        PushNotificationMessage message = intent.getParcelableExtra(PushConst.MESSAGE);
        RongCallSession callSession = null;
        boolean checkPermissions = false;
        if (intent.hasExtra("callsession")) {
            callSession = intent.getParcelableExtra("callsession");
            checkPermissions = intent.getBooleanExtra("checkPermissions",false);
        }

        if (TextUtils.equals(ACTION_CALLINVITEMESSAGE,action)) {
            String objName = message.getObjectName();
            if (TextUtils.equals(objName,INVITE) && callSession != null ){
                UserInfo userInfo = RongContext.getInstance().getUserInfoFromCache(callSession.getCallerUserId());
                sendNotification(context,message,callSession,checkPermissions,userInfo);
            }else {
                sendNotification(context, message, callSession, checkPermissions);
            }
        }else if (TextUtils.equals(ACTION_CALLINVITEMESSAGE_CLICKED,action)){
            handleNotificationClickEvent(context,message,callSession,checkPermissions);
        }
    }

    private void handleNotificationClickEvent(Context context, PushNotificationMessage message,RongCallSession callSession, boolean checkPermissions) {
        Intent intent;
        //如果进程被杀 RongCallClient.getInstance() 返回Null
        if (RongCallClient.getInstance() != null && callSession != null){
            intent = RongCallModule.createVoIPIntent(context,callSession,checkPermissions);
        }else {
            intent = createConversationListIntent(context);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage(context.getPackageName());
        context.startActivity(intent);
    }

    private void sendNotification(Context context, PushNotificationMessage message, RongCallSession callSession, boolean checkPermissions, UserInfo userInfo) {
        String pushContent;
        boolean isAudio = callSession.getMediaType() == RongCallCommon.CallMediaType.AUDIO;
        if (userInfo == null){
            pushContent = context.getResources().getString(isAudio ? R.string.rc_voip_notificatio_audio_call_inviting_general : R.string.rc_voip_notificatio_video_call_inviting_general);
        }else {
            pushContent = userInfo.getName() + context.getResources().getString(isAudio ? R.string.rc_voip_notificatio_audio_call_inviting : R.string.rc_voip_notificatio_video_call_inviting);
        }
        message.setPushContent(pushContent);
        sendNotification(context,message,callSession,checkPermissions);
    }

    private void sendNotification(Context context, PushNotificationMessage message,RongCallSession callSession,boolean checkPermissions) {
        String objName = message.getObjectName();
        if (TextUtils.isEmpty(objName)) {
            return;
        }

        String title;
        String content;
        int notificationId = VOIP_NOTIFICATION_ID;
        RLog.i(TAG, "sendNotification() messageType: " + message.getConversationType()
                + " messagePushContent: " + message.getPushContent()
                + " messageObjectName: " + message.getObjectName());


        if (objName.equals(INVITE) || objName.equals(HANGUP)) {
            if (objName.equals(HANGUP)) {
                RongNotificationInterface.removeNotification(context, VOIP_NOTIFICATION_ID);
                return;
            }
            content = message.getPushContent();
            title = message.getPushTitle();
        }else {
            return;
        }
//        if (left > 0) {
//            return;
//        }
        Notification notification = RongNotificationInterface.createNotification(context, title, createPendingIntent(context,message,callSession,checkPermissions,VOIP_REQUEST_CODE,false), content, RongNotificationInterface.SoundType.VOIP);
        NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_HIGH;
            String channelName = context.getResources().getString(context.getResources().getIdentifier("rc_notification_channel_name", "string", context.getPackageName()));
            NotificationChannel notificationChannel = new NotificationChannel("rc_notification_id", channelName, importance);
            notificationChannel.enableLights(true);
            notificationChannel.setLightColor(Color.GREEN);
            if (notification != null && notification.sound != null) {
                notificationChannel.setSound(notification.sound, null);
            }
            nm.createNotificationChannel(notificationChannel);
        }
        if (notification != null) {
            RLog.i(TAG, "sendNotification() real notify! notificationId: " + notificationId +
                    " notification: " + notification.toString());
            nm.notify(notificationId, notification);
        }
    }

    private static PendingIntent createPendingIntent(Context context, PushNotificationMessage message,RongCallSession callSession, boolean checkPermissions, int requestCode, boolean isMulti) {
        Intent intent = new Intent();
        intent.setAction(ACTION_CALLINVITEMESSAGE_CLICKED);
        intent.putExtra(PushConst.MESSAGE, message);
        intent.putExtra("callsession",callSession);
        intent.putExtra("checkPermissions",checkPermissions);
        intent.putExtra(PushConst.IS_MULTI, isMulti);
        intent.setPackage(context.getPackageName());
        return PendingIntent.getBroadcast(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static Intent createConversationListIntent(Context context) {
        Intent intent = new Intent();
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Uri uri = Uri.parse("rong://" + context.getPackageName()).buildUpon()
                .appendPath("conversationlist").build();
        intent.setData(uri);
        intent.setPackage(context.getPackageName());
        return intent;
    }

}
