package io.rong.callkit;

import static io.rong.callkit.CallSelectMemberActivity.DISCONNECT_ACTION;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.rongcloud.rtc.api.RCRTCEngine;
import cn.rongcloud.rtc.audioroute.RCAudioRouteType;
import cn.rongcloud.rtc.utils.FinLog;
import io.rong.callkit.util.BluetoothUtil;
import io.rong.callkit.util.CallKitUtils;
import io.rong.callkit.util.CallVerticalScrollView;
import io.rong.callkit.util.DefaultPushConfig;
import io.rong.callkit.util.HeadsetInfo;
import io.rong.callkit.util.ICallScrollView;
import io.rong.callkit.util.RingingMode;
import io.rong.callkit.util.RongCallPermissionUtil;
import io.rong.callkit.util.SPUtils;
import io.rong.calllib.CallUserProfile;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
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
public class MultiAudioCallActivity extends BaseCallActivity {
    private static final String TAG = "VoIPMultiAudioCallActivity";
    LinearLayout audioContainer;
    ICallScrollView memberContainer;

    RelativeLayout incomingLayout;
    RelativeLayout outgoingLayout;
    RelativeLayout outgoingController;
    RelativeLayout incomingController;
    RongCallAction callAction;
    RongCallSession callSession;

    boolean shouldShowFloat = true;
    boolean startForCheckPermissions = false;
    private boolean handFree = false;
    private boolean muted = false;
    private final String KEY_MUTED = "muted";
    private final String KEY_HAND_FREE = "handFree";

