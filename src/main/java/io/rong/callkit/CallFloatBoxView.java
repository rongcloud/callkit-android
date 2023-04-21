package io.rong.callkit;

import static io.rong.callkit.util.CallKitUtils.isDial;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import io.rong.callkit.util.ActivityStartCheckUtils;
import io.rong.callkit.util.CallKitUtils;
import io.rong.calllib.CallUserProfile;
import io.rong.calllib.IRongCallListener;
import io.rong.calllib.ReportUtil;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallCommon.CallMediaType;
import io.rong.calllib.RongCallSession;
import io.rong.calllib.message.CallSTerminateMessage;
import io.rong.common.RLog;
import io.rong.imkit.IMCenter;
import io.rong.imkit.manager.AudioPlayManager;
import io.rong.imkit.notification.NotificationUtil;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;
import io.rong.message.InformationNotificationMessage;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;

/** Created by weiqinxiao on 16/3/17. */
public class CallFloatBoxView {
    private static Context mContext;
    private static Timer timer;
    private static long mTime;
    private static View mView;
    private static Boolean isShown = false;
    private static WindowManager wm;
    private static Bundle mBundle;
    private static final String TAG = "CallFloatBoxView";
    private static TextView showFBCallTime = null;
    private static FrameLayout remoteVideoContainer = null;
    private static boolean activityResuming = false;

    public static void showFB(Context context, Bundle bundle) {
        Log.i("audioTag", "CallKitUtils.isDial=" + CallKitUtils.isDial);
        setExcludeFromRecents(context, true);
        activityResuming = false;
        if (CallKitUtils.isDial) {
            CallFloatBoxView.showFloatBoxToCall(context, bundle);
        } else {
            CallFloatBoxView.showFloatBox(context, bundle);
        }
    }

