package io.rong.callkit;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallMissedListener;
import io.rong.calllib.RongCallSession;
import io.rong.imkit.manager.InternalModuleManager;
import io.rong.imkit.utilities.PermissionCheckUtil;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;
import java.util.ArrayList;

public class RongCallKit {

    public enum CallMediaType {
        CALL_MEDIA_TYPE_AUDIO,
        CALL_MEDIA_TYPE_VIDEO
    }

    public interface ICallUsersProvider {
        void onGotUserList(ArrayList<String> userIds);
    }

    private static GroupMembersProvider mGroupMembersProvider;

    private static RongCallCustomerHandlerListener customerHandlerListener;

    /**
     * 发起单人通话。
     *
     * @param context 上下文
     * @param targetId 目标会话 id ，单人通话为对方 UserId ,群组通话为 GroupId ，如果实现的是不基于群组的通话，那此参数无意义，传 null 即可
     * @param mediaType 会话媒体类型
     */
    public static void startSingleCall(Context context, String targetId, CallMediaType mediaType) {
        if (checkEnvironment(context, mediaType)) {
            String action;
            if (mediaType.equals(CallMediaType.CALL_MEDIA_TYPE_AUDIO)) {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO;
            } else {
                action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEVIDEO;
            }
            Intent intent = new Intent(action);
            intent.putExtra(
                    "conversationType",
                    Conversation.ConversationType.PRIVATE.getName().toLowerCase());
            intent.putExtra("targetId", targetId);
            intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
            intent.setPackage(context.getPackageName());
            context.startActivity(intent);
        }
    }

    /**
     * 发起多人通话
     *
     * @param context 上下文
     * @param conversationType 会话类型
     * @param targetId 目标会话 id ，单人通话为对方 UserId ,群组通话为 GroupId ，如果实现的是不基于群组的通话，那此参数无意义，传 null 即可
     * @param mediaType 会话媒体类型
     * @param userIds 参与者 id 列表
     */
    public static void startMultiCall(
            Context context,
            Conversation.ConversationType conversationType,
            String targetId,
            CallMediaType mediaType,
            ArrayList<String> userIds) {
        if (checkEnvironment(context, mediaType)) {
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
            intent.setPackage(context.getPackageName());
            intent.putStringArrayListExtra("invitedUsers", userIds);
            context.startActivity(intent);
        }
    }

