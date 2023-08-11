package io.rong.callkit.util;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetFileDescriptor;
import android.media.AudioAttributes;
import android.media.AudioAttributes.Builder;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Vibrator;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.DrawableRes;
import androidx.annotation.RequiresApi;
import io.rong.callkit.R;
import io.rong.callkit.RongIncomingCallService;
import io.rong.common.RLog;
import io.rong.imkit.notification.NotificationUtil;
import io.rong.push.notification.RongNotificationHelper;
import java.io.IOException;

/** @author gusd @Date 2021/09/14 */
public class CallRingingUtil {
    private static final String TAG = "CallRingingUtil";
    private volatile boolean isRinging = false;
    private volatile RingingMode mCurrentRingingMode = null;

    private MediaPlayer mMediaPlayer;
    private Vibrator mVibrator;
    private Context applicationContext;
    private volatile boolean stopServiceAndRingingTag = false;
    private volatile boolean isFirstReceivedBroadcast = true;

    private static final String DEFAULT_CHANNEL_NAME = "VOIP";
    private static final String DEFAULT_CHANNEL_ID =
            RongNotificationHelper.getDefaultVoipChannelId();
    public static final int DEFAULT_ANSWER_ICON = R.drawable.rc_voip_notification_answer;
    public static final int DEFAULT_HANGUP_ICON = R.drawable.rc_voip_notification_hangup;

    @DrawableRes private int answerIcon = DEFAULT_ANSWER_ICON;
    @DrawableRes private int hangupIcon = DEFAULT_HANGUP_ICON;

    private CallRingingUtil() {}

    private static class InstanceHolder {
        static final CallRingingUtil instance = new CallRingingUtil();
    }

    public static CallRingingUtil getInstance() {
        return InstanceHolder.instance;
    }

    @RequiresApi(api = Build.VERSION_CODES.O)
    public void startRingingService(Context context, Bundle bundle) {
        if (!isRingingServiceRunning(context)) {
            Intent intent = new Intent();
            intent.putExtras(bundle);
            intent.setPackage(context.getPackageName());
            // KNOTE: 2021/9/29 前台服务启动限制
            try {
                RongIncomingCallService.getInstance().startRing(context, intent);
            } catch (Exception e) {
                e.printStackTrace();
                RLog.e(TAG, e.getMessage());
            }
        }
    }

    public void stopService(Context context) {
        RLog.d(TAG, "stopService: ");
        if (isRingingServiceRunning(context)) {
            RongIncomingCallService.getInstance().stopRinging(context);
        }
    }

    public void stopServiceButContinueRinging(Context context) {
        RLog.d(TAG, "stopServiceButContinueRinging: ");
        if (!isRingingServiceRunning(context)) {
            stopServiceAndRingingTag = false;
            return;
        }
        stopServiceAndRingingTag = true;
        stopService(context);
    }

    public boolean isRingingServiceRunning(Context context) {
        return RongIncomingCallService.getInstance().isRinging();
    }

    public void startRinging(Context context, RingingMode mode) {
        RLog.d(TAG, "startRinging: ");
        if (isRinging) {
            return;
        }

        if (context == null) {
            return;
        }
        applicationContext = context.getApplicationContext();

        // 注册 BroadcastReceiver 监听情景模式的切换
        IntentFilter filter = new IntentFilter();
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        isFirstReceivedBroadcast = true;
        applicationContext.registerReceiver(mRingModeReceiver, filter);

        if (mode == RingingMode.Incoming || mode == RingingMode.Incoming_Custom) {
            int ringerMode = NotificationUtil.getInstance().getRingerMode(context);
            if (ringerMode != AudioManager.RINGER_MODE_SILENT) {
                if (ringerMode == AudioManager.RINGER_MODE_VIBRATE) {
                    startVibrator(context);
                } else {
                    if (isVibrateWhenRinging(context)) {
                        startVibrator(context);
                    }
                    callRinging(context, mode);
                }
            }
        } else {
            callRinging(context, mode);
        }
        mCurrentRingingMode = mode;
        isRinging = true;
    }

