package io.rong.callkit;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import cn.rongcloud.rtc.utils.FinLog;
import io.rong.callkit.util.ActivityStartCheckUtils;
import io.rong.calllib.IRongReceivedCallListener;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallMissedListener;
import io.rong.calllib.RongCallSession;
import io.rong.calllib.message.CallSTerminateMessage;
import io.rong.calllib.message.MultiCallEndMessage;
import io.rong.common.RLog;
import io.rong.imkit.RongIM;
import io.rong.imkit.manager.IExternalModule;
import io.rong.imkit.plugin.IPluginModule;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;
import io.rong.push.RongPushClient;
import io.rong.push.notification.PushNotificationMessage;

/**
 * Created by weiqinxiao on 16/8/15.
 */
public class RongCallModule implements IExternalModule {
    private final static String TAG = "RongCallModule";

    private RongCallSession mCallSession;
    private boolean mStartForCheckPermissions;
    private boolean mViewLoaded;
    private Context mContext;
    private static RongCallMissedListener missedListener;
    private static boolean ignoreIncomingCall;

    public RongCallModule() {
        RLog.i(TAG, "Constructor");
    }

    @Override
    public void onInitialized(String appKey) {
        RongIM.registerMessageTemplate(new CallEndMessageItemProvider());
        RongIM.registerMessageTemplate(new MultiCallEndMessageProvider());
        initMissedCallListener();
    }

    private void initMissedCallListener() {
        RongCallClient.setMissedCallListener(new RongCallMissedListener() {
            @Override
            public void onRongCallMissed(RongCallSession callSession, RongCallCommon.CallDisconnectedReason reason) {
                if (!TextUtils.isEmpty(callSession.getInviterUserId())) {
                    long insertTime = callSession.getEndTime();
                    if (insertTime == 0) {
                        insertTime = callSession.getStartTime();
                    }
                    if (callSession.getConversationType() == Conversation.ConversationType.PRIVATE) {
                        CallSTerminateMessage message = new CallSTerminateMessage();
                        message.setReason(reason);
                        message.setMediaType(callSession.getMediaType());

                        String extra;
                        long time = (callSession.getEndTime() - callSession.getStartTime()) / 1000;
                        if (time >= 3600) {
                            extra = String.format("%d:%02d:%02d", time / 3600, (time % 3600) / 60, (time % 60));
                        } else {
                            extra = String.format("%02d:%02d", (time % 3600) / 60, (time % 60));
                        }
                        message.setExtra(extra);

                        String senderId = callSession.getInviterUserId();
                        if (senderId.equals(callSession.getSelfUserId())) {
                            message.setDirection("MO");
                            RongIM.getInstance().insertOutgoingMessage(Conversation.ConversationType.PRIVATE, callSession.getTargetId(), io.rong.imlib.model.Message.SentStatus.SENT, message, insertTime, null);
                        } else {
                            message.setDirection("MT");
                            io.rong.imlib.model.Message.ReceivedStatus receivedStatus = new io.rong.imlib.model.Message.ReceivedStatus(0);
                            RongIM.getInstance().insertIncomingMessage(Conversation.ConversationType.PRIVATE, callSession.getTargetId(), senderId, receivedStatus, message, insertTime, null);
                        }
                    } else if (callSession.getConversationType() == Conversation.ConversationType.GROUP) {
                        MultiCallEndMessage multiCallEndMessage = new MultiCallEndMessage();
                        multiCallEndMessage.setReason(reason);
                        if (callSession.getMediaType() == RongCallCommon.CallMediaType.AUDIO) {
                            multiCallEndMessage.setMediaType(RongIMClient.MediaType.AUDIO);
                        } else if (callSession.getMediaType() == RongCallCommon.CallMediaType.VIDEO) {
                            multiCallEndMessage.setMediaType(RongIMClient.MediaType.VIDEO);
                        }
                        RongIM.getInstance().insertMessage(callSession.getConversationType(), callSession.getTargetId(), callSession.getCallerUserId(), multiCallEndMessage, insertTime, null);
                    }
                }
                if (missedListener != null) {
                    missedListener.onRongCallMissed(callSession, reason);
                }
            }
        });
    }

