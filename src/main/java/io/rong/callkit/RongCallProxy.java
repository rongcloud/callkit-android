package io.rong.callkit;

import android.text.TextUtils;
import android.view.SurfaceView;
import cn.rongcloud.rtc.api.RCRTCAudioRouteManager;
import io.rong.callkit.util.CallKitUtils;
import io.rong.callkit.util.IncomingCallExtraHandleUtil;
import io.rong.calllib.IRongCallListener;
import io.rong.calllib.ReportUtil;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallCommon.CallMediaType;
import io.rong.calllib.RongCallSession;
import io.rong.calllib.message.CallSTerminateMessage;
import io.rong.calllib.message.MultiCallEndMessage;
import io.rong.common.RLog;
import io.rong.imkit.IMCenter;
import io.rong.imlib.IRongCoreEnum;
import io.rong.imlib.model.Conversation;
import java.util.HashMap;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

/** Created by jiangecho on 2016/10/27. */
public class RongCallProxy implements IRongCallListener {

    private static final String TAG = "RongCallProxy";
    private IRongCallListener mCallListener;
    private Queue<CallDisconnectedInfo> mCachedCallQueue;
    private static RongCallProxy mInstance;

    private RongCallProxy() {
        mCachedCallQueue = new LinkedBlockingQueue<>();
    }

    public static synchronized RongCallProxy getInstance() {
        if (mInstance == null) {
            mInstance = new RongCallProxy();
        }
        return mInstance;
    }

    public void setCallListener(IRongCallListener listener) {
        RLog.d(TAG, "setCallListener listener = " + listener);
        this.mCallListener = listener;
        //        if (listener != null) {
        //            CallDisconnectedInfo callDisconnectedInfo = mCachedCallQueue.poll();
        //            if (callDisconnectedInfo != null) {
        //                listener.onCallDisconnected(callDisconnectedInfo.mCallSession,
        // callDisconnectedInfo.mReason);
        //            }
        //        }
    }

