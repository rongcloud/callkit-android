package io.rong.imkit;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;

import io.rong.common.RLog;
import io.rong.imkit.widget.provider.InputProvider;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.Conversation;
import io.rong.imlib.model.Discussion;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallSession;

public class AudioCallInputProvider extends InputProvider.ExtendProvider {
    private static final String TAG = "AudioCallInputProvider";
    ArrayList<String> allMembers;
    public AudioCallInputProvider(RongContext context) {
        super(context);
    }

    @Override
    public Drawable obtainPluginDrawable(Context context) {
        return context.getResources().getDrawable(R.drawable.rc_ic_phone);
    }

    @Override
    public CharSequence obtainPluginTitle(Context context) {
        return context.getString(R.string.rc_voip_audio);
    }

    @Override
    public void onPluginClick(View view) {
        final Conversation conversation = getCurrentConversation();

        RongCallSession profile = RongCallClient.getInstance().getCallSession();
        if (profile != null && profile.getActiveTime() > 0) {
            Toast.makeText(getContext(), getContext().getString(R.string.rc_voip_call_start_fail), Toast.LENGTH_SHORT).show();
            return;
        }
        ConnectivityManager cm = (ConnectivityManager) getContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = cm.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnected() || !networkInfo.isAvailable()) {
            Toast.makeText(getContext(), getContext().getString(R.string.rc_voip_call_network_error), Toast.LENGTH_SHORT).show();
            return;
        }

        if (conversation.getConversationType().equals(Conversation.ConversationType.PRIVATE)) {
            Intent intent = new Intent(RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO);
            intent.putExtra("conversationType", conversation.getConversationType().getName().toLowerCase());
            intent.putExtra("targetId", conversation.getTargetId());
            intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setPackage(getContext().getPackageName());
            getContext().getApplicationContext().startActivity(intent);
        } else if (conversation.getConversationType().equals(Conversation.ConversationType.DISCUSSION)) {
            RongIM.getInstance().getDiscussion(conversation.getTargetId(), new RongIMClient.ResultCallback<Discussion>() {
                @Override
                public void onSuccess(Discussion discussion) {
                    Intent intent = new Intent(getContext(), CallSelectMemberActivity.class);
                    allMembers = (ArrayList<String>)discussion.getMemberIdList();
                    intent.putStringArrayListExtra("allMembers", allMembers);
                    String myId = RongIMClient.getInstance().getCurrentUserId();
                    ArrayList<String> invited = new ArrayList<>();
                    invited.add(myId);
                    intent.putStringArrayListExtra("invitedMembers", invited);
                    startActivityForResult(intent, 110);
                }

                @Override
                public void onError(RongIMClient.ErrorCode e) {
                    RLog.d(TAG, "get discussion errorCode = " + e.getValue());
                }
            });
        } else if (conversation.getConversationType().equals(Conversation.ConversationType.GROUP)) {
            Intent intent = new Intent(getContext(), CallSelectMemberActivity.class);
            String myId = RongIMClient.getInstance().getCurrentUserId();
            ArrayList<String> invited = new ArrayList<>();
            invited.add(myId);
            intent.putStringArrayListExtra("invitedMembers", invited);
            intent.putExtra("groupId", conversation.getTargetId());
            startActivityForResult(intent, 110);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            return;
        }

        final Conversation conversation = getCurrentConversation();
        Intent intent = new Intent(RongVoIPIntent.RONG_INTENT_ACTION_VOIP_MULTIAUDIO);
        ArrayList<String> userIds = data.getStringArrayListExtra("invited");
        userIds.add(RongIMClient.getInstance().getCurrentUserId());
        intent.putExtra("conversationType", conversation.getConversationType().getName().toLowerCase());
        intent.putExtra("targetId", conversation.getTargetId());
        intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
        intent.putStringArrayListExtra("invitedUsers", userIds);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setPackage(getContext().getPackageName());
        getContext().getApplicationContext().startActivity(intent);
    }
}
