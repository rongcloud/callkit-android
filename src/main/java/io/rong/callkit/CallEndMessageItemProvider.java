package io.rong.callkit;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import io.rong.callkit.util.CallKitUtils;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallCommon;
import io.rong.calllib.RongCallSession;
import io.rong.calllib.message.CallSTerminateMessage;
import io.rong.imkit.RongMessageItemLongClickActionManager;
import io.rong.imkit.widget.AutoLinkTextView;
import io.rong.imkit.model.ProviderTag;
import io.rong.imkit.model.UIMessage;
import io.rong.imkit.utilities.OptionsPopupDialog;
import io.rong.imkit.widget.provider.IContainerItemProvider;
import io.rong.imkit.widget.provider.MessageItemLongClickAction;
import io.rong.imlib.model.Message;

import static io.rong.calllib.RongCallCommon.CallDisconnectedReason.HANGUP;
import static io.rong.calllib.RongCallCommon.CallDisconnectedReason.OTHER_DEVICE_HAD_ACCEPTED;

@ProviderTag(messageContent = CallSTerminateMessage.class, showSummaryWithName = false, showProgress = false, showWarning = false, showReadState = false)
public class CallEndMessageItemProvider extends IContainerItemProvider.MessageProvider<CallSTerminateMessage> {
    private static class ViewHolder {
        AutoLinkTextView message;
    }


    @Override
    public View newView(Context context, ViewGroup group) {
        View view = LayoutInflater.from(context).inflate(R.layout.rc_item_text_message, null);

        ViewHolder holder = new ViewHolder();
        holder.message = (AutoLinkTextView) view.findViewById(android.R.id.text1);
        view.setTag(holder);
        return view;
    }

