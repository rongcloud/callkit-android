package io.rong.imkit;

import android.content.Context;
import android.content.Intent;

import java.util.List;

import io.rong.calllib.IRongReceivedCallListener;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
import io.rong.common.RLog;
import io.rong.imkit.model.ConversationInfo;
import io.rong.imkit.widget.provider.InputProvider;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;

/**
 * Created by weiqinxiao on 16/4/27.
 */
public class RongCallService {
    private static Context mContext;
    private static boolean uiReady;

    private static RongCallSession mCallSession;

    private static IRongReceivedCallListener callListener = new IRongReceivedCallListener() {
        @Override
        public void onReceivedCall(final RongCallSession callSession) {
            RLog.d("VoIPReceiver", "onReceivedCall");
            if (uiReady) {
                startVoIPActivity(mContext, callSession, false);
            } else {
                mCallSession = callSession;
            }
        }

        @Override
        public void onCheckPermission(RongCallSession callSession) {
            RLog.d("VoIPReceiver", "onCheckPermissions");
            if (uiReady) {
                startVoIPActivity(mContext, callSession, true);
            }
        }
    };

    /**
     * {@link RongIM#init(Context)}的时候发送io.rong.intent.action.SDK_INIT，
     * CallKit收到这个intent，注册接收通话监听。
     *
     * @param context 上下文
     */
    public static void onInit(Context context) {
        mContext = context.getApplicationContext();

        RongIM.registerMessageTemplate(new CallEndMessageItemProvider());
        RongCallClient.setReceivedCallListener(callListener);
    }

    /**
     * 用户刚登陆的时候收到来电，需要确保来电界面在主界面出来之后再显示，否则来电界面会被覆盖。
     * RongIM在主界面显示完成之后发送io.rong.intent.action.UI_READY。
     */
    public static void onUiReady() {
        uiReady = true;
        if (mCallSession != null) {
            startVoIPActivity(mContext, mCallSession, false);
        }
    }

    /**
     * {@link RongIM#connect(String, RongIMClient.ConnectCallback)}成功之后发送io.rong.intent.action.SDK_CONNECTED，
     * CallKit收到这个intent，在会话扩展区域添加音视频通话按钮。
     */
    public static void onConnected() {
        Conversation conversation = null;
        List<ConversationInfo> infoList = RongContext.getInstance().getCurrentConversationList();
        if (infoList.size() > 0) {
            Conversation.ConversationType conversationType = infoList.get(0).getConversationType();
            String targetId = infoList.get(0).getTargetId();
            conversation = Conversation.obtain(conversationType, targetId, null);
        }

        AudioCallInputProvider audioCallInputProvider = new AudioCallInputProvider(RongContext.getInstance());
        VideoCallInputProvider videoCallInputProvider = new VideoCallInputProvider(RongContext.getInstance());
        audioCallInputProvider.setCurrentConversation(conversation);
        videoCallInputProvider.setCurrentConversation(conversation);

        InputProvider.ExtendProvider[] audioProvider = {
            audioCallInputProvider
        };
        InputProvider.ExtendProvider[] videoProvider = {
            videoCallInputProvider
        };

        boolean hasAudio = false;
        boolean hasVideo = false;
        for (InputProvider provider : RongContext.getInstance().getRegisteredExtendProviderList(Conversation.ConversationType.PRIVATE)) {
            if (provider instanceof AudioCallInputProvider) {
                hasAudio = true;
            } else if (provider instanceof VideoCallInputProvider) {
                hasVideo = true;
            }
        }
        if (!hasAudio) {
            RongIM.addInputExtensionProvider(Conversation.ConversationType.PRIVATE, audioProvider);
        }
        if (!hasVideo) {
            RongIM.addInputExtensionProvider(Conversation.ConversationType.PRIVATE, videoProvider);
        }

        hasAudio = false;
        hasVideo = false;
        for (InputProvider provider : RongContext.getInstance().getRegisteredExtendProviderList(Conversation.ConversationType.DISCUSSION)) {
            if (provider instanceof AudioCallInputProvider) {
                hasAudio = true;
            } else if (provider instanceof VideoCallInputProvider) {
                hasVideo = true;
            }
        }
        if (!hasAudio) {
            RongIM.addInputExtensionProvider(Conversation.ConversationType.DISCUSSION, audioProvider);
        }
        if (!hasVideo) {
            RongIM.addInputExtensionProvider(Conversation.ConversationType.DISCUSSION, videoProvider);
        }

        hasAudio = false;
        hasVideo = false;
        for (InputProvider provider : RongContext.getInstance().getRegisteredExtendProviderList(Conversation.ConversationType.GROUP)) {
            if (provider instanceof AudioCallInputProvider) {
                hasAudio = true;
            } else if (provider instanceof VideoCallInputProvider) {
                hasVideo = true;
            }
        }
        if (!hasAudio) {
            RongIM.addInputExtensionProvider(Conversation.ConversationType.GROUP, audioProvider);
        }
        if (!hasVideo) {
            RongIM.addInputExtensionProvider(Conversation.ConversationType.GROUP, videoProvider);
        }
    }

    /**
     * 启动通话界面
     * @param context                   上下文
     * @param callSession               通话实体
     * @param startForCheckPermissions  android6.0需要实时获取应用权限。
     *                                  当需要实时获取权限时，设置startForCheckPermissions为true，
     *                                  其它情况下设置为false。
     */
    public static void startVoIPActivity(Context context, final RongCallSession callSession, boolean startForCheckPermissions) {
        RLog.d("VoIPReceiver", "startVoIPActivity");
        String action;
        if (callSession.getConversationType().equals(Conversation.ConversationType.DISCUSSION)
                || callSession.getConversationType().equals(Conversation.ConversationType.GROUP)) {
            if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.VIDEO)) {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIVIDEO;
            } else {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIAUDIO;
            }
            Intent intent = new Intent(action);
            intent.putExtra("callSession", callSession);
            intent.putExtra("callAction", RongCallAction.ACTION_INCOMING_CALL.getName());
            if (startForCheckPermissions) {
                intent.putExtra("checkPermissions", true);
            } else {
                intent.putExtra("checkPermissions", false);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage(context.getPackageName());
            context.startActivity(intent);
        } else {
            if (callSession.getMediaType().equals(RongCallCommon.CallMediaType.VIDEO)) {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEVIDEO;
            } else {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO;
            }
            Intent intent = new Intent(action);
            intent.putExtra("callSession", callSession);
            intent.putExtra("callAction", RongCallAction.ACTION_INCOMING_CALL.getName());
            if (startForCheckPermissions) {
                intent.putExtra("checkPermissions", true);
            } else {
                intent.putExtra("checkPermissions", false);
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage(context.getPackageName());
            context.startActivity(intent);
        }
    }
}