    @Override
    public void onCallIncoming(RongCallSession callSession, SurfaceView localVideo) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                callSession,
                "state|desc",
                "onCallIncoming",
                getDescription());
        if (mCallListener != null) {
            mCallListener.onCallIncoming(callSession, localVideo);
        }
        if (RongCallClient.getInstance().getContext() != null) {
            RCRTCAudioRouteManager.getInstance()
                    .init(RongCallClient.getInstance().getContext().getApplicationContext());
        }
    }

    @Override
    public void onCallOutgoing(RongCallSession callSession, SurfaceView localVideo) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                callSession,
                "state|desc",
                "onCallOutgoing",
                getDescription());
        if (mCallListener != null) {
            mCallListener.onCallOutgoing(callSession, localVideo);
        }
        if (RongCallClient.getInstance().getContext() != null) {
            RCRTCAudioRouteManager.getInstance()
                    .init(RongCallClient.getInstance().getContext().getApplicationContext());
        }
    }

    @Override
    public void onCallConnected(RongCallSession callSession, SurfaceView localVideo) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                callSession,
                "state|desc",
                "onCallConnected",
                getDescription());
        if (mCallListener != null) {
            mCallListener.onCallConnected(callSession, localVideo);
        }
    }

    @Override
    public void onCallDisconnected(
            RongCallSession callSession, RongCallCommon.CallDisconnectedReason reason) {
        RLog.d(TAG, "RongCallProxy onCallDisconnected mCallListener = " + mCallListener);
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                callSession,
                "state|reason|desc",
                "onCallDisconnected",
                reason.getValue(),
                getDescription());
        if (mCallListener != null) {
            mCallListener.onCallDisconnected(callSession, reason);
        } else if (!IncomingCallExtraHandleUtil.needNotify()) {
            mCachedCallQueue.offer(new CallDisconnectedInfo(callSession, reason));
        } else { // android 10 后台来电，被叫端不响应，主叫挂断时 mCallListener 为空 ，需要生成通话记录
            insertCallLogMessage(callSession, reason);
        }
        // 取消耳机监听
        RCRTCAudioRouteManager.getInstance().unInit();
    }

    @Override
    public void onRemoteUserRinging(String userId) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|desc",
                userId,
                "onRemoteUserRinging",
                getDescription());
        if (mCallListener != null) {
            mCallListener.onRemoteUserRinging(userId);
        }
    }

    @Override
    public void onRemoteUserAccept(String userId, CallMediaType mediaType) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|desc",
                userId,
                "onRemoteUserAccept",
                getDescription());
        if (mCallListener != null) {
            mCallListener.onRemoteUserAccept(userId, mediaType);
        }
    }

    @Override
    public void onRemoteUserJoined(
            String userId,
            RongCallCommon.CallMediaType mediaType,
            int userType,
            SurfaceView remoteVideo) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|desc",
                userId,
                "onRemoteUserJoined",
                getDescription());
        if (mCallListener != null) {
            mCallListener.onRemoteUserJoined(userId, mediaType, userType, remoteVideo);
        }
    }

    @Override
    public void onRemoteUserInvited(String userId, RongCallCommon.CallMediaType mediaType) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|desc",
                userId,
                "onRemoteUserInvited",
                getDescription());
        if (mCallListener != null) {
            mCallListener.onRemoteUserInvited(userId, mediaType);
        }
    }

    @Override
    public void onRemoteUserLeft(String userId, RongCallCommon.CallDisconnectedReason reason) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|reason|desc",
                userId,
                "onRemoteUserLeft",
                reason.getValue(),
                getDescription());
        if (mCallListener != null) {
            mCallListener.onRemoteUserLeft(userId, reason);
        }
    }

    @Override
    public void onMediaTypeChanged(
            String userId, RongCallCommon.CallMediaType mediaType, SurfaceView video) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|mediaType|desc",
                userId,
                "onMediaTypeChanged",
                mediaType.getValue(),
                getDescription());
        if (mCallListener != null) {
            mCallListener.onMediaTypeChanged(userId, mediaType, video);
        }
    }

    @Override
    public void onError(RongCallCommon.CallErrorCode errorCode) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "state|code|desc",
                "onError",
                errorCode.getValue(),
                getDescription());
        if (mCallListener != null) {
            mCallListener.onError(errorCode);
        }
    }

    @Override
    public void onRemoteCameraDisabled(String userId, boolean disabled) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|disabled|desc",
                userId,
                "onRemoteCameraDisabled",
                disabled,
                getDescription());
        if (mCallListener != null) {
            mCallListener.onRemoteCameraDisabled(userId, disabled);
        }
    }

    @Override
    public void onRemoteMicrophoneDisabled(String userId, boolean disabled) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|disabled|desc",
                userId,
                "onRemoteMicrophoneDisabled",
                disabled,
                getDescription());
        if (mCallListener != null) {
            mCallListener.onRemoteMicrophoneDisabled(userId, disabled);
        }
    }

    @Override
    public void onNetworkSendLost(int lossRate, int delay) {
        if (mCallListener != null) {
            mCallListener.onNetworkSendLost(lossRate, delay);
        }
    }

    @Override
    public void onFirstRemoteVideoFrame(String userId, int height, int width) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|desc",
                userId,
                "onFirstRemoteVideoFrame",
                getDescription());
        if (mCallListener != null) {
            mCallListener.onFirstRemoteVideoFrame(userId, height, width);
        }
    }

    @Override
    public void onFirstRemoteAudioFrame(String userId) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|desc",
                userId,
                "onFirstRemoteAudioFrame",
                getDescription());
        if (mCallListener != null) {
            mCallListener.onFirstRemoteAudioFrame(userId);
        }
    }

    @Override
    public void onAudioLevelSend(String audioLevel) {
        if (mCallListener != null) {
            mCallListener.onAudioLevelSend(audioLevel);
        }
    }

    public void onRemoteUserPublishVideoStream(
            String userId, String streamId, String tag, SurfaceView surfaceView) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|streamId|desc",
                userId,
                "onRemoteUserPublishVideoStream",
                streamId,
                getDescription());
        if (mCallListener != null) {
            mCallListener.onRemoteUserPublishVideoStream(userId, streamId, tag, surfaceView);
        }
    }

    @Override
    public void onAudioLevelReceive(HashMap<String, String> audioLevel) {
        if (mCallListener != null) {
            mCallListener.onAudioLevelReceive(audioLevel);
        }
    }

    public void onRemoteUserUnpublishVideoStream(String userId, String streamId, String tag) {
        ReportUtil.appStatus(
                ReportUtil.TAG.CALL_LISTENER,
                "userId|state|streamId|desc",
                userId,
                "onRemoteUserUnpublishVideoStream",
                streamId,
                getDescription());
        if (mCallListener != null) {
            mCallListener.onRemoteUserUnpublishVideoStream(userId, streamId, tag);
        }
    }

    @Override
    public void onNetworkReceiveLost(String userId, int lossRate) {
        if (mCallListener != null) {
            mCallListener.onNetworkReceiveLost(userId, lossRate);
        }
    }

    private static class CallDisconnectedInfo {
        RongCallSession mCallSession;
        RongCallCommon.CallDisconnectedReason mReason;

        public CallDisconnectedInfo(
                RongCallSession callSession, RongCallCommon.CallDisconnectedReason reason) {
            this.mCallSession = callSession;
            this.mReason = reason;
        }
    }

    private String getDescription() {
        if (mCallListener != null) {
            return mCallListener.getClass().getSimpleName();
        }
        return "no callListener set";
    }

    private void insertCallLogMessage(
            RongCallSession callSession, RongCallCommon.CallDisconnectedReason reason) {
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
                    extra =
                            String.format(
                                    Locale.ROOT,
                                    "%d:%02d:%02d",
                                    time / 3600,
                                    (time % 3600) / 60,
                                    (time % 60));
                } else {
                    extra =
                            String.format(
                                    Locale.ROOT, "%02d:%02d", (time % 3600) / 60, (time % 60));
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
            } else if (callSession.getConversationType() == Conversation.ConversationType.GROUP) {
                MultiCallEndMessage multiCallEndMessage = new MultiCallEndMessage();
                multiCallEndMessage.setReason(reason);
                if (callSession.getMediaType() == RongCallCommon.CallMediaType.AUDIO) {
                    multiCallEndMessage.setMediaType(IRongCoreEnum.MediaType.AUDIO);
                } else if (callSession.getMediaType() == RongCallCommon.CallMediaType.VIDEO) {
                    multiCallEndMessage.setMediaType(IRongCoreEnum.MediaType.VIDEO);
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
    }
}
