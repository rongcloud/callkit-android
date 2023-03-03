package io.rong.callkit;

import static io.rong.callkit.CallSelectMemberActivity.DISCONNECT_ACTION;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.rongcloud.rtc.api.RCRTCEngine;
import cn.rongcloud.rtc.api.stream.RCRTCVideoView;
import cn.rongcloud.rtc.audioroute.RCAudioRouteType;
import cn.rongcloud.rtc.base.RCRTCStream;
import cn.rongcloud.rtc.core.RendererCommon;
import cn.rongcloud.rtc.utils.FinLog;
import io.rong.callkit.util.BluetoothUtil;
import io.rong.callkit.util.CallKitUtils;
import io.rong.callkit.util.DefaultPushConfig;
import io.rong.callkit.util.HeadsetInfo;
import io.rong.callkit.util.RingingMode;
import io.rong.callkit.util.RongCallPermissionUtil;
import io.rong.calllib.CallUserProfile;
import io.rong.calllib.ReportUtil;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
import io.rong.calllib.StartIncomingPreviewCallback;
import io.rong.calllib.StreamProfile;
import io.rong.calllib.Utils;
import io.rong.calllib.message.MultiCallEndMessage;
import io.rong.common.RLog;
import io.rong.imkit.IMCenter;
import io.rong.imkit.userinfo.RongUserInfoManager;
import io.rong.imlib.IRongCoreCallback;
import io.rong.imlib.IRongCoreEnum;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.discussion.base.RongDiscussionClient;
import io.rong.imlib.discussion.model.Discussion;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Group;
import io.rong.imlib.model.UserInfo;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** <a href="http://support.rongcloud.cn/kb/Njcy">如何实现不基于于群组的voip</a> */
public class MultiVideoCallActivity extends BaseCallActivity {
    private static final String TAG = "MultiVideoCallActivity";
    private static final String REMOTE_FURFACEVIEW_TAG = "surfaceview"; //
    private static final String REMOTE_VIEW_TAG = "remoteview"; // rc_voip_viewlet_remote_user tag
    private static final String VOIP_USERNAME_TAG =
            "username"; // topContainer.findViewById(R.id.rc_voip_user_name);
    private static final String VOIP_PARTICIPANT_PORTAIT_CONTAINER_TAG =
            "participantPortraitView"; // 被叫方显示头像容器tag
    RongCallSession callSession;
    SurfaceView localView;
    ContainerLayout localViewContainer;
    LinearLayout remoteViewContainer;
    LinearLayout remoteViewContainer2;
    LinearLayout topContainer;
    LinearLayout waitingContainer;
    LinearLayout bottomButtonContainer;
    LinearLayout participantPortraitContainer;
    LinearLayout portraitContainer1; // 维护未接听时，所有成员列表
    LayoutInflater inflater;
    // 通话中的最小化按钮、呼叫中的最小化按钮
    ImageView minimizeButton, rc_voip_multiVideoCall_minimize;
    ImageView moreButton;
    ImageView switchCameraButton;
    ImageView userPortrait;
    LinearLayout infoLayout;
    ImageView signalView;
    TextView userNameView;
    private WebView whiteboardView;
    private RelativeLayout mRelativeWebView;
    private int remoteUserViewWidth;
    //    private int  remoteUserViewHeight;
    // 主叫、通话中 远端View
    private float remoteUserViewMarginsRight = 10;
    private float remoteUserViewMarginsLeft = 20;

    boolean isFullScreen = false;
    boolean isMuteMIC = false;
    boolean startForCheckPermissions = false;

    String localViewUserId;
    private CallOptionMenu optionMenu;
    ImageView muteButtion;
    ImageView disableCameraButtion;
    CallPromptDialog dialog = null;
    RelativeLayout observerLayout;
    private ImageView iv_large_preview_mutilvideo, iv_large_preview_Mask;
    private String topUserName = "", topUserNameTag = "";

