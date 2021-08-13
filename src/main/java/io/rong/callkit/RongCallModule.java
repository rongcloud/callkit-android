package io.rong.callkit;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.fragment.app.Fragment;

import io.rong.callkit.util.CallKitUtils;
import io.rong.calllib.ReportUtil;
import java.util.ArrayList;
import java.util.List;

import io.rong.callkit.util.ActivityStartCheckUtils;
import io.rong.calllib.IRongReceivedCallListener;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallMissedListener;
import io.rong.calllib.RongCallSession;
import io.rong.calllib.message.CallSTerminateMessage;
import io.rong.calllib.message.MultiCallEndMessage;
import io.rong.common.RLog;
import io.rong.imkit.IMCenter;
import io.rong.imkit.RongIM;
import io.rong.imkit.config.RongConfigCenter;
import io.rong.imkit.conversation.extension.IExtensionModule;
import io.rong.imkit.conversation.extension.RongExtension;
import io.rong.imkit.conversation.extension.component.emoticon.IEmoticonTab;
import io.rong.imkit.conversation.extension.component.plugin.IPluginModule;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Message;
import io.rong.push.RongPushClient;
import io.rong.push.notification.PushNotificationMessage;

/**
 * Created by weiqinxiao on 16/8/15.
 */
public class RongCallModule implements IExtensionModule {
    private static final String TAG = "RongCallModule";

    private RongCallSession mCallSession;
    private boolean mStartForCheckPermissions;
    private static boolean mViewLoaded = true;
    private Context mContext;
    private static RongCallMissedListener missedListener;
    private static boolean ignoreIncomingCall;
    private Application mApplication;

    public RongCallModule() {
        RLog.i(TAG, "Constructor");
    }