    public static void setMissedCallListener(RongCallMissedListener listener) {
        missedListener = listener;
    }

    @Override
    public void onConnected(String token) {
        RongCallClient.getInstance().setVoIPCallListener(RongCallProxy.getInstance());
        RongCallClient.getInstance().setEnablePrintLog(true);

        /**
         * 音视频参数配置信息设置方法：{@link BaseCallActivity#audioVideoConfig()}
         */
    }

    @Override
    public void onCreate(final Context context) {
        mContext = context;
        IRongReceivedCallListener callListener = new IRongReceivedCallListener() {
            @Override
            public void onReceivedCall(final RongCallSession callSession) {
                FinLog.d("VoIPReceiver", "onReceivedCall");
                if (mViewLoaded) {
                    FinLog.d("VoIPReceiver", "onReceivedCall->onCreate->mViewLoaded=true");
                    startVoIPActivity(mContext, callSession, false);
                } else {
                    FinLog.d("VoIPReceiver", "onReceivedCall->onCreate->mViewLoaded=false");
                    mCallSession = callSession;
                }
            }

            @Override
            public void onCheckPermission(RongCallSession callSession) {
                FinLog.d("VoIPReceiver", "onCheckPermissions");
                mCallSession = callSession;
                if (mViewLoaded) {
                    startVoIPActivity(mContext, callSession, true);
                } else {
                    mStartForCheckPermissions = true;
                }
            }
        };

        RongCallClient.setReceivedCallListener(callListener);
        ActivityStartCheckUtils.getInstance().registerActivityLifecycleCallbacks(context);
    }

    /**
     * 此方法的目的是，防止 voip 通话界面被会话或者会话列表界面覆盖。
     * 所有要等待会话或者会话列表加载出后，再显示voip 通话界面。
     * <p>
     * 当会话列表或者会话界面加载出来后，此方法会被回调。
     * 如果开发者没有会话或者会话列表界面，只需要将下面的 mViewLoaded 在 onCreate 时设置为 true 即可。
     */
    @Override
    public void onViewCreated() {
        mViewLoaded = true;
        if (mCallSession != null) {
            startVoIPActivity(mContext, mCallSession, mStartForCheckPermissions);
            mStartForCheckPermissions = false;
        }
    }

    @Override
    public List<IPluginModule> getPlugins(Conversation.ConversationType conversationType) {
        List<IPluginModule> pluginModules = new ArrayList<>();
        try {
            if (RongCallClient.getInstance().isVoIPEnabled(mContext)) {
                pluginModules.add(new AudioPlugin());
                pluginModules.add(new VideoPlugin());
            }
        } catch (Exception e) {
            e.printStackTrace();
            RLog.i(TAG, "getPlugins()->Error :" + e.getMessage());
        }
        return pluginModules;
    }

    @Override
    public void onDisconnected() {

    }

    /**
     * 启动通话界面
     *
     * @param context                  上下文
     * @param callSession              通话实体
     * @param startForCheckPermissions android6.0需要实时获取应用权限。
     *                                 当需要实时获取权限时，设置startForCheckPermissions为true，
     *                                 其它情况下设置为false。
     */
    private void startVoIPActivity(Context context, final RongCallSession callSession, boolean startForCheckPermissions) {
        if (ignoreIncomingCall) {
            RongCallClient.getInstance().hangUpCall();
            return;
        }
        FinLog.d("VoIPReceiver", "startVoIPActivity");
        // 在 Android 10 以上版本不再允许后台运行 Activity
        if (Build.VERSION.SDK_INT < 29 || isAppOnForeground(context)) {
            context.startActivity(createVoIPIntent(context, callSession, startForCheckPermissions));
        } else {
            onSendBroadcast(context, callSession, startForCheckPermissions);
        }
        mCallSession = null;
    }

