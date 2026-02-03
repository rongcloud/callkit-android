package io.rong.callkit;

import static io.rong.imkit.utils.RongDateUtils.formatDate;

import android.content.Context;
import android.text.Spannable;
import android.text.SpannableString;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.fragment.app.FragmentActivity;
import io.rong.calllib.message.CallAISummaryMessage;
import io.rong.imkit.conversation.messgelist.provider.BaseMessageItemProvider;
import io.rong.imkit.model.UiMessage;
import io.rong.imkit.widget.adapter.IViewProviderListener;
import io.rong.imkit.widget.adapter.ViewHolder;
import io.rong.imlib.model.Message;
import io.rong.imlib.model.MessageContent;
import java.util.Date;
import java.util.List;

/** Created by RongCloud on 2025/12/16. */
public class CallAISumMsgProvider extends BaseMessageItemProvider<CallAISummaryMessage> {

    private static final String SPACE_CHAR = " ";

    @Override
    protected ViewHolder onCreateMessageContentViewHolder(ViewGroup parent, int viewType) {
        View view =
                LayoutInflater.from(parent.getContext())
                        .inflate(R.layout.rc_voip_item_ai_summarization, parent, false);
        return new ViewHolder(parent.getContext(), view);
    }

    @Override
    protected void bindMessageContentViewHolder(
            ViewHolder holder,
            ViewHolder parentHolder,
            CallAISummaryMessage callAISummarizationMessage,
            UiMessage uiMessage,
            int position,
            List<UiMessage> list,
            IViewProviderListener<UiMessage> listener) {
        Message message = uiMessage.getMessage();
        if (message.getMessageDirection() == Message.MessageDirection.SEND) {
            holder.getConvertView()
                    .setBackgroundResource(io.rong.imkit.R.drawable.rc_ic_bubble_right);
        } else {
            holder.getConvertView()
                    .setBackgroundResource(io.rong.imkit.R.drawable.rc_ic_bubble_left);
        }
        long connectedTime = callAISummarizationMessage.getConnectedTime();
        String timeString = formatDate(holder.getContext(), new Date(connectedTime), "HH:mm:ss");
        String summaryText =
                holder.getContext().getString(R.string.rc_voip_ai_sum_start_time)
                        + SPACE_CHAR
                        + timeString;
        holder.setText(R.id.rc_voip_ai_sum_start_time, summaryText);
    }

    @Override
    protected boolean onItemClick(
            ViewHolder holder,
            CallAISummaryMessage callAISummarizationMessage,
            UiMessage uiMessage,
            int position,
            List<UiMessage> list,
            IViewProviderListener<UiMessage> listener) {
        if (!(holder.getContext() instanceof FragmentActivity)) {
            return true;
        }
        FragmentActivity activity = (FragmentActivity) holder.getContext();
        RongAISummarizationDialog.newInstance(
                        callAISummarizationMessage.getCallId(),
                        callAISummarizationMessage.getTaskId(),
                        "")
                .show(activity.getSupportFragmentManager(), RongAISummarizationDialog.TAG);
        return true;
    }

    @Override
    protected boolean isMessageViewType(MessageContent messageContent) {
        return messageContent instanceof CallAISummaryMessage;
    }

    @Override
    public Spannable getSummarySpannable(
            Context context, CallAISummaryMessage callAISummarizationMessage) {
        return new SpannableString(context.getString(R.string.rc_voip_ai_sum_subtitle));
    }
}
