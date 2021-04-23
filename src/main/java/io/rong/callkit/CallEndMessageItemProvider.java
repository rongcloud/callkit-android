package io.rong.callkit;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;

import io.rong.callkit.util.CallKitUtils;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
import io.rong.calllib.message.CallSTerminateMessage;
import io.rong.imkit.conversation.messgelist.provider.BaseMessageItemProvider;
import io.rong.imkit.model.UiMessage;
import io.rong.imkit.widget.adapter.IViewProviderListener;
import io.rong.imkit.widget.adapter.ViewHolder;
import io.rong.imlib.model.Message;
import io.rong.imlib.model.MessageContent;

import static io.rong.calllib.RongCallCommon.CallDisconnectedReason.HANGUP;
import static io.rong.calllib.RongCallCommon.CallDisconnectedReason.OTHER_DEVICE_HAD_ACCEPTED;

public class CallEndMessageItemProvider extends BaseMessageItemProvider<CallSTerminateMessage> {
    @Override
    protected io.rong.imkit.widget.adapter.ViewHolder onCreateMessageContentViewHolder(ViewGroup parent, int viewType) {
        View textView = LayoutInflater.from(parent.getContext()).inflate(R.layout.rc_text_message_item, parent, false);
        return new ViewHolder(parent.getContext(), textView);
    }