    private void onSendBroadcast(Context context, RongCallSession callSession, boolean startForCheckPermissions) {
        Intent intent = new Intent();
        intent.setPackage(context.getPackageName());
        //intent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.putExtra("message", transformToPushMessage(context, callSession));
        intent.putExtra("callsession", callSession);
        intent.putExtra("checkPermissions", startForCheckPermissions);
        intent.setAction(VoIPBroadcastReceiver.ACTION_CALLINVITEMESSAGE);
        context.sendBroadcast(intent);
    }

    public static Intent createVoIPIntent(Context context, RongCallSession callSession, boolean startForCheckPermissions) {
        Intent intent;
        String action;
        if (callSession.getConversationType().equals(Conversation.ConversationType.DISCUSSION)
                || callSession.getConversationType().equals(Conversation.ConversationType.GROUP)
                || callSession.getConversationType().equals(Conversation.ConversationType.NONE)) {
            if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.VIDEO)) {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIVIDEO;
            } else {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIAUDIO;
            }
            intent = new Intent(action);
            intent.putExtra("callSession", callSession);
            intent.putExtra("callAction", RongCallAction.ACTION_INCOMING_CALL.getName());
            if (startForCheckPermissions) {
                intent.putExtra("checkPermissions", true);
            } else {
                intent.putExtra("checkPermissions", false);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage(context.getPackageName());
        } else {
            if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.VIDEO)) {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEVIDEO;
            } else {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO;
            }
            intent = new Intent(action);
            intent.putExtra("callSession", callSession);
            intent.putExtra("callAction", RongCallAction.ACTION_INCOMING_CALL.getName());
            if (startForCheckPermissions) {
                intent.putExtra("checkPermissions", true);
            } else {
                intent.putExtra("checkPermissions", false);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage(context.getPackageName());
        }
        return intent;
    }

    /**
     * 将 RongCallSession 转换为 PushNotificationMessage
     *
     * @param session
     * @return
     */
    private PushNotificationMessage transformToPushMessage(Context context, RongCallSession session) {
        PushNotificationMessage pushMsg = new PushNotificationMessage();
//        pushMsg.setPushContent(session.getMediaType() == RongCallCommon.CallMediaType.AUDIO ? "音频电话呼叫" : "视频电话呼叫");
        pushMsg.setPushTitle((String) context.getPackageManager().getApplicationLabel(context.getApplicationInfo()));
        pushMsg.setConversationType(RongPushClient.ConversationType.setValue(session.getConversationType().getValue()));
        pushMsg.setTargetId(session.getTargetId());
        pushMsg.setTargetUserName("");
        pushMsg.setSenderId(session.getCallerUserId());
        pushMsg.setSenderName("");
        pushMsg.setObjectName("RC:VCInvite");
        pushMsg.setPushFlag("false");
        pushMsg.setToId(RongIMClient.getInstance().getCurrentUserId());
        pushMsg.setSourceType(PushNotificationMessage.PushSourceType.LOCAL_MESSAGE);
//        pushMsg.setPushId(session.getUId());
        return pushMsg;
    }

    /**
     * 判断应用是否处于前台
     *
     * @param context
     * @return
     */
    private boolean isAppOnForeground(Context context) {
        if (context == null)
            return false;
        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses = activityManager.getRunningAppProcesses();
        if (appProcesses == null)
            return false;
        String apkName = context.getPackageName();

        for (ActivityManager.RunningAppProcessInfo app : appProcesses) {
            if (TextUtils.equals(apkName, app.processName) && ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND == app.importance)
                return true;
        }
        return false;
    }

    public static void ignoreIncomingCall(boolean ignore) {
        ignoreIncomingCall = ignore;
    }
}
