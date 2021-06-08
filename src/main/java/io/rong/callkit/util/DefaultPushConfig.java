package io.rong.callkit.util;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import io.rong.callkit.R;
import io.rong.imkit.RongIM;
import io.rong.imkit.userinfo.RongUserInfoManager;
import io.rong.imkit.userinfo.UserDataProvider.UserInfoProvider;
import io.rong.imlib.model.AndroidConfig;
import io.rong.imlib.model.IOSConfig;
import io.rong.imlib.model.MessagePushConfig;
import io.rong.imlib.model.UserInfo;

public class DefaultPushConfig {

    /**
     * 获取邀请的 push config
     *
     * @param isPrivate 是否单人呼叫
     * @param groupName 群组呼叫的时候才需要填写：
     */
    public static MessagePushConfig getInviteConfig(Context context, boolean isAudio, boolean isPrivate, String groupName) {
        UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(RongIM.getInstance().getCurrentUserId());
        String userName = userInfo == null ? "" : userInfo.getName();

        //自定义音视频通话推送内容测试代码，融云SealTalk测试时配置写入SharedPreferences，
        //开发者根据实际需求定义发起通话和挂断时的push配置，在 startCall 前设置即可
        SharedPreferences sharedPreferences = context.getSharedPreferences("push_config", MODE_PRIVATE);
        String id = sharedPreferences.getString("id", "");
        String title = sharedPreferences.getString("title", "");
        String pushTile = TextUtils.isEmpty(title) ? (isPrivate ? userName : groupName) : title;
        String content = sharedPreferences.getString("content", "");
        String invitePushContent = TextUtils.isEmpty(content) ? (userName + " " + context.getResources().getString(
            isAudio ? R.string.rc_voip_notificatio_audio_call_inviting : R.string.rc_voip_notificatio_video_call_inviting)) : content;
        String data = sharedPreferences.getString("data", "");
        String hw = sharedPreferences.getString("hw", "");
        String mi = sharedPreferences.getString("mi", "");
        String oppo = sharedPreferences.getString("oppo", "");
        String threadId = sharedPreferences.getString("threadId", "");
        String apnsId = sharedPreferences.getString("apnsId", "");
        boolean vivo = sharedPreferences.getBoolean("vivo", false);
        boolean forceDetail = sharedPreferences.getBoolean("forceDetail", false);
        MessagePushConfig invitePushConfig = getMessagePushConfig(id, pushTile, invitePushContent, data, hw, mi, oppo, threadId, apnsId, forceDetail);

        return invitePushConfig;
    }

    /**
     * 获取挂断的 push config
     *
     * @param isPrivate 是否单人呼叫
     * @param groupName 群组呼叫的时候才需要填写：
     */
    public static MessagePushConfig getHangupConfig(Context context, boolean isPrivate, String groupName) {
        UserInfo userInfo = RongUserInfoManager.getInstance().getCurrentUserInfo();
        String userName = userInfo == null ? "" : userInfo.getName();

        //自定义音视频通话推送内容测试代码，融云SealTalk测试时配置写入SharedPreferences，
        //开发者根据实际需求定义发起通话和挂断时的push配置，在 startCall 前设置即可
        SharedPreferences sharedPreferences = context.getSharedPreferences("push_config", MODE_PRIVATE);
        String id = sharedPreferences.getString("id", "");
        String title = sharedPreferences.getString("title", "");
        String pushTile = TextUtils.isEmpty(title) ? (isPrivate ? userName : groupName) : title;
        String content = sharedPreferences.getString("content", "");
        String hangupPushContent = TextUtils.isEmpty(content) ? context.getResources().getString(R.string.rc_voip_call_terminalted_notify) : content;
        String data = sharedPreferences.getString("data", "");
        String hw = sharedPreferences.getString("hw", "");
        String mi = sharedPreferences.getString("mi", "");
        String oppo = sharedPreferences.getString("oppo", "");
        String threadId = sharedPreferences.getString("threadId", "");
        String apnsId = sharedPreferences.getString("apnsId", "");
        boolean vivo = sharedPreferences.getBoolean("vivo", false);
        boolean forceDetail = sharedPreferences.getBoolean("forceDetail", false);
        MessagePushConfig hangupPushConfig = getMessagePushConfig(id, pushTile, hangupPushContent, data, hw, mi, oppo, threadId, apnsId, forceDetail);
        return hangupPushConfig;
    }

    private static MessagePushConfig getMessagePushConfig(String id, String pushTile, String invitePushContent, String data, String hw, String mi, String oppo, String threadId, String apnsId, boolean forceDetail) {
        return new MessagePushConfig.Builder()  //
            .setPushTitle(pushTile) //
            .setPushContent(invitePushContent) //
            .setPushData(data) //
            .setForceShowDetailContent(forceDetail) //
            .setAndroidConfig(new AndroidConfig.Builder() //
                .setNotificationId(id) //
                .setChannelIdHW(hw) //
                .setChannelIdMi(mi) //
                .setChannelIdOPPO(oppo) //
                .build()) //
            .setIOSConfig(new IOSConfig(threadId, apnsId)).build(); //
    }
}
