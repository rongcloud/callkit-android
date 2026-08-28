package io.rong.callkit.util;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import io.rong.callkit.R;
import io.rong.imkit.RongIM;
import io.rong.imkit.userinfo.RongUserInfoManager;
import io.rong.imlib.model.AndroidConfig;
import io.rong.imlib.model.IOSConfig;
import io.rong.imlib.model.MessagePushConfig;
import io.rong.imlib.model.UserInfo;
import java.lang.reflect.Type;
import java.util.Map;

public class DefaultPushConfig {

    // CallKit 音视频通话小米模板配置常量
    private static final String PUSH_CONFIG_PREFERENCES = "push_config";
    private static final String CALL_MI_TEMPLATE_ENABLED = "callMiTemplateEnabled";
    private static final String CALL_MI_TEMPLATE_ID = "callMiTemplateId";
    private static final String CALL_INVITE_MI_TEMPLATE_PARAM = "callInviteMiTemplateParam";
    private static final String CALL_HANGUP_MI_TEMPLATE_PARAM = "callHangupMiTemplateParam";
    private static final boolean DEFAULT_CALL_MI_TEMPLATE_ENABLED = false;
    private static final String DEFAULT_CALL_MI_TEMPLATE_ID = "";

    /**
     * 获取邀请的 push config
     *
     * @param isPrivate 是否单人呼叫
     * @param groupName 群组呼叫的时候才需要填写：
     */
    public static MessagePushConfig getInviteConfig(
            Context context, boolean isAudio, boolean isPrivate, String groupName) {
        UserInfo userInfo =
                RongUserInfoManager.getInstance()
                        .getUserInfo(RongIM.getInstance().getCurrentUserId());
        String userName = userInfo == null ? "" : userInfo.getName();
        // 自定义音视频通话推送内容测试代码，融云SealTalk测试时配置写入SharedPreferences，
        // 开发者根据实际需求定义发起通话和挂断时的push配置，在 startCall 前设置即可
        SharedPreferences sharedPreferences =
                context.getSharedPreferences("push_config", MODE_PRIVATE);
        String id = sharedPreferences.getString("id", "");
        String title = sharedPreferences.getString("title", "");
        String pushTile = TextUtils.isEmpty(title) ? (isPrivate ? userName : groupName) : title;
        String content = sharedPreferences.getString("content", "");
        if (TextUtils.isEmpty(content)) {
            content =
                    TextUtils.isEmpty(userName)
                            ? context.getResources()
                                    .getString(
                                            isAudio
                                                    ? R.string
                                                            .rc_voip_notificatio_audio_call_inviting_general
                                                    : R.string
                                                            .rc_voip_notificatio_video_call_inviting_general)
                            : userName
                                    + " "
                                    + context.getResources()
                                            .getString(
                                                    isAudio
                                                            ? R.string
                                                                    .rc_voip_notificatio_audio_call_inviting
                                                            : R.string
                                                                    .rc_voip_notificatio_video_call_inviting);
        }
        String invitePushContent = content;
        String data = sharedPreferences.getString("data", "");
        String hw = sharedPreferences.getString("hw", "");
        String mi = sharedPreferences.getString("mi", "");
        String oppo = sharedPreferences.getString("oppo", "");
        String threadId = sharedPreferences.getString("threadId", "");
        String apnsId = sharedPreferences.getString("apnsId", "");
        boolean vivo = sharedPreferences.getBoolean("vivo", false);
        boolean forceDetail = sharedPreferences.getBoolean("forceDetail", false);
        MessagePushConfig invitePushConfig =
                getMessagePushConfig(
                        context,
                        true,
                        id,
                        pushTile,
                        invitePushContent,
                        data,
                        hw,
                        mi,
                        oppo,
                        threadId,
                        apnsId,
                        forceDetail);

        return invitePushConfig;
    }