    @Override
    protected void bindMessageContentViewHolder(ViewHolder holder, ViewHolder parentHolder, CallSTerminateMessage callSTerminateMessage, UiMessage uiMessage, int position, List<UiMessage> list, IViewProviderListener<UiMessage> listener) {
        Message message = uiMessage.getMessage();
        final TextView view = holder.getView(io.rong.imkit.R.id.rc_text);
        if (message.getMessageDirection() == Message.MessageDirection.SEND) {
            view.setBackgroundResource(R.drawable.rc_ic_bubble_right);
        } else {
            view.setBackgroundResource(R.drawable.rc_ic_bubble_left);
        }

        RongCallCommon.CallMediaType mediaType = callSTerminateMessage.getMediaType();
        String direction = callSTerminateMessage.getDirection();
        Drawable drawable = null;

        String msgContent = "";
        switch (callSTerminateMessage.getReason()) {
            case CANCEL:
                msgContent = view.getResources().getString(R.string.rc_voip_mo_cancel);
                break;
            case REJECT:
                msgContent = view.getResources().getString(R.string.rc_voip_mo_reject);
                break;
            case NO_RESPONSE:
            case BUSY_LINE:
                msgContent = view.getResources().getString(R.string.rc_voip_mo_no_response);
                break;
            case REMOTE_BUSY_LINE:
                msgContent = view.getResources().getString(R.string.rc_voip_mt_busy);
                break;
            case REMOTE_CANCEL:
                msgContent = view.getResources().getString(R.string.rc_voip_mt_cancel);
                break;
            case REMOTE_REJECT:
                msgContent = view.getResources().getString(R.string.rc_voip_mt_reject);
                break;
            case REMOTE_NO_RESPONSE:
                msgContent = view.getResources().getString(R.string.rc_voip_mt_no_response);
                break;
            case NETWORK_ERROR:
            case REMOTE_NETWORK_ERROR:
            case INIT_VIDEO_ERROR:
                msgContent = view.getResources().getString(R.string.rc_voip_call_interrupt);
                break;
            case OTHER_DEVICE_HAD_ACCEPTED:
                msgContent = view.getResources().getString(R.string.rc_voip_call_other);
                break;
            case SERVICE_NOT_OPENED:
            case REMOTE_ENGINE_UNSUPPORTED:
                msgContent = view.getResources().getString(R.string.rc_voip_engine_notfound);
                break;
             default:
                 String mo_reject = view.getResources().getString(R.string.rc_voip_mo_reject);
                 String mt_reject = view.getResources().getString(R.string.rc_voip_mt_reject);
                 String extra = callSTerminateMessage.getExtra();
                 String timeRegex = "([0-9]?[0-9]:)?([0-5][0-9]:)?([0-5][0-9])$";
                 if (!TextUtils.isEmpty(extra)) {
                     boolean val = extra.matches(timeRegex);
                     if (val) {
                         msgContent = view.getResources().getString(R.string.rc_voip_call_time_length);
                         msgContent += extra;
                     } else {
                         msgContent = callSTerminateMessage.getReason() == HANGUP ? mo_reject : mt_reject;
                     }
                 }
                 break;
        }

        view.setText(msgContent);
        view.setCompoundDrawablePadding(15);

        if (mediaType.equals(RongCallCommon.CallMediaType.VIDEO)) {
            if (direction != null && direction.equals("MO")) {
                drawable = view.getResources().getDrawable(R.drawable.rc_voip_video_right);
                drawable.setBounds(
                        0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                view.setCompoundDrawablesRelative(null, null, drawable, null);
                view.setTextColor(view.getResources().getColor(R.color.rc_voip_color_right));
            } else {
                drawable = view.getResources().getDrawable(R.drawable.rc_voip_video_left);
                drawable.setBounds(
                        0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                view.setCompoundDrawablesRelative(drawable, null, null, null);
                view.setTextColor(view.getResources().getColor(R.color.rc_voip_color_left));
            }
        } else {
            if (direction != null && direction.equals("MO")) {
                if (callSTerminateMessage.getReason().equals(HANGUP)
                        || callSTerminateMessage.getReason()
                        .equals(RongCallCommon.CallDisconnectedReason.REMOTE_HANGUP)) {
                    drawable =
                            view.getResources().getDrawable(R.drawable.rc_voip_audio_right_connected);
                } else {
                    drawable = view.getResources().getDrawable(R.drawable.rc_voip_audio_right_cancel);
                }
                drawable.setBounds(
                        0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                view.setCompoundDrawablesRelative(null, null, drawable, null);
                view.setTextColor(view.getResources().getColor(R.color.rc_voip_color_right));
            } else {
                if (callSTerminateMessage.getReason().equals(HANGUP)
                        || callSTerminateMessage.getReason()
                        .equals(RongCallCommon.CallDisconnectedReason.REMOTE_HANGUP)) {
                    drawable =
                            view.getResources().getDrawable(R.drawable.rc_voip_audio_left_connected);
                } else {
                    drawable = view.getResources().getDrawable(R.drawable.rc_voip_audio_left_cancel);
                }
                drawable.setBounds(
                        0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                view.setCompoundDrawablesRelative(drawable, null, null, null);
                view.setTextColor(view.getResources().getColor(R.color.rc_voip_color_left));
            }
        }
    }

    @Override
    protected boolean onItemClick(ViewHolder holder, CallSTerminateMessage callSTerminateMessage, UiMessage uiMessage, int position, List<UiMessage> list, IViewProviderListener<UiMessage> listener) {
        if (callSTerminateMessage.getReason() == OTHER_DEVICE_HAD_ACCEPTED) {
            return true;
        }
        Context context = holder.getContext();
        RongCallSession profile = RongCallClient.getInstance().getCallSession();
        if (profile != null && profile.getActiveTime() > 0) {
            if (profile.getMediaType() == RongCallCommon.CallMediaType.AUDIO) {
                Toast.makeText(context, context.getString(R.string.rc_voip_call_audio_start_fail), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(context, context.getString(R.string.rc_voip_call_video_start_fail), Toast.LENGTH_SHORT).show();
            }
            return true;
        }
        if (!CallKitUtils.isNetworkAvailable(context)) {
            Toast.makeText(context, context.getString(R.string.rc_voip_call_network_error), Toast.LENGTH_SHORT).show();
            return true;
        }
        RongCallCommon.CallMediaType mediaType = callSTerminateMessage.getMediaType();
        String action = null;
        if (mediaType.equals(RongCallCommon.CallMediaType.VIDEO)) {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEVIDEO;
        } else {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO;
        }
        Intent intent = new Intent(action);
        intent.setPackage(context.getPackageName());
        intent.putExtra(
                "conversationType", uiMessage.getMessage().getConversationType().getName().toLowerCase(Locale.US));
        intent.putExtra("targetId", uiMessage.getMessage().getTargetId());
        intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
        context.startActivity(intent);
        return true;
    }

    @Override
    protected boolean isMessageViewType(MessageContent messageContent) {
        return messageContent instanceof CallSTerminateMessage;
    }

    @Override
    public Spannable getSummarySpannable(Context context, CallSTerminateMessage callSTerminateMessage) {
        RongCallCommon.CallMediaType mediaType = callSTerminateMessage.getMediaType();
        if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
            return new SpannableString(context.getString(R.string.rc_voip_message_audio));
        } else {
            return new SpannableString(context.getString(R.string.rc_voip_message_video));
        }
    }
}