    private void initMissedCallListener() {
        RongCallClient.setMissedCallListener(
            new RongCallMissedListener() {
                @Override
                public void onRongCallMissed(
                    RongCallSession callSession,
                    RongCallCommon.CallDisconnectedReason reason) {
                    if (!TextUtils.isEmpty(callSession.getInviterUserId())) {
                        long insertTime = callSession.getEndTime();
                        if (insertTime == 0) {
                            insertTime = callSession.getStartTime();
                        }
                        if (callSession.getConversationType()
                            == Conversation.ConversationType.PRIVATE) {
                            CallSTerminateMessage message = new CallSTerminateMessage();
                            message.setReason(reason);
                            message.setMediaType(callSession.getMediaType());

                            String extra;
                            long time =
                                (callSession.getEndTime() - callSession.getStartTime())
                                    / 1000;
                            if (time >= 3600) {
                                extra =
                                    String.format(
                                        "%d:%02d:%02d",
                                        time / 3600, (time % 3600) / 60, (time % 60));
                            } else {
                                extra =
                                    String.format(
                                        "%02d:%02d", (time % 3600) / 60, (time % 60));
                            }
                            message.setExtra(extra);

                            String senderId = callSession.getInviterUserId();
                            if (senderId.equals(callSession.getSelfUserId())) {
                                message.setDirection("MO");
                                IMCenter.getInstance()
                                    .insertOutgoingMessage(
                                        Conversation.ConversationType.PRIVATE,
                                        callSession.getTargetId(),
                                        io.rong.imlib.model.Message.SentStatus.SENT,
                                        message,
                                        insertTime,
                                        null);
                            } else {
                                message.setDirection("MT");
                                io.rong.imlib.model.Message.ReceivedStatus receivedStatus =
                                    new io.rong.imlib.model.Message.ReceivedStatus(0);
                                IMCenter.getInstance()
                                        .insertIncomingMessage(
                                                Conversation.ConversationType.PRIVATE,
                                                callSession.getTargetId(),
                                                senderId,
                                                CallKitUtils.getReceivedStatus(reason),
                                                message,
                                                insertTime,
                                                null);
                            }
                        } else if (callSession.getConversationType()
                            == Conversation.ConversationType.GROUP) {
                            MultiCallEndMessage multiCallEndMessage = new MultiCallEndMessage();
                            multiCallEndMessage.setReason(reason);
                            if (callSession.getMediaType()
                                == RongCallCommon.CallMediaType.AUDIO) {
                                multiCallEndMessage.setMediaType(RongIMClient.MediaType.AUDIO);
                            } else if (callSession.getMediaType()
                                == RongCallCommon.CallMediaType.VIDEO) {
                                multiCallEndMessage.setMediaType(RongIMClient.MediaType.VIDEO);
                            }
                            IMCenter.getInstance()
                                .insertIncomingMessage(
                                        callSession.getConversationType(),
                                        callSession.getTargetId(),
                                        callSession.getCallerUserId(),
                                        CallKitUtils.getReceivedStatus(reason),
                                        multiCallEndMessage,
                                        insertTime,
                                        null);
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

    /**
     * 启动通话界面
     *
     * @param context                  上下文
     * @param callSession              通话实体
     * @param startForCheckPermissions android6.0需要实时获取应用权限。
     *                                 当需要实时获取权限时，设置startForCheckPermissions为true， 其它情况下设置为false。
     */
    private void startVoIPActivity(
        Context context, final RongCallSession callSession, boolean startForCheckPermissions) {
        RLog.d(TAG, "startVoIPActivity.ignoreIncomingCall : " + ignoreIncomingCall + " , AndroidVersion :" + Build.VERSION.SDK_INT + " ,startForCheckPermissions : " + startForCheckPermissions);
        if (ignoreIncomingCall) {
            RongCallClient.getInstance().hangUpCall();
            return;
        }
        ReportUtil.appStatus(ReportUtil.TAG.RECEIVE_CALL_LISTENER, callSession, "state|desc", "startVoIPActivity", Build.VERSION.SDK_INT);
        // 在 Android 10 以上版本不再允许后台运行 Activity
        if (Build.VERSION.SDK_INT < 29 || isAppOnForeground(context)) {
            context.startActivity(createVoIPIntent(context, callSession, startForCheckPermissions));
        } else {
            onSendBroadcast(context, callSession, startForCheckPermissions);
        }
        mCallSession = null;
    }

    private void onSendBroadcast(Context context, RongCallSession callSession, boolean startForCheckPermissions) {
        RLog.d(TAG, "onSendBroadcast");
        Intent intent = new Intent();
        intent.setPackage(context.getPackageName());
        // intent.setFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        intent.putExtra("message", transformToPushMessage(context, callSession));
        intent.putExtra("callsession", callSession);
        intent.putExtra("checkPermissions", startForCheckPermissions);
        intent.setAction(VoIPBroadcastReceiver.ACTION_CALLINVITEMESSAGE);
        context.sendBroadcast(intent);
    }

    public static Intent createVoIPIntent(
        Context context, RongCallSession callSession, boolean startForCheckPermissions) {
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
    private PushNotificationMessage transformToPushMessage(
        Context context, RongCallSession session) {
        PushNotificationMessage pushMsg = new PushNotificationMessage();
        //        pushMsg.setPushContent(session.getMediaType() ==
        // RongCallCommon.CallMediaType.AUDIO ? "音频电话呼叫" : "视频电话呼叫");
        pushMsg.setPushTitle(
            (String)
                context.getPackageManager()
                    .getApplicationLabel(context.getApplicationInfo()));
        pushMsg.setConversationType(
            RongPushClient.ConversationType.setValue(session.getConversationType().getValue()));
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
        if (context == null) return false;
        ActivityManager activityManager =
            (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> appProcesses =
            activityManager.getRunningAppProcesses();
        if (appProcesses == null) return false;
        String apkName = context.getPackageName();

        for (ActivityManager.RunningAppProcessInfo app : appProcesses) {
            if (TextUtils.equals(apkName, app.processName)
                && ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
                == app.importance) return true;
        }
        return false;
    }

    public static void ignoreIncomingCall(boolean ignore) {
        ignoreIncomingCall = ignore;
    }

    @Override
    public void onInit(Context context, String appKey) {
        RLog.d(TAG, "onInit");
        mContext = context.getApplicationContext();
        registerLifecycleCallbacks(mContext);
        RongConfigCenter.conversationConfig().addMessageProvider(new CallEndMessageItemProvider());
        RongConfigCenter.conversationConfig().addMessageProvider(new MultiCallEndMessageProvider());
        initMissedCallListener();

        IRongReceivedCallListener callListener =
            new IRongReceivedCallListener() {
                @Override
                public void onReceivedCall(final RongCallSession callSession) {
                    ReportUtil.appStatus(ReportUtil.TAG.RECEIVE_CALL_LISTENER, callSession, "state|desc", "onReceivedCall", TAG);
                    RLog.d(TAG, "onReceivedCall.mViewLoaded :" + mViewLoaded);
                    if (mViewLoaded) {
                        startVoIPActivity(mContext, callSession, false);
                    } else {
                        mCallSession = callSession;
                    }
                }

                @Override
                public void onCheckPermission(RongCallSession callSession) {
                    ReportUtil.appStatus(ReportUtil.TAG.RECEIVE_CALL_LISTENER, callSession, "state|desc", "onCheckPermission", TAG);
                    RLog.d(TAG, "onCheckPermissions.mViewLoaded : " + mViewLoaded);
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
        IMCenter.getInstance().addConnectStatusListener(new RongIMClient.ConnectCallback() {
            @Override
            public void onSuccess(String t) {
                if (RongCallClient.getInstance() != null) {
                    RongCallClient.getInstance().setVoIPCallListener(RongCallProxy.getInstance());
                }
            }

            @Override
            public void onError(RongIMClient.ConnectionErrorCode e) {
                if (RongCallClient.getInstance() != null) {
                    RongCallClient.getInstance().setVoIPCallListener(RongCallProxy.getInstance());
                }
            }

            @Override
            public void onDatabaseOpened(RongIMClient.DatabaseOpenStatus code) {

            }
        });
    }

    private void registerLifecycleCallbacks(Context context) {
        RLog.d(TAG, "registerLifecycleCallbacks");
        mApplication = (Application) context;

        if (mApplication == null) {
            return;
        }

        mApplication.registerActivityLifecycleCallbacks(myActivityLifecycleCallbacks);
    }

    private ActivityLifecycleCallbacks myActivityLifecycleCallbacks = new ActivityLifecycleCallbacks() {
        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
            RLog.d(TAG, "onActivityCreated ---- : " + activity);

            if (mActivities == null || mActivities.size() == 0) {
                RLog.d(TAG, "onActivityCreated . mainPageClass is empty.");
                return;
            }
            String className1 = activity.getClass().getName();
            mActivities.remove(className1);
            if (mActivities.size() == 0) {
                retryStartVoIPActivity();
            }
        }

        @Override
        public void onActivityStarted(Activity activity) {

        }

        @Override
        public void onActivityResumed(Activity activity) {
        }

        @Override
        public void onActivityPaused(Activity activity) {
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
        }
    };

    private void retryStartVoIPActivity() {
        RLog.i(TAG, "Find the exact class, change mViewLoaded  as true . mCallSession ==null ?" + (mCallSession == null));
        mViewLoaded = true;
        if (mCallSession != null) {
            startVoIPActivity(mContext, mCallSession, mStartForCheckPermissions);
            mStartForCheckPermissions = false;
        }
    }

    @Override
    public void onAttachedToExtension(Fragment fragment, RongExtension extension) {
        RLog.d(TAG, "onAttachedToExtension");
    }

    @Override
    public void onDetachedFromExtension() {
        RLog.d(TAG, "onDetachedFromExtension");
    }

    @Override
    public void onReceivedMessage(Message message) {

    }

    @Override
    public List<IPluginModule> getPluginModules(Conversation.ConversationType conversationType) {
        RLog.d(TAG, "getPluginModules");
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
    public List<IEmoticonTab> getEmoticonTabs() {
        return null;
    }

    @Override
    public void onDisconnect() {
        RLog.d(TAG, "onDisconnect");
    }

    private static ArrayList<String> mActivities;

    /**
     * 设置可能会覆盖音视频通话页面的类，比如主页面。设置后，如果此页面尚未打开，即使收到音视频呼叫，也会暂缓唤起页面，会再设置的页面启动成功后，再尝试启动音视频通话页面。
     */
    public static void setMainPageActivity(String[] className) {
        if (className != null && className.length > 0) {
            int length = className.length;
            RLog.i(TAG, "setMainPageActivity.length :" + length);
            mActivities = new ArrayList<>();
            mViewLoaded = false;
            for (int i = 0; i < length; i++) {
                mActivities.add(className[i]);
            }
        }
    }
}