    /**
     * 获取挂断的 push config
     *
     * @param isPrivate 是否单人呼叫
     * @param groupName 群组呼叫的时候才需要填写：
     */
    public static MessagePushConfig getHangupConfig(
            Context context, boolean isPrivate, String groupName) {
        UserInfo userInfo = RongUserInfoManager.getInstance().getCurrentUserInfo();
        String userName = userInfo == null ? "" : userInfo.getName();

        // 自定义音视频通话推送内容测试代码，融云SealTalk测试时配置写入SharedPreferences，
        // 开发者根据实际需求定义发起通话和挂断时的push配置，在 startCall 前设置即可
        SharedPreferences sharedPreferences =
                context.getSharedPreferences("push_config", MODE_PRIVATE);
        String id = sharedPreferences.getString("id", "");
        String title = sharedPreferences.getString("title", "");
        String pushTile = TextUtils.isEmpty(title) ? (isPrivate ? userName : groupName) : title;
        String content = sharedPreferences.getString("content", "");
        String hangupPushContent =
                TextUtils.isEmpty(content)
                        ? context.getResources().getString(R.string.rc_voip_call_terminalted_notify)
                        : content;
        String data = sharedPreferences.getString("data", "");
        String hw = sharedPreferences.getString("hw", "");
        String mi = sharedPreferences.getString("mi", "");
        String oppo = sharedPreferences.getString("oppo", "");
        String threadId = sharedPreferences.getString("threadId", "");
        String apnsId = sharedPreferences.getString("apnsId", "");
        boolean vivo = sharedPreferences.getBoolean("vivo", false);
        boolean forceDetail = sharedPreferences.getBoolean("forceDetail", false);
        MessagePushConfig hangupPushConfig =
                getMessagePushConfig(
                        context,
                        false,
                        id,
                        pushTile,
                        hangupPushContent,
                        data,
                        hw,
                        mi,
                        oppo,
                        threadId,
                        apnsId,
                        forceDetail);
        return hangupPushConfig;
    }

    private static MessagePushConfig getMessagePushConfig(
            Context context,
            boolean isInvite,
            String id,
            String pushTile,
            String pushContent,
            String data,
            String hw,
            String mi,
            String oppo,
            String threadId,
            String apnsId,
            boolean forceDetail) {
        SharedPreferences sharedPreferences =
                context.getSharedPreferences(PUSH_CONFIG_PREFERENCES, MODE_PRIVATE);
        boolean callMiTemplateEnabled =
                sharedPreferences.getBoolean(
                        CALL_MI_TEMPLATE_ENABLED, DEFAULT_CALL_MI_TEMPLATE_ENABLED);
        String callMiTemplateId =
                sharedPreferences.getString(CALL_MI_TEMPLATE_ID, DEFAULT_CALL_MI_TEMPLATE_ID);
        String callMiTemplateParamKey =
                isInvite ? CALL_INVITE_MI_TEMPLATE_PARAM : CALL_HANGUP_MI_TEMPLATE_PARAM;
        String defaultCallMiTemplateParam =
                "{\"keywords1\":\"" + pushTile + "\",\"keywords2\":\"" + pushContent + "\"}";
        String callMiTemplateParam =
                sharedPreferences.getString(callMiTemplateParamKey, defaultCallMiTemplateParam);
        if (TextUtils.isEmpty(callMiTemplateParam)) {
            callMiTemplateParam = defaultCallMiTemplateParam;
        }

        AndroidConfig.Builder androidConfigBuilder =
                new AndroidConfig.Builder()
                        .setNotificationId(id)
                        .setChannelIdHW(hw)
                        .setChannelIdMi(mi)
                        .setChannelIdOPPO(oppo)
                        .setCategoryHW("VOIP")
                        .setCategoryVivo("IM");

        applyCallMiTemplateConfig(
                androidConfigBuilder, callMiTemplateEnabled, callMiTemplateId, callMiTemplateParam);

        return new MessagePushConfig.Builder()
                .setPushTitle(pushTile)
                .setPushContent(pushContent)
                .setPushData(data)
                .setForceShowDetailContent(forceDetail)
                .setAndroidConfig(androidConfigBuilder.build())
                .setIOSConfig(new IOSConfig(threadId, apnsId))
                .build();
    }

    private static void applyCallMiTemplateConfig(
            AndroidConfig.Builder androidConfigBuilder,
            boolean enabled,
            String templateId,
            String templateParamJson) {
        if (!enabled || TextUtils.isEmpty(templateId)) {
            return;
        }
        androidConfigBuilder.setTemplateIdMi(templateId);
        Map<String, String> templateParameters = parseMiTemplateParameters(templateParamJson);
        if (templateParameters != null && !templateParameters.isEmpty()) {
            androidConfigBuilder.setTemplateParamMi(templateParameters);
        }
    }

    private static Map<String, String> parseMiTemplateParameters(String templateParamJson) {
        try {
            Type mapType = new TypeToken<Map<String, String>>() {}.getType();
            return new Gson().fromJson(templateParamJson, mapType);
        } catch (JsonSyntaxException e) {
            return null;
        }
    }
}