    public static void showFloatBox(Context context, Bundle bundle) {
        if (isShown) {
            return;
        }
        mContext = context;
        isShown = true;
        RongCallSession session = RongCallClient.getInstance().getCallSession();
        long activeTime = session != null ? session.getActiveTime() : 0;
        mTime = activeTime == 0 ? 0 : (System.currentTimeMillis() - activeTime) / 1000;
        if (mTime > 0) {
            setAudioMode(AudioManager.MODE_IN_COMMUNICATION);
        }
        mBundle = bundle;
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        WindowManager.LayoutParams params = createLayoutParams(context);
        RongCallCommon.CallMediaType mediaType =
                RongCallCommon.CallMediaType.valueOf(bundle.getInt("mediaType"));
        if (mediaType == RongCallCommon.CallMediaType.VIDEO
                && session != null
                && session.getConversationType() == Conversation.ConversationType.PRIVATE) {
            SurfaceView remoteVideo = null;
            for (CallUserProfile profile : session.getParticipantProfileList()) {
                if (!TextUtils.equals(
                        profile.getUserId(), RongIMClient.getInstance().getCurrentUserId())) {
                    remoteVideo = profile.getVideoView();
                }
            }
            if (remoteVideo != null) {
                ViewGroup parent = (ViewGroup) remoteVideo.getParent();
                if (parent != null) {
                    parent.removeView(remoteVideo);
                }
                Resources resources = mContext.getResources();
                params.width = resources.getDimensionPixelSize(R.dimen.callkit_dimen_size_60);
                params.height = resources.getDimensionPixelSize(R.dimen.callkit_dimen_size_80);
                remoteVideoContainer = new FrameLayout(mContext);
                remoteVideoContainer.addView(
                        remoteVideo,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT);
                remoteVideoContainer.setOnTouchListener(createTouchListener());
                wm.addView(remoteVideoContainer, params);
            }
        }
        if (remoteVideoContainer == null) {
            mView = LayoutInflater.from(context).inflate(R.layout.rc_voip_float_box, null);
            mView.setOnTouchListener(createTouchListener());
            wm.addView(mView, params);
            TextView timeV = (TextView) mView.findViewById(R.id.rc_time);
            setupTime(timeV);
            ImageView mediaIconV = (ImageView) mView.findViewById(R.id.rc_voip_media_type);
            if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
                mediaIconV.setImageResource(R.drawable.rc_voip_float_audio);
            } else {
                mediaIconV.setImageResource(R.drawable.rc_voip_float_video);
            }
        } else {
            // 视频悬浮窗下，不需要UI显示时间，但是时间值也需要同步更新
            setupTime(null);
        }
        RongCallClient.getInstance()
                .setVoIPCallListener(
                        new IRongCallListener() {
                            @Override
                            public void onCallIncoming(
                                    RongCallSession callSession, SurfaceView localVideo) {}

                            @Override
                            public void onCallOutgoing(
                                    RongCallSession callInfo, SurfaceView localVideo) {}

                            @Override
                            public void onRemoteUserRinging(String userId) {}

                            @Override
                            public void onRemoteUserAccept(
                                    String userId, CallMediaType mediaType) {}

                            @Override
                            public void onCallDisconnected(
                                    RongCallSession callProfile,
                                    RongCallCommon.CallDisconnectedReason reason) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        callProfile,
                                        "state|reason|desc",
                                        "onCallDisconnected",
                                        reason.getValue(),
                                        TAG);
                                setExcludeFromRecents(mContext, false);
                                String senderId;
                                String extra = "";
                                senderId = callProfile.getInviterUserId();
                                long activeTime = callProfile.getActiveTime();
                                long tmpTime =
                                        activeTime == 0
                                                ? 0
                                                : (System.currentTimeMillis() - activeTime) / 1000;
                                mTime = tmpTime == 0 ? mTime : tmpTime;
                                if (mTime >= 3600) {
                                    extra =
                                            String.format(
                                                    Locale.ROOT,
                                                    "%d:%02d:%02d",
                                                    mTime / 3600,
                                                    (mTime % 3600) / 60,
                                                    (mTime % 60));
                                } else {
                                    extra =
                                            String.format(
                                                    Locale.ROOT,
                                                    "%02d:%02d",
                                                    (mTime % 3600) / 60,
                                                    (mTime % 60));
                                }
                                if (!TextUtils.isEmpty(senderId)) {
                                    switch (callProfile.getConversationType()) {
                                        case PRIVATE:
                                            CallSTerminateMessage callSTerminateMessage =
                                                    new CallSTerminateMessage();
                                            callSTerminateMessage.setReason(reason);
                                            callSTerminateMessage.setMediaType(
                                                    callProfile.getMediaType());
                                            callSTerminateMessage.setExtra(extra);
                                            long serverTime =
                                                    System.currentTimeMillis()
                                                            - RongIMClient.getInstance()
                                                                    .getDeltaTime();
                                            if (senderId.equals(callProfile.getSelfUserId())) {
                                                callSTerminateMessage.setDirection("MO");
                                                IMCenter.getInstance()
                                                        .insertOutgoingMessage(
                                                                Conversation.ConversationType
                                                                        .PRIVATE,
                                                                callProfile.getTargetId(),
                                                                io.rong.imlib.model.Message
                                                                        .SentStatus.SENT,
                                                                callSTerminateMessage,
                                                                serverTime,
                                                                null);
                                            } else {
                                                callSTerminateMessage.setDirection("MT");
                                                io.rong.imlib.model.Message.ReceivedStatus
                                                        receivedStatus =
                                                                new io.rong.imlib.model.Message
                                                                        .ReceivedStatus(0);
                                                IMCenter.getInstance()
                                                        .insertIncomingMessage(
                                                                Conversation.ConversationType
                                                                        .PRIVATE,
                                                                callProfile.getTargetId(),
                                                                senderId,
                                                                receivedStatus,
                                                                callSTerminateMessage,
                                                                serverTime,
                                                                null);
                                            }
                                            break;
                                        case GROUP:
                                            InformationNotificationMessage
                                                    informationNotificationMessage;
                                            serverTime =
                                                    System.currentTimeMillis()
                                                            - RongIMClient.getInstance()
                                                                    .getDeltaTime();
                                            if (reason.equals(
                                                    RongCallCommon.CallDisconnectedReason
                                                            .NO_RESPONSE)) {
                                                informationNotificationMessage =
                                                        InformationNotificationMessage.obtain(
                                                                mContext.getString(
                                                                        R.string
                                                                                .rc_voip_audio_no_response));
                                            } else {
                                                informationNotificationMessage =
                                                        InformationNotificationMessage.obtain(
                                                                mContext.getString(
                                                                        R.string
                                                                                .rc_voip_audio_ended));
                                            }

                                            if (senderId.equals(callProfile.getSelfUserId())) {
                                                IMCenter.getInstance()
                                                        .insertOutgoingMessage(
                                                                Conversation.ConversationType.GROUP,
                                                                callProfile.getTargetId(),
                                                                io.rong.imlib.model.Message
                                                                        .SentStatus.SENT,
                                                                informationNotificationMessage,
                                                                serverTime,
                                                                null);
                                            } else {
                                                IMCenter.getInstance()
                                                        .insertIncomingMessage(
                                                                Conversation.ConversationType.GROUP,
                                                                callProfile.getTargetId(),
                                                                senderId,
                                                                CallKitUtils.getReceivedStatus(
                                                                        reason),
                                                                informationNotificationMessage,
                                                                serverTime,
                                                                null);
                                            }
                                            break;
                                        default:
                                            break;
                                    }
                                }
                                Toast.makeText(
                                                mContext,
                                                mContext.getString(
                                                        R.string.rc_voip_call_terminalted),
                                                Toast.LENGTH_SHORT)
                                        .show();

                                if (wm != null && mView != null && mView.isAttachedToWindow()) {
                                    wm.removeView(mView);
                                    mView = null;
                                }
                                if (wm != null
                                        && remoteVideoContainer != null
                                        && remoteVideoContainer.isAttachedToWindow()) {
                                    wm.removeView(remoteVideoContainer);
                                    remoteVideoContainer.setOnTouchListener(null);
                                    remoteVideoContainer = null;
                                }
                                if (timer != null) {
                                    timer.cancel();
                                    timer = null;
                                }
                                isShown = false;
                                mTime = 0;
                                setAudioMode(AudioManager.MODE_NORMAL);
                                AudioPlayManager.getInstance().setInVoipMode(false);
                                NotificationUtil.getInstance()
                                        .clearNotification(
                                                mContext, BaseCallActivity.CALL_NOTIFICATION_ID);
                                RongCallClient.getInstance()
                                        .setVoIPCallListener(RongCallProxy.getInstance());
                            }