    /**
     * 开始多人通话。 返回当前会话用户列表提供者对象，用户拿到该对象后，异步从服务器取出当前会话用户列表后， 调用提供者中的 onGotUserList 方法，填充
     * ArrayList<String> userIds 后，就会自动发起多人通话。
     *
     * @param context 上下文
     * @param conversationType 会话类型
     * @param targetId 目标会话 id ，单人通话为对方 UserId ,群组通话为 GroupId ，如果实现的是不基于群组的通话，那此参数无意义，传 null 即可
     * @param mediaType 通话的媒体类型：CALL_MEDIA_TYPE_AUDIO， CALL_MEDIA_TYPE_VIDEO
     * @return 返回当前会话用户列表提供者对象
     */
    public static ICallUsersProvider startMultiCall(
            final Context context,
            final Conversation.ConversationType conversationType,
            final String targetId,
            final CallMediaType mediaType) {
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
                intent.setPackage(context.getPackageName());
                intent.putStringArrayListExtra("invitedUsers", userIds);
                context.startActivity(intent);
            }
        };
    }

    /**
     * 发起的多人通话，不依赖群、讨论组等
     *
     * @param context
     * @param userIds 邀请的成员
     * @param oberverIds 邀请的以观察者身份加入房间的成员
     * @param mediaType
     */
    public static void startMultiCall(
            final Context context,
            ArrayList<String> userIds,
            ArrayList<String> oberverIds,
            final CallMediaType mediaType) {
        String action;
        if (mediaType.equals(CallMediaType.CALL_MEDIA_TYPE_AUDIO)) {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIAUDIO;
        } else {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIVIDEO;
        }
        Intent intent = new Intent(action);
        userIds.add(RongIMClient.getInstance().getCurrentUserId());
        intent.putExtra(
                "conversationType", Conversation.ConversationType.NONE.getName().toLowerCase());
        intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
        intent.putStringArrayListExtra("invitedUsers", userIds);
        intent.putStringArrayListExtra("observerUsers", oberverIds);
        intent.setPackage(context.getPackageName());
        context.startActivity(intent);
    }

    /**
     * 发起的多人通话，不依赖群、讨论组等
     *
     * <p><a href="http://support.rongcloud.cn/kb/Njcy">如何实现不基于于群组的voip</a>
     *
     * @param context
     * @param mediaType
     * @return
     */
    public static void startMultiCall(
            final Context context, ArrayList<String> userIds, final CallMediaType mediaType) {
        String action;
        if (mediaType.equals(CallMediaType.CALL_MEDIA_TYPE_AUDIO)) {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIAUDIO;
        } else {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIVIDEO;
        }
        Intent intent = new Intent(action);
        userIds.add(RongIMClient.getInstance().getCurrentUserId());
        intent.putExtra(
                "conversationType", Conversation.ConversationType.NONE.getName().toLowerCase());
        intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
        intent.putStringArrayListExtra("invitedUsers", userIds);
        intent.setPackage(context.getPackageName());
        context.startActivity(intent);
    }

    /**
     * 检查应用音视频授权信息 检查网络连接状态 检查是否在通话中
     *
     * @param context 启动的 activity
     * @param mediaType 启动音视频的媒体类型
     * @return 是否允许启动通话界面
     */
    private static boolean checkEnvironment(Context context, CallMediaType mediaType) {
        if (context instanceof Activity) {
            String[] permissions;
            if (mediaType.equals(CallMediaType.CALL_MEDIA_TYPE_AUDIO)) {
                permissions = new String[] {Manifest.permission.RECORD_AUDIO};
            } else {
                permissions =
                        new String[] {Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO};
            }
            if (!PermissionCheckUtil.requestPermissions((Activity) context, permissions)) {
                return false;
            }
        }

        if (isInVoipCall(context)) {
            return false;
        }
        if (!RongIMClient.getInstance()
                .getCurrentConnectionStatus()
                .equals(RongIMClient.ConnectionStatusListener.ConnectionStatus.CONNECTED)) {
            Toast.makeText(
                            context,
                            context.getResources().getString(R.string.rc_voip_call_network_error),
                            Toast.LENGTH_SHORT)
                    .show();
            return false;
        }
        return true;
    }

    /**
     * 是否在VOIP通话中
     *
     * @param context
     * @return 是否在VOIP通话中
     */
    public static boolean isInVoipCall(Context context) {
        RongCallSession callSession = RongCallClient.getInstance().getCallSession();
        if (callSession != null && callSession.getStartTime() > 0) {
            Toast.makeText(
                            context,
                            callSession.getMediaType() == RongCallCommon.CallMediaType.AUDIO
                                    ? context.getResources()
                                            .getString(R.string.rc_voip_call_audio_start_fail)
                                    : context.getResources()
                                            .getString(R.string.rc_voip_call_video_start_fail),
                            Toast.LENGTH_SHORT)
                    .show();
            return true;
        }
        return false;
    }

    /** 群组成员提供者。 CallKit 本身不保存群组成员，如果在聊天中需要使用群组成员，CallKit 将调用此 Provider 获取群组成员。 */
    public interface GroupMembersProvider {
        /**
         * 获取群组成员列表，用户根据groupId返回对应的群组成员列表。
         *
         * @param groupId 群组id
         * @param result getMemberList可以同步返回，也可以异步返回。 同步返回的情况下，直接返回成员列表。 异步返回的情况下，需要在异步返回的时候调用{@link
         *     OnGroupMembersResult#onGotMemberList(ArrayList)} 来通知CallKit刷新列表。
         * @return 同步返回的时候返回列表，异步返回直接返回null。
         */
        ArrayList<String> getMemberList(String groupId, OnGroupMembersResult result);
    }

    /** 群组成员提供者的异步回调接口。 */
    public interface OnGroupMembersResult {
        /**
         * 群组成员提供者的异步回调接口。
         *
         * @param members 成员列表。
         */
        void onGotMemberList(ArrayList<String> members);
    }

    /**
     * 设置群组成员的提供者。
     *
     * <p>设置后，当 {@link CallSelectMemberActivity} 界面展示群组成员时，会回调 {@link
     * GroupMembersProvider#getMemberList(String, OnGroupMembersResult)}， 使用者只需要根据对应的 groupId
     * 提供对应的群组成员。 如果需要异步从服务器获取群组成员，使用者可以在此方法中发起异步请求，然后返回 null 信息。 在异步请求结果返回后，根据返回的结果调用 {@link
     * OnGroupMembersResult#onGotMemberList(ArrayList)} 刷新信息。
     *
     * @param groupMembersProvider 群组成员提供者。
     */
    public static void setGroupMemberProvider(GroupMembersProvider groupMembersProvider) {
        mGroupMembersProvider = groupMembersProvider;
    }

    /**
     * 获取群组成员提供者。
     *
     * @return 群组成员提供者。
     */
    public static GroupMembersProvider getGroupMemberProvider() {
        return mGroupMembersProvider;
    }

    /**
     * 设置通话时用户自定义操作监听。
     *
     * <p>CallKit中的Activity是通过action隐式启动，如果用户想继承现有的Activity自定义操作，子类Activity在
     * AndroidManifest.xml声明后启动该Activity时会弹出提示框让用户选择，这个问题解决方式开发者可以直接把
     * callKit/AndroidManifest.xml中对应的Activity声明去掉，此Listener提供了另一种实现方案,
     * RongCallCustomerHandlerListener中并没有定义很多方法，开发者如果需要，可以新增自己的方法
     */
    public static void setCustomerHandlerListener(
            RongCallCustomerHandlerListener callCustomerHandlerListener) {
        customerHandlerListener = callCustomerHandlerListener;
    }

    /** 通话过程中用户自定义操作。 */
    public static RongCallCustomerHandlerListener getCustomerHandlerListener() {
        return customerHandlerListener;
    }

    public static void setRongCallMissedListener(
            final RongCallMissedListener rongCallMissedListener) {
        RongCallModule.setMissedCallListener(rongCallMissedListener);
    }

    /**
     * 防止 voip 通话页面被会话列表、会话页面或者开发者 app 层页面覆盖。 使用 maven 接入 callkit 的开发者在 app 层主页面的 onCreate 调用此方法即可。
     * 针对导入 callkit 源码的开发者，不使用会话列表和会话页面我们建议在 {@link RongCallModule#onCreate(Context)}方法中设置
     * mViewLoaded 为 true 即可。
     */
    public static void onViewCreated() {
        InternalModuleManager.getInstance().onLoaded();
    }

    /**
     * 忽略 voip 来电，不弹出来电界面，直接挂断。
     *
     * @param ignore true 时忽略来电，false 恢复默认值接收来电，弹出来电界面。 此接口针对音视频会议过程中不能被 voip 打断等的细分场景
     */
    public static void ignoreIncomingCall(boolean ignore) {
        RongCallModule.ignoreIncomingCall(ignore);
    }
}
