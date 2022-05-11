package io.rong.callkit;

import static io.rong.callkit.BaseCallActivity.REQUEST_CODE_ADD_MEMBER;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import io.rong.callkit.util.CallKitUtils;
import io.rong.callkit.util.RongCallPermissionUtil;
import io.rong.callkit.util.permission.PermissionType;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
import io.rong.common.RLog;
import io.rong.imkit.conversation.extension.RongExtension;
import io.rong.imkit.conversation.extension.component.plugin.IPluginModule;
import io.rong.imkit.conversation.extension.component.plugin.IPluginRequestPermissionResultCallback;
import io.rong.imlib.IRongCoreCallback;
import io.rong.imlib.IRongCoreEnum;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.discussion.base.RongDiscussionClient;
import io.rong.imlib.discussion.model.Discussion;
import io.rong.imlib.model.Conversation;
import java.util.ArrayList;
import java.util.Locale;

/** Created by weiqinxiao on 16/8/16. */
public class VideoPlugin implements IPluginModule, IPluginRequestPermissionResultCallback {
    private static final String TAG = "VideoPlugin";
    private ArrayList<String> allMembers;
    private Context context;

    private Conversation.ConversationType conversationType;
    private String targetId;

    @Override
    public Drawable obtainDrawable(Context context) {
        return context.getResources().getDrawable(R.drawable.rc_ic_video_selector);
    }

    @Override
    public String obtainTitle(Context context) {
        return context.getString(R.string.rc_voip_video);
    }

    @Override
    public void onClick(Fragment currentFragment, RongExtension extension, int index) {
        context = currentFragment.getActivity().getApplicationContext();
        conversationType = extension.getConversationType();
        targetId = extension.getTargetId();

        PermissionType[] audioCallPermissions =
                RongCallPermissionUtil.getVideoCallPermissions(context);
        String[] permissions = new String[audioCallPermissions.length];
        for (int i = 0; i < audioCallPermissions.length; i++) {
            permissions[i] = audioCallPermissions[i].getPermissionName();
        }
        if (RongCallPermissionUtil.checkPermissions(currentFragment.getActivity(), permissions)) {
            startVideoActivity(extension);
        } else {
            extension.requestPermissionForPluginResult(
                    permissions,
                    IPluginRequestPermissionResultCallback.REQUEST_CODE_PERMISSION_PLUGIN,
                    this);
        }
    }

    private void startVideoActivity(final RongExtension extension) {

        RongCallSession profile = RongCallClient.getInstance().getCallSession();
        if (profile != null && profile.getStartTime() > 0) {
            Toast.makeText(
                            context,
                            profile.getMediaType() == RongCallCommon.CallMediaType.AUDIO
                                    ? context.getString(R.string.rc_voip_call_audio_start_fail)
                                    : context.getString(R.string.rc_voip_call_video_start_fail),
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        if (!CallKitUtils.isNetworkAvailable(context)) {
            Toast.makeText(
                            context,
                            context.getString(R.string.rc_voip_call_network_error),
                            Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        if (conversationType.equals(Conversation.ConversationType.PRIVATE)) {
            Intent intent = new Intent(RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEVIDEO);
            intent.putExtra("conversationType", conversationType.getName().toLowerCase(Locale.US));
            intent.putExtra("targetId", targetId);
            intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage(context.getPackageName());
            context.getApplicationContext().startActivity(intent);
        } else if (conversationType.equals(Conversation.ConversationType.DISCUSSION)) {
            RongDiscussionClient.getInstance()
                    .getDiscussion(
                            targetId,
                            new IRongCoreCallback.ResultCallback<Discussion>() {
                                @Override
                                public void onSuccess(Discussion discussion) {

                                    Intent intent =
                                            new Intent(context, CallSelectMemberActivity.class);
                                    allMembers = (ArrayList<String>) discussion.getMemberIdList();
                                    intent.putStringArrayListExtra("allMembers", allMembers);
                                    String myId = RongIMClient.getInstance().getCurrentUserId();
                                    ArrayList<String> invited = new ArrayList<>();
                                    invited.add(myId);
                                    intent.putStringArrayListExtra("invitedMembers", invited);
                                    intent.putExtra(
                                            "conversationType", conversationType.getValue());
                                    intent.putExtra(
                                            "mediaType",
                                            RongCallCommon.CallMediaType.VIDEO.getValue());
                                    extension.startActivityForPluginResult(
                                            intent, 110, VideoPlugin.this);
                                }

                                @Override
                                public void onError(IRongCoreEnum.CoreErrorCode e) {
                                    RLog.d(TAG, "get discussion errorCode = " + e.getValue());
                                }
                            });
        } else if (conversationType.equals(Conversation.ConversationType.GROUP)) {
            Intent intent = new Intent(context, CallSelectMemberActivity.class);
            String myId = RongIMClient.getInstance().getCurrentUserId();
            ArrayList<String> invited = new ArrayList<>();
            invited.add(myId);
            intent.putStringArrayListExtra("invitedMembers", invited);
            intent.putExtra("groupId", targetId);
            intent.putExtra("conversationType", conversationType.getValue());
            intent.putExtra("mediaType", RongCallCommon.CallMediaType.VIDEO.getValue());
            extension.startActivityForPluginResult(intent, 110, this);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        if (requestCode == REQUEST_CODE_ADD_MEMBER) {
            if (resultCode == Activity.RESULT_OK) {
                if (data.getBooleanExtra("remote_hangup", false)) {
                    RLog.d(TAG, "Remote exit, end the call.");
                    return;
                }
            }
        }

        Intent intent = new Intent(RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIVIDEO);
        ArrayList<String> userIds = data.getStringArrayListExtra("invited");
        ArrayList<String> observerIds = data.getStringArrayListExtra("observers");
        userIds.add(RongIMClient.getInstance().getCurrentUserId());
        intent.putExtra("conversationType", conversationType.getName().toLowerCase(Locale.US));
        intent.putExtra("targetId", targetId);
        intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
        intent.putStringArrayListExtra("invitedUsers", userIds);
        intent.putStringArrayListExtra("observerUsers", observerIds);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage(context.getPackageName());
        context.getApplicationContext().startActivity(intent);
    }

    @Override
    public boolean onRequestPermissionResult(
            Fragment fragment,
            RongExtension extension,
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        Context context = fragment.getContext();
        if (RongCallPermissionUtil.checkPermissions(context, permissions)) {
            startVideoActivity(extension);
        } else {
            RongCallPermissionUtil.showRequestPermissionFailedAlter(
                    context, permissions, grantResults);
        }
        return true;
    }
}
