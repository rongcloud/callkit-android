package io.rong.callkit;

import static io.rong.callkit.CallSelectMemberActivity.DISCONNECT_ACTION;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
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
import io.rong.callkit.util.GlideUtils;
import io.rong.callkit.util.HeadsetInfo;
import io.rong.callkit.util.RingingMode;
import io.rong.callkit.util.RongCallPermissionUtil;
import io.rong.callkit.util.UserProfileOrderManager;
import io.rong.calllib.CallUserProfile;
import io.rong.calllib.ReportUtil;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
import io.rong.calllib.StartIncomingPreviewCallback;
import io.rong.calllib.StreamProfile;
import io.rong.calllib.Utils;
import io.rong.calllib.message.MultiCallEndMessage;
import io.rong.common.rlog.RLog;
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
    private UserProfileOrderManager mUserProfileOrderManager;

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
        RLog.i(TAG, "onCreate initViews requestCallPermissions=" + val);
        if (val) {
            RLog.i(TAG, "--- onCreate  initViews------");
            initViews();
            setupIntent();
        }
        mUserProfileOrderManager = new UserProfileOrderManager();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        RLog.d(TAG, "onNewIntent: [intent]");
        startForCheckPermissions = intent.getBooleanExtra("checkPermissions", false);
        super.onNewIntent(intent);
        boolean bool =
                requestCallPermissions(
                        RongCallCommon.CallMediaType.VIDEO, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS);
        RLog.i(TAG, "mult onNewIntent==" + bool);
        if (bool) {
            RLog.i(TAG, "mult onNewIntent initViews");
            initViews();
            setupIntent();
        }
    }

    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        RLog.d(TAG, "onRequestPermissionsResult: " + requestCode);
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
        RLog.i(TAG, "mult  onActivityResult requestCode=" + requestCode);
        super.onActivityResult(requestCode, resultCode, data);
        callSession = RongCallClient.getInstance().getCallSession();
        if (requestCode == REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS) {
            if (RongCallPermissionUtil.checkVideoCallNeedPermission(this)) {
                if (startForCheckPermissions) {
                    startForCheckPermissions = false;
                    RongCallClient.getInstance().onPermissionGranted();
                } else {
                    RLog.i(TAG, "mult  onActivityResult initView");
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
        bundle.putStringArrayList(
                EXTRA_BUNDLE_KEY_USER_PROFILE_TAG_ORDER_TAG, mUserProfileOrderManager.getUserIds());
        RLog.d(TAG, "onSaveFloatBoxState-->localViewUserId ： " + localViewUserId);
        return intentAction;
    }

    @Override
    public void onRestoreFloatBox(Bundle bundle) {
        super.onRestoreFloatBox(bundle);
        try {
            RLog.i(TAG, "--- onRestoreFloatBox  ------");
            callSession = RongCallClient.getInstance().getCallSession();
            if (bundle != null) {
                RongCallAction callAction =
                        RongCallAction.getAction(bundle.getString("callAction"));
                if (!callAction.equals(RongCallAction.ACTION_RESUME_CALL)) {
                    return;
                }

                if (mUserProfileOrderManager != null) {
                    mUserProfileOrderManager = null;
                }
                mUserProfileOrderManager =
                        new UserProfileOrderManager(
                                bundle.getStringArrayList(
                                        EXTRA_BUNDLE_KEY_USER_PROFILE_TAG_ORDER_TAG));
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
                    FrameLayout bottomButtonLayout;
                    if (CallKitUtils.findConfigurationLanguage(MultiVideoCallActivity.this, "ar")) {
                        bottomButtonLayout =
                                (FrameLayout)
                                        inflater.inflate(
                                                R.layout
                                                        .rc_voip_multi_video_calling_bottom_view_rtl,
                                                null);
                    } else {
                        bottomButtonLayout =
                                (FrameLayout)
                                        inflater.inflate(
                                                R.layout.rc_voip_multi_video_calling_bottom_view,
                                                null);
                    }
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
            RLog.i(TAG, "MultiVideoCallActivity onRestoreFloatBox Error=" + e.getMessage());
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
        RLog.d("bugtags", "onFirstRemoteVideoFrame,uid :" + userId);
        if (remoteViewContainer2 == null) {
            RLog.e(
                    "bugtags",
                    "onFirstRemoteVideoFrame()->remoteViewContainer2 is empty.userId : " + userId);
            return;
        }

        View singleRemoteView =
                remoteViewContainer2.findViewWithTag(
                        CallKitUtils.getStitchedContent(userId, REMOTE_VIEW_TAG));
        if (singleRemoteView == null) {
            RLog.e("bugtags", "onFirstRemoteVideoFrame(). singleRemoteView is empty");

            if (localViewContainer == null || localViewContainer.getChildCount() == 0) {
                RLog.e("bugtags", "onFirstRemoteVideoFrame(). localViewContainer is empty");
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
                //                if (!TextUtils.equals(Build.MODEL, "PEPM00")) {
                //                    ((RCRTCVideoView)
                // remoteVideoView.getChildAt(i)).setZOrderOnTop(true);
                //                    ((RCRTCVideoView)
                // remoteVideoView.getChildAt(i)).setZOrderMediaOverlay(true);
                //                }
                remoteVideoView.getChildAt(i).setBackgroundColor(Color.TRANSPARENT);
                break;
            }
        }

        TextView textView = singleRemoteView.findViewById(R.id.user_name);
        textView.setVisibility(View.VISIBLE);
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
        if (remoteVideo == null) {
            RLog.e(TAG, "onRemoteUserJoined()->remoteVideo is empty");
            return;
        }
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
        RLog.d(TAG, "onRemoteUserLeft->>userId: " + userId);
        // 通话过程中 toast "通话结束"有些突兀，所以只有远端忙线和拒绝时我们提醒用户
        if (reason.equals(RongCallCommon.CallDisconnectedReason.REMOTE_BUSY_LINE)
                || reason.equals(RongCallCommon.CallDisconnectedReason.REMOTE_REJECT)) {
            super.onRemoteUserLeft(userId, reason);
        }

        // incomming state
        if (participantPortraitContainer != null) {
            View participantView =
                    participantPortraitContainer.findViewWithTag(
                            CallKitUtils.getStitchedContent(
                                    userId, VOIP_PARTICIPANT_PORTAIT_CONTAINER_TAG));
            if (participantView != null) {
                LinearLayout portraitContainer = (LinearLayout) participantView.getParent();
                portraitContainer.removeView(participantView);
            }
        } else {
            RLog.e(TAG, "onRemoteUserLeft->>participantPortraitContainer is empty");
        }

        if (isTopContainerUserExit(userId)) {
            RLog.e(TAG, "onRemoteUserLeft->>isTopContainerUserExit---true");
            return;
        }
        String delUserid = userId;

        // incoming状态，localViewUserId为空
        if (localViewUserId == null) {
            RLog.e(TAG, "onRemoteUserLeft->>localViewUserId is empty");
            return;
        }
        if (localViewUserId.equals(userId)) {
            RLog.d(TAG, "onRemoteUserLeft->>localViewUserId---localViewUserId: " + localViewUserId);
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
            RLog.d(TAG, "onRemoteUserLeft->>remoteViewContainer2!=null");
            View singleRemoteView =
                    remoteViewContainer2.findViewWithTag(
                            CallKitUtils.getStitchedContent(delUserid, REMOTE_VIEW_TAG));
            if (singleRemoteView == null) {
                RLog.e(
                        TAG,
                        "onRemoteUserLeft->>remoteViewContainer2!=null  --  singleRemoteView is empty");
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
                    RongCallKit.getKitImageEngine()
                            .loadPortrait(
                                    getBaseContext(),
                                    firstUserInfo.getPortraitUri(),
                                    R.drawable.rc_default_portrait,
                                    userPortrait);
                    userPortrait.setVisibility(View.VISIBLE);
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
                if (firstView != null) {
                    LinearLayout.LayoutParams layoutParams =
                            (LayoutParams) firstView.getLayoutParams();
                    layoutParams.setMargins(
                            CallKitUtils.dp2px(
                                    remoteUserViewMarginsLeft, MultiVideoCallActivity.this),
                            0,
                            CallKitUtils.dp2px(
                                    remoteUserViewMarginsRight, MultiVideoCallActivity.this),
                            0);
                    firstView.requestLayout();
                }
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
        if (TextUtils.equals(userId, localViewUserId) || surfaceView == null) {
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
        addRemoteVideo(singleRemoteView, surfaceView, userId, false);
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
        //        CallKit UI层逻辑没有观察者显示需要。如果您需要邀请观察者加入通话，可以通过如下注释的代码 获取到被邀请人的身份
        //        RongCallSession currentCallSession =
        // RongCallClient.getInstance().getCallSession();
        //        int callUserType = 1;
        //        if (currentCallSession.getObserverUserList() != null
        //            && currentCallSession.getObserverUserList().contains(userId)) {
        //            callUserType = 2;
        //        }

        if (participantPortraitContainer != null
                && participantPortraitContainer.getVisibility() == View.VISIBLE) {
            RLog.i(TAG, "onRemoteUserInvited-->participantPortraitContainer--userId: " + userId);
            try {
                View userPortraitView = inflater.inflate(R.layout.rc_voip_user_portrait, null);
                ImageView portraitView =
                        (ImageView) userPortraitView.findViewById(R.id.rc_user_portrait);
                UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(userId);
                if (userInfo != null) {
                    GlideUtils.showPortrait(
                            getBaseContext(), portraitView, userInfo.getPortraitUri());
                }

                LayoutParams params =
                        new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                params.setMargins(
                        0,
                        0,
                        CallKitUtils.dp2px(remoteUserViewMarginsRight, MultiVideoCallActivity.this),
                        0);
                portraitContainer1.addView(userPortraitView, params);
                userPortraitView.setTag(
                        CallKitUtils.getStitchedContent(
                                userId, VOIP_PARTICIPANT_PORTAIT_CONTAINER_TAG));
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (callSession != null) {
            RLog.d(TAG, "onRemoteUserInvited-->addSingleRemoteView--userId: " + userId);
            for (CallUserProfile profile : callSession.getParticipantProfileList()) {
                if (profile.getUserId().equals(RongIMClient.getInstance().getCurrentUserId())) {
                    if (profile.getCallStatus().equals(RongCallCommon.CallStatus.CONNECTED)) {
                        int callUserType = 1;
                        if (callSession.getObserverUserList() != null
                                && callSession.getObserverUserList().contains(userId)) {
                            callUserType = 2;
                        }
                        RLog.d(
                                TAG,
                                "onRemoteUserInvited-->addSingleRemoteView---addSingleRemoteView");
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
        stopRing();
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

        FrameLayout bottomButtonLayout;
        if (CallKitUtils.findConfigurationLanguage(MultiVideoCallActivity.this, "ar")) {
            bottomButtonLayout =
                    (FrameLayout)
                            inflater.inflate(
                                    R.layout.rc_voip_multi_video_calling_bottom_view_rtl, null);
            bottomButtonLayout.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        } else {
            bottomButtonLayout =
                    (FrameLayout)
                            inflater.inflate(
                                    R.layout.rc_voip_multi_video_calling_bottom_view, null);
        }

        RelativeLayout relativeHangup = bottomButtonLayout.findViewById(R.id.relativeHangup);
        if (CallKitUtils.findConfigurationLanguage(MultiVideoCallActivity.this, "ar")) {
            relativeHangup.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
        }
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
        List<CallUserProfile> callUserProfileList =
                mUserProfileOrderManager.getSortedProfileList(
                        callSession.getParticipantProfileList());
        for (CallUserProfile profile : callUserProfileList) {
            remoteUserID = profile.getUserId();
            RLog.d(
                    TAG,
                    "remoteUserID : " + remoteUserID + " , localViewUserId : " + localViewUserId);
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
            GlideUtils.showPortrait(getBaseContext(), userPortraitView, userInfo.getPortraitUri());
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

        TextView remoteNameTextView = (TextView) singleRemoteView.findViewById(R.id.user_name);
        if (userInfo != null) {
            remoteNameTextView.setText(userInfo.getName());
        } else {
            remoteNameTextView.setText(userId);
        }
        CallKitUtils.textViewShadowLayer(remoteNameTextView, MultiVideoCallActivity.this);
        remoteNameTextView.setVisibility(View.VISIBLE);
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
            GlideUtils.showPortrait(getBaseContext(), userPortraitView, userInfo.getPortraitUri());
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
            GlideUtils.showPortrait(getBaseContext(), userPortraitView, userInfo.getPortraitUri());
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
        View singleRemoteView =
                remoteViewContainer2.findViewWithTag(
                        CallKitUtils.getStitchedContent(userId, REMOTE_VIEW_TAG));
        if (singleRemoteView != null) {
            ImageView userPortraitView =
                    (ImageView) singleRemoteView.findViewById(R.id.user_portrait);
            userPortraitView.setVisibility(disabled ? View.VISIBLE : View.GONE);
            TextView tv = (TextView) singleRemoteView.findViewById(R.id.user_name);
            tv.setVisibility(View.VISIBLE);
        } else {
            RLog.e(TAG, "onRemoteCameraDisabled->singleRemoteView is empty");
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
        RLog.i(TAG, "---------- initViews ---------------");
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
        portraitContainer1 =
                (LinearLayout)
                        participantPortraitContainer.findViewById(
                                R.id.rc_participant_portait_container_1);
        minimizeButton = (ImageView) findViewById(R.id.rc_voip_call_minimize);
        rc_voip_multiVideoCall_minimize =
                (ImageView) findViewById(R.id.rc_voip_multiVideoCall_minimize);
        userPortrait = (ImageView) findViewById(R.id.rc_voip_user_portrait);
        moreButton = (ImageView) findViewById(R.id.rc_voip_call_more);
        switchCameraButton = (ImageView) findViewById(R.id.rc_voip_switch_camera);
        iv_large_preview_mutilvideo = (ImageView) findViewById(R.id.iv_large_preview_mutilvideo);
        iv_large_preview_Mask = (ImageView) findViewById(R.id.iv_large_preview_Mask);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        remoteUserViewWidth = (metrics.widthPixels - 50) / 4;

        localView = null;
        localViewContainer.removeAllViews();
        remoteViewContainer2.removeAllViews();
        portraitContainer1.removeAllViews();

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
        String name = intent.getStringExtra("callAction");
        if (TextUtils.isEmpty(name)) {
            return;
        }
        RongCallAction callAction = RongCallAction.valueOf(name);
        if (callAction == null || callAction.equals(RongCallAction.ACTION_RESUME_CALL)) {
            return;
        }
        ArrayList<String> invitedList = new ArrayList<>();
        ArrayList<String> observerList = new ArrayList<>();
        if (callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
            callSession = intent.getParcelableExtra("callSession");
            // 正常在收到呼叫后，RongCallClient 和 CallSession均不会为空
            if (RongCallClient.getInstance() == null
                    || RongCallClient.getInstance().getCallSession() == null) {
                // 如果为空 表示通话已经结束 但依然启动了本页面，这样会导致页面无法销毁问题
                // 所以 需要在这里 finish 结束当前页面  推荐开发者在结束当前页面前跳转至APP主页或者其他页面
                RLog.e(
                        TAG,
                        "MultiVideoCallActivity#setupIntent()->RongCallClient or CallSession is empty---->finish()");
                finish();
                return;
            }

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
                    RongCallKit.getKitImageEngine()
                            .loadPortrait(
                                    getBaseContext(),
                                    userInfo.getPortraitUri(),
                                    R.drawable.rc_default_portrait,
                                    userPortrait);
                    userPortrait.setVisibility(View.VISIBLE);
                    //
                    // GlideUtils.showPortrait(MultiVideoCallActivity.this,iv_large_preview_mutilvideo,null!=userInfo?userInfo.getPortraitUri():null);
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
                    if (userInfo != null) {
                        GlideUtils.showPortrait(
                                getBaseContext(), portraitView, userInfo.getPortraitUri());
                    }
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
            FrameLayout bottomButtonLayout;
            if (CallKitUtils.findConfigurationLanguage(MultiVideoCallActivity.this, "ar")) {
                bottomButtonLayout =
                        (FrameLayout)
                                inflater.inflate(
                                        R.layout.rc_voip_multi_video_calling_bottom_view_rtl, null);
                bottomButtonLayout.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            } else {
                bottomButtonLayout =
                        (FrameLayout)
                                inflater.inflate(
                                        R.layout.rc_voip_multi_video_calling_bottom_view, null);
            }

            bottomButtonLayout.findViewById(R.id.rc_voip_call_mute).setVisibility(View.GONE);
            bottomButtonLayout.findViewById(R.id.rc_voip_disable_camera).setVisibility(View.GONE);
            bottomButtonLayout.findViewById(R.id.rc_voip_handfree).setVisibility(View.GONE);
            RelativeLayout relativeHangup = bottomButtonLayout.findViewById(R.id.relativeHangup);
            if (CallKitUtils.findConfigurationLanguage(MultiVideoCallActivity.this, "ar")) {
                relativeHangup.setLayoutDirection(View.LAYOUT_DIRECTION_LTR);
            }

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

        // false：摄像头已关闭      true:摄像头已打开
        boolean isSelected = view.isSelected();
        RongCallClient.getInstance().setEnableLocalVideo(isSelected);
        RLog.d(
                "onDisableCameraBtnClick",
                "isSelected: "
                        + isSelected
                        + " ,localViewUserId : "
                        + localViewUserId
                        + " , currentUserId : "
                        + currentUserId);

        if (isSelected) {
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

                View singleRemoteView =
                        remoteViewContainer2.findViewWithTag(
                                CallKitUtils.getStitchedContent(currentUserId, REMOTE_VIEW_TAG));
                if (singleRemoteView != null) {
                    ImageView userPortraitView =
                            (ImageView) singleRemoteView.findViewById(R.id.user_portrait);
                    if (userPortraitView != null) {
                        userPortraitView.setVisibility(View.GONE);
                    }
                }
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

                View singleRemoteView =
                        remoteViewContainer2.findViewWithTag(
                                CallKitUtils.getStitchedContent(currentUserId, REMOTE_VIEW_TAG));
                if (singleRemoteView != null) {
                    ImageView userPortraitView =
                            (ImageView) singleRemoteView.findViewById(R.id.user_portrait);
                    if (userPortraitView != null) {
                        userPortraitView.setVisibility(View.VISIBLE);
                    }
                }
            }
        }
        isMuteCamera = !isSelected;
        view.setSelected(isMuteCamera);
    }

    /**
     * 小窗口FrameLayout点击事件
     *
     * @param view
     */
    public void onSwitchRemoteUsers(View view) {
        String from = (String) view.getTag();
        RLog.i(TAG, "onSwitchRemoteUsers->from = " + from);
        if (from == null) return;
        String to = (String) localView.getTag();
        RLog.i(TAG, "onSwitchRemoteUsers->to = " + to);
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

        String fromUid = from;
        if (from.contains(RCRTCStream.RONG_TAG) || from.contains(RONG_TAG_CALL)) {
            fromUid = Utils.parseUserId(from);
        }
        String toUid = to;
        if (to.contains(RCRTCStream.RONG_TAG) || to.contains(RONG_TAG_CALL)) {
            toUid = Utils.parseUserId(to);
        }
        mUserProfileOrderManager.exchange(from, toUid);

        RLog.i(TAG, "onSwitchRemoteUsers->getUserInfo->fromUid : " + fromUid + " toUid : " + toUid);
        UserInfo toUserInfo = RongUserInfoManager.getInstance().getUserInfo(toUid);
        UserInfo fromUserInfo = RongUserInfoManager.getInstance().getUserInfo(fromUid);
        String toTag = Utils.parseTag(to);
        if (TextUtils.equals(toTag, RONG_TAG_CALL)) {
            userPortraitView.setVisibility(View.VISIBLE);
        } else {
            userPortraitView.setVisibility(View.GONE);
        }

        if (RongCallClient.getInstance().getCallSession() != null) {
            String currentUserId = RongIMClient.getInstance().getCurrentUserId();
            for (CallUserProfile userProfile :
                    RongCallClient.getInstance().getCallSession().getParticipantProfileList()) {
                if (TextUtils.equals(currentUserId, toUid)) {
                    RLog.e(TAG, "onSwitchRemoteUsers->isMuteCamera: " + isMuteCamera);
                    userPortraitView.setVisibility(isMuteCamera ? View.VISIBLE : View.GONE);
                    ImageView imageView =
                            bottomButtonContainer.findViewById(R.id.rc_voip_disable_camera_btn);
                    imageView.setSelected(isMuteCamera);
                } else if (TextUtils.equals(userProfile.getUserId(), toUid)) {
                    userPortraitView.setVisibility(
                            userProfile.isCameraDisabled() ? View.VISIBLE : View.GONE);
                }
            }
        }

        if (toUserInfo != null) {
            Uri portraitUri = toUserInfo.getPortraitUri();
            GlideUtils.showPortrait(getBaseContext(), userPortraitView, portraitUri);
            RLog.d(
                    TAG,
                    "onSwitchRemoteUsers-> getKitImageEngine->PortraitUri: "
                            + portraitUri.toString()
                            + " , userPortraitViewVisibility : "
                            + (userPortraitView.getVisibility() == View.VISIBLE)
                            + " , wxh: "
                            + userPortraitView.getWidth()
                            + " x "
                            + userPortraitView.getHeight());
        } else {
            RLog.e(
                    TAG,
                    "onSwitchRemoteUsers-> toUserInfo is empty or userPortraitViewVisibility : "
                            + userPortraitView.getVisibility());
        }

        fromView.setZOrderOnTop(false);
        fromView.setZOrderMediaOverlay(false);
        localViewContainer.addView(fromView); // 将点击的小屏视频流添加至本地大容器中
        fromView.setVisibility(View.INVISIBLE);
        /** 本地容器添加观察者图层 */
        getObserverLayout();
        localViewContainer.addView(observerLayout);

        if (RongCallClient.getInstance().getCallSession() != null
                && RongCallClient.getInstance().getCallSession().getSelfUserId().equals(from)
                && callSession.getUserType().getValue()
                        == RongCallCommon.CallUserType.OBSERVER.getValue()) {
            observerLayout.setVisibility(View.VISIBLE);
            RLog.d(TAG, "onSwitchRemoteUsers->observerLayout  VISIBLE");
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

        TextView tv = (TextView) singleRemoteView.findViewById(R.id.user_name);
        if (toUserInfo != null && !TextUtils.isEmpty(toUserInfo.getName())) {
            tv.setText(toUserInfo.getName());
        } else {
            FinLog.e(TAG, "onSwitchRemoteUsers->toUserInfo or getName is empty");
            tv.setText(to);
        }
        CallKitUtils.textViewShadowLayer(tv, MultiVideoCallActivity.this);
        tv.setVisibility(View.VISIBLE);

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
            if (participantView != null) {
                ImageView portraitView =
                        (ImageView) participantView.findViewById(R.id.rc_user_portrait);
                GlideUtils.showPortrait(getBaseContext(), portraitView, userInfo.getPortraitUri());
            }
        }
        if (remoteViewContainer.getVisibility() == View.VISIBLE) {
            View remoteView =
                    remoteViewContainer.findViewWithTag(
                            CallKitUtils.getStitchedContent(userInfo.getUserId(), REMOTE_VIEW_TAG));
            if (remoteView != null) {
                ImageView portraitView = (ImageView) remoteView.findViewById(R.id.user_portrait);
                GlideUtils.showPortrait(getBaseContext(), portraitView, userInfo.getPortraitUri());
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

    public void onHeadsetPlugUpdate(HeadsetInfo headsetInfo) {
        if (headsetInfo == null || !BluetoothUtil.isForground(MultiVideoCallActivity.this)) {
            FinLog.v(TAG, "MultiVideoCallActivity is not in the foreground!！");
            return;
        }
        RLog.i(
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
