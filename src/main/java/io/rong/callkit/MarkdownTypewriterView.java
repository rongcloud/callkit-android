package io.rong.callkit;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration.Builder;
import io.noties.markwon.SoftBreakAddsNewLinePlugin;
import io.noties.markwon.syntax.SyntaxHighlightNoOp;

/** Created by RongCloud on 2025/12/9. */
public class MarkdownTypewriterView extends FrameLayout {

    private static final long UPDATE_INTERVAL = 300;
    private static final String TAG = "MarkdownTypewriterView";
    private static final int MIN_RENDER_SIZE = 15;
    private static final int MAX_DELAY_TIME = 500;

    private ScrollView scrollView;
    private TextView textView;
    private Markwon markwon;
    private final StringBuilder rawBuffer = new StringBuilder();
    private final Object lock = new Object();
    private int preLength = 0;
    private boolean isRunning = false;
    private boolean isComplete = false;
    private boolean isMarkDown = true;

    public MarkdownTypewriterView(@NonNull Context context) {
        super(context);
        initView();
    }

    public MarkdownTypewriterView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public MarkdownTypewriterView(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    public MarkdownTypewriterView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView();
    }

    private int dp2px(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    private void initView() {
        scrollView = new ScrollView(getContext());
        textView = new TextView(getContext());
        textView.setTextSize(18);
        int padding = dp2px(18);
        textView.setPadding(padding, padding, padding, padding * 2);
        scrollView.addView(
                textView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        markwon =
                Markwon.builder(getContext())
                        .usePlugin(SoftBreakAddsNewLinePlugin.create())
                        .usePlugin(
                                new AbstractMarkwonPlugin() {
                                    @Override
                                    public void configureConfiguration(@NonNull Builder builder) {
                                        builder.syntaxHighlight(new SyntaxHighlightNoOp());
                                    }
                                })
                        .build();
    }

    private Runnable updateRunnable =
            new Runnable() {
                @Override
                public void run() {
                    if (!isRunning) {
                        Log.d(TAG, "updateRunnable: not running, return");
                        return;
                    }
                    synchronized (lock) {
                        if (rawBuffer.length() == 0) {
                            postDelayed(updateRunnable, MAX_DELAY_TIME);
                            return;
                        }
                    }
                    String content;
                    synchronized (lock) {
                        content =
                                rawBuffer.substring(
                                        0,
                                        Math.min(rawBuffer.length(), preLength + MIN_RENDER_SIZE));
                    }
                    if (content.length() == preLength) {
                        // 如果长度没变化，并且已经完成了接收数据，则停止任务
                        if (isComplete) {
                            return;
                        }
                        postDelayed(updateRunnable, UPDATE_INTERVAL);
                        return;
                    }
                    preLength = content.length();
                    if (isMarkDown) {
                        markwon.setMarkdown(textView, content);
                    } else {
                        textView.setText(content);
                    }
                    scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
                    postDelayed(updateRunnable, UPDATE_INTERVAL);
                }
            };

    public void setComplete() {
        isComplete = true;
    }

    public void reset() {
        isRunning = false;
        preLength = 0;
        isComplete = false;
        rawBuffer.setLength(0);
        textView.setText("");
        removeCallbacks(updateRunnable);
    }

    public void start(boolean isMarkDown) {
        if (isRunning) {
            Log.d(TAG, "start: already running");
            return;
        }
        reset();
        this.isMarkDown = isMarkDown;
        isRunning = true;
        post(updateRunnable);
    }

    public void appendContent(String content) {
        if (content == null) {
            return;
        }
        rawBuffer.append(content);
    }

    public void setContent(String content) {
        rawBuffer.setLength(0);
        if (TextUtils.isEmpty(content)) {
            textView.setText("");
            return;
        }
        rawBuffer.append(content);
        preLength = content.length();
        if (isMarkDown) {
            markwon.setMarkdown(textView, content);
        } else {
            textView.setText(content);
        }
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }
}