    @Override
    public void bindView(View v, int position, CallSTerminateMessage content, UIMessage data) {
        ViewHolder holder = (ViewHolder) v.getTag();

        if (data == null || content == null) {
            return;
        }
        if (data.getMessageDirection() == Message.MessageDirection.SEND) {
            holder.message.setBackgroundResource(R.drawable.rc_ic_bubble_right);
        } else {
            holder.message.setBackgroundResource(R.drawable.rc_ic_bubble_left);
        }

        RongCallCommon.CallMediaType mediaType = content.getMediaType();
        String direction = content.getDirection();
        Drawable drawable = null;

        String msgContent = "";
        switch (content.getReason()) {
            case CANCEL:
                msgContent = v.getResources().getString(R.string.rc_voip_mo_cancel);
                break;
            case REJECT:
                msgContent = v.getResources().getString(R.string.rc_voip_mo_reject);
                break;
            case NO_RESPONSE:
            case BUSY_LINE:
                msgContent = v.getResources().getString(R.string.rc_voip_mo_no_response);
                break;
            case REMOTE_BUSY_LINE:
                msgContent = v.getResources().getString(R.string.rc_voip_mt_busy);
                break;
            case REMOTE_CANCEL:
                msgContent = v.getResources().getString(R.string.rc_voip_mt_cancel);
                break;
            case REMOTE_REJECT:
                msgContent = v.getResources().getString(R.string.rc_voip_mt_reject);
                break;
            case REMOTE_NO_RESPONSE:
                msgContent = v.getResources().getString(R.string.rc_voip_mt_no_response);
                break;
            case HANGUP:
            case REMOTE_HANGUP:
                String mo_reject = v.getResources().getString(R.string.rc_voip_mo_reject);
                String mt_reject = v.getResources().getString(R.string.rc_voip_mt_reject);
                String extra = content.getExtra();
                String timeRegex= "([0-9]?[0-9]:)?([0-5][0-9]:)?([0-5][0-9])$";
                if (!TextUtils.isEmpty(extra)) {
                    boolean val = extra.matches(timeRegex);
                    if (val) {
                        msgContent = v.getResources().getString(R.string.rc_voip_call_time_length);
                        msgContent += extra;
                    } else {
                        msgContent = content.getReason() == HANGUP ? mo_reject : mt_reject;
                    }
                }
                break;
            case NETWORK_ERROR:
            case REMOTE_NETWORK_ERROR:
            case INIT_VIDEO_ERROR:
                msgContent = v.getResources().getString(R.string.rc_voip_call_interrupt);
                break;
            case OTHER_DEVICE_HAD_ACCEPTED:
                msgContent = v.getResources().getString(R.string.rc_voip_call_other);
                break;
        }

        holder.message.setText(msgContent);
        holder.message.setCompoundDrawablePadding(15);

        if (mediaType.equals(RongCallCommon.CallMediaType.VIDEO)) {
            if (direction != null && direction.equals("MO")) {
                drawable = v.getResources().getDrawable(R.drawable.rc_voip_video_right);
                drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                holder.message.setCompoundDrawables(null, null, drawable, null);
                holder.message.setTextColor(v.getResources().getColor(R.color.rc_voip_color_right));
            } else {
                drawable = v.getResources().getDrawable(R.drawable.rc_voip_video_left);
                drawable.setBounds(0, 0,  drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                holder.message.setCompoundDrawables(drawable, null, null, null);
                holder.message.setTextColor(v.getResources().getColor(R.color.rc_voip_color_left));
            }
        } else {
            if (direction != null && direction.equals("MO")) {
                if (content.getReason().equals(HANGUP) ||
                        content.getReason().equals(RongCallCommon.CallDisconnectedReason.REMOTE_HANGUP)) {
                    drawable = v.getResources().getDrawable(R.drawable.rc_voip_audio_right_connected);
                } else {
                    drawable = v.getResources().getDrawable(R.drawable.rc_voip_audio_right_cancel);
                }
                drawable.setBounds(0, 0,  drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                holder.message.setCompoundDrawables(null, null, drawable, null);
                holder.message.setTextColor(v.getResources().getColor(R.color.rc_voip_color_right));
            } else {
                if (content.getReason().equals(HANGUP) ||
                        content.getReason().equals(RongCallCommon.CallDisconnectedReason.REMOTE_HANGUP)) {
                    drawable = v.getResources().getDrawable(R.drawable.rc_voip_audio_left_connected);
                } else {
                    drawable = v.getResources().getDrawable(R.drawable.rc_voip_audio_left_cancel);
                }
                drawable.setBounds(0, 0,  drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
                holder.message.setCompoundDrawables(drawable, null, null, null);
                holder.message.setTextColor(v.getResources().getColor(R.color.rc_voip_color_left));
            }
        }
    }

    @Override
    public Spannable getContentSummary(CallSTerminateMessage data) {
        return null;
    }

    @Override
    public Spannable getContentSummary(Context context, CallSTerminateMessage data) {

        RongCallCommon.CallMediaType mediaType = data.getMediaType();
        if (mediaType.equals(RongCallCommon.CallMediaType.AUDIO)) {
            return new SpannableString(context.getString(R.string.rc_voip_message_audio));
        } else {
            return new SpannableString(context.getString(R.string.rc_voip_message_video));
        }
    }

    @Override
    public void onItemClick(View view, int position, CallSTerminateMessage content, UIMessage message) {
        if (content.getReason() == OTHER_DEVICE_HAD_ACCEPTED){
            return;
        }
        RongCallSession profile = RongCallClient.getInstance().getCallSession();
        if (profile != null && profile.getActiveTime() > 0) {
            Toast.makeText(view.getContext(),
                    profile.getMediaType() == RongCallCommon.CallMediaType.AUDIO ?
                            view.getContext().getString(R.string.rc_voip_call_audio_start_fail) :
                            view.getContext().getString(R.string.rc_voip_call_video_start_fail),
                    Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        Context context = view.getContext();
        if (!CallKitUtils.isNetworkAvailable(context)) {
            Toast.makeText(context, context.getString(R.string.rc_voip_call_network_error), Toast.LENGTH_SHORT).show();
            return;
        }
        RongCallCommon.CallMediaType mediaType = content.getMediaType();
        String action = null;
        if (mediaType.equals(RongCallCommon.CallMediaType.VIDEO)) {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEVIDEO;
        } else {
            action = RongVoIPIntent.RONG_INTENT_ACTION_VOIP_SINGLEAUDIO;
        }
        Intent intent = new Intent(action);
        intent.setPackage(view.getContext().getPackageName());
        intent.putExtra("conversationType", message.getConversationType().getName().toLowerCase(Locale.US));
        intent.putExtra("targetId", message.getTargetId());
        intent.putExtra("callAction", RongCallAction.ACTION_OUTGOING_CALL.getName());
        view.getContext().startActivity(intent);
    }

    @Override
    public void onItemLongClick(final View view, final int position, final CallSTerminateMessage content, final UIMessage message) {
        final List<MessageItemLongClickAction> messageItemLongClickActions = RongMessageItemLongClickActionManager.getInstance().getMessageItemLongClickActions(message);
        Collections.sort(messageItemLongClickActions, new Comparator<MessageItemLongClickAction>() {
            @Override
            public int compare(MessageItemLongClickAction lhs, MessageItemLongClickAction rhs) {
                // desc sort
                return rhs.priority - lhs.priority;
            }
        });
        List<String> titles = new ArrayList<>();
        String recallStr = view.getContext().getResources().getString(R.string.rc_dialog_item_message_recall);
        for (MessageItemLongClickAction action : messageItemLongClickActions) {
            if (TextUtils.equals(recallStr, action.getTitle(view.getContext()))) {
                messageItemLongClickActions.remove(action);
                break;
            }
        }
        for (MessageItemLongClickAction action : messageItemLongClickActions) {
            titles.add(action.getTitle(view.getContext()));
        }

        OptionsPopupDialog dialog = OptionsPopupDialog.newInstance(view.getContext(), titles.toArray(new String[titles.size()]))
                .setOptionsPopupDialogListener(new OptionsPopupDialog.OnOptionsItemClickedListener() {
                    @Override
                    public void onOptionsItemClicked(int which) {
                        if (!messageItemLongClickActions.get(which).listener.onMessageItemLongClick(view.getContext(), message)) {
                            onItemLongClickAction(view, position, message);
                        }
                    }
                });
        RongMessageItemLongClickActionManager.getInstance().setLongClickDialog(dialog);
        RongMessageItemLongClickActionManager.getInstance().setLongClickMessage(message.getMessage());
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                RongMessageItemLongClickActionManager.getInstance().setLongClickDialog(null);
                RongMessageItemLongClickActionManager.getInstance().setLongClickMessage(null);
            }
        });
        dialog.show();
    }
}
