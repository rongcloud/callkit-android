package io.rong.imkit;

import android.content.Context;
import android.content.Intent;

import java.util.ArrayList;

import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;

public class RongCallKit {

    public enum CallMediaType {
        CALL_MEDIA_TYPE_AUDIO, CALL_MEDIA_TYPE_VIDEO
    }

    public interface ICallUsersProvider {
        void onGotUserList(ArrayList<String> userIds);
    }

    /**
     * 发起单人通话。
     *
     * @param context   上下文
     * @param targetId  会话 id
     * @param mediaType 会话媒体类型
     */
    public static void startSingleCall(Context context, String targetId, CallMediaType mediaType) {
        String action;
        if (mediaType.equals(CallMediaType.CALL_MEDIA_TYPE_AUDIO)) {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO;
        } else {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEVIDEO;
        }
        Intent intent = new Intent(action);
        intent.putExtra("conversationType", Conversation.ConversationType.PRIVATE.getName().toLowerCase());
        intent.putExtra("targetId", targetId);
        intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
        context.startActivity(intent);
    }

    /**
     * 发起多人通话
     *
     * @param context          上下文
     * @param conversationType 会话类型
     * @param targetId         会话 id
     * @param mediaType        会话媒体类型
     * @param userIds          参与者 id 列表
     */
    public static void startMultiCall(Context context, Conversation.ConversationType conversationType, String targetId, CallMediaType mediaType, ArrayList<String> userIds) {
        String action;
        if (mediaType.equals(CallMediaType.CALL_MEDIA_TYPE_AUDIO)) {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIAUDIO;
        } else {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIVIDEO;
        }

        Intent intent = new Intent(action);
        userIds.add(RongIMClient.getInstance().getCurrentUserId());
        intent.putExtra("conversationType", conversationType.getName().toLowerCase());
        intent.putExtra("targetId", targetId);
        intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
        intent.putStringArrayListExtra("invitedUsers", userIds);
        context.startActivity(intent);
    }


    /**
     * 开始多人通话。
     * 返回当前会话用户列表提供者对象，用户拿到该对象后，异步从服务器取出当前会话用户列表后，
     * 调用提供者中的 onGotUserList 方法，填充 ArrayList<String> userIds 后，就会自动发起多人通话。
     *
     * @param context          上下文
     * @param conversationType 会话类型
     * @param targetId         会话 id
     * @param mediaType        通话的媒体类型：CALL_MEDIA_TYPE_AUDIO， CALL_MEDIA_TYPE_VIDEO
     * @return 返回当前会话用户列表提供者对象
     */
    public static ICallUsersProvider startMultiCall(final Context context, final Conversation.ConversationType conversationType, final String targetId, final CallMediaType mediaType) {
        return new ICallUsersProvider() {
            @Override
            public void onGotUserList(ArrayList<String> userIds) {
                String action;
                if (mediaType.equals(CallMediaType.CALL_MEDIA_TYPE_AUDIO)) {
                    action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIAUDIO;
                } else {
                    action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIVIDEO;
                }
                Intent intent = new Intent(action);
                userIds.add(RongIMClient.getInstance().getCurrentUserId());
                intent.putExtra("conversationType", conversationType.getName().toLowerCase());
                intent.putExtra("targetId", targetId);
                intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
                intent.putStringArrayListExtra("invitedUsers", userIds);
                context.startActivity(intent);
            }
        };
    }
}