    protected void startVibrator(Context context) {
        Log.d(TAG, "startVibrator: ");
        if (mVibrator == null) {
            mVibrator =
                    (Vibrator)
                            context.getApplicationContext()
                                    .getSystemService(Context.VIBRATOR_SERVICE);
        } else {
            mVibrator.cancel();
        }

        long[] pattern = {500, 1000};
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            AudioAttributes build =
                    new Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                            .build();
            mVibrator.vibrate(pattern, 0, build);
        } else {
            mVibrator.vibrate(pattern, 0);
        }
    }

    /** 判断系统是否设置了 响铃时振动 */
    private boolean isVibrateWhenRinging(Context context) {
        ContentResolver resolver = context.getApplicationContext().getContentResolver();
        if (Build.MANUFACTURER.equals("Xiaomi")) {
            return Settings.System.getInt(resolver, "vibrate_in_normal", 0) == 1;
        } else if (Build.MANUFACTURER.equals("smartisan")) {
            return Settings.Global.getInt(resolver, "telephony_vibration_enabled", 0) == 1;
        } else {
            return Settings.System.getInt(resolver, "vibrate_when_ringing", 0) == 1;
        }
    }

    private void callRinging(Context context, RingingMode mode) {
        initMp();
        try {
            if (mode == RingingMode.Incoming) {
                Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                mMediaPlayer.setDataSource(context.getApplicationContext(), uri);
            } else if (mode == RingingMode.Incoming_Custom || mode == RingingMode.Outgoing) {
                int rawResId =
                        mode == RingingMode.Outgoing
                                ? R.raw.voip_outgoing_ring
                                : R.raw.voip_incoming_ring;
                AssetFileDescriptor assetFileDescriptor =
                        context.getResources().openRawResourceFd(rawResId);
                mMediaPlayer.setDataSource(
                        assetFileDescriptor.getFileDescriptor(),
                        assetFileDescriptor.getStartOffset(),
                        assetFileDescriptor.getLength());
                assetFileDescriptor.close();
            }

            // 设置 MediaPlayer 播放的声音用途
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                AudioAttributes attributes =
                        new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                                .build();
                mMediaPlayer.setAudioAttributes(attributes);
            } else {
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_VOICE_CALL);
            }
            mMediaPlayer.prepareAsync();
            final AudioManager am =
                    (AudioManager)
                            context.getApplicationContext().getSystemService(Context.AUDIO_SERVICE);
            if (am != null) {
                am.setSpeakerphoneOn(
                        mode == RingingMode.Incoming || mode == RingingMode.Incoming_Custom);
                // 设置此值可在拨打时控制响铃音量
                am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                // 设置拨打时响铃音量默认值
                am.setStreamVolume(
                        AudioManager.STREAM_VOICE_CALL, 5, AudioManager.STREAM_VOICE_CALL);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } catch (Exception e1) {
            RLog.i(TAG, "---onOutgoingCallRinging Error---" + e1.getMessage());
        }
    }

    private void initMp() {
        if (mMediaPlayer == null) {
            mMediaPlayer = new MediaPlayer();
            mMediaPlayer.setOnPreparedListener(
                    new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            try {
                                if (mp != null) {
                                    mp.setLooping(true);
                                    mp.start();
                                }
                            } catch (IllegalStateException e) {
                                e.printStackTrace();
                                RLog.i(TAG, "setOnPreparedListener Error!");
                            }
                        }
                    });
        }
    }

    public void stopRinging() {
        try {
            RLog.d(TAG, "stopRinging: ");
            if (stopServiceAndRingingTag) {
                stopServiceAndRingingTag = false;
                return;
            }
            if (mMediaPlayer != null && mMediaPlayer.isPlaying()) {
                mMediaPlayer.stop();
            }
            if (mMediaPlayer != null) {
                mMediaPlayer.reset();
            }
            if (mVibrator != null) {
                mVibrator.cancel();
            }
            if (applicationContext != null) {
                try {
                    applicationContext.unregisterReceiver(mRingModeReceiver);
                } catch (Exception e) {
                }
                final AudioManager am =
                        (AudioManager) applicationContext.getSystemService(Context.AUDIO_SERVICE);
                if (am != null) {
                    am.setMode(AudioManager.MODE_IN_COMMUNICATION);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            RLog.i(TAG, "mMediaPlayer stopRing error=" + e.getMessage());
        } finally {
            isRinging = false;
            mCurrentRingingMode = null;
        }
    }

    protected final BroadcastReceiver mRingModeReceiver =
            new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    RLog.d(
                            TAG,
                            "onReceive : " + "context = " + context + "," + "intent = " + intent);
                    // 此类广播为 sticky 类型的，首次注册广播便会收到，因此第一次收到的广播不作处理
                    if (isFirstReceivedBroadcast) {
                        isFirstReceivedBroadcast = false;
                        return;
                    }
                    if (!isRinging) {
                        return;
                    }
                    // 根据 isIncoming 判断只有在接听界面时做铃声和振动的切换，拨打界面不作处理
                    if ((mCurrentRingingMode == RingingMode.Incoming
                                    || mCurrentRingingMode == RingingMode.Incoming_Custom)
                            && AudioManager.RINGER_MODE_CHANGED_ACTION.equals(intent.getAction())
                            && !CallKitUtils.callConnected) {
                        AudioManager am =
                                (AudioManager)
                                        context.getApplicationContext()
                                                .getSystemService(Context.AUDIO_SERVICE);
                        final int ringMode = am.getRingerMode();
                        RLog.i(TAG, "Ring mode Receiver mode=" + ringMode);
                        switch (ringMode) {
                            case AudioManager.RINGER_MODE_NORMAL:
                                stopRinging();
                                callRinging(context.getApplicationContext(), RingingMode.Incoming);
                                break;
                            case AudioManager.RINGER_MODE_SILENT:
                                stopRinging();
                                break;
                            case AudioManager.RINGER_MODE_VIBRATE:
                                stopRinging();
                                startVibrator(context);
                                break;
                            default:
                        }
                    }
                }
            };

    /**
     * 创建通知通道
     *
     * @param context
     */
    public void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(
                            getNotificationChannelId(),
                            getNotificationChannelName(context),
                            NotificationManager.IMPORTANCE_HIGH);
            channel.setSound(null, null);
            context.getSystemService(NotificationManager.class).createNotificationChannel(channel);
        }
    }

    public String getNotificationChannelId() {
        return DEFAULT_CHANNEL_ID;
    }

    public String getNotificationChannelName(Context context) {
        int id =
                context.getResources()
                        .getIdentifier(
                                RongNotificationHelper.getDefaultVoipChannelName(),
                                "string",
                                context.getPackageName());
        String channelName = id == 0 ? null : context.getResources().getString(id);
        return TextUtils.isEmpty(channelName) ? DEFAULT_CHANNEL_NAME : channelName;
    }

    public void setNotificationHangupIcon(@DrawableRes int hangupIcon) {
        this.hangupIcon = hangupIcon;
    }

    @DrawableRes
    public int getNotificationHangupIcon() {
        return this.hangupIcon;
    }

    public void setNotificationAnswerIcon(@DrawableRes int answerIcon) {
        this.answerIcon = answerIcon;
    }

    @DrawableRes
    public int getNotificationAnswerIcon() {
        return this.answerIcon;
    }

    /**
     * 创建通道并检查是否有悬浮通知权限
     *
     * @param context
     */
    public boolean createChannelAndCheckFullScreenPermission(Context context) {
        createNotificationChannel(context);
        return checkFullScreenPermission(context, getNotificationChannelId());
    }

    /**
     * 检查是否有悬浮通知权限
     *
     * @param context
     * @param channelId
     * @return
     */
    public boolean checkFullScreenPermission(Context context, String channelId) {
        NotificationManager mNotificationManager =
                (NotificationManager)
                        context.getApplicationContext()
                                .getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) { // Android 8.0及以上
            NotificationChannel channel =
                    mNotificationManager.getNotificationChannel(channelId); // CHANNEL_ID是自己定义的渠道ID
            if (channel.getImportance() == NotificationManager.IMPORTANCE_DEFAULT) { // 未开启
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 跳转到通知设置界面
     *
     * @param context
     */
    public void gotoChannelSettingPage(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            intent.setPackage(context.getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, getNotificationChannelId());
            context.startActivity(intent);
        }
    }
}