    @Override
    @TargetApi(23)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && RongCallClient.getInstance() == null) {
            // 音视频请求权限时，用户在设置页面取消权限，导致应用重启，退出当前activity.
            finish();
            return;
        }
        setContentView(R.layout.rc_voip_multi_video_call);
        Intent intent = getIntent();
        startForCheckPermissions = intent.getBooleanExtra("checkPermissions", false);
        boolean val =
                requestCallPermissions(
                        RongCallCommon.CallMediaType.VIDEO, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        Log.i(TAG, "onCreate initViews requestCallPermissions=" + val);
        if (val) {
            Log.i(TAG, "--- onCreate  initViews------");
            initViews();
            setupIntent();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Log.d(TAG, "onNewIntent: [intent]");
        startForCheckPermissions = intent.getBooleanExtra("checkPermissions", false);
        super.onNewIntent(intent);
        boolean bool =
                requestCallPermissions(
                        RongCallCommon.CallMediaType.VIDEO, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        Log.i(TAG, "mult onNewIntent==" + bool);
        if (bool) {
            Log.i(TAG, "mult onNewIntent initViews");
            initViews();
            setupIntent();
        }
    }

    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        Log.d(TAG, "onRequestPermissionsResult: " + requestCode);
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS:
                if (RongCallPermissionUtil.checkVideoCallNeedPermission(this)) {
                    if (startForCheckPermissions) {
                        startForCheckPermissions = false;
                        RongCallClient.getInstance().onPermissionGranted();
                    } else {
                        initViews();
                        setupIntent();
                    }
                } else {
                    if (permissions.length > 0) {
                        RongCallClient.getInstance().onPermissionDenied();
                        Toast.makeText(
                                        this,
                                        getString(R.string.rc_voip_relevant_permissions),
                                        Toast.LENGTH_SHORT)
                                .show();
                        finish();
                    }
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        Log.i(TAG, "mult  onActivityResult requestCode=" + requestCode);
        super.onActivityResult(requestCode, resultCode, data);
        callSession = RongCallClient.getInstance().getCallSession();
        if (requestCode == REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS) {
            if (RongCallPermissionUtil.checkVideoCallNeedPermission(this)) {
                if (startForCheckPermissions) {
                    startForCheckPermissions = false;
                    RongCallClient.getInstance().onPermissionGranted();
                } else {
                    Log.i(TAG, "mult  onActivityResult initView");
                    initViews();
                    setupIntent();
                }
            } else {
                if (startForCheckPermissions) {
                    startForCheckPermissions = false;
                    RongCallClient.getInstance().onPermissionDenied();
                } else {
                    finish();
                }
            }

        } else if (requestCode == REQUEST_CODE_ADD_MEMBER) {
            if (resultCode == RESULT_OK) {
                if (data.getBooleanExtra("remote_hangup", false)) {
                    RLog.d(TAG, "Remote exit, end the call.");
                    return;
                }
            }
            if (callSession.getEndTime() != 0) {
                finish();
                return;
            }
            setShouldShowFloat(true);
            if (resultCode == RESULT_OK) {
                ArrayList<String> invited = data.getStringArrayListExtra("invited");
                ArrayList<String> observers = data.getStringArrayListExtra("observers");
                List<CallUserProfile> callUserProfiles = callSession.getParticipantProfileList();
                Iterator<String> iterator = invited.iterator();
                while (iterator.hasNext()) {
                    String id = iterator.next();
                    for (CallUserProfile profile : callUserProfiles) {
                        if (profile.getUserId().equals(id)) {
                            iterator.remove();
                        }
                    }
                }
                RongCallClient.getInstance()
                        .addParticipants(callSession.getCallId(), invited, observers);
            }
        } else if (requestCode == REQUEST_CODE_ADD_MEMBER_NONE) {
            try {
                if (callSession.getEndTime() != 0) {
                    finish();
                    return;
                }
                setShouldShowFloat(true);
                if (resultCode == RESULT_OK) {
                    ArrayList<String> invited = data.getStringArrayListExtra("pickedIds");
                    RongCallClient.getInstance()
                            .addParticipants(callSession.getCallId(), invited, null);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CallKitUtils.callConnected = false;
        if (localViewContainer != null) {
            localViewContainer.setIsNeedFillScrren(true);
        }
    }

    @Override
    public String onSaveFloatBoxState(Bundle bundle) {
        super.onSaveFloatBoxState(bundle);
        String intentAction = getIntent().getAction();
        bundle.putBoolean(EXTRA_BUNDLE_KEY_MUTECAMERA, isMuteCamera);
        bundle.putBoolean(EXTRA_BUNDLE_KEY_MUTEMIC, isMuteMIC);
        bundle.putString(EXTRA_BUNDLE_KEY_LOCALVIEWUSERID, localViewUserId);
        bundle.putString(EXTRA_BUNDLE_KEY_CALLACTION, RongCallAction.ACTION_RESUME_CALL.getName());
        bundle.putInt(EXTRA_BUNDLE_KEY_MEDIATYPE, RongCallCommon.CallMediaType.VIDEO.getValue());
        bundle.putString(EXTRA_BUNDLE_KEY_USER_TOP_NAME, topUserName);
        bundle.putString(EXTRA_BUNDLE_KEY_USER_TOP_NAME_TAG, topUserNameTag);
        return intentAction;
    }

    @Override
    public void onRestoreFloatBox(Bundle bundle) {
        super.onRestoreFloatBox(bundle);
        try {
            Log.i(TAG, "--- onRestoreFloatBox  ------");
            callSession = RongCallClient.getInstance().getCallSession();
            if (bundle != null) {
                RongCallAction callAction = RongCallAction.valueOf(bundle.getString("callAction"));
                if (!callAction.equals(RongCallAction.ACTION_RESUME_CALL)) {
                    return;
                }
                localViewUserId = bundle.getString(EXTRA_BUNDLE_KEY_LOCALVIEWUSERID);
                isMuteCamera = bundle.getBoolean(EXTRA_BUNDLE_KEY_MUTECAMERA);
                isMuteMIC = bundle.getBoolean(EXTRA_BUNDLE_KEY_MUTEMIC);
                topUserName = bundle.getString(EXTRA_BUNDLE_KEY_USER_TOP_NAME);
                topUserNameTag = bundle.getString(EXTRA_BUNDLE_KEY_USER_TOP_NAME_TAG);
                if (callSession == null) {
                    setShouldShowFloat(false);
                    finish();
                    return;
                }

                boolean isLocalViewExist = false;
                for (CallUserProfile profile : callSession.getParticipantProfileList()) {
                    if (profile.getUserId().equals(localViewUserId)) {
                        isLocalViewExist = true;
                        break;
                    }
                    for (StreamProfile streamProfile : profile.streamProfiles) {
                        if (TextUtils.equals(streamProfile.streamId, localViewUserId)) {
                            isLocalViewExist = true;
                            break;
                        }
                    }
                    if (isLocalViewExist) {
                        break;
                    }
                }
                if (remoteViewContainer2 != null) {
                    remoteViewContainer2.removeAllViews();
                }
                for (CallUserProfile profile : callSession.getParticipantProfileList()) {
                    String currentUserId = RongIMClient.getInstance().getCurrentUserId();
                    if (profile.getUserId().equals(localViewUserId)
                            || (!isLocalViewExist && profile.getUserId().equals(currentUserId))) {
                        localView = profile.getVideoView();
                    }
                    if (isLocalViewExist) {
                        for (StreamProfile streamProfile : profile.streamProfiles) {
                            if (TextUtils.equals(streamProfile.streamId, localViewUserId)) {
                                localView = streamProfile.videoView;
                            }
                        }
                    }
                    if (localView != null) {
                        if (localView.getParent() != null) {
                            ((ViewGroup) localView.getParent()).removeAllViews();
                        }
                        String tag = (String) localView.getTag();
                        localViewUserId = tag.substring(0, tag.indexOf(REMOTE_FURFACEVIEW_TAG));
                        localView.setZOrderOnTop(false);
                        localView.setZOrderMediaOverlay(false);
                        localViewContainer.addView(localView);
                        localViewContainer.addView(getObserverLayout());
                        localView.setTag(
                                CallKitUtils.getStitchedContent(
                                        localViewUserId, REMOTE_FURFACEVIEW_TAG));
                        TextView userNameView =
                                (TextView) topContainer.findViewById(R.id.rc_voip_user_name);
                        userNameView.setLines(1);
                        userNameView.setEllipsize(TextUtils.TruncateAt.END);
                        if (!TextUtils.isEmpty(topUserName)) {
                            userNameView.setTag(topUserNameTag);
                            userNameView.setText(CallKitUtils.nickNameRestrict(topUserName));
                        } else {
                            userNameView.setTag(
                                    CallKitUtils.getStitchedContent(
                                            localViewUserId, VOIP_USERNAME_TAG));
                            UserInfo userInfo =
                                    RongUserInfoManager.getInstance().getUserInfo(localViewUserId);
                            if (userInfo != null) {
                                userNameView.setText(
                                        CallKitUtils.nickNameRestrict(userInfo.getName()));
                            } else {
                                userNameView.setText(localViewUserId);
                            }
                        }
                        break;
                    }
                }
                if (!(boolean) bundle.get("isDial")) {
                    // 已经有用户接听
                    onCallConnected(callSession, null);
                } else {
                    // 无用户接听
                    updateRemoteVideoViews(callSession);
                    FrameLayout bottomButtonLayout =
                            (FrameLayout)
                                    inflater.inflate(
                                            R.layout.rc_voip_multi_video_calling_bottom_view, null);
                    bottomButtonLayout
                            .findViewById(R.id.rc_voip_call_mute)
                            .setVisibility(View.GONE);
                    bottomButtonLayout
                            .findViewById(R.id.rc_voip_disable_camera)
                            .setVisibility(View.GONE);
                    bottomButtonLayout.findViewById(R.id.rc_voip_handfree).setVisibility(View.GONE);
                    bottomButtonContainer.removeAllViews();
                    bottomButtonContainer.addView(bottomButtonLayout);
                    topContainer.setVisibility(View.GONE);
                    waitingContainer.setVisibility(View.VISIBLE);
                    remoteViewContainer.setVisibility(View.VISIBLE);
                    participantPortraitContainer.setVisibility(View.GONE);
                    bottomButtonContainer.setVisibility(View.VISIBLE);
                    rc_voip_multiVideoCall_minimize.setVisibility(View.GONE);

                    callRinging(RingingMode.Outgoing);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "MultiVideoCallActivity onRestoreFloatBox Error=" + e.getMessage());
        }
    }

    private void incomingPreview() {
        RongCallClient.getInstance().setEnableLocalAudio(true);
        RongCallClient.getInstance().setEnableLocalVideo(true);
        RongCallClient.getInstance()
                .startIncomingPreview(
                        new StartIncomingPreviewCallback() {
                            @Override
                            public void onDone(boolean isFront, SurfaceView localVideo) {
                                localView = localVideo;
                                ((RCRTCVideoView) localView)
                                        .setScalingType(
                                                RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);
                                //        localView.setZOrderOnTop(true);
                                //        localView.setZOrderMediaOverlay(true);
                                ViewParent parent = localView.getParent();
                                if (parent != null) {
                                    ((ViewGroup) parent).removeView(localView);
                                }
                                localViewContainer.addView(localView);

                                // 加载观察者布局 默认不显示
                                localViewContainer.addView(getObserverLayout());
                                localViewUserId = RongIMClient.getInstance().getCurrentUserId();
                                localView.setTag(
                                        CallKitUtils.getStitchedContent(
                                                localViewUserId, REMOTE_FURFACEVIEW_TAG));
                            }

                            @Override
                            public void onError(int errorCode) {}
                        });
    }

    /**
     * 电话已拨出。 主叫端拨出电话后
     *
     * @param callSession 通话实体。
     * @param localVideo 本地 camera 信息。
     */
    @Override
    public void onCallOutgoing(final RongCallSession callSession, SurfaceView localVideo) {
        super.onCallOutgoing(callSession, localVideo);
        this.callSession = callSession;
        RongCallClient.getInstance().setEnableLocalAudio(true);
        RongCallClient.getInstance().setEnableLocalVideo(true);
        localView = localVideo;
        callRinging(RingingMode.Outgoing);
        ((RCRTCVideoView) localView)
                .setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_BALANCED);
        //        localView.setZOrderOnTop(true);
        //        localView.setZOrderMediaOverlay(true);
        localViewContainer.addView(localView);

        // 加载观察者布局 默认不显示
        localViewContainer.addView(getObserverLayout());

        localViewUserId = RongIMClient.getInstance().getCurrentUserId();
        localView.setTag(CallKitUtils.getStitchedContent(localViewUserId, REMOTE_FURFACEVIEW_TAG));
    }

    @Override
    public void onFirstRemoteVideoFrame(String userId, int height, int width) {
        Log.d("bugtags", "onFirstRemoteVideoFrame,uid :" + userId);
        if (remoteViewContainer2 == null) {
            Log.e(
                    "bugtags",
                    "onFirstRemoteVideoFrame()->remoteViewContainer2 is empty.userId : " + userId);
            return;
        }

        View singleRemoteView =
                remoteViewContainer2.findViewWithTag(
                        CallKitUtils.getStitchedContent(userId, REMOTE_VIEW_TAG));
        if (singleRemoteView == null) {
            Log.e("bugtags", "onFirstRemoteVideoFrame(). singleRemoteView is empty");

            if (localViewContainer == null || localViewContainer.getChildCount() == 0) {
                Log.e("bugtags", "onFirstRemoteVideoFrame(). localViewContainer is empty");
            } else {
                for (int i = 0; i < localViewContainer.getChildCount(); i++) {
                    if (localViewContainer.getChildAt(i) instanceof RCRTCVideoView) {
                        ((RCRTCVideoView) localViewContainer.getChildAt(i)).setZOrderOnTop(false);
                        ((RCRTCVideoView) localViewContainer.getChildAt(i))
                                .setZOrderMediaOverlay(false);
                        ((RCRTCVideoView) localViewContainer.getChildAt(i))
                                .setBackgroundColor(Color.TRANSPARENT);
                        break;
                    }
                }
            }
            return;
        }
        View stateView = singleRemoteView.findViewById(R.id.user_status);
        if (stateView != null) {
            stateView.setVisibility(View.GONE);
        }

        FrameLayout remoteVideoView =
                (FrameLayout) singleRemoteView.findViewById(R.id.viewlet_remote_video_user);
        if (remoteVideoView == null) {
            return;
        }
        int childCount = remoteVideoView.getChildCount();
        for (int i = 0; i < childCount; i++) {
            if (remoteVideoView.getChildAt(i) != null
                    && remoteVideoView.getChildAt(i) instanceof RCRTCVideoView) {
                ((RCRTCVideoView) remoteVideoView.getChildAt(i)).setZOrderOnTop(true);
                ((RCRTCVideoView) remoteVideoView.getChildAt(i)).setZOrderMediaOverlay(true);
                remoteVideoView.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
                break;
            }
        }
    }

    /**
     * 被叫端加入通话。 主叫端拨出电话，被叫端收到请求后，加入通话，回调 onRemoteUserJoined。
     *
     * @param userId 加入用户的 id。
     * @param mediaType 加入用户的媒体类型，audio or video。
     * @param userType 加入用户的类型，1:正常用户,2:观察者。
     * @param remoteVideo 加入用户者的 camera 信息。
     */
    @Override
    public void onRemoteUserJoined(
            String userId,
            RongCallCommon.CallMediaType mediaType,
            int userType,
            SurfaceView remoteVideo) {
        remoteVideo.setBackgroundColor(Color.BLACK);
        stopRing();
        if (localViewContainer != null && localViewContainer.getVisibility() != View.VISIBLE) {
            localViewContainer.setVisibility(View.VISIBLE);
            if (null != iv_large_preview_mutilvideo) {
                iv_large_preview_mutilvideo.setVisibility(View.GONE);
            }
            if (null != iv_large_preview_Mask) {
                iv_large_preview_Mask.setVisibility(View.GONE);
            }
        }
        if (localViewUserId != null && localViewUserId.equals(userId)) {
            return;
        }
        View singleRemoteView =
                remoteViewContainer.findViewWithTag(
                        CallKitUtils.getStitchedContent(userId, REMOTE_VIEW_TAG));

        if (singleRemoteView == null) {
            singleRemoteView = addSingleRemoteView(userId, userType);
        }
        addRemoteVideo(singleRemoteView, remoteVideo, userId, false);
        singleRemoteView.findViewById(R.id.user_status).setVisibility(View.GONE);
        singleRemoteView.findViewById(R.id.user_name).setVisibility(View.GONE);
    }

    @Override
    public void onRemoteUserLeft(String userId, RongCallCommon.CallDisconnectedReason reason) {
        // 通话过程中 toast "通话结束"有些突兀，所以只有远端忙线和拒绝时我们提醒用户
        if (reason.equals(RongCallCommon.CallDisconnectedReason.REMOTE_BUSY_LINE)
                || reason.equals(RongCallCommon.CallDisconnectedReason.REMOTE_REJECT)) {
            super.onRemoteUserLeft(userId, reason);
        }
        if (isTopContainerUserExit(userId)) {
            return;
        }
        String delUserid = userId;
        // incomming state
        if (participantPortraitContainer != null
                && participantPortraitContainer.getVisibility() == View.VISIBLE) {
            View participantView =
                    participantPortraitContainer.findViewWithTag(
                            CallKitUtils.getStitchedContent(
                                    userId, VOIP_PARTICIPANT_PORTAIT_CONTAINER_TAG));
            if (participantView == null) {
                return;
            }
            LinearLayout portraitContainer = (LinearLayout) participantView.getParent();
            portraitContainer.removeView(participantView);
        }
        // incoming状态，localViewUserId为空
        if (localViewUserId == null) {
            return;
        }
        if (localViewUserId.equals(userId)) {
            localViewContainer.removeAllViews();
            delUserid = RongIMClient.getInstance().getCurrentUserId();
            // 拿到本地视频流装载对象
            FrameLayout remoteVideoView =
                    (FrameLayout) remoteViewContainer.findViewWithTag(delUserid);
            localView = (SurfaceView) remoteVideoView.getChildAt(0);
            remoteVideoView.removeAllViews();
            localView.setZOrderOnTop(false);
            localViewContainer.addView(localView); // 将本地的给大屏

            localViewContainer.addView(getObserverLayout());

            TextView topUserNameView = (TextView) topContainer.findViewById(R.id.rc_voip_user_name);
            topUserNameView.setTag(CallKitUtils.getStitchedContent(delUserid, VOIP_USERNAME_TAG));
            UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(delUserid);
            if (userInfo != null) {
                topUserNameView.setText(CallKitUtils.nickNameRestrict(userInfo.getName()));
            } else {
                topUserNameView.setText(delUserid);
            }
            localViewUserId = delUserid;
        }
        if (remoteViewContainer2 != null && !TextUtils.isEmpty(delUserid)) { // 删除退出用户的头像框
            View singleRemoteView =
                    remoteViewContainer2.findViewWithTag(
                            CallKitUtils.getStitchedContent(delUserid, REMOTE_VIEW_TAG));
            if (singleRemoteView == null) {
                return;
            }
            remoteViewContainer2.removeView(singleRemoteView);
        }
    }

    private boolean isTopContainerUserExit(String userId) {
        if (CallKitUtils.callConnected) {
            return false;
        }
        if (callSession != null
                && TextUtils.equals(callSession.getInviterUserId(), userId)
                && portraitContainer1 != null) {
            View userPortraitView = portraitContainer1.getChildAt(0);
            if (userPortraitView != null && userPortraitView.getTag() != null) {
                String tag = (String) userPortraitView.getTag();
                String firstUserId = tag.replace(VOIP_PARTICIPANT_PORTAIT_CONTAINER_TAG, "");
                UserInfo firstUserInfo = RongUserInfoManager.getInstance().getUserInfo(firstUserId);
                // topContainer
                TextView userNameView =
                        (TextView) topContainer.findViewById(R.id.rc_voip_user_name);
                userNameView.setTag(
                        CallKitUtils.getStitchedContent(firstUserId, VOIP_USERNAME_TAG));
                if (firstUserInfo != null) {
                    userNameView.setText(CallKitUtils.nickNameRestrict(firstUserInfo.getName()));
                    if (firstUserInfo.getPortraitUri() != null) {
                        RongCallKit.getKitImageEngine()
                                .loadPortrait(
                                        getBaseContext(),
                                        firstUserInfo.getPortraitUri(),
                                        R.drawable.rc_default_portrait,
                                        userPortrait);
                        userPortrait.setVisibility(View.VISIBLE);
                    }
                } else {
                    userNameView.setText(firstUserId);
                }
                //
                if (participantPortraitContainer != null
                        && participantPortraitContainer.getVisibility() == View.VISIBLE) {
                    View participantView =
                            participantPortraitContainer.findViewWithTag(
                                    CallKitUtils.getStitchedContent(
                                            firstUserId, VOIP_PARTICIPANT_PORTAIT_CONTAINER_TAG));
                    if (participantView != null) {
                        LinearLayout portraitContainer = (LinearLayout) participantView.getParent();
                        portraitContainer.removeView(participantView);
                    }
                }
                //
                View firstView = portraitContainer1.getChildAt(0);
                LinearLayout.LayoutParams layoutParams = (LayoutParams) firstView.getLayoutParams();
                layoutParams.setMargins(
                        CallKitUtils.dp2px(remoteUserViewMarginsLeft, MultiVideoCallActivity.this),
                        0,
                        CallKitUtils.dp2px(remoteUserViewMarginsRight, MultiVideoCallActivity.this),
                        0);
                firstView.requestLayout();
                return true;
            }
        }
        return false;
    }

    private ArrayList<String> getInvitedList() {
        ArrayList<String> invitedList = new ArrayList<>();
        List<CallUserProfile> list = callSession.getParticipantProfileList();
        List<String> incomingObserverUserList = callSession.getObserverUserList();
        for (CallUserProfile profile : list) {
            if (!profile.getUserId().equals(callSession.getCallerUserId())) {
                if (null != incomingObserverUserList
                        && !incomingObserverUserList.contains(profile.getUserId())) {
                    invitedList.add(profile.getUserId());
                }
            }
        }
        return invitedList;
    }

    @Override
    public void onRemoteUserPublishVideoStream(
            String userId, String streamId, String tag, SurfaceView surfaceView) {
        if (TextUtils.equals(userId, localViewUserId)) {
            return;
        }
        View singleRemoteView = null;
        if (remoteViewContainer2 != null) {
            // 先去找是否已经添加了对方的viewGroup，没有再创建
            singleRemoteView =
                    remoteViewContainer2.findViewWithTag(
                            CallKitUtils.getStitchedContent(userId, REMOTE_VIEW_TAG));
        }
        if (singleRemoteView == null) {
            singleRemoteView = addSingleRemoteView(userId, 1);
        }
        singleRemoteView.findViewById(R.id.user_status).setVisibility(View.GONE);
        singleRemoteView.findViewById(R.id.user_portrait).setVisibility(View.GONE);
        singleRemoteView.findViewById(R.id.user_name).setVisibility(View.GONE);
        // 把最新的 surfaceView 展示出来，onRemoteUserJoined 返回的 surfaceView 已经失效了，流被绑定到新的 surfaceView 上了
        addRemoteVideo(singleRemoteView, surfaceView, userId, true);
    }

    @Override
    public void onRemoteUserUnpublishVideoStream(String userId, String streamId, String tag) {
        if (remoteViewContainer2 != null) { // 删除退出用户的头像框
            View singleRemoteView =
                    remoteViewContainer2.findViewWithTag(
                            CallKitUtils.getStitchedContent(streamId, REMOTE_VIEW_TAG));
            if (singleRemoteView == null) {
                onRemoteUserLeft(streamId, RongCallCommon.CallDisconnectedReason.HANGUP);
            } else {
                remoteViewContainer2.removeView(singleRemoteView);
            }
        }
    }

    /**
     * @param userId
     * @param mediaType
     */
    @Override
    public void onRemoteUserInvited(String userId, RongCallCommon.CallMediaType mediaType) {
        super.onRemoteUserInvited(userId, mediaType);
        if (callSession != null) {
            for (CallUserProfile profile : callSession.getParticipantProfileList()) {
                if (profile.getUserId().equals(RongIMClient.getInstance().getCurrentUserId())) {
                    if (profile.getCallStatus().equals(RongCallCommon.CallStatus.CONNECTED)) {
                        int callUserType = 1;
                        if (callSession.getObserverUserList() != null
                                && callSession.getObserverUserList().contains(userId)) {
                            callUserType = 2;
                        }
                        addSingleRemoteView(userId, callUserType);
                    }
                }
            }
        }
    }

    /**
     * 已建立通话。 通话接通时，通过回调 onCallConnected 通知当前 call 的详细信息。
     *
     * @param callSession 通话实体。
     * @param localVideo 本地 camera 信息。
     */
    @SuppressLint("NewApi")
    @Override
    public void onCallConnected(RongCallSession callSession, SurfaceView localVideo) {
        super.onCallConnected(callSession, localVideo);
        this.callSession = callSession;
        if (null != rc_voip_multiVideoCall_minimize) {
            rc_voip_multiVideoCall_minimize.setVisibility(View.GONE);
        }
        if (iv_large_preview_mutilvideo != null
                && iv_large_preview_mutilvideo.getVisibility() == View.VISIBLE) {
            iv_large_preview_mutilvideo.setVisibility(View.GONE);
        }
        if (null != iv_large_preview_Mask) {
            iv_large_preview_Mask.setVisibility(View.GONE);
        }

        if (localView == null) {
            localView = localVideo;
            //            localView.setZOrderOnTop(true);
            //            localView.setZOrderMediaOverlay(true);
            localViewContainer.removeAllViews();
            localViewContainer.addView(localView);
            getObserverLayout();
            localViewContainer.addView(observerLayout);
            observerLayout.setVisibility(
                    callSession.getUserType() == RongCallCommon.CallUserType.OBSERVER
                            ? View.VISIBLE
                            : View.GONE);
            localViewUserId = RongIMClient.getInstance().getCurrentUserId();
            localView.setTag(
                    CallKitUtils.getStitchedContent(localViewUserId, REMOTE_FURFACEVIEW_TAG));
        }

        localViewContainer.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (!isFullScreen) {
                            isFullScreen = true;
                            rc_voip_multiVideoCall_minimize.setVisibility(View.INVISIBLE);
                            topContainer.setVisibility(View.GONE);
                            bottomButtonContainer.setVisibility(View.GONE);
                        } else {
                            isFullScreen = false;
                            rc_voip_multiVideoCall_minimize.setVisibility(View.VISIBLE);
                            topContainer.setVisibility(View.VISIBLE);
                            bottomButtonContainer.setVisibility(View.VISIBLE);
                        }
                    }
                });
        bottomButtonContainer.removeAllViews();
        FrameLayout bottomButtonLayout =
                (FrameLayout)
                        inflater.inflate(R.layout.rc_voip_multi_video_calling_bottom_view, null);
        bottomButtonContainer.addView(bottomButtonLayout);
        muteButtion = bottomButtonContainer.findViewById(R.id.rc_voip_call_mute_btn);
        muteButtion.setSelected(isMuteMIC);
        disableCameraButtion = bottomButtonContainer.findViewById(R.id.rc_voip_disable_camera_btn);
        disableCameraButtion.setSelected(isMuteCamera);
        topContainer.setVisibility(View.VISIBLE);
        minimizeButton.setVisibility(View.VISIBLE);
        rc_voip_multiVideoCall_minimize.setVisibility(View.VISIBLE);
        userPortrait.setVisibility(View.GONE);
        moreButton.setVisibility(View.VISIBLE);
        switchCameraButton.setVisibility(View.VISIBLE);
        waitingContainer.setVisibility(View.GONE);
        remoteViewContainer.setVisibility(View.VISIBLE);
        participantPortraitContainer.setVisibility(View.GONE);

        userNameView = (TextView) topContainer.findViewById(R.id.rc_voip_user_name);
        CallKitUtils.textViewShadowLayer(userNameView, MultiVideoCallActivity.this);

        String currentUserId = RongIMClient.getInstance().getCurrentUserId();
        if (!TextUtils.isEmpty(topUserName)) {
            userNameView.setTag(topUserNameTag);
            userNameView.setText(CallKitUtils.nickNameRestrict(topUserName));
        } else {
            UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(currentUserId);
            userNameView.setTag(CallKitUtils.getStitchedContent(currentUserId, VOIP_USERNAME_TAG));
            if (userInfo != null) {
                userNameView.setText(CallKitUtils.nickNameRestrict(userInfo.getName()));
            } else {
                userNameView.setText(currentUserId);
            }
        }
        RelativeLayout.LayoutParams parm =
                new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
        parm.addRule(RelativeLayout.CENTER_HORIZONTAL); // ALIGN_PARENT_LEFT
        parm.setMarginEnd(10);
        parm.setMarginStart(20);
        parm.setMargins(20, 40, 0, 0);
        userNameView.setLayoutParams(parm);

        TextView remindInfo = (TextView) topContainer.findViewById(R.id.rc_voip_call_remind_info);
        CallKitUtils.textViewShadowLayer(remindInfo, MultiVideoCallActivity.this);
        setupTime(remindInfo);

        infoLayout = (LinearLayout) topContainer.findViewById(R.id.rc_voip_call_info_layout);
        parm =
                new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.WRAP_CONTENT,
                        RelativeLayout.LayoutParams.WRAP_CONTENT);
        parm.addRule(RelativeLayout.CENTER_HORIZONTAL);
        parm.addRule(RelativeLayout.BELOW, userNameView.getId());
        parm.setMargins(0, 8, 0, 0);
        infoLayout.setLayoutParams(parm);

        signalView = (ImageView) topContainer.findViewById(R.id.rc_voip_signal);
        signalView.setVisibility(View.VISIBLE);

        updateRemoteVideoViews(callSession);
        RCRTCEngine.getInstance().enableSpeaker(true);
    }

    protected void resetHandFreeStatus(RCAudioRouteType type) {
        ImageView handFreeV = null;
        if (null != bottomButtonContainer) {
            handFreeV = bottomButtonContainer.findViewById(R.id.rc_voip_handfree_btn);
        }
        if (handFreeV != null) {
            // 耳机状态
            if (type == RCAudioRouteType.HEADSET || type == RCAudioRouteType.HEADSET_BLUETOOTH) {
                //                handFreeV.setSelected(false);
            } else {
                // 非耳机状态
                handFreeV.setSelected(type == RCAudioRouteType.SPEAKER_PHONE);
            }
        }
    }

    void updateRemoteVideoViews(RongCallSession callSession) {
        String remoteUserID = "";
        FrameLayout remoteVideoView = null;
        View singleRemoteView = null;
        SurfaceView video;
        for (CallUserProfile profile : callSession.getParticipantProfileList()) {
            remoteUserID = profile.getUserId();
            if (remoteUserID.equals(localViewUserId)
                    || profile.getUserType() == RongCallCommon.CallUserType.OBSERVER) continue;
            singleRemoteView =
                    remoteViewContainer.findViewWithTag(
                            CallKitUtils.getStitchedContent(remoteUserID, REMOTE_VIEW_TAG));
            if (singleRemoteView == null) {
                int userType = 1;
                if (callSession.getObserverUserList() != null
                        && callSession.getObserverUserList().contains(remoteUserID)) {
                    userType = 2;
                }
                singleRemoteView = addSingleRemoteView(remoteUserID, userType);
            }
            video = profile.getVideoView();
            if (video != null) {
                remoteVideoView = (FrameLayout) remoteViewContainer.findViewWithTag(remoteUserID);
                if (remoteVideoView == null) {
                    addRemoteVideo(singleRemoteView, video, remoteUserID, false);
                }
            }
            for (StreamProfile streamProfile : profile.streamProfiles) {
                if (TextUtils.equals(localViewUserId, streamProfile.streamId)) {
                    continue;
                }
                onRemoteUserPublishVideoStream(
                        streamProfile.userId,
                        streamProfile.streamId,
                        streamProfile.tag,
                        streamProfile.videoView);
            }
            if (profile.drawed()
                    || TextUtils.equals(
                            profile.getUserId(), RongIMClient.getInstance().getCurrentUserId())) {
                View statusView = singleRemoteView.findViewById(R.id.user_status);
                if (statusView != null) {
                    statusView.setVisibility(View.GONE);
                }
            }
        }
    }

    /**
     * 添加 远端视频流 至singleRemoteView 的FrameLayout中，并缓存最新的远端用户头像
     *
     * @param userId 自定义流时，传入的是streamID
     */
    void addRemoteVideo(
            View singleRemoteView, SurfaceView video, String userId, boolean isStreamId) {
        if (singleRemoteView == null) return;
        String realUserId = userId;
        String streamTag = RCRTCStream.RONG_TAG;
        if (isStreamId) {
            realUserId = Utils.parseUserId(userId);
            streamTag = Utils.parseTag(userId);
        }
        FinLog.d(TAG, "addRemoteVideo realUserId = " + realUserId + "  streamTag = " + streamTag);
        UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(realUserId);
        FrameLayout remoteVideoView =
                (FrameLayout) singleRemoteView.findViewById(R.id.viewlet_remote_video_user);

        remoteVideoView.removeAllViews();
        ImageView userPortraitView = (ImageView) singleRemoteView.findViewById(R.id.user_portrait);
        if (userInfo != null) {
            if (userInfo.getPortraitUri() != null) {
                RongCallKit.getKitImageEngine()
                        .loadPortrait(
                                getBaseContext(),
                                userInfo.getPortraitUri(),
                                R.drawable.rc_default_portrait,
                                userPortraitView);
            }
        }
        if (video.getParent() != null) {
            ((ViewGroup) video.getParent()).removeView(video);
        }
        video.setTag(CallKitUtils.getStitchedContent(userId, REMOTE_FURFACEVIEW_TAG));
        if (TextUtils.equals(RONG_TAG_CALL, streamTag)) {
            ((RCRTCVideoView) video).setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        } else {
            ((RCRTCVideoView) video).setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FIT);
        }
        remoteVideoView.addView(
                video,
                new FrameLayout.LayoutParams(
                        remoteUserViewWidth, remoteUserViewWidth, Gravity.CENTER));

        TextView remoteNameTextView = new TextView(this);
        TextView tv = (TextView) singleRemoteView.findViewById(R.id.user_name);
        CallKitUtils.textViewShadowLayer(remoteNameTextView, MultiVideoCallActivity.this);

        ViewGroup.LayoutParams params = tv.getLayoutParams();
        remoteNameTextView.setLayoutParams(params);
        remoteNameTextView.setTextAppearance(this, R.style.rc_voip_text_style_style);
        remoteNameTextView.setLines(1);
        remoteNameTextView.setEllipsize(TextUtils.TruncateAt.END);
        if (userInfo != null) {
            remoteNameTextView.setText(userInfo.getName());
        } else {
            remoteNameTextView.setText(userId);
        }
        remoteVideoView.addView(remoteNameTextView);
        remoteVideoView.setVisibility(View.VISIBLE);

        remoteVideoView.setTag(userId);
    }

    /**
     * 根据userid创建RemoteView显示头像，并添加至远端View容器
     *
     * @param userId 用户id
     * @param userType 加入用户的类型，1:正常用户,2:观察者。
     * @return
     */
    View addSingleRemoteView(String userId, int userType) {
        View singleRemoteView = inflater.inflate(R.layout.rc_voip_viewlet_remote_user, null);
        UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(userId);
        singleRemoteView.setTag(CallKitUtils.getStitchedContent(userId, REMOTE_VIEW_TAG));
        TextView userStatus = (TextView) singleRemoteView.findViewById(R.id.user_status);
        CallKitUtils.textViewShadowLayer(userStatus, MultiVideoCallActivity.this);
        TextView nameView = (TextView) singleRemoteView.findViewById(R.id.user_name);
        ImageView userPortraitView = (ImageView) singleRemoteView.findViewById(R.id.user_portrait);
        if (userInfo != null) {
            if (userInfo.getPortraitUri() != null) {
                RongCallKit.getKitImageEngine()
                        .loadPortrait(
                                getBaseContext(),
                                userInfo.getPortraitUri(),
                                R.drawable.rc_default_portrait,
                                userPortraitView);
            }
            if (!TextUtils.isEmpty(userInfo.getName())) {
                nameView.setText(userInfo.getName());
            }
        }
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(remoteUserViewWidth, remoteUserViewWidth);
        params.setMargins(
                0,
                0,
                CallKitUtils.dp2px(remoteUserViewMarginsRight, MultiVideoCallActivity.this),
                0);
        remoteViewContainer2.addView(singleRemoteView, params);
        if (userType == 2) {
            singleRemoteView.setVisibility(View.GONE);
        }
        return singleRemoteView;
    }

    /**
     * 根据userid创建每个正常视频用户的RemoteView头像，并添加至远端View容器 观察者不显示头像
     *
     * @param userId 用户id
     * @param i 控制第一个头像边距位置
     */
    private void createAddSingleRemoteView(String userId, int i) {
        View singleRemoteView = inflater.inflate(R.layout.rc_voip_viewlet_remote_user, null);
        UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(userId);
        singleRemoteView.setTag(CallKitUtils.getStitchedContent(userId, REMOTE_VIEW_TAG));
        TextView userStatus = (TextView) singleRemoteView.findViewById(R.id.user_status);
        CallKitUtils.textViewShadowLayer(userStatus, MultiVideoCallActivity.this);
        ImageView userPortraitView = (ImageView) singleRemoteView.findViewById(R.id.user_portrait);
        TextView nameView = (TextView) singleRemoteView.findViewById(R.id.user_name);
        if (userInfo != null) {
            if (userInfo.getPortraitUri() != null) {
                RongCallKit.getKitImageEngine()
                        .loadPortrait(
                                getBaseContext(),
                                userInfo.getPortraitUri(),
                                R.drawable.rc_default_portrait,
                                userPortraitView);
            }
            if (!TextUtils.isEmpty(userInfo.getName())) {
                nameView.setText(userInfo.getName());
            }
        }
        LinearLayout.LayoutParams params =
                new LinearLayout.LayoutParams(remoteUserViewWidth, remoteUserViewWidth);
        if (i == 0) {
            params.setMargins(
                    CallKitUtils.dp2px(remoteUserViewMarginsLeft, MultiVideoCallActivity.this),
                    0,
                    CallKitUtils.dp2px(remoteUserViewMarginsRight, MultiVideoCallActivity.this),
                    0);
        } else {
            params.setMargins(
                    0,
                    0,
                    CallKitUtils.dp2px(remoteUserViewMarginsRight, MultiVideoCallActivity.this),
                    0);
        }
        remoteViewContainer2.addView(singleRemoteView, params);
    }

    @Override
    public void onCallDisconnected(
            RongCallSession callSession, RongCallCommon.CallDisconnectedReason reason) {
        isFinishing = true;
        if (reason == null || callSession == null) {
            RLog.e(TAG, "onCallDisconnected. callSession is null!");
            postRunnableDelay(
                    new Runnable() {
                        @Override
                        public void run() {
                            finish();
                        }
                    });
            return;
        }

        MultiCallEndMessage multiCallEndMessage = new MultiCallEndMessage();
        multiCallEndMessage.setMediaType(IRongCoreEnum.MediaType.VIDEO);
        multiCallEndMessage.setReason(reason);
        long serverTime = System.currentTimeMillis() - RongIMClient.getInstance().getDeltaTime();
        IMCenter.getInstance()
                .insertIncomingMessage( //
                        callSession.getConversationType(), //
                        callSession.getTargetId(), //
                        callSession.getCallerUserId(), //
                        CallKitUtils.getReceivedStatus(reason), //
                        multiCallEndMessage, //
                        serverTime, //
                        null); //
        cancelTime();
        stopRing();
        postRunnableDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
        super.onCallDisconnected(callSession, reason);
        sendBroadcast(new Intent(DISCONNECT_ACTION).setPackage(getPackageName()));
    }

    @Override
    public void onRemoteCameraDisabled(String userId, boolean disabled) {
        if (!disabled) {
            if (localViewUserId.equals(userId)) {
                localView.setBackgroundColor(Color.TRANSPARENT);
            } else {
                View remoteView =
                        remoteViewContainer.findViewWithTag(
                                CallKitUtils.getStitchedContent(userId, REMOTE_FURFACEVIEW_TAG));
                if (remoteView != null) {
                    remoteView.setBackgroundColor(Color.TRANSPARENT);
                }
            }
        } else {
            if (localViewUserId.equals(userId)) {
                localView.setBackgroundColor(Color.BLACK);
            } else {
                View remoteView =
                        remoteViewContainer.findViewWithTag(
                                CallKitUtils.getStitchedContent(userId, REMOTE_FURFACEVIEW_TAG));
                if (remoteView != null) {
                    remoteView.setBackgroundColor(Color.BLACK);
                }
            }
        }
    }

    @Override
    public void onNetworkSendLost(int lossRate, int delay) {
        super.onNetworkSendLost(lossRate, delay);
    }

    @Override
    public void onNetworkReceiveLost(String userId, int lossRate) {
        final int resId;
        if (signalView != null) {
            if (lossRate < 5) {
                resId = R.drawable.rc_voip_signal_1;
            } else if (lossRate < 15) {
                resId = R.drawable.rc_voip_signal_2;
            } else if (lossRate < 30) {
                resId = R.drawable.rc_voip_signal_3;
            } else if (lossRate < 45) {
                resId = R.drawable.rc_voip_signal_4;
            } else {
                resId = R.drawable.rc_voip_signal_5;
            }
            signalView.post(
                    new Runnable() {
                        @Override
                        public void run() {
                            signalView.setImageResource(resId);
                        }
                    });
        }
    }

    protected void initViews() {
        Log.i(TAG, "---------- initViews ---------------");
        inflater = LayoutInflater.from(this);
        localViewContainer = (ContainerLayout) findViewById(R.id.rc_local_user_view);
        remoteViewContainer = (LinearLayout) findViewById(R.id.rc_remote_user_container);
        remoteViewContainer2 = (LinearLayout) findViewById(R.id.rc_remote_user_container_2);
        topContainer = (LinearLayout) findViewById(R.id.rc_top_container);
        topContainer.setVisibility(View.VISIBLE);
        waitingContainer = (LinearLayout) findViewById(R.id.rc_waiting_container);
        bottomButtonContainer = (LinearLayout) findViewById(R.id.rc_bottom_button_container);
        participantPortraitContainer =
                (LinearLayout) findViewById(R.id.rc_participant_portait_container);
        minimizeButton = (ImageView) findViewById(R.id.rc_voip_call_minimize);
        rc_voip_multiVideoCall_minimize =
                (ImageView) findViewById(R.id.rc_voip_multiVideoCall_minimize);
        userPortrait = (ImageView) findViewById(R.id.rc_voip_user_portrait);
        moreButton = (ImageView) findViewById(R.id.rc_voip_call_more);
        switchCameraButton = (ImageView) findViewById(R.id.rc_voip_switch_camera);
        progressDialog = new ProgressDialog(this);
        progressDialog.setCancelable(false);
        progressDialog.setMessage("白板加载中...");
        mRelativeWebView = (RelativeLayout) findViewById(R.id.rc_whiteboard);
        whiteboardView = new WebView(getApplicationContext());
        ViewGroup.LayoutParams params =
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        whiteboardView.setLayoutParams(params);
        mRelativeWebView.addView(whiteboardView);
        iv_large_preview_mutilvideo = (ImageView) findViewById(R.id.iv_large_preview_mutilvideo);
        iv_large_preview_Mask = (ImageView) findViewById(R.id.iv_large_preview_Mask);
        WebSettings settings = whiteboardView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.SINGLE_COLUMN);
        settings.setLoadWithOverviewMode(true);
        settings.setBlockNetworkImage(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        remoteUserViewWidth = (metrics.widthPixels - 50) / 4;

        localView = null;
        localViewContainer.removeAllViews();
        remoteViewContainer2.removeAllViews();

        minimizeButton.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        MultiVideoCallActivity.super.onMinimizeClick(v);
                    }
                });
        rc_voip_multiVideoCall_minimize.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        MultiVideoCallActivity.super.onMinimizeClick(v);
                    }
                });
    }

    protected void setupIntent() {
        Intent intent = getIntent();
        RongCallAction callAction = RongCallAction.valueOf(intent.getStringExtra("callAction"));
        if (callAction == null || callAction.equals(RongCallAction.ACTION_RESUME_CALL)) {
            return;
        }
        ArrayList<String> invitedList = new ArrayList<>();
        ArrayList<String> observerList = new ArrayList<>();
        if (callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
            callSession = intent.getParcelableExtra("callSession");

            onIncomingCallRinging(callSession);
            TextView callRemindInfoView =
                    (TextView) topContainer.findViewById(R.id.rc_voip_call_remind_info);
            TextView userNameView = (TextView) topContainer.findViewById(R.id.rc_voip_user_name);
            callRemindInfoView.setText(R.string.rc_voip_video_call_inviting);
            if (callSession != null) {
                if (!RongCallClient.getInstance().canCallContinued(callSession.getCallId())) {
                    RLog.w(TAG, "Already received hangup message before, finish current activity");
                    ReportUtil.libStatus(
                            ReportUtil.TAG.ACTIVITYFINISH, "reason", "canCallContinued not");
                    finish();
                    return;
                }
                UserInfo userInfo =
                        RongUserInfoManager.getInstance()
                                .getUserInfo(callSession.getInviterUserId());
                userNameView.setTag(
                        CallKitUtils.getStitchedContent(
                                callSession.getInviterUserId(), VOIP_USERNAME_TAG));
                if (userInfo != null) {
                    userNameView.setText(CallKitUtils.nickNameRestrict(userInfo.getName()));
                    if (userInfo.getPortraitUri() != null) {
                        RongCallKit.getKitImageEngine()
                                .loadPortrait(
                                        getBaseContext(),
                                        userInfo.getPortraitUri(),
                                        R.drawable.rc_default_portrait,
                                        userPortrait);
                        userPortrait.setVisibility(View.VISIBLE);
                    }
                    //
                    // GlideUtils.showBlurTransformation(MultiVideoCallActivity.this,iv_large_preview_mutilvideo,null!=userInfo?userInfo.getPortraitUri():null);
                    //                    iv_large_preview_mutilvideo.setVisibility(View.VISIBLE);
                    //                    iv_large_preview_Mask.setVisibility(View.VISIBLE);
                } else {
                    userNameView.setText(callSession.getInviterUserId());
                }
                invitedList = getInvitedList();
                RelativeLayout bottomButtonLayout =
                        (RelativeLayout)
                                inflater.inflate(
                                        R.layout.rc_voip_call_bottom_incoming_button_layout, null);
                ImageView answerV =
                        (ImageView) bottomButtonLayout.findViewById(R.id.rc_voip_call_answer_btn);
                answerV.setImageResource(R.drawable.rc_voip_vedio_answer_selector);
                bottomButtonContainer.removeAllViews();
                bottomButtonContainer.addView(bottomButtonLayout);

                for (int i = 0; i < invitedList.size(); i++) {
                    boolean bool = invitedList.get(i).equals(callSession.getCallerUserId());
                    if (bool) {
                        continue;
                    }
                    View userPortraitView = inflater.inflate(R.layout.rc_voip_user_portrait, null);
                    ImageView portraitView =
                            (ImageView) userPortraitView.findViewById(R.id.rc_user_portrait);
                    userInfo = RongUserInfoManager.getInstance().getUserInfo(invitedList.get(i));
                    if (userInfo != null && userInfo.getPortraitUri() != null) {
                        RongCallKit.getKitImageEngine()
                                .loadPortrait(
                                        getBaseContext(),
                                        userInfo.getPortraitUri(),
                                        R.drawable.rc_default_portrait,
                                        portraitView);
                    }
                    portraitContainer1 =
                            (LinearLayout)
                                    participantPortraitContainer.findViewById(
                                            R.id.rc_participant_portait_container_1);
                    LinearLayout.LayoutParams params =
                            new LinearLayout.LayoutParams(
                                    LinearLayout.LayoutParams.WRAP_CONTENT,
                                    LinearLayout.LayoutParams.WRAP_CONTENT);
                    if (i == 0 && !bool) {
                        params.setMargins(
                                CallKitUtils.dp2px(
                                        remoteUserViewMarginsLeft, MultiVideoCallActivity.this),
                                0,
                                CallKitUtils.dp2px(
                                        remoteUserViewMarginsRight, MultiVideoCallActivity.this),
                                0);
                    } else {
                        params.setMargins(
                                0,
                                0,
                                CallKitUtils.dp2px(
                                        remoteUserViewMarginsRight, MultiVideoCallActivity.this),
                                0);
                    }
                    portraitContainer1.addView(userPortraitView, params);
                    userPortraitView.setTag(
                            CallKitUtils.getStitchedContent(
                                    invitedList.get(i), VOIP_PARTICIPANT_PORTAIT_CONTAINER_TAG));
                }
            }

            topContainer.setVisibility(View.VISIBLE);
            minimizeButton.setVisibility(View.GONE);
            rc_voip_multiVideoCall_minimize.setVisibility(View.GONE);
            moreButton.setVisibility(View.GONE);
            switchCameraButton.setVisibility(View.GONE);
            waitingContainer.setVisibility(View.GONE);
            remoteViewContainer.setVisibility(View.GONE);
            participantPortraitContainer.setVisibility(View.VISIBLE);
            bottomButtonContainer.setVisibility(View.VISIBLE);
            incomingPreview();
        } else if (callAction.equals(RongCallAction.ACTION_OUTGOING_CALL)) {
            Conversation.ConversationType conversationType =
                    Conversation.ConversationType.valueOf(
                            intent.getStringExtra("conversationType").toUpperCase(Locale.US));
            String targetId = intent.getStringExtra("targetId");
            ArrayList<String> userIds = intent.getStringArrayListExtra("invitedUsers");
            ArrayList<String> observerIds = intent.getStringArrayListExtra("observerUsers");
            if (observerIds != null && observerIds.size() > 0) {
                observerList.addAll(observerIds);
            }

            for (int i = 0; i < userIds.size(); i++) {
                if (!userIds.get(i).equals(RongIMClient.getInstance().getCurrentUserId())) {
                    invitedList.add(userIds.get(i));
                    String userId = userIds.get(i);
                    if (observerList.size() == 0 || !observerList.contains(userId)) {
                        createAddSingleRemoteView(userId, i);
                    }
                }
            }

            String groupName = "";
            Group group = RongUserInfoManager.getInstance().getGroupInfo(targetId);
            if (group != null && !TextUtils.isEmpty(group.getName())) {
                groupName = group.getName();
            }
            RongCallClient.getInstance()
                    .setPushConfig(
                            DefaultPushConfig.getInviteConfig(this, false, false, groupName),
                            DefaultPushConfig.getHangupConfig(this, false, groupName));

            RongCallClient.getInstance()
                    .startCall(
                            conversationType,
                            targetId,
                            invitedList,
                            observerList,
                            RongCallCommon.CallMediaType.VIDEO,
                            "multi");
            FrameLayout bottomButtonLayout =
                    (FrameLayout)
                            inflater.inflate(
                                    R.layout.rc_voip_multi_video_calling_bottom_view, null);
            bottomButtonLayout.findViewById(R.id.rc_voip_call_mute).setVisibility(View.GONE);
            bottomButtonLayout.findViewById(R.id.rc_voip_disable_camera).setVisibility(View.GONE);
            bottomButtonLayout.findViewById(R.id.rc_voip_handfree).setVisibility(View.GONE);
            bottomButtonContainer.removeAllViews();
            bottomButtonContainer.addView(bottomButtonLayout);
            topContainer.setVisibility(View.GONE);
            waitingContainer.setVisibility(View.VISIBLE);
            remoteViewContainer.setVisibility(View.VISIBLE);
            participantPortraitContainer.setVisibility(View.GONE);
            bottomButtonContainer.setVisibility(View.VISIBLE);
            rc_voip_multiVideoCall_minimize.setVisibility(View.GONE);
        }
    }

    /** 挂断通话 */
    public void onHangupBtnClick(View view) {
        CallKitUtils.callConnected = false;
        if (callSession == null || isFinishing) {
            FinLog.e(
                    TAG,
                    "hangup call error:  callSession="
                            + (callSession == null)
                            + ",isFinishing="
                            + isFinishing);
            return;
        }
        stopRing();
        RongCallClient.getInstance().hangUpCall(callSession.getCallId());
    }

    /** 接听通话 */
    public void onReceiveBtnClick(View view) {
        if (callSession == null || isFinishing) {
            FinLog.e(
                    TAG,
                    "hangup call error:  callSession="
                            + (callSession == null)
                            + ",isFinishing="
                            + isFinishing);
            return;
        }
        RongCallClient.getInstance().acceptCall(callSession.getCallId());
        RongCallClient.getInstance().setEnableLocalAudio(true);
        RongCallClient.getInstance().setEnableLocalVideo(true);
        stopRing();
    }

    private void addButtionClickEvent() {
        setShouldShowFloat(false);

        if (callSession.getConversationType().equals(Conversation.ConversationType.DISCUSSION)) {
            RongDiscussionClient.getInstance()
                    .getDiscussion(
                            callSession.getTargetId(),
                            new IRongCoreCallback.ResultCallback<Discussion>() {
                                @Override
                                public void onSuccess(Discussion discussion) {
                                    Intent intent =
                                            new Intent(
                                                    MultiVideoCallActivity.this,
                                                    CallSelectMemberActivity.class);
                                    ArrayList<String> added = new ArrayList<>();
                                    List<CallUserProfile> list =
                                            RongCallClient.getInstance()
                                                    .getCallSession()
                                                    .getParticipantProfileList();
                                    for (CallUserProfile profile : list) {
                                        added.add(profile.getUserId());
                                    }
                                    List<String> allObserver =
                                            RongCallClient.getInstance()
                                                    .getCallSession()
                                                    .getObserverUserList();
                                    intent.putStringArrayListExtra(
                                            "allObserver", new ArrayList<>(allObserver));
                                    intent.putStringArrayListExtra(
                                            "allMembers",
                                            (ArrayList<String>) discussion.getMemberIdList());
                                    intent.putStringArrayListExtra("invitedMembers", added);
                                    intent.putExtra(
                                            "conversationType",
                                            callSession.getConversationType().getValue());
                                    intent.putExtra(
                                            "mediaType",
                                            RongCallCommon.CallMediaType.VIDEO.getValue());
                                    startActivityForResult(intent, REQUEST_CODE_ADD_MEMBER);
                                }

                                @Override
                                public void onError(IRongCoreEnum.CoreErrorCode e) {}
                            });
        } else if (callSession.getConversationType().equals(Conversation.ConversationType.GROUP)) {
            Intent intent = new Intent(MultiVideoCallActivity.this, CallSelectMemberActivity.class);
            ArrayList<String> added = new ArrayList<>();
            List<CallUserProfile> list =
                    RongCallClient.getInstance().getCallSession().getParticipantProfileList();
            for (CallUserProfile profile : list) {
                added.add(profile.getUserId());
            }
            List<String> allObserver =
                    RongCallClient.getInstance().getCallSession().getObserverUserList();
            intent.putStringArrayListExtra("allObserver", new ArrayList<>(allObserver));
            intent.putStringArrayListExtra("invitedMembers", added);
            intent.putExtra("callId", callSession.getCallId());
            intent.putExtra("groupId", callSession.getTargetId());
            intent.putExtra("conversationType", callSession.getConversationType().getValue());
            intent.putExtra("mediaType", RongCallCommon.CallMediaType.VIDEO.getValue());
            startActivityForResult(intent, REQUEST_CODE_ADD_MEMBER);
        } else {
            ArrayList<String> added = new ArrayList<>();
            List<CallUserProfile> list =
                    RongCallClient.getInstance().getCallSession().getParticipantProfileList();
            for (CallUserProfile profile : list) {
                added.add(profile.getUserId());
            }
            addMember(added);
        }
    }

    public void onMoreButtonClick(View view) {
        optionMenu = new CallOptionMenu(MultiVideoCallActivity.this);
        optionMenu.setHandUpvisibility(
                callSession.getUserType() == RongCallCommon.CallUserType.OBSERVER);
        optionMenu.setOnItemClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        int i = v.getId();
                        if (i == R.id.voipItemAdd) {
                            addButtionClickEvent();
                        }
                        optionMenu.dismiss();
                    }
                });
        optionMenu.showAsDropDown(moreButton, (int) moreButton.getX(), 0);
    }

    @Override
    protected void onAddMember(List<String> newMemberIds) {
        if (newMemberIds == null || newMemberIds.isEmpty()) {
            return;
        }
        ArrayList<String> added = new ArrayList<>();
        List<String> participants = new ArrayList<>();
        List<CallUserProfile> list =
                RongCallClient.getInstance().getCallSession().getParticipantProfileList();
        for (CallUserProfile profile : list) {
            participants.add(profile.getUserId());
        }
        for (String id : newMemberIds) {
            if (participants.contains(id)) {
                continue;
            } else {
                added.add(id);
            }
        }
        if (added.isEmpty()) {
            return;
        }

        RongCallClient.getInstance().addParticipants(callSession.getCallId(), added, null);
    }

    public void onSwitchCameraClick(View view) {
        RongCallClient.getInstance().switchCamera();
    }

    public void onMuteButtonClick(View view) {
        RongCallClient.getInstance().setEnableLocalAudio(view.isSelected());
        view.setSelected(!view.isSelected());
        isMuteMIC = view.isSelected();
    }

    public void onDisableCameraBtnClick(View view) {
        TextView text =
                (TextView) bottomButtonContainer.findViewById(R.id.rc_voip_disable_camera_text);
        String currentUserId = RongIMClient.getInstance().getCurrentUserId();

        RongCallClient.getInstance().setEnableLocalVideo(view.isSelected());
        if (view.isSelected()) {
            text.setText(R.string.rc_voip_disable_camera);
            if (localViewUserId.equals(currentUserId)) {
                localView.setVisibility(View.VISIBLE);
            } else {
                remoteViewContainer
                        .findViewWithTag(currentUserId)
                        .findViewWithTag(
                                CallKitUtils.getStitchedContent(
                                        currentUserId, REMOTE_FURFACEVIEW_TAG))
                        .setVisibility(View.VISIBLE);
            }
        } else {
            text.setText(R.string.rc_voip_enable_camera);
            if (localViewUserId.equals(currentUserId)) {
                localView.setVisibility(View.INVISIBLE);
            } else {
                remoteViewContainer
                        .findViewWithTag(currentUserId)
                        .findViewWithTag(
                                CallKitUtils.getStitchedContent(
                                        currentUserId, REMOTE_FURFACEVIEW_TAG))
                        .setVisibility(View.INVISIBLE);
            }
        }
        view.setSelected(!view.isSelected());
        isMuteCamera = view.isSelected();
    }

    /**
     * 小窗口FrameLayout点击事件
     *
     * @param view
     */
    public void onSwitchRemoteUsers(View view) {
        String from = (String) view.getTag();
        Log.i(TAG, "from = " + from);
        if (from == null) return;
        String to = (String) localView.getTag();
        to = to.substring(0, to.length() - REMOTE_FURFACEVIEW_TAG.length());
        FrameLayout frameLayout = (FrameLayout) view;
        SurfaceView fromView = (SurfaceView) frameLayout.getChildAt(0);
        SurfaceView toSurfaceView = localView;
        if (fromView == null || toSurfaceView == null) {
            return;
        }
        // 大屏删除frameLayout和observerLayout
        localViewContainer.removeAllViews();
        // 清空小屏装载SurfaceView的FrameLayout
        frameLayout.removeAllViews();

        /** 从远端容器中取出被点击的小屏装载视频流和头像的View，并将头像修改成大屏的 */
        View singleRemoteView =
                remoteViewContainer2.findViewWithTag(
                        CallKitUtils.getStitchedContent(from, REMOTE_VIEW_TAG));
        ImageView userPortraitView = (ImageView) singleRemoteView.findViewById(R.id.user_portrait);
        String fromUid = Utils.parseUserId(from);
        String toUid = Utils.parseUserId(to);
        UserInfo toUserInfo = RongUserInfoManager.getInstance().getUserInfo(toUid);
        UserInfo fromUserInfo = RongUserInfoManager.getInstance().getUserInfo(fromUid);
        String toTag = Utils.parseTag(to);
        if (TextUtils.equals(toTag, RONG_TAG_CALL)) {
            userPortraitView.setVisibility(View.VISIBLE);
        } else {
            userPortraitView.setVisibility(View.GONE);
        }
        if (toUserInfo != null) {
            if (toUserInfo.getPortraitUri() != null) {
                RongCallKit.getKitImageEngine()
                        .loadPortrait(
                                getBaseContext(),
                                toUserInfo.getPortraitUri(),
                                R.drawable.rc_default_portrait,
                                userPortraitView);
            }
        }
        fromView.setZOrderOnTop(false);
        fromView.setZOrderMediaOverlay(false);
        localViewContainer.addView(fromView); // 将点击的小屏视频流添加至本地大容器中
        fromView.setVisibility(View.INVISIBLE);
        /** 本地容器添加观察者图层 */
        getObserverLayout();
        localViewContainer.addView(observerLayout);
        if (RongCallClient.getInstance().getCallSession().getSelfUserId().equals(from)
                && callSession.getUserType().getValue()
                        == RongCallCommon.CallUserType.OBSERVER.getValue()) {
            observerLayout.setVisibility(View.VISIBLE);
        } else {
            observerLayout.setVisibility(View.GONE);
        }

        /** 将原大屏视频流添加到小屏的FrameLayout上 */
        singleRemoteView.setTag(CallKitUtils.getStitchedContent(to, REMOTE_VIEW_TAG));
        toSurfaceView.setZOrderOnTop(true);
        toSurfaceView.setZOrderMediaOverlay(true);
        toSurfaceView.setTag(CallKitUtils.getStitchedContent(to, REMOTE_FURFACEVIEW_TAG));
        frameLayout.addView(
                toSurfaceView,
                new FrameLayout.LayoutParams(
                        remoteUserViewWidth, remoteUserViewWidth, Gravity.CENTER));

        TextView remoteNameTextView = new TextView(this);
        TextView tv = (TextView) singleRemoteView.findViewById(R.id.user_name);
        ViewGroup.LayoutParams params = tv.getLayoutParams();
        remoteNameTextView.setLayoutParams(params);
        remoteNameTextView.setTextAppearance(this, R.style.rc_voip_text_style_style);
        remoteNameTextView.setLines(1);
        remoteNameTextView.setEllipsize(TextUtils.TruncateAt.END);
        if (toUserInfo != null && !TextUtils.isEmpty(toUserInfo.getName())) {
            remoteNameTextView.setText(toUserInfo.getName());
        } else {
            remoteNameTextView.setText(to);
        }
        CallKitUtils.textViewShadowLayer(remoteNameTextView, MultiVideoCallActivity.this);
        frameLayout.addView(remoteNameTextView);

        TextView topUserNameView = (TextView) topContainer.findViewById(R.id.rc_voip_user_name);
        CallKitUtils.textViewShadowLayer(topUserNameView, MultiVideoCallActivity.this);

        topUserNameView.setTag(CallKitUtils.getStitchedContent(from, VOIP_USERNAME_TAG));
        topUserNameView.setLines(1);
        topUserNameView.setEllipsize(TextUtils.TruncateAt.END);
        if (fromUserInfo != null && !TextUtils.isEmpty(fromUserInfo.getName())) {
            topUserNameView.setText(CallKitUtils.nickNameRestrict(fromUserInfo.getName()));
        } else {
            topUserNameView.setText(from);
        }
        topUserName = topUserNameView.getText().toString();
        topUserNameTag = topUserNameView.getTag().toString();
        frameLayout.setTag(to);
        localView = fromView;
        localView.setTag(CallKitUtils.getStitchedContent(from, REMOTE_FURFACEVIEW_TAG));
        localViewUserId = from;

        Handler handler = new Handler();
        handler.postDelayed(
                new Runnable() {
                    @Override
                    public void run() {
                        localView.setVisibility(View.VISIBLE);
                    }
                },
                30);
    }

    @Override
    public void onBackPressed() {
        return;
    }

    @Override
    public void onUserUpdate(UserInfo userInfo) {
        if (isFinishing() || inflater == null) {
            return;
        }
        if (participantPortraitContainer.getVisibility() == View.VISIBLE) {
            View participantView =
                    participantPortraitContainer.findViewWithTag(
                            CallKitUtils.getStitchedContent(
                                    userInfo.getUserId(), VOIP_PARTICIPANT_PORTAIT_CONTAINER_TAG));
            if (participantView != null && userInfo.getPortraitUri() != null) {
                ImageView portraitView =
                        (ImageView) participantView.findViewById(R.id.rc_user_portrait);
                RongCallKit.getKitImageEngine()
                        .loadPortrait(
                                getBaseContext(),
                                userInfo.getPortraitUri(),
                                R.drawable.rc_default_portrait,
                                portraitView);
            }
        }
        if (remoteViewContainer.getVisibility() == View.VISIBLE) {
            View remoteView =
                    remoteViewContainer.findViewWithTag(
                            CallKitUtils.getStitchedContent(userInfo.getUserId(), REMOTE_VIEW_TAG));
            if (remoteView != null && userInfo.getPortraitUri() != null) {
                ImageView portraitView = (ImageView) remoteView.findViewById(R.id.user_portrait);
                RongCallKit.getKitImageEngine()
                        .loadPortrait(
                                getBaseContext(),
                                userInfo.getPortraitUri(),
                                R.drawable.rc_default_portrait,
                                portraitView);
            }
        }
        if (topContainer.getVisibility() == View.VISIBLE) {
            TextView nameView =
                    (TextView)
                            topContainer.findViewWithTag(
                                    CallKitUtils.getStitchedContent(
                                            userInfo.getUserId(), VOIP_USERNAME_TAG));
            if (nameView != null && userInfo.getName() != null)
                nameView.setText(userInfo.getName());
        }
    }

    private ProgressDialog progressDialog;

    private void loadWhiteBoard(String url, boolean isReload) {
        if (isReload) {
            whiteboardView.reload();
            progressDialog.show();
            return;
        }

        if (TextUtils.isEmpty(url)) {
            return;
        }

        progressDialog.show();
        mRelativeWebView.setVisibility(View.VISIBLE);
        whiteboardView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        // https://web.blinktalk.online/ewbweb/blink-wb.html?roomKey=1234567890abcdefg1dg%40blinktest&token=eyJhbGciOiJIUzUxMiJ9.eyJyb29tS2V5IjoiMTIzNDU2Nzg5MGFiY2RlZmcxZGdAYmxpbmt0ZXN0IiwiZXhwIjoxNTE2MzQ0MTc1fQ.6izAdEW6yfYns7ACmKBVL6ymASq_28crvseMCIsjv-ITjfCXB2S8O7gcKv1CUclkSSfCGOvgfo4Pycl_Z0yM0Q&type=android

        // https://web.blinkcloud.cn/ewbweb/blink-wb.html?roomKey=1234567890abcdefg1dg%40blink&token=eyJhbGciOiJIUzUxMiJ9.eyJyb29tS2V5IjoiMTIzNDU2Nzg5MGFiY2RlZmcxZGdAYmxpbmsiLCJleHAiOjE1MTYzNDM3NjJ9.DJCa1mt67xW_5sfzxHUWi5O143UjgFl-LDNLfc8GlWp-khWACXIzYipA_L-9SIU7h8_16N2Pu-fLmePOeRX6pA&type=android
        whiteboardView.loadUrl(url);
        whiteboardView.setWebViewClient(
                new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView view, String url) {
                        // TODO Auto-generated method stub
                        // 返回值是true的时候控制去WebView打开，为false调用系统浏览器或第三方浏览器
                        view.loadUrl(url);
                        return true;
                    }

                    @Override
                    public void onPageFinished(WebView view, String url) {
                        super.onPageFinished(view, url);
                        view.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                        progressDialog.dismiss();
                    }

                    @Override
                    public void onReceivedSslError(
                            WebView view, final SslErrorHandler handler, SslError error) {
                        final AlertDialog.Builder builder =
                                new AlertDialog.Builder(MultiVideoCallActivity.this);
                        builder.setMessage(getString(R.string.rc_voip_web_page_cetificate_invalid));
                        builder.setPositiveButton(
                                R.string.rc_voip_ok,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        handler.proceed();
                                    }
                                });
                        builder.setNegativeButton(
                                R.string.rc_voip_cancel,
                                new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        handler.cancel();
                                    }
                                });
                        final AlertDialog dialog = builder.create();
                        dialog.show();
                    }
                });
    }

    public void onHeadsetPlugUpdate(HeadsetInfo headsetInfo) {
        if (headsetInfo == null || !BluetoothUtil.isForground(MultiVideoCallActivity.this)) {
            FinLog.v(TAG, "MultiVideoCallActivity is not in the foreground!！");
            return;
        }
        Log.i(
                TAG,
                "Insert="
                        + headsetInfo.isInsert()
                        + ",headsetInfo.getType="
                        + headsetInfo.getType().getValue());
        try {
            if (headsetInfo.isInsert()) {
                RongCallClient.getInstance().setEnableSpeakerphone(false);
                ImageView handFreeV = null;
                if (null != bottomButtonContainer) {
                    handFreeV = bottomButtonContainer.findViewById(R.id.rc_voip_handfree_btn);
                }
                if (handFreeV != null) {
                    handFreeV.setSelected(false);
                    handFreeV.setEnabled(false);
                    handFreeV.setClickable(false);
                }
                if (headsetInfo.getType() == HeadsetInfo.HeadsetType.BluetoothA2dp) {
                    AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                    am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                    am.startBluetoothSco();
                    am.setBluetoothScoOn(true);
                    am.setSpeakerphoneOn(false);
                }
            } else {
                if (headsetInfo.getType() == HeadsetInfo.HeadsetType.WiredHeadset
                        && BluetoothUtil.hasBluetoothA2dpConnected()) {
                    return;
                }
                RongCallClient.getInstance().setEnableSpeakerphone(true);
                ImageView handFreeV = bottomButtonContainer.findViewById(R.id.rc_voip_handfree_btn);
                if (handFreeV != null) {
                    handFreeV.setSelected(true);
                    handFreeV.setEnabled(true);
                    handFreeV.setClickable(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private RelativeLayout getObserverLayout() {
        observerLayout = (RelativeLayout) inflater.inflate(R.layout.rc_voip_observer_hint, null);
        RelativeLayout.LayoutParams param =
                new RelativeLayout.LayoutParams(
                        RelativeLayout.LayoutParams.MATCH_PARENT,
                        RelativeLayout.LayoutParams.MATCH_PARENT);
        observerLayout.setGravity(Gravity.CENTER);
        observerLayout.setLayoutParams(param);
        return observerLayout;
    }
}
