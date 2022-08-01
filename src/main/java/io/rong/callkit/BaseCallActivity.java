package io.rong.callkit;

import static io.rong.callkit.CallFloatBoxView.showFB;
import static io.rong.callkit.util.CallKitUtils.isDial;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import cn.rongcloud.rtc.api.RCRTCAudioRouteManager;
import cn.rongcloud.rtc.api.callback.IRCRTCAudioRouteListener;
import cn.rongcloud.rtc.api.stream.RCRTCVideoStreamConfig.Builder;
import cn.rongcloud.rtc.audioroute.RCAudioRouteType;
import cn.rongcloud.rtc.base.RCRTCParamsType.RCRTCVideoFps;
import cn.rongcloud.rtc.base.RCRTCParamsType.RCRTCVideoResolution;
import cn.rongcloud.rtc.utils.FinLog;
import io.rong.callkit.util.BluetoothUtil;
import io.rong.callkit.util.CallKitUtils;
import io.rong.callkit.util.CallRingingUtil;
import io.rong.callkit.util.HeadsetInfo;
import io.rong.callkit.util.RingingMode;
import io.rong.callkit.util.RongCallPermissionUtil;
import io.rong.calllib.IRongCallListener;
import io.rong.calllib.PublishCallBack;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallCommon.CallMediaType;
import io.rong.calllib.RongCallSession;
import io.rong.common.RLog;
import io.rong.imkit.manager.AudioPlayManager;
import io.rong.imkit.manager.AudioRecordManager;
import io.rong.imkit.notification.NotificationUtil;
import io.rong.imkit.userinfo.RongUserInfoManager;
import io.rong.imkit.userinfo.model.GroupUserInfo;
import io.rong.imlib.model.Group;
import io.rong.imlib.model.UserInfo;
import io.rong.push.notification.RongNotificationInterface;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/** Created by weiqinxiao on 16/3/9. */
public class BaseCallActivity extends BaseNoActionBarActivity
        implements IRongCallListener,
                PickupDetector.PickupDetectListener,
                RongUserInfoManager.UserDataObserver {

    private static final String TAG = "BaseCallActivity";
    private static final String MEDIAPLAYERTAG = "MEDIAPLAYERTAG";
    private static final long DELAY_TIME = 1000;
    static final int REQUEST_CODE_ASK_MULTIPLE_PERMISSIONS = 100;
    static final int REQUEST_CODE_ADD_MEMBER = 110;
    public final int REQUEST_CODE_ADD_MEMBER_NONE = 120;
    static final int VOIP_MAX_NORMAL_COUNT = 6;

    private long time = 0;
    private Runnable updateTimeRunnable;

    private boolean shouldRestoreFloat;
    // 是否是请求开启悬浮窗权限的过程中
    private boolean checkingOverlaysPermission;
    private boolean notRemindRequestFloatWindowPermissionAgain = false;
    protected Handler handler;
    /** 表示是否正在挂断 */
    protected boolean isFinishing;

    protected PickupDetector pickupDetector;
    protected PowerManager powerManager;
    protected PowerManager.WakeLock wakeLock;
    protected PowerManager.WakeLock screenLock;

    //    static final String[] VIDEO_CALL_PERMISSIONS = {
    //        Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA
    //    };
    //    static final String[] AUDIO_CALL_PERMISSIONS = {Manifest.permission.RECORD_AUDIO};

    public static final int CALL_NOTIFICATION_ID = 4000;
    boolean isMuteCamera = false;

    /**
     * 融云 SDK 默认麦克风、摄像头流唯一标识，和 RongCallClient#publishCustomVideoStream(tag, PublishCallBack) 方法中 tag
     * 用法一致; 用户发布自定义视频流唯一标示，不允许带下划线，不能为 “RongCloudRTC”;
     *
     * @see RongCallClient#publishCustomVideoStream(String, PublishCallBack)
     */
    public static final String RONG_TAG_CALL = "RongCloudRTC";

    RelativeLayout.LayoutParams mLargeLayoutParams =
            new RelativeLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

    public void setShouldShowFloat(boolean ssf) {
        CallKitUtils.shouldShowFloat = ssf;
    }

    public void showShortToast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    public void postRunnableDelay(Runnable runnable) {
        handler.postDelayed(runnable, DELAY_TIME);
    }

    private HeadsetPlugReceiver headsetPlugReceiver = null;
    private AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener;

    public static final String EXTRA_BUNDLE_KEY_MUTECAMERA = "muteCamera";
    public static final String EXTRA_BUNDLE_KEY_MUTEMIC = "muteMIC";
    public static final String EXTRA_BUNDLE_KEY_LOCALVIEWUSERID = "localViewUserId";
    public static final String EXTRA_BUNDLE_KEY_CALLACTION = "callAction";
    public static final String EXTRA_BUNDLE_KEY_MEDIATYPE = "mediaType";
    public static final String EXTRA_BUNDLE_KEY_USER_TOP_NAME = "rc_voip_user_top_name";
    public static final String EXTRA_BUNDLE_KEY_USER_TOP_NAME_TAG = "rc_voip_user_top_name_tag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        RLog.d(TAG, "BaseCallActivity onCreate");
        audioVideoConfig();
        getWindow()
                .setFlags(
                        WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
                        WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow()
                .setFlags(
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
                        WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow()
                .addFlags(
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                                | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        shouldRestoreFloat = true;
        CallKitUtils.shouldShowFloat = false;

        createPowerManager();
        boolean isScreenOn = powerManager.isScreenOn();
        if (!isScreenOn) {
            wakeLock.acquire();
        }
        handler = new Handler();
        mLargeLayoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        RongCallProxy.getInstance().setCallListener(this);

        AudioPlayManager.getInstance().stopPlay();
        AudioRecordManager.getInstance().destroyRecord();
        RongUserInfoManager.getInstance().addUserDataObserver(this);

        AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (am != null) {
            onAudioFocusChangeListener =
                    new AudioManager.OnAudioFocusChangeListener() {
                        @Override
                        public void onAudioFocusChange(int focusChange) {}
                    };
            am.requestAudioFocus(
                    onAudioFocusChangeListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        if (Build.VERSION.SDK_INT >= 31
                && getApplication().getApplicationInfo().targetSdkVersion >= 31) {
            // Android 12 禁止了通过广播和服务跳板的方式启动 Activity，此代码是为了兼容之前的逻辑
            Intent intent = new Intent();
            intent.setAction(VoIPBroadcastReceiver.ACTION_CLEAR_VOIP_NOTIFICATION);
            intent.setPackage(getPackageName());
            sendBroadcast(intent);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent intent = getIntent();
        Bundle bundle = intent.getBundleExtra("floatbox");
        if (shouldRestoreFloat && bundle != null) {
            onRestoreFloatBox(bundle);
        }
    }

    public void callRinging(RingingMode mode) {
        CallRingingUtil.getInstance().startRinging(this, mode);
    }

    public void onIncomingCallRinging(RongCallSession callSession) {
        CallRingingUtil.getInstance().startRinging(this, RingingMode.Incoming);
        if (callSession != null) {
            String callId = callSession.getCallId();
            if (!TextUtils.isEmpty(callId)
                    && callId.equals(
                            getIntent()
                                    .getStringExtra(
                                            RongIncomingCallService.KEY_NEED_AUTO_ANSWER))) {
                RongCallClient.getInstance().acceptCall(callId);
            }
        }
    }

    public void setupTime(final TextView timeView) {
        try {
            if (updateTimeRunnable != null) {
                handler.removeCallbacks(updateTimeRunnable);
            }
            timeView.setVisibility(View.VISIBLE);
            updateTimeRunnable = new UpdateTimeRunnable(timeView);
            handler.post(updateTimeRunnable);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void cancelTime() {
        if (handler != null && updateTimeRunnable != null) {
            handler.removeCallbacks(updateTimeRunnable);
        }
    }

    public long getTime(long activeTime) {
        long tmpTime = activeTime == 0 ? 0 : (System.currentTimeMillis() - activeTime) / 1000;
        time = tmpTime == 0 ? time : tmpTime;
        return time;
    }

    @SuppressLint("MissingPermission")
    protected void stopRing() {
        CallRingingUtil.getInstance().stopRinging();
    }

    @Override
    public void onCallOutgoing(RongCallSession callProfile, SurfaceView localVideo) {
        CallKitUtils.shouldShowFloat = true;
        CallKitUtils.isDial = true;
    }

    @Override
    public void onRemoteUserRinging(String userId) {}

    @Override
    public void onRemoteUserAccept(String userId, CallMediaType mediaType) {}

    @Override
    public void onCallDisconnected(
            RongCallSession callProfile, RongCallCommon.CallDisconnectedReason reason) {
        if (RongCallKit.getCustomerHandlerListener() != null) {
            RongCallKit.getCustomerHandlerListener().onCallDisconnected(callProfile, reason);
        }
        CallKitUtils.callConnected = false;
        CallKitUtils.shouldShowFloat = false;

        toastDisconnectReason(reason);

        AudioPlayManager.getInstance().setInVoipMode(false);
        stopRing();
        NotificationUtil.getInstance()
                .clearNotification(this, BaseCallActivity.CALL_NOTIFICATION_ID);
        RongCallProxy.getInstance().setCallListener(null);
    }

    @Override
    public void onRemoteUserJoined(
            String userId,
            RongCallCommon.CallMediaType mediaType,
            int userType,
            SurfaceView remoteVideo) {
        CallKitUtils.isDial = false;
    }

    @Override
    public void onRemoteUserInvited(String userId, RongCallCommon.CallMediaType mediaType) {
        if (RongCallKit.getCustomerHandlerListener() != null) {
            RongCallKit.getCustomerHandlerListener().onRemoteUserInvited(userId, mediaType);
        }
    }

    @Override
    public void onRemoteUserLeft(String userId, RongCallCommon.CallDisconnectedReason reason) {
        RLog.i(
                TAG,
                "onRemoteUserLeft userId :"
                        + userId
                        + ", CallDisconnectedReason :"
                        + reason.name());
        toastDisconnectReason(reason);
    }

    @Override
    public void onMediaTypeChanged(
            String userId, RongCallCommon.CallMediaType mediaType, SurfaceView video) {}

    @Override
    public void onError(RongCallCommon.CallErrorCode errorCode) {
        AudioPlayManager.getInstance().setInVoipMode(false);
        if (RongCallCommon.CallErrorCode.ENGINE_NOT_FOUND.getValue() == errorCode.getValue()) {
            Toast.makeText(
                            this,
                            getResources().getString(R.string.rc_voip_engine_notfound),
                            Toast.LENGTH_SHORT)
                    .show();
            finish();
        }
    }

    @Override
    public void onCallConnected(RongCallSession callProfile, SurfaceView localVideo) {
        registerAudioRouteTypeChange();
        if (RongCallKit.getCustomerHandlerListener() != null) {
            RongCallKit.getCustomerHandlerListener().onCallConnected(callProfile, localVideo);
        }
        CallKitUtils.callConnected = true;
        CallKitUtils.shouldShowFloat = true;
        CallKitUtils.isDial = false;
        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (audioManager != null) {
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
        }
        AudioPlayManager.getInstance().setInVoipMode(true);
        AudioRecordManager.getInstance().destroyRecord();
    }

    private void registerAudioRouteTypeChange() {
        RCRTCAudioRouteManager.getInstance()
                .setOnAudioRouteChangedListener(
                        new IRCRTCAudioRouteListener() {
                            @Override
                            public void onRouteChanged(RCAudioRouteType type) {
                                resetHandFreeStatus(type);
                            }

                            @Override
                            public void onRouteSwitchFailed(
                                    RCAudioRouteType fromType, RCAudioRouteType toType) {}
                        });
    }

    protected void resetHandFreeStatus(RCAudioRouteType type) {}

    @Override
    protected void onStop() {
        super.onStop();
        RLog.d(TAG, "BaseCallActivity onStop");
        if (CallKitUtils.shouldShowFloat && !checkingOverlaysPermission) {
            Bundle bundle = new Bundle();
            String action = onSaveFloatBoxState(bundle);
            if (checkDrawOverlaysPermission(true)) {
                if (action != null) {
                    bundle.putString("action", action);
                    showFB(getApplicationContext(), bundle);
                    int mediaType = bundle.getInt("mediaType");
                    showOnGoingNotification(
                            getString(R.string.rc_call_on_going),
                            mediaType == RongCallCommon.CallMediaType.AUDIO.getValue()
                                    ? getString(R.string.rc_audio_call_on_going)
                                    : getString(R.string.rc_video_call_on_going));
                    if (!isFinishing()) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            RLog.d(TAG, "BaseCallActivity onStop finishAndRemoveTask()");
                            finishAndRemoveTask();
                        } else {
                            RLog.d(TAG, "BaseCallActivity onStop finish()");
                            finish();
                        }
                    }
                }
            } else {
                Toast.makeText(
                                this,
                                getString(R.string.rc_voip_float_window_not_allowed),
                                Toast.LENGTH_SHORT)
                        .show();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        RLog.d(TAG, "BaseCallActivity onResume");
        try {
            RongCallSession session = RongCallClient.getInstance().getCallSession();
            if (session != null) {
                if (session.getMediaType() == RongCallCommon.CallMediaType.VIDEO && !isMuteCamera) {
                    RongCallClient.getInstance().startCapture();
                }
                RongCallProxy.getInstance().setCallListener(this);
                if (shouldRestoreFloat) {
                    CallFloatBoxView.hideFloatBox();
                    NotificationUtil.getInstance()
                            .clearNotification(this, BaseCallActivity.CALL_NOTIFICATION_ID);
                    CallRingingUtil.getInstance().stopServiceButContinueRinging(this);
                }
                time = getTime(session.getActiveTime());
                shouldRestoreFloat = true;
                if (time > 0) {
                    CallKitUtils.shouldShowFloat = true;
                }
                if (checkingOverlaysPermission) {
                    checkDrawOverlaysPermission(false);
                    checkingOverlaysPermission = false;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            RLog.d(TAG, "BaseCallActivity onResume Error : " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        shouldRestoreFloat = false;
        if (RongCallKit.getCustomerHandlerListener() != null) {
            List<String> selectedUserIds =
                    RongCallKit.getCustomerHandlerListener()
                            .handleActivityResult(requestCode, resultCode, data);
            if (selectedUserIds != null && selectedUserIds.size() > 0) onAddMember(selectedUserIds);
        }
    }

    @Override
    protected void onDestroy() {
        try {
            RLog.d(TAG, "BaseCallActivity onDestroy");
            RongUserInfoManager.getInstance().removeUserDataObserver(this);
            //            RongUserInfoManager.getInstance().remove
            handler.removeCallbacks(updateTimeRunnable);
            CallRingingUtil.getInstance().stopRinging();

            // 退出此页面后应设置成正常模式，否则按下音量键无法更改其他音频类型的音量
            AudioManager am = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setMode(AudioManager.MODE_NORMAL);
                if (onAudioFocusChangeListener != null) {
                    am.abandonAudioFocus(onAudioFocusChangeListener);
                }
            }
        } catch (IllegalStateException e) {
            e.printStackTrace();
            Log.i(MEDIAPLAYERTAG, "--- onDestroy IllegalStateException---");
        }
        super.onDestroy();
        //        unRegisterHeadsetplugReceiver();
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }

        if (screenLock != null && screenLock.isHeld()) {
            try {
                screenLock.setReferenceCounted(false);
                screenLock.release();
            } catch (Exception e) {

            }
        }
    }

    @Override
    public void onRemoteCameraDisabled(String userId, boolean disabled) {}

    @Override
    public void onRemoteMicrophoneDisabled(String userId, boolean disabled) {}

    @Override
    public void onNetworkReceiveLost(String userId, int lossRate) {}

    @Override
    public void onNetworkSendLost(int lossRate, int delay) {}

    @Override
    public void onFirstRemoteVideoFrame(String userId, int height, int width) {}

    @Override
    public void onFirstRemoteAudioFrame(String userId) {}

    @Override
    public void onAudioLevelSend(String audioLevel) {}

    @Override
    public void onAudioLevelReceive(HashMap<String, String> audioLevel) {}

    private void toastDisconnectReason(RongCallCommon.CallDisconnectedReason reason) {
        String text = null;
        switch (reason) {
            case CANCEL:
                text = getString(R.string.rc_voip_mo_cancel);
                break;
            case REJECT:
                text = getString(R.string.rc_voip_mo_reject);
                break;
            case NO_RESPONSE:
            case BUSY_LINE:
                text = getString(R.string.rc_voip_mo_no_response);
                break;
            case REMOTE_BUSY_LINE:
                text = getString(R.string.rc_voip_mt_busy_toast);
                break;
            case REMOTE_CANCEL:
                text = getString(R.string.rc_voip_mt_cancel);
                break;
            case REMOTE_REJECT:
                text = getString(R.string.rc_voip_mt_reject);
                break;
            case REMOTE_NO_RESPONSE:
                text = getString(R.string.rc_voip_mt_no_response);
                break;
            case NETWORK_ERROR:
                if (!CallKitUtils.isNetworkAvailable(this)) {
                    text = getString(R.string.rc_voip_call_network_error);
                } else {
                    text = getString(R.string.rc_voip_call_terminalted);
                }
                break;
            case REMOTE_HANGUP:
            case HANGUP:
            case INIT_VIDEO_ERROR:
                text = getString(R.string.rc_voip_call_terminalted);
                break;
            case OTHER_DEVICE_HAD_ACCEPTED:
                text = getString(R.string.rc_voip_call_other);
                break;
        }
        if (text != null) {
            showShortToast(text);
        }
    }

    public void onRemoteUserPublishVideoStream(
            String userId, String streamId, String tag, SurfaceView surfaceView) {}

    @Override
    public void onRemoteUserUnpublishVideoStream(String userId, String streamId, String tag) {}

    /** onStart时恢复浮窗 * */
    public void onRestoreFloatBox(Bundle bundle) {
        isMuteCamera = bundle.getBoolean(EXTRA_BUNDLE_KEY_MUTECAMERA);
    }

    protected void addMember(ArrayList<String> currentMemberIds) {
        // do your job to add more member
        // after got your new member, call onAddMember
        if (RongCallKit.getCustomerHandlerListener() != null) {
            RongCallKit.getCustomerHandlerListener().addMember(this, currentMemberIds);
        }
    }

    protected void onAddMember(List<String> newMemberIds) {}

    /** onPause时保存页面各状态数据 * */
    public String onSaveFloatBoxState(Bundle bundle) {
        return null;
    }

    public void showOnGoingNotification(String title, String content) {
        Intent intent = new Intent(getIntent().getAction());
        Bundle bundle = new Bundle();
        onSaveFloatBoxState(bundle);
        bundle.putBoolean("isDial", isDial);
        intent.putExtra("floatbox", bundle);
        intent.putExtra("callAction", RongCallAction.ACTION_RESUME_CALL.getName());
        PendingIntent pendingIntent;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            pendingIntent =
                    PendingIntent.getActivity(
                            this,
                            1000,
                            intent,
                            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            pendingIntent =
                    PendingIntent.getActivity(
                            this, 1000, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        }
        showNotification(this, title, content, pendingIntent, CALL_NOTIFICATION_ID);
    }

    private void showNotification(
            Context context,
            String title,
            String content,
            PendingIntent pendingIntent,
            int notificationId) {
        String channelId = CallRingingUtil.getInstance().getNotificationChannelId();
        Notification notification =
                RongNotificationInterface.createNotification(
                        context,
                        title,
                        pendingIntent,
                        content,
                        RongNotificationInterface.SoundType.SILENT,
                        channelId);
        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        notification.flags |= Notification.FLAG_AUTO_CANCEL;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            int importance = NotificationManager.IMPORTANCE_LOW;
            String channelName = CallRingingUtil.getInstance().getNotificationChannelName(this);
            NotificationChannel notificationChannel =
                    new NotificationChannel(channelId, channelName, importance);
            notificationChannel.enableLights(false);
            notificationChannel.setLightColor(Color.GREEN);
            notificationChannel.enableVibration(false);
            notificationChannel.setSound(null, null);
            nm.createNotificationChannel(notificationChannel);
        }

        if (notification != null) {
            io.rong.push.common.RLog.i(
                    TAG,
                    "sendNotification() real notify! notificationId: "
                            + notificationId
                            + " notification: "
                            + notification.toString());
            nm.notify(notificationId, notification);
        }
    }

    @TargetApi(23)
    boolean requestCallPermissions(RongCallCommon.CallMediaType type, int requestCode) {
        //        String[] permissions = null;
        Log.i(TAG, "BaseActivty requestCallPermissions requestCode=" + requestCode);
        //        if (type.equals(RongCallCommon.CallMediaType.VIDEO)
        //                || type.equals(RongCallCommon.CallMediaType.AUDIO)) {
        //            permissions = CallKitUtils.getCallpermissions();
        //        }
        //        boolean result = false;
        //        if (permissions != null) {
        //            boolean granted = CallKitUtils.checkPermissions(this, permissions);
        //            Log.i(TAG, "BaseActivty requestCallPermissions granted=" + granted);
        //            if (granted) {
        //                result = true;
        //            } else {
        //                PermissionCheckUtil.requestPermissions(this, permissions, requestCode);
        //            }
        //        }

        return RongCallPermissionUtil.checkAndRequestPermissionByCallType(this, type, requestCode);
    }

    @Override
    public void onUserUpdate(UserInfo info) {}

    @Override
    public void onGroupUpdate(Group group) {}

    @Override
    public void onGroupUserInfoUpdate(GroupUserInfo groupUserInfo) {}

    private class UpdateTimeRunnable implements Runnable {
        private TextView timeView;

        public UpdateTimeRunnable(TextView timeView) {
            this.timeView = timeView;
        }

        @Override
        public void run() {
            time++;
            if (time >= 3600) {
                timeView.setText(
                        String.format(
                                "%d:%02d:%02d", time / 3600, (time % 3600) / 60, (time % 60)));
            } else {
                timeView.setText(String.format("%02d:%02d", (time % 3600) / 60, (time % 60)));
            }
            handler.postDelayed(this, 1000);
        }
    }

    void onMinimizeClick(View view) {
        if (checkDrawOverlaysPermission(true)) {
            finish();
        } else {
            Toast.makeText(
                            this,
                            getString(R.string.rc_voip_float_window_not_allowed),
                            Toast.LENGTH_SHORT)
                    .show();
        }
    }

    private boolean checkDrawOverlaysPermission(boolean needOpenPermissionSetting) {
        if (RongCallPermissionUtil.checkFloatWindowPermission(this)) {
            checkingOverlaysPermission = false;
            return true;
        } else {
            if (needOpenPermissionSetting
                    && !checkingOverlaysPermission
                    && !notRemindRequestFloatWindowPermissionAgain) {
                RongCallPermissionUtil.requestFloatWindowNeedPermission(
                        this,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (DialogInterface.BUTTON_POSITIVE == which) {
                                    checkingOverlaysPermission = true;
                                } else if (DialogInterface.BUTTON_NEGATIVE == which) {
                                    checkingOverlaysPermission = false;
                                } else if (DialogInterface.BUTTON_NEUTRAL == which) {
                                    checkingOverlaysPermission = false;
                                    notRemindRequestFloatWindowPermissionAgain = true;
                                }
                            }
                        });
            }
            return false;
        }
    }

    private void createPowerManager() {
        if (powerManager == null) {
            powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            wakeLock =
                    powerManager.newWakeLock(
                            PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.SCREEN_DIM_WAKE_LOCK,
                            TAG);
            wakeLock.setReferenceCounted(false);
            screenLock = powerManager.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG);
            screenLock.setReferenceCounted(false);
        }
    }

    protected void createPickupDetector() {
        if (pickupDetector == null) {
            pickupDetector = new PickupDetector(this);
        }
    }

    @Override
    public void onPickupDetected(boolean isPickingUp) {
        if (screenLock == null) {
            RLog.d(TAG, "No PROXIMITY_SCREEN_OFF_WAKE_LOCK");
            return;
        }
        if (isPickingUp && !screenLock.isHeld()) {
            screenLock.acquire();
        }
        if (!isPickingUp && screenLock.isHeld()) {
            try {
                screenLock.setReferenceCounted(false);
                screenLock.release();
            } catch (Exception e) {

            }
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (!RongCallPermissionUtil.checkPermissions(this, permissions)) {
            RongCallPermissionUtil.showRequestPermissionFailedAlter(
                    this, permissions, grantResults);
        }
    }

    /** outgoing （initView）incoming处注册 */
    public void regisHeadsetPlugReceiver() {
        if (BluetoothUtil.isSupportBluetooth()) {
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction("android.intent.action.HEADSET_PLUG");
            intentFilter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED);
            intentFilter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
            headsetPlugReceiver = new HeadsetPlugReceiver();
            registerReceiver(headsetPlugReceiver, intentFilter);
        }
    }

    /** onHangupBtnClick onDestory 处解绑 */
    public void unRegisterHeadsetplugReceiver() {
        if (headsetPlugReceiver != null) {
            unregisterReceiver(headsetPlugReceiver);
            headsetPlugReceiver = null;
        }
    }

    /**
     * 设置开始音视频参数配置信息<br>
     * 必须在{@link RongCallClient#startCall} 和 {@link RongCallClient#acceptCall(String)}之前设置 <br>
     */
    public void audioVideoConfig() {
        //        RongRTCConfig.Builder configBuilder = new RongRTCConfig.Builder();
        //        configBuilder.setVideoResolution(RCRTCVideoResolution.RESOLUTION_480_640);
        //        configBuilder.setVideoFps(RCRTCVideoFps.Fps_15);
        //        configBuilder.setMaxRate(1000);
        //        configBuilder.setMinRate(350);
        //        /*
        //         * 设置建立 Https 连接时，是否使用自签证书。
        //         * 公有云用户无需调用此方法，私有云用户使用自签证书时调用此方法设置
        //         */
        //        // configBuilder.enableHttpsSelfCertificate(true);
        //        RongCallClient.getInstance().setRTCConfig(configBuilder);

        Builder builder =
                Builder.create()
                        .setVideoResolution(RCRTCVideoResolution.RESOLUTION_480_640)
                        .setVideoFps(RCRTCVideoFps.Fps_15)
                        .setMaxRate(1000)
                        .setMinRate(350);
        RongCallClient instance = RongCallClient.getInstance();
        if (instance != null) {
            instance.setVideoConfig(builder);
        }
    }

    protected void onHeadsetPlugUpdate(HeadsetInfo headsetInfo) {}

    public class HeadsetPlugReceiver extends BroadcastReceiver {

        private final String TAG = HeadsetPlugReceiver.class.getSimpleName();
        // 动态注册了监听有线耳机之后 默认会调用一次有限耳机拔出
        public boolean FIRST_HEADSET_PLUG_RECEIVER = false;

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            HeadsetInfo headsetInfo = null;
            if ("android.intent.action.HEADSET_PLUG".equals(action)) {
                int state = -1;
                if (FIRST_HEADSET_PLUG_RECEIVER) {
                    if (intent.hasExtra("state")) {
                        state = intent.getIntExtra("state", -1);
                    }
                    if (state == 1) {
                        headsetInfo = new HeadsetInfo(true, HeadsetInfo.HeadsetType.WiredHeadset);
                    } else if (state == 0) {
                        headsetInfo = new HeadsetInfo(false, HeadsetInfo.HeadsetType.WiredHeadset);
                    }
                } else {
                    FIRST_HEADSET_PLUG_RECEIVER = true;
                }
            } else if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1);
                switch (state) {
                    case BluetoothProfile.STATE_DISCONNECTED:
                        headsetInfo = new HeadsetInfo(false, HeadsetInfo.HeadsetType.BluetoothA2dp);
                        break;
                    case BluetoothProfile.STATE_CONNECTED:
                        headsetInfo = new HeadsetInfo(true, HeadsetInfo.HeadsetType.BluetoothA2dp);
                        break;
                }
            }
            if (null != headsetInfo) { // onHandFreeButtonClick
                onHeadsetPlugUpdate(headsetInfo);
            } else {
                FinLog.e(TAG, "HeadsetPlugReceiver headsetInfo=null !");
            }
        }
    }
}