                            @Override
                            public void onRemoteUserJoined(
                                    String userId,
                                    RongCallCommon.CallMediaType mediaType,
                                    int userType,
                                    SurfaceView remoteVideo) {
                                CallKitUtils.isDial = false;
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|desc",
                                        userId,
                                        "onRemoteUserJoined",
                                        TAG);
                            }

                            @Override
                            public void onRemoteUserInvited(
                                    String userId, RongCallCommon.CallMediaType mediaType) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|desc",
                                        userId,
                                        "onRemoteUserInvited",
                                        TAG);
                            }

                            @Override
                            public void onRemoteUserLeft(
                                    String userId, RongCallCommon.CallDisconnectedReason reason) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|desc",
                                        userId,
                                        "onRemoteUserLeft",
                                        TAG);
                            }

                            @Override
                            public void onMediaTypeChanged(
                                    String userId,
                                    RongCallCommon.CallMediaType mediaType,
                                    SurfaceView video) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        RongCallClient.getInstance().getCallSession(),
                                        "state|desc",
                                        "onMediaTypeChanged",
                                        TAG);
                                if (mContext == null || !isShown || wm == null) {
                                    Log.e(
                                            TAG,
                                            "set onMediaTypeChanged Failed CallFloatBoxView is Hiden");
                                    return;
                                }
                                WindowManager.LayoutParams params = createLayoutParams(mContext);
                                if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
                                    if (remoteVideoContainer != null) {
                                        wm.removeView(remoteVideoContainer);
                                        remoteVideoContainer = null;
                                    }
                                    if (mView == null) {
                                        mView =
                                                LayoutInflater.from(mContext)
                                                        .inflate(R.layout.rc_voip_float_box, null);
                                        mView.setOnTouchListener(createTouchListener());
                                        wm.addView(mView, params);
                                        TextView timeV =
                                                (TextView) mView.findViewById(R.id.rc_time);
                                        setupTime(timeV);
                                        ImageView mediaIconV =
                                                (ImageView)
                                                        mView.findViewById(R.id.rc_voip_media_type);
                                        mediaIconV.setImageResource(R.drawable.rc_voip_float_audio);
                                    }
                                } else if (RongCallClient.getInstance().getCallSession() != null) {
                                    RongCallSession callSession =
                                            RongCallClient.getInstance().getCallSession();
                                    if (callSession.getConversationType()
                                            == Conversation.ConversationType.PRIVATE) {
                                        if (mView != null) {
                                            wm.removeView(mView);
                                            mView = null;
                                        }
                                        SurfaceView remoteVideo = null;
                                        for (CallUserProfile profile :
                                                callSession.getParticipantProfileList()) {
                                            if (!TextUtils.equals(
                                                    profile.getUserId(),
                                                    RongIMClient.getInstance()
                                                            .getCurrentUserId())) {
                                                remoteVideo = profile.getVideoView();
                                            }
                                        }
                                        if (remoteVideo != null) {
                                            ViewGroup parent = (ViewGroup) remoteVideo.getParent();
                                            if (parent != null) parent.removeView(remoteVideo);
                                            Resources resources = mContext.getResources();
                                            params.width =
                                                    resources.getDimensionPixelSize(
                                                            R.dimen.callkit_dimen_size_60);
                                            params.height =
                                                    resources.getDimensionPixelSize(
                                                            R.dimen.callkit_dimen_size_80);
                                            remoteVideoContainer = new FrameLayout(mContext);
                                            remoteVideoContainer.addView(
                                                    remoteVideo,
                                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                                    ViewGroup.LayoutParams.MATCH_PARENT);
                                            remoteVideoContainer.setOnTouchListener(
                                                    createTouchListener());
                                            wm.addView(remoteVideoContainer, params);
                                        }
                                    } else if (mView != null) {
                                        ImageView mediaIconV =
                                                (ImageView)
                                                        mView.findViewById(R.id.rc_voip_media_type);
                                        if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
                                            mediaIconV.setImageResource(
                                                    R.drawable.rc_voip_float_audio);
                                        } else {
                                            mediaIconV.setImageResource(
                                                    R.drawable.rc_voip_float_video);
                                        }
                                    }
                                }
                            }

                            @Override
                            public void onError(RongCallCommon.CallErrorCode errorCode) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        RongCallClient.getInstance().getCallSession(),
                                        "code|state|desc",
                                        errorCode.getValue(),
                                        "onError",
                                        TAG);
                                setAudioMode(AudioManager.MODE_NORMAL);
                                AudioPlayManager.getInstance().setInVoipMode(false);
                            }

                            @Override
                            public void onCallConnected(
                                    RongCallSession callInfo, SurfaceView localVideo) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        callInfo,
                                        "state|desc",
                                        "onCallConnected",
                                        TAG);
                                CallKitUtils.isDial = false;
                                setAudioMode(AudioManager.MODE_IN_COMMUNICATION);
                                AudioPlayManager.getInstance().setInVoipMode(true);
                            }

                            @Override
                            public void onRemoteCameraDisabled(String userId, boolean disabled) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|disabled|desc",
                                        userId,
                                        "onRemoteCameraDisabled",
                                        disabled,
                                        TAG);
                            }

                            @Override
                            public void onRemoteMicrophoneDisabled(
                                    String userId, boolean disabled) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|disabled|desc",
                                        userId,
                                        "onRemoteMicrophoneDisabled",
                                        disabled,
                                        TAG);
                            }

                            @Override
                            public void onNetworkReceiveLost(String userId, int lossRate) {}

                            @Override
                            public void onNetworkSendLost(int lossRate, int delay) {}

                            @Override
                            public void onFirstRemoteVideoFrame(
                                    String userId, int height, int width) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|desc",
                                        userId,
                                        "onFirstRemoteVideoFrame",
                                        TAG);
                            }

                            @Override
                            public void onFirstRemoteAudioFrame(String userId) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|desc",
                                        userId,
                                        "onFirstRemoteAudioFrame",
                                        TAG);
                            }

                            @Override
                            public void onAudioLevelSend(String audioLevel) {}

                            public void onRemoteUserPublishVideoStream(
                                    String userId,
                                    String streamId,
                                    String tag,
                                    SurfaceView surfaceView) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|streamId|desc",
                                        userId,
                                        "onRemoteUserPublishVideoStream",
                                        streamId,
                                        TAG);
                            }

                            @Override
                            public void onAudioLevelReceive(HashMap<String, String> audioLevel) {}

                            public void onRemoteUserUnpublishVideoStream(
                                    String userId, String streamId, String tag) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|streamId|desc",
                                        userId,
                                        "onRemoteUserUnpublishVideoStream",
                                        streamId,
                                        TAG);
                            }
                        });
    }

    private static WindowManager.LayoutParams createLayoutParams(Context context) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();

        int type;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && Build.VERSION.SDK_INT < 24) {
            type = WindowManager.LayoutParams.TYPE_TOAST;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            type = WindowManager.LayoutParams.TYPE_PHONE;
        }
        params.type = type;
        params.flags =
                WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM
                        | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;
        params.format = PixelFormat.TRANSLUCENT;
        params.width = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        params.gravity = Gravity.END | Gravity.CENTER_VERTICAL;
        return params;
    }

    private static View.OnTouchListener createTouchListener() {
        return new View.OnTouchListener() {
            float lastX, lastY;
            int oldOffsetX, oldOffsetY;
            int tag = 0;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                final int action = event.getAction();
                float x = event.getX();
                float y = event.getY();
                WindowManager.LayoutParams params =
                        (WindowManager.LayoutParams) v.getLayoutParams();
                if (params == null) {
                    return true;
                }
                if (tag == 0) {
                    oldOffsetX = params.x;
                    oldOffsetY = params.y;
                }
                if (action == MotionEvent.ACTION_DOWN) {
                    lastX = x;
                    lastY = y;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    // 减小偏移量,防止过度抖动
                    params.x += (int) (x - lastX) / 3;
                    params.y += (int) (y - lastY) / 3;
                    tag = 1;
                    //                    if (mView != null)
                    //                        wm.updateViewLayout(mView, params);
                    //                    if (remoteVideoContainer != null) {
                    //                        wm.updateViewLayout(remoteVideoContainer, params);
                    //                    }
                    wm.updateViewLayout(v, params);
                } else if (action == MotionEvent.ACTION_UP) {
                    int newOffsetX = params.x;
                    int newOffsetY = params.y;
                    if (Math.abs(oldOffsetX - newOffsetX) <= 20
                            && Math.abs(oldOffsetY - newOffsetY) <= 20) {
                        if (!CallKitUtils.isFastDoubleClick()) {
                            onClickToResume();
                        }
                    } else {
                        tag = 0;
                    }
                }
                return true;
            }
        };
    }

    public static void showFloatBoxToCall(Context context, Bundle bundle) {
        if (isShown) {
            return;
        }
        mContext = context;
        isShown = true;

        mBundle = bundle;
        wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        final WindowManager.LayoutParams params = createLayoutParams(context);

        mView = LayoutInflater.from(context).inflate(R.layout.rc_voip_float_box, null);
        mView.setOnTouchListener(
                new View.OnTouchListener() {
                    float lastX, lastY;
                    int oldOffsetX, oldOffsetY;
                    int tag = 0;

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        final int action = event.getAction();
                        float x = event.getX();
                        float y = event.getY();
                        if (tag == 0) {
                            oldOffsetX = params.x;
                            oldOffsetY = params.y;
                        }
                        if (action == MotionEvent.ACTION_DOWN) {
                            lastX = x;
                            lastY = y;
                        } else if (action == MotionEvent.ACTION_MOVE) {
                            // 减小偏移量,防止过度抖动
                            params.x += (int) (x - lastX) / 3;
                            params.y += (int) (y - lastY) / 3;
                            tag = 1;
                            if (mView != null) wm.updateViewLayout(mView, params);
                        } else if (action == MotionEvent.ACTION_UP) {
                            int newOffsetX = params.x;
                            int newOffsetY = params.y;
                            if (Math.abs(oldOffsetX - newOffsetX) <= 20
                                    && Math.abs(oldOffsetY - newOffsetY) <= 20) {
                                if (!CallKitUtils.isFastDoubleClick()) {
                                    onClickToResume();
                                }
                            } else {
                                tag = 0;
                            }
                        }
                        return true;
                    }
                });
        wm.addView(mView, params);
        showFBCallTime = (TextView) mView.findViewById(R.id.rc_time);
        showFBCallTime.setVisibility(View.GONE);

        ImageView mediaIconV = (ImageView) mView.findViewById(R.id.rc_voip_media_type);
        RongCallCommon.CallMediaType mediaType =
                RongCallCommon.CallMediaType.valueOf(bundle.getInt("mediaType"));
        if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
            mediaIconV.setImageResource(R.drawable.rc_voip_float_audio);
        } else {
            mediaIconV.setImageResource(R.drawable.rc_voip_float_video);
        }
        RongCallClient.getInstance()
                .setVoIPCallListener(
                        new IRongCallListener() {
                            @Override
                            public void onCallIncoming(
                                    RongCallSession callSession, SurfaceView localVideo) {}

                            @Override
                            public void onCallOutgoing(
                                    RongCallSession callInfo, SurfaceView localVideo) {}

                            @Override
                            public void onRemoteUserRinging(String userId) {}

                            @Override
                            public void onRemoteUserAccept(
                                    String userId, CallMediaType mediaType) {}

                            @Override
                            public void onCallDisconnected(
                                    RongCallSession callProfile,
                                    RongCallCommon.CallDisconnectedReason reason) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        callProfile,
                                        "state|reason|desc",
                                        "onCallDisconnected",
                                        reason.getValue(),
                                        TAG);
                                setExcludeFromRecents(mContext, false);
                                String senderId;
                                String extra = "";
                                senderId = callProfile.getInviterUserId();
                                long activeTime = callProfile.getActiveTime();
                                long tmpTime =
                                        activeTime == 0
                                                ? 0
                                                : (System.currentTimeMillis() - activeTime) / 1000;
                                mTime = tmpTime == 0 ? mTime : tmpTime;
                                if (mTime >= 3600) {
                                    extra =
                                            String.format(
                                                    Locale.ROOT,
                                                    "%d:%02d:%02d",
                                                    mTime / 3600,
                                                    (mTime % 3600) / 60,
                                                    (mTime % 60));
                                } else {
                                    extra =
                                            String.format(
                                                    Locale.ROOT,
                                                    "%02d:%02d",
                                                    (mTime % 3600) / 60,
                                                    (mTime % 60));
                                }
                                if (!TextUtils.isEmpty(senderId)) {
                                    switch (callProfile.getConversationType()) {
                                        case PRIVATE:
                                            CallSTerminateMessage callSTerminateMessage =
                                                    new CallSTerminateMessage();
                                            callSTerminateMessage.setReason(reason);
                                            callSTerminateMessage.setMediaType(
                                                    callProfile.getMediaType());
                                            callSTerminateMessage.setExtra(extra);
                                            if (senderId.equals(callProfile.getSelfUserId())) {
                                                callSTerminateMessage.setDirection("MO");
                                                IMCenter.getInstance()
                                                        .insertOutgoingMessage(
                                                                Conversation.ConversationType
                                                                        .PRIVATE,
                                                                callProfile.getTargetId(),
                                                                io.rong.imlib.model.Message
                                                                        .SentStatus.SENT,
                                                                callSTerminateMessage,
                                                                null);
                                            } else {
                                                callSTerminateMessage.setDirection("MT");
                                                io.rong.imlib.model.Message.ReceivedStatus
                                                        receivedStatus =
                                                                new io.rong.imlib.model.Message
                                                                        .ReceivedStatus(0);
                                                IMCenter.getInstance()
                                                        .insertIncomingMessage(
                                                                Conversation.ConversationType
                                                                        .PRIVATE,
                                                                callProfile.getTargetId(),
                                                                senderId,
                                                                receivedStatus,
                                                                callSTerminateMessage,
                                                                null);
                                            }
                                            break;
                                        case GROUP:
                                            InformationNotificationMessage
                                                    informationNotificationMessage;
                                            if (reason.equals(
                                                    RongCallCommon.CallDisconnectedReason
                                                            .NO_RESPONSE)) {
                                                informationNotificationMessage =
                                                        InformationNotificationMessage.obtain(
                                                                mContext.getString(
                                                                        R.string
                                                                                .rc_voip_audio_no_response));
                                            } else {
                                                informationNotificationMessage =
                                                        InformationNotificationMessage.obtain(
                                                                mContext.getString(
                                                                        R.string
                                                                                .rc_voip_audio_ended));
                                            }

                                            if (senderId.equals(callProfile.getSelfUserId())) {
                                                IMCenter.getInstance()
                                                        .insertOutgoingMessage(
                                                                Conversation.ConversationType.GROUP,
                                                                callProfile.getTargetId(),
                                                                io.rong.imlib.model.Message
                                                                        .SentStatus.SENT,
                                                                informationNotificationMessage,
                                                                null);
                                            } else {
                                                io.rong.imlib.model.Message.ReceivedStatus
                                                        receivedStatus =
                                                                new io.rong.imlib.model.Message
                                                                        .ReceivedStatus(0);
                                                IMCenter.getInstance()
                                                        .insertIncomingMessage(
                                                                Conversation.ConversationType.GROUP,
                                                                callProfile.getTargetId(),
                                                                senderId,
                                                                receivedStatus,
                                                                informationNotificationMessage,
                                                                null);
                                            }
                                            break;
                                        default:
                                            break;
                                    }
                                }
                                Toast.makeText(
                                                mContext,
                                                mContext.getString(
                                                        R.string.rc_voip_call_terminalted),
                                                Toast.LENGTH_SHORT)
                                        .show();

                                if (wm != null && mView != null) {
                                    wm.removeView(mView);
                                    if (null != timer) {
                                        timer.cancel();
                                        timer = null;
                                    }
                                    isShown = false;
                                    mView = null;
                                    mTime = 0;
                                }
                                setAudioMode(AudioManager.MODE_NORMAL);
                                AudioPlayManager.getInstance().setInVoipMode(false);
                                NotificationUtil.getInstance()
                                        .clearNotification(
                                                mContext, BaseCallActivity.CALL_NOTIFICATION_ID);
                                RongCallClient.getInstance()
                                        .setVoIPCallListener(RongCallProxy.getInstance());
                            }

                            @Override
                            public void onRemoteUserLeft(
                                    String userId, RongCallCommon.CallDisconnectedReason reason) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|desc",
                                        userId,
                                        "onRemoteUserLeft",
                                        TAG);
                            }

                            @Override
                            public void onMediaTypeChanged(
                                    String userId,
                                    RongCallCommon.CallMediaType mediaType,
                                    SurfaceView video) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        RongCallClient.getInstance().getCallSession(),
                                        "state|desc",
                                        "onMediaTypeChanged",
                                        TAG);
                                ImageView mediaIconV =
                                        (ImageView) mView.findViewById(R.id.rc_voip_media_type);
                                if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
                                    mediaIconV.setImageResource(R.drawable.rc_voip_float_audio);
                                } else {
                                    mediaIconV.setImageResource(R.drawable.rc_voip_float_video);
                                }
                            }

                            @Override
                            public void onError(RongCallCommon.CallErrorCode errorCode) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        RongCallClient.getInstance().getCallSession(),
                                        "code|state|desc",
                                        errorCode.getValue(),
                                        "onError",
                                        TAG);
                                setAudioMode(AudioManager.MODE_NORMAL);
                                AudioPlayManager.getInstance().setInVoipMode(false);
                            }

                            @Override
                            public void onCallConnected(
                                    RongCallSession callInfo, SurfaceView localVideo) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        callInfo,
                                        "state|desc",
                                        "onCallConnected",
                                        TAG);
                                if (CallKitUtils.isDial && isShown) {
                                    CallFloatBoxView.showFloatBoxToCallTime();
                                    CallKitUtils.isDial = false;
                                }
                                AudioPlayManager.getInstance().setInVoipMode(true);
                                setAudioMode(AudioManager.MODE_IN_COMMUNICATION);
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
                                        TAG);
                                if (CallKitUtils.isDial && isShown) {
                                    CallFloatBoxView.showFloatBoxToCallTime();
                                    CallKitUtils.isDial = false;
                                }
                            }

                            @Override
                            public void onRemoteUserInvited(
                                    String userId, RongCallCommon.CallMediaType mediaType) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|desc",
                                        userId,
                                        "onRemoteUserInvited",
                                        TAG);
                            }

                            @Override
                            public void onRemoteCameraDisabled(String userId, boolean disabled) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|disabled|desc",
                                        userId,
                                        "onRemoteCameraDisabled",
                                        disabled,
                                        TAG);
                            }

                            @Override
                            public void onRemoteMicrophoneDisabled(
                                    String userId, boolean disabled) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|disabled|desc",
                                        userId,
                                        "onRemoteMicrophoneDisabled",
                                        disabled,
                                        TAG);
                            }

                            @Override
                            public void onNetworkReceiveLost(String userId, int lossRate) {}

                            @Override
                            public void onNetworkSendLost(int lossRate, int delay) {}

                            @Override
                            public void onFirstRemoteVideoFrame(
                                    String userId, int height, int width) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|desc",
                                        userId,
                                        "onFirstRemoteVideoFrame",
                                        TAG);
                            }

                            @Override
                            public void onFirstRemoteAudioFrame(String userId) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|desc",
                                        userId,
                                        "onFirstRemoteAudioFrame",
                                        TAG);
                            }

                            @Override
                            public void onAudioLevelSend(String audioLevel) {}

                            public void onRemoteUserPublishVideoStream(
                                    String userId,
                                    String streamId,
                                    String tag,
                                    SurfaceView surfaceView) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|streamId|desc",
                                        userId,
                                        "onRemoteUserPublishVideoStream",
                                        streamId,
                                        TAG);
                            }

                            @Override
                            public void onAudioLevelReceive(HashMap<String, String> audioLevel) {}

                            public void onRemoteUserUnpublishVideoStream(
                                    String userId, String streamId, String tag) {
                                ReportUtil.appStatus(
                                        ReportUtil.TAG.CALL_LISTENER,
                                        "userId|state|streamId|desc",
                                        userId,
                                        "onRemoteUserUnpublishVideoStream",
                                        streamId,
                                        TAG);
                            }
                        });
    }

    /** * 调用showFloatBoxToCall 之后 调用该方法设置 */
    public static void showFloatBoxToCallTime() {
        if (!isShown) {
            return;
        }
        RongCallSession session = RongCallClient.getInstance().getCallSession();
        long activeTime = session != null ? session.getActiveTime() : 0;
        mTime = activeTime == 0 ? 0 : (System.currentTimeMillis() - activeTime) / 1000;
        //        mView = LayoutInflater.from(context).inflate(R.layout.rc_voip_float_box, null);
        //        TextView timeV = (TextView) mView.findViewById(R.id.rc_time);
        if (null != showFBCallTime) {
            setupTime(showFBCallTime);
        }
    }

    public static void hideFloatBox() {
        setExcludeFromRecents(mContext, false);
        RongCallClient.getInstance().setVoIPCallListener(RongCallProxy.getInstance());
        if (isShown) {
            if (mView != null) {
                wm.removeView(mView);
            }
            mView = null;
            if (remoteVideoContainer != null) {
                wm.removeView(remoteVideoContainer);
            }
            remoteVideoContainer = null;
            if (null != timer) {
                timer.cancel();
                timer = null;
            }
            isShown = false;
            mView = null;
            mTime = 0;
            mBundle = null;
            showFBCallTime = null;
        }
    }

    public static Intent getResumeIntent() {
        if (mBundle == null) {
            return null;
        }
        mBundle.putBoolean("isDial", isDial);
        RongCallClient.getInstance().setVoIPCallListener(RongCallProxy.getInstance());
        Intent intent = new Intent(mBundle.getString("action"));
        intent.putExtra("floatbox", mBundle);
        intent.setPackage(mContext.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("callAction", RongCallAction.ACTION_RESUME_CALL.getName());

        return intent;
    }

    public static void onClickToResume() {
        // 当快速双击悬浮窗时，第一次点击之后会把mBundle置为空，第二次点击的时候出现NPE
        if (mBundle == null) {
            RLog.d(TAG, "onClickToResume mBundle is null");
            return;
        }
        if (activityResuming) {
            return;
        }
        activityResuming = true;
        boolean muteCamera = mBundle.getBoolean("muteCamera");
        if (mBundle.getInt("mediaType") == RongCallCommon.CallMediaType.VIDEO.getValue()
                && !isDial
                && !muteCamera) {
            RLog.d(TAG, "onClickToResume setEnableLocalVideo(true)");
            RongCallClient.getInstance().setEnableLocalVideo(true);
        }
        mBundle.putBoolean("isDial", isDial);
        RongCallClient.getInstance().setVoIPCallListener(RongCallProxy.getInstance());
        Intent intent = new Intent(mBundle.getString("action"));
        intent.setPackage(mContext.getPackageName());
        intent.putExtra("floatbox", mBundle);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("callAction", RongCallAction.ACTION_RESUME_CALL.getName());

        ActivityStartCheckUtils.getInstance()
                .startActivity(
                        mContext,
                        intent,
                        BaseCallActivity.class.getSimpleName(),
                        new ActivityStartCheckUtils.ActivityStartResultCallback() {
                            @Override
                            public void onStartActivityResult(boolean isActivityStarted) {
                                activityResuming = false;
                                if (isActivityStarted) {
                                    mBundle = null;
                                } else {
                                    Toast.makeText(
                                                    mContext,
                                                    mContext.getString(
                                                            R.string
                                                                    .rc_background_start_actvity_deny),
                                                    Toast.LENGTH_SHORT)
                                            .show();
                                }
                            }
                        });
    }

    private static void setupTime(final TextView timeView) {
        final Handler handler = new Handler(Looper.getMainLooper());
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        TimerTask task =
                new TimerTask() {
                    @Override
                    public void run() {
                        handler.post(
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        mTime++;
                                        if (timeView != null) {
                                            if (mTime >= 3600) {
                                                timeView.setText(
                                                        String.format(
                                                                Locale.ROOT,
                                                                "%d:%02d:%02d",
                                                                mTime / 3600,
                                                                (mTime % 3600) / 60,
                                                                (mTime % 60)));
                                                timeView.setVisibility(View.VISIBLE);
                                            } else {
                                                timeView.setText(
                                                        String.format(
                                                                Locale.ROOT,
                                                                "%02d:%02d",
                                                                (mTime % 3600) / 60,
                                                                (mTime % 60)));
                                                timeView.setVisibility(View.VISIBLE);
                                            }
                                        }
                                    }
                                });
                    }
                };

        timer = new Timer();
        timer.schedule(task, 0, 1000);
    }

    private static void setAudioMode(int mode) {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(mode);
        }
    }

    /**
     * 设置app是否现在在最近列表中，
     *
     * @param appContext
     * @param excluded
     */
    private static void setExcludeFromRecents(Context appContext, boolean excluded) {
        if (appContext == null) return;
        ActivityManager manager =
                (ActivityManager) appContext.getSystemService(Context.ACTIVITY_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            for (ActivityManager.AppTask task : manager.getAppTasks()) {
                task.setExcludeFromRecents(excluded);
            }
        }
    }

    public static boolean isCallFloatBoxShown() {
        return isShown;
    }
}