    @Override
    @TargetApi(23)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null && RongCallClient.getInstance() == null) {
            // 音视频请求权限时，用户在设置页面取消权限，导致应用重启，退出当前activity.
            finish();
            return;
        }
        setContentView(R.layout.rc_voip_ac_muti_audio);
        audioContainer = (LinearLayout) findViewById(R.id.rc_voip_container);
        incomingLayout =
                (RelativeLayout)
                        LayoutInflater.from(this)
                                .inflate(R.layout.rc_voip_item_incoming_maudio, null);
        TextView tv_invite_incoming_audio =
                incomingLayout.findViewById(R.id.tv_invite_incoming_audio);
        CallKitUtils.textViewShadowLayer(tv_invite_incoming_audio, MultiAudioCallActivity.this);

        outgoingLayout =
                (RelativeLayout)
                        LayoutInflater.from(this)
                                .inflate(R.layout.rc_voip_item_outgoing_maudio, null);
        TextView rc_voip_remind = incomingLayout.findViewById(R.id.rc_voip_remind);
        CallKitUtils.textViewShadowLayer(rc_voip_remind, MultiAudioCallActivity.this);

        outgoingController =
                (RelativeLayout)
                        LayoutInflater.from(this)
                                .inflate(
                                        R.layout.rc_voip_call_bottom_connected_button_layout, null);
        ImageView button = outgoingController.findViewById(R.id.rc_voip_call_mute_btn);
        button.setEnabled(true);
        incomingController =
                (RelativeLayout)
                        LayoutInflater.from(this)
                                .inflate(R.layout.rc_voip_call_bottom_incoming_button_layout, null);

        startForCheckPermissions = getIntent().getBooleanExtra("checkPermissions", false);
        if (requestCallPermissions(
                RongCallCommon.CallMediaType.AUDIO, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS)) {
            initView();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        startForCheckPermissions = getIntent().getBooleanExtra("checkPermissions", false);
        super.onNewIntent(intent);
        if (requestCallPermissions(
                RongCallCommon.CallMediaType.AUDIO, REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS)) {
            initView();
        }
    }

    @TargetApi(23)
    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS:
                if (RongCallPermissionUtil.checkAudioCallNeedPermission(this)) {
                    if (startForCheckPermissions) {
                        startForCheckPermissions = false;
                        RongCallClient.getInstance().onPermissionGranted();
                    } else {
                        initView();
                    }
                } else {
                    if (startForCheckPermissions) {
                        startForCheckPermissions = false;
                        Toast.makeText(
                                        this,
                                        getString(R.string.rc_voip_relevant_permissions),
                                        Toast.LENGTH_SHORT)
                                .show();
                        RongCallClient.getInstance().onPermissionDenied();
                    } else {
                        finish();
                    }
                }
                break;

            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    @Override
    public void onRestoreFloatBox(Bundle bundle) {
        super.onRestoreFloatBox(bundle);
        if (bundle != null) {
            handFree = bundle.getBoolean(KEY_HAND_FREE);
            muted = bundle.getBoolean(KEY_MUTED);
            audioContainer.removeAllViews();
            audioContainer.addView(outgoingLayout);
            String str = (String) SPUtils.get(MultiAudioCallActivity.this, "ICallScrollView", "");

            FrameLayout controller =
                    (FrameLayout) audioContainer.findViewById(R.id.rc_voip_control_layout);
            controller.removeAllViews();
            controller.addView(outgoingController);
            callSession = RongCallClient.getInstance().getCallSession();
            if (callSession == null) {
                setShouldShowFloat(false);
                finish();
                return;
            }
            List<CallUserProfile> participantProfiles = callSession.getParticipantProfileList();

            /** 初始化列表* */
            if (str.equals("CallVerticalScrollView")) {
                memberContainer =
                        (CallVerticalScrollView)
                                audioContainer.findViewById(R.id.rc_voip_members_container);
            } else {
                memberContainer =
                        (CallUserGridView)
                                audioContainer.findViewById(
                                        R.id.rc_voip_members_container_gridView);
            }
            memberContainer.enableShowState(true);
            LinearLayout linear_scrollviewTag =
                    (LinearLayout) outgoingLayout.findViewById(R.id.linear_scrollviewTag);
            if (participantProfiles.size() > 4) {
                ViewGroup.LayoutParams params = linear_scrollviewTag.getLayoutParams();
                params.height = CallKitUtils.dp2px(200, MultiAudioCallActivity.this);
                linear_scrollviewTag.setLayoutParams(params);
            }
            // 添加数据
            for (CallUserProfile item : participantProfiles) {
                if (!item.getUserId().equals(callSession.getSelfUserId())
                        && memberContainer.findChildById(item.getUserId()) == null) {
                    if (item.getCallStatus().equals(RongCallCommon.CallStatus.CONNECTED)) {
                        memberContainer.addChild(
                                item.getUserId(),
                                RongUserInfoManager.getInstance().getUserInfo(item.getUserId()));
                        memberContainer.updateChildState(item.getUserId(), false);
                    } else {
                        String state = getString(R.string.rc_voip_call_connecting);
                        memberContainer.addChild(
                                item.getUserId(),
                                RongUserInfoManager.getInstance().getUserInfo(item.getUserId()),
                                state);
                    }
                }
            }
            if (!(boolean) bundle.get("isDial")) {
                onCallConnected(callSession, null); // 接听
            } else {
                onCallOutgoing(callSession, null);
            }
        }
    }

    void initView() {
        Intent intent = getIntent();
        callAction = RongCallAction.valueOf(intent.getStringExtra("callAction"));
        if (callAction == null || callAction.equals(RongCallAction.ACTION_RESUME_CALL)) {
            RelativeLayout relativeLayout =
                    (RelativeLayout)
                            outgoingLayout.findViewById(R.id.reltive_voip_outgoing_audio_title);
            relativeLayout.setVisibility(View.VISIBLE);
            return;
        }
        ArrayList<String> invitedList = new ArrayList<>();
        if (callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
            // 正常在收到呼叫后，RongCallClient 和 CallSession均不会为空
            if (RongCallClient.getInstance() == null
                    || RongCallClient.getInstance().getCallSession() == null) {
                // 如果为空 表示通话已经结束 但依然启动了本页面，这样会导致页面无法销毁问题
                // 所以 需要在这里 finish 结束当前页面  推荐开发者在结束当前页面前跳转至APP主页或者其他页面
                RLog.e(
                        TAG,
                        "MultiAudioCallActivity#initView()->RongCallClient or CallSession is empty---->finish()");
                finish();
                return;
            }
            audioContainer.removeAllViews();
            callSession = RongCallClient.getInstance().getCallSession();
            UserInfo userInfo =
                    RongUserInfoManager.getInstance().getUserInfo(callSession.getCallerUserId());
            setTopContainerUserView(callSession.getCallerUserId());

            audioContainer.addView(incomingLayout);
            memberContainer =
                    (CallUserGridView)
                            audioContainer.findViewById(R.id.rc_voip_members_container_gridView);
            SPUtils.put(MultiAudioCallActivity.this, "ICallScrollView", "CallUserGridView");
            memberContainer.removeAllChild();
            memberContainer.setChildPortraitSize(memberContainer.dip2pix(55));
            List<CallUserProfile> list = callSession.getParticipantProfileList();
            for (CallUserProfile profile : list) {
                if (!profile.getUserId().equals(callSession.getCallerUserId())) {
                    invitedList.add(profile.getUserId());
                    userInfo = RongUserInfoManager.getInstance().getUserInfo(profile.getUserId());
                    memberContainer.addChild(profile.getUserId(), userInfo);
                }
            }
            FrameLayout controller =
                    (FrameLayout) audioContainer.findViewById(R.id.rc_voip_control_layout);
            controller.removeAllViews();
            controller.addView(incomingController);

            ImageView iv_answerBtn =
                    (ImageView) incomingController.findViewById(R.id.rc_voip_call_answer_btn);
            iv_answerBtn.setBackground(
                    CallKitUtils.BackgroundDrawable(
                            R.drawable.rc_voip_audio_answer_selector_new,
                            MultiAudioCallActivity.this));

            onIncomingCallRinging(callSession);
        } else if (callAction.equals(RongCallAction.ACTION_OUTGOING_CALL)) {
            audioContainer.removeAllViews();
            Conversation.ConversationType conversationType =
                    Conversation.ConversationType.valueOf(
                            intent.getStringExtra("conversationType").toUpperCase(Locale.US));
            String targetId = intent.getStringExtra("targetId");
            ArrayList<String> userIds = intent.getStringArrayListExtra("invitedUsers");
            ArrayList<String> observers = intent.getStringArrayListExtra("observers");
            audioContainer.addView(outgoingLayout);

            LinearLayout linear_scrollviewTag =
                    (LinearLayout) outgoingLayout.findViewById(R.id.linear_scrollviewTag);

            // 多人语音主叫方顶部布局
            RelativeLayout relativeLayout =
                    (RelativeLayout)
                            outgoingLayout.findViewById(R.id.reltive_voip_outgoing_audio_title);
            relativeLayout.setVisibility(View.VISIBLE);

            memberContainer =
                    (CallVerticalScrollView)
                            audioContainer.findViewById(R.id.rc_voip_members_container);
            SPUtils.put(MultiAudioCallActivity.this, "ICallScrollView", "CallVerticalScrollView");
            memberContainer.enableShowState(true);
            FrameLayout controller =
                    (FrameLayout) audioContainer.findViewById(R.id.rc_voip_control_layout);
            controller.removeAllViews();
            controller.addView(outgoingController);

            ImageView iv_answerBtn =
                    (ImageView) incomingController.findViewById(R.id.rc_voip_call_answer_btn);
            iv_answerBtn.setBackground(
                    CallKitUtils.BackgroundDrawable(
                            R.drawable.rc_voip_audio_answer_selector_new,
                            MultiAudioCallActivity.this));

            ImageView button = outgoingController.findViewById(R.id.rc_voip_call_mute_btn);
            button.setEnabled(true);
            for (int i = 0; i < userIds.size(); i++) {
                if (!userIds.get(i).equals(RongIMClient.getInstance().getCurrentUserId())) {
                    invitedList.add(userIds.get(i));
                    UserInfo userInfo =
                            RongUserInfoManager.getInstance().getUserInfo(userIds.get(i));
                    memberContainer.addChild(
                            userIds.get(i), userInfo, getString(R.string.rc_voip_call_connecting));
                }
            }
            //
            if (userIds.size() > 4) {
                ViewGroup.LayoutParams params = linear_scrollviewTag.getLayoutParams();
                params.height = CallKitUtils.dp2px(200, MultiAudioCallActivity.this);
                linear_scrollviewTag.setLayoutParams(params);
            }

            String groupName = "";
            Group group = RongUserInfoManager.getInstance().getGroupInfo(targetId);
            if (group != null && !TextUtils.isEmpty(group.getName())) {
                groupName = group.getName();
            }
            RongCallClient.getInstance()
                    .setPushConfig(
                            DefaultPushConfig.getInviteConfig(this, true, false, groupName),
                            DefaultPushConfig.getHangupConfig(this, false, groupName));

            RongCallClient.getInstance()
                    .startCall(
                            conversationType,
                            targetId,
                            invitedList,
                            observers,
                            RongCallCommon.CallMediaType.AUDIO,
                            "multi");
        }
        memberContainer.setScrollViewOverScrollMode(View.OVER_SCROLL_NEVER);
        createPickupDetector();
    }

    @Override
    protected void onPause() {
        if (pickupDetector != null) {
            pickupDetector.unRegister();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        if (pickupDetector == null) createPickupDetector();
        if (pickupDetector != null) {
            pickupDetector.register(this);
        }
        super.onResume();
    }

    public void onHangupBtnClick(View view) {
        //        unRegisterHeadsetplugReceiver();
        if (callSession == null || isFinishing) {
            FinLog.e(
                    TAG,
                    "_挂断多人语音出错 callSession="
                            + (callSession == null)
                            + ",isFinishing="
                            + isFinishing);
            return;
        }
        RongCallClient.getInstance().hangUpCall(callSession.getCallId());
    }

    public void onReceiveBtnClick(View view) {
        if (callSession == null || isFinishing) {
            FinLog.e(
                    TAG,
                    "_接听多人语音出错 callSession="
                            + (callSession == null)
                            + ",isFinishing="
                            + isFinishing);
            return;
        }
        RongCallClient.getInstance().acceptCall(callSession.getCallId());
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

    @Override
    public void onRemoteUserRinging(String userId) {}

    @Override
    public void onCallOutgoing(RongCallSession callSession, SurfaceView localVideo) {
        super.onCallOutgoing(callSession, localVideo);
        this.callSession = callSession;
        callRinging(RingingMode.Outgoing);
    }

    @Override
    public void onRemoteUserInvited(String userId, RongCallCommon.CallMediaType mediaType) {
        super.onRemoteUserInvited(userId, mediaType);
        memberContainer.addChild(
                userId,
                RongUserInfoManager.getInstance().getUserInfo(userId),
                getString(R.string.rc_voip_call_connecting));
    }

    @Override
    public void onRemoteUserJoined(
            String userId,
            RongCallCommon.CallMediaType mediaType,
            int userType,
            SurfaceView remoteVideo) {
        View view = memberContainer.findChildById(userId);
        if (view != null) {
            memberContainer.updateChildState(userId, false);
        } else {
            memberContainer.addChild(userId, RongUserInfoManager.getInstance().getUserInfo(userId));
        }
    }

    @Override
    public void onRemoteUserLeft(
            final String userId, RongCallCommon.CallDisconnectedReason reason) {
        if (isTopContainerUserExit(userId)) {
            return;
        }
        String text = null;
        switch (reason) {
            case REMOTE_BUSY_LINE:
                text = getString(R.string.rc_voip_mt_busy_toast);
                break;
            case REMOTE_CANCEL:
                text = getString(R.string.rc_voip_mt_cancel);
                break;
            case REMOTE_REJECT:
                text = getString(R.string.rc_voip_mt_reject);
                break;
            case NO_RESPONSE:
                text = getString(R.string.rc_voip_mt_no_response);
                break;
            case NETWORK_ERROR:
            case HANGUP:
            case REMOTE_HANGUP:
                break;
        }
        if (text != null && memberContainer != null) {
            memberContainer.updateChildState(userId, text);
        }
        if (memberContainer != null) {
            memberContainer.removeChild(userId);
        }
    }

    private boolean isTopContainerUserExit(String userId) {
        if (CallKitUtils.callConnected) {
            return false;
        }
        if (callSession != null
                && TextUtils.equals(callSession.getInviterUserId(), userId)
                && memberContainer != null) {
            if (((LinearLayout) memberContainer.getChildAtIndex(0)) != null
                    && ((LinearLayout) memberContainer.getChildAtIndex(0)).getChildAt(0) != null
                    && ((LinearLayout) memberContainer.getChildAtIndex(0)).getChildAt(0).getTag()
                            != null) {
                LinearLayout firstView =
                        (LinearLayout)
                                ((LinearLayout) memberContainer.getChildAtIndex(0)).getChildAt(0);
                String firstUserId = (String) firstView.getTag();
                setTopContainerUserView(firstUserId);

                memberContainer.removeChild(firstUserId);

                LinearLayout linearLayout =
                        (LinearLayout)
                                ((LinearLayout) memberContainer.getChildAtIndex(0)).getChildAt(0);
                linearLayout.setPadding(
                        memberContainer.dip2pix(15),
                        0,
                        memberContainer.dip2pix(memberContainer.getChildrenSpace()),
                        0);
                linearLayout.requestLayout();
                return true;
            }
        }
        return false;
    }

    private void setTopContainerUserView(String userId) {
        TextView name = (TextView) incomingLayout.findViewById(R.id.rc_user_name);
        ImageView userPortrait =
                (ImageView) incomingLayout.findViewById(R.id.rc_voip_user_portrait);
        UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(userId);
        if (userInfo != null && userInfo.getName() != null) {
            name.setText(userInfo.getName());
        } else {
            name.setText(userId);
        }
        if (userInfo != null && userInfo.getPortraitUri() != null) {
            RongCallKit.getKitImageEngine()
                    .loadPortrait(
                            getBaseContext(),
                            userInfo.getPortraitUri(),
                            R.drawable.rc_default_portrait,
                            userPortrait);
            userPortrait.setVisibility(View.VISIBLE);
        }
        name.setTag(userId + "callerName");
    }

    /**
     * 已建立通话。 通话接通时，通过回调 onCallConnected 通知当前 call 的详细信息。
     *
     * @param callSession 通话实体。
     * @param localVideo 本地 camera 信息。
     */
    @Override
    public void onCallConnected(final RongCallSession callSession, SurfaceView localVideo) {
        super.onCallConnected(callSession, localVideo);
        RongCallClient.getInstance().setEnableLocalVideo(false);
        this.callSession = callSession;
        if (callAction.equals(RongCallAction.ACTION_INCOMING_CALL)) {
            audioContainer.removeAllViews();
            FrameLayout controller =
                    (FrameLayout) outgoingLayout.findViewById(R.id.rc_voip_control_layout);
            controller.addView(outgoingController);
            audioContainer.addView(outgoingLayout);
            SPUtils.put(MultiAudioCallActivity.this, "ICallScrollView", "CallVerticalScrollView");
            // 多人语音通话中竖向滑动
            memberContainer =
                    (CallVerticalScrollView)
                            outgoingLayout.findViewById(R.id.rc_voip_members_container);
            memberContainer.enableShowState(true);
            LinearLayout linear_scrollviewTag =
                    (LinearLayout) outgoingLayout.findViewById(R.id.linear_scrollviewTag);
            if (callSession.getParticipantProfileList().size() > 4) {
                ViewGroup.LayoutParams params = linear_scrollviewTag.getLayoutParams();
                params.height = CallKitUtils.dp2px(200, MultiAudioCallActivity.this);
                linear_scrollviewTag.setLayoutParams(params);
            }
            for (CallUserProfile profile : callSession.getParticipantProfileList()) {
                if (!profile.getUserId().equals(callSession.getSelfUserId())) {
                    UserInfo userInfo =
                            RongUserInfoManager.getInstance().getUserInfo(profile.getUserId());
                    String state =
                            profile.getCallStatus().equals(RongCallCommon.CallStatus.CONNECTED)
                                    ? null
                                    : getString(R.string.rc_voip_call_connecting);
                    memberContainer.addChild(profile.getUserId(), userInfo, state);
                }
            }
        }

        outgoingLayout.findViewById(R.id.rc_voip_remind).setVisibility(View.GONE);
        outgoingLayout.findViewById(R.id.rc_voip_handfree).setVisibility(View.VISIBLE);
        ImageView button = outgoingController.findViewById(R.id.rc_voip_call_mute_btn);
        button.setEnabled(true);
        outgoingLayout.findViewById(R.id.rc_voip_call_mute).setVisibility(View.VISIBLE);
        // 多人语音主叫方顶部布局
        RelativeLayout relativeLayout =
                (RelativeLayout)
                        outgoingLayout.findViewById(R.id.reltive_voip_outgoing_audio_title);
        relativeLayout.setVisibility(View.GONE);

        View muteV = outgoingLayout.findViewById(R.id.rc_voip_call_mute_btn);
        muteV.setVisibility(View.VISIBLE);
        muteV.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onMuteButtonClick(v);
                    }
                });

        View handfreeV = outgoingLayout.findViewById(R.id.rc_voip_handfree_btn);
        handfreeV.setVisibility(View.VISIBLE);
        handfreeV.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        onHandFreeButtonClick(v);
                    }
                });

        outgoingLayout.findViewById(R.id.rc_voip_title).setVisibility(View.VISIBLE);
        TextView timeV = (TextView) outgoingLayout.findViewById(R.id.rc_voip_time);
        setupTime(timeV);

        View imgvAdd = outgoingLayout.findViewById(R.id.rc_voip_add_btn);
        imgvAdd.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setShouldShowFloat(false);
                        if (callSession
                                .getConversationType()
                                .equals(Conversation.ConversationType.DISCUSSION)) {
                            RongDiscussionClient.getInstance()
                                    .getDiscussion(
                                            callSession.getTargetId(),
                                            new IRongCoreCallback.ResultCallback<Discussion>() {
                                                @Override
                                                public void onSuccess(Discussion discussion) {
                                                    Intent intent =
                                                            new Intent(
                                                                    MultiAudioCallActivity.this,
                                                                    CallSelectMemberActivity.class);
                                                    ArrayList<String> added =
                                                            new ArrayList<String>();
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
                                                            "allObserver",
                                                            new ArrayList<>(allObserver));
                                                    intent.putStringArrayListExtra(
                                                            "allMembers",
                                                            (ArrayList<String>)
                                                                    discussion.getMemberIdList());
                                                    intent.putStringArrayListExtra(
                                                            "invitedMembers", added);
                                                    intent.putExtra(
                                                            "conversationType",
                                                            callSession
                                                                    .getConversationType()
                                                                    .getValue());
                                                    intent.putExtra(
                                                            "mediaType",
                                                            RongCallCommon.CallMediaType.AUDIO
                                                                    .getValue());
                                                    startActivityForResult(
                                                            intent, REQUEST_CODE_ADD_MEMBER);
                                                }

                                                @Override
                                                public void onError(
                                                        IRongCoreEnum.CoreErrorCode e) {}
                                            });
                        } else if (callSession
                                .getConversationType()
                                .equals(Conversation.ConversationType.GROUP)) {
                            Intent intent =
                                    new Intent(
                                            MultiAudioCallActivity.this,
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
                            intent.putExtra("callId", callSession.getCallId());
                            intent.putStringArrayListExtra(
                                    "allObserver", new ArrayList<>(allObserver));
                            intent.putStringArrayListExtra("invitedMembers", added);
                            intent.putExtra(
                                    "conversationType",
                                    callSession.getConversationType().getValue());
                            intent.putExtra("groupId", callSession.getTargetId());
                            intent.putExtra(
                                    "mediaType", RongCallCommon.CallMediaType.AUDIO.getValue());
                            startActivityForResult(intent, REQUEST_CODE_ADD_MEMBER);
                        } else {
                            ArrayList<String> added = new ArrayList<>();
                            List<CallUserProfile> list =
                                    RongCallClient.getInstance()
                                            .getCallSession()
                                            .getParticipantProfileList();
                            for (CallUserProfile profile : list) {
                                added.add(profile.getUserId());
                            }
                            addMember(added);
                        }
                    }
                });
        outgoingLayout.findViewById(R.id.rc_voip_minimize_outgoing).setVisibility(View.VISIBLE);
        View minimizeV = outgoingLayout.findViewById(R.id.rc_voip_minimize);
        minimizeV.setVisibility(View.VISIBLE);
        minimizeV.setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Log.i(
                                "audioTag",
                                "************ outgoingLayout.findViewById(R.id.rc_voip_minimize)*****************");
                        MultiAudioCallActivity.super.onMinimizeClick(v);
                    }
                });

        RongCallClient.getInstance().setEnableLocalAudio(!muted);
        if (muteV != null) {
            muteV.setSelected(muted);
        }
        RCRTCEngine.getInstance().enableSpeaker(false);

        stopRing();
    }

    protected void resetHandFreeStatus(RCAudioRouteType type) {
        ImageView handFreeV = null;
        if (null != outgoingLayout) {
            handFreeV = outgoingLayout.findViewById(R.id.rc_voip_handfree_btn);
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

    @Override
    public void onCallDisconnected(
            RongCallSession callSession, RongCallCommon.CallDisconnectedReason reason) {
        super.onCallDisconnected(callSession, reason);

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
        multiCallEndMessage.setReason(reason);
        multiCallEndMessage.setMediaType(IRongCoreEnum.MediaType.AUDIO);
        long serverTime = System.currentTimeMillis() - RongIMClient.getInstance().getDeltaTime();
        IMCenter.getInstance()
                .insertIncomingMessage(
                        callSession.getConversationType(),
                        callSession.getTargetId(),
                        callSession.getCallerUserId(),
                        CallKitUtils.getReceivedStatus(reason),
                        multiCallEndMessage,
                        serverTime,
                        null);
        cancelTime();
        stopRing();
        postRunnableDelay(
                new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
        sendBroadcast(new Intent(DISCONNECT_ACTION).setPackage(getPackageName()));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        callSession = RongCallClient.getInstance().getCallSession();
        if (requestCode == REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS) {
            if (RongCallPermissionUtil.checkAudioCallNeedPermission(this)) {
                if (startForCheckPermissions) {
                    startForCheckPermissions = false;
                    RongCallClient.getInstance().onPermissionGranted();
                } else {
                    initView();
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
            shouldShowFloat = true;
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

    public void onHandFreeButtonClick(View view) {
        RongCallClient.getInstance().setEnableSpeakerphone(!view.isSelected());
        view.setSelected(!view.isSelected());
        handFree = view.isSelected();
    }

    public void onMuteButtonClick(View view) {
        RongCallClient.getInstance().setEnableLocalAudio(view.isSelected());
        view.setSelected(!view.isSelected());
        muted = view.isSelected();
    }

    @Override
    public String onSaveFloatBoxState(Bundle bundle) {
        super.onSaveFloatBoxState(bundle);
        String intentAction = null;
        Log.i("audioTag", "onSaveFloatBoxState  shouldShowFloat=" + shouldShowFloat);
        if (shouldShowFloat) {
            intentAction = getIntent().getAction();
            bundle.putInt("mediaType", RongCallCommon.CallMediaType.AUDIO.getValue());
            bundle.putBoolean(KEY_HAND_FREE, handFree);
            bundle.putBoolean(KEY_MUTED, muted);
        }
        return intentAction;
    }

    @Override
    public void onBackPressed() {
        return;
    }

    public void onMinimizeClick(View view) {
        super.onMinimizeClick(view);
    }

    @Override
    public void onUserUpdate(UserInfo userInfo) {
        if (isFinishing()) {
            return;
        }
        TextView callerName =
                (TextView) audioContainer.findViewWithTag(userInfo.getUserId() + "callerName");

        if (callerName != null && userInfo.getName() != null) {
            callerName.setLines(1);
            callerName.setEllipsize(TextUtils.TruncateAt.END);
            callerName.setText(userInfo.getName());
        }
        if (memberContainer != null
                && memberContainer.findChildById(userInfo.getUserId()) != null) {
            memberContainer.updateChildInfo(userInfo.getUserId(), userInfo);
        }
    }

    public void onHeadsetPlugUpdate(HeadsetInfo headsetInfo) {
        if (headsetInfo == null || !BluetoothUtil.isForground(MultiAudioCallActivity.this)) {
            FinLog.v("bugtags", "MultiAudioCallActivity 不在前台！");
            return;
        }
        Log.i(
                "bugtags",
                "Insert="
                        + headsetInfo.isInsert()
                        + ",headsetInfo.getType="
                        + headsetInfo.getType().getValue());
        try {
            if (headsetInfo.isInsert()) {
                RongCallClient.getInstance().setEnableSpeakerphone(false);
                ImageView handFreeV = null;
                if (null != outgoingLayout) {
                    handFreeV = outgoingLayout.findViewById(R.id.rc_voip_handfree_btn);
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
                ImageView handFreeV = null;
                if (null != outgoingLayout) {
                    handFreeV = outgoingLayout.findViewById(R.id.rc_voip_handfree_btn);
                }
                if (handFreeV != null) {
                    handFreeV.setSelected(true);
                    handFreeV.setEnabled(true);
                    handFreeV.setClickable(true);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.i("bugtags", "MultiAudioCallActivity->onHeadsetPlugUpdate Error=" + e.getMessage());
        }
    }
}
