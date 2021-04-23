package io.rong.callkit;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.List;

import io.rong.calllib.RongCallCommon;
import io.rong.calllib.message.MultiCallEndMessage;
import io.rong.imkit.conversation.messgelist.provider.BaseNotificationMessageItemProvider;
import io.rong.imkit.model.UiMessage;
import io.rong.imkit.widget.adapter.IViewProviderListener;
import io.rong.imkit.widget.adapter.ViewHolder;
import io.rong.imlib.RongIMClient;
import io.rong.imlib.model.MessageContent;

public class MultiCallEndMessageProvider
        extends BaseNotificationMessageItemProvider<MultiCallEndMessage> {

    @Override
    protected ViewHolder onCreateMessageContentViewHolder(ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.rc_voip_msg_multi_call_end, parent, false);
        return new ViewHolder(parent.getContext(), v);
    }

    @Override
    protected void bindMessageContentViewHolder(ViewHolder holder,ViewHolder parentHolder, MultiCallEndMessage multiCallEndMessage, UiMessage uiMessage, int position, List<UiMessage> list, IViewProviderListener<UiMessage> listener) {
        Context context = holder.getContext();
        String msg = "";
        RongCallCommon.CallDisconnectedReason reason = multiCallEndMessage.getReason();
        RongIMClient.MediaType mediaType = multiCallEndMessage.getMediaType();
        if (reason == RongCallCommon.CallDisconnectedReason.OTHER_DEVICE_HAD_ACCEPTED) {
            msg = context.getResources().getString(R.string.rc_voip_call_other);
        } else if (reason == RongCallCommon.CallDisconnectedReason.REMOTE_HANGUP
                || reason == RongCallCommon.CallDisconnectedReason.HANGUP) {
            if (mediaType == RongIMClient.MediaType.AUDIO) {
                msg = context.getResources().getString(R.string.rc_voip_audio_ended);
            } else if (mediaType == RongIMClient.MediaType.VIDEO) {
                msg = context.getResources().getString(R.string.rc_voip_video_ended);
            }
        } else if (reason == RongCallCommon.CallDisconnectedReason.REMOTE_REJECT
                || reason == RongCallCommon.CallDisconnectedReason.REJECT) {
            if (mediaType == RongIMClient.MediaType.AUDIO) {
                msg = context.getResources().getString(R.string.rc_voip_audio_refuse);
            } else if (mediaType == RongIMClient.MediaType.VIDEO) {
                msg = context.getResources().getString(R.string.rc_voip_video_refuse);
            }
        } else if (reason == RongCallCommon.CallDisconnectedReason.SERVICE_NOT_OPENED
                || reason == RongCallCommon.CallDisconnectedReason.REMOTE_ENGINE_UNSUPPORTED) {
            msg = context.getResources().getString(R.string.rc_voip_engine_notfound);
        } else {
            if (mediaType == RongIMClient.MediaType.AUDIO) {
                msg = context.getResources().getString(R.string.rc_voip_audio_no_response);
            } else if (mediaType == RongIMClient.MediaType.VIDEO) {
                msg = context.getResources().getString(R.string.rc_voip_video_no_response);
            }
        }
        TextView tv = holder.getView(R.id.rc_msg);
        tv.setText(msg);
    }

    @Override
    protected boolean isMessageViewType(MessageContent messageContent) {
        return messageContent instanceof MultiCallEndMessage;
    }

    @Override
    public Spannable getSummarySpannable(Context context, MultiCallEndMessage multiCallEndMessage) {
        String msg = "";
        if (multiCallEndMessage.getReason() == RongCallCommon.CallDisconnectedReason.NO_RESPONSE) {
            if (multiCallEndMessage.getMediaType() == RongIMClient.MediaType.AUDIO) {
                msg = context.getResources().getString(R.string.rc_voip_audio_no_response);
            } else if (multiCallEndMessage.getMediaType() == RongIMClient.MediaType.VIDEO) {
                msg = context.getResources().getString(R.string.rc_voip_video_no_response);
            }
        } else {
            if (multiCallEndMessage.getMediaType() == RongIMClient.MediaType.AUDIO) {
                msg = context.getResources().getString(R.string.rc_voip_message_audio);
            } else if (multiCallEndMessage.getMediaType() == RongIMClient.MediaType.VIDEO) {
                msg = context.getResources().getString(R.string.rc_voip_message_video);
            }
        }
        return new SpannableString(msg);
    }
}
