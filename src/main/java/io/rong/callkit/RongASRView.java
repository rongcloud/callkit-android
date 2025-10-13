package io.rong.callkit;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.FragmentActivity;
import cn.rongcloud.rtc.api.RCRTCASRContent;
import cn.rongcloud.rtc.api.RCRTCRealtimeTranslationContent;
import cn.rongcloud.rtc.api.callback.IRCRTCResultCallback;
import cn.rongcloud.rtc.base.RTCErrorCode;
import io.rong.callkit.RongASRItemView.ASRItemData;
import io.rong.callkit.RongASRSettingsDialog.ASRSettings;
import io.rong.callkit.databinding.RcVoipAsrViewBinding;
import io.rong.callkit.util.CallKitUtils;
import io.rong.calllib.IRongCallASRListener;
import io.rong.calllib.RongCallClient;
import java.util.HashMap;
import java.util.Map;

/** Created by RongCloud on 2025/8/12. */
public class RongASRView extends ConstraintLayout implements IRongCallASRListener {

    public static final float MIN_HEIGHT = 140;
    public static final float MAX_COUNT = 2;
    private final String TAG = "RongASRView";

    private ISubtitleViewCallback callback;
    private RcVoipAsrViewBinding binding;
    private Map<String, ASRItemData> dataMap = new HashMap<>();
    private ASRSettings settings = new ASRSettings();

    public RongASRView(@NonNull Context context) {
        super(context);
        initView();
    }

    public RongASRView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public RongASRView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    public RongASRView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView();
    }

    private void initView() {
        binding = RcVoipAsrViewBinding.inflate(LayoutInflater.from(getContext()), this);
        setBackground(getResources().getDrawable(R.drawable.rc_voip_white_bg));
        int minHeight = CallKitUtils.dp2px(MIN_HEIGHT, getContext());
        setMinHeight(minHeight);
        setMinimumHeight(minHeight);

        binding.rcSubtitleClose.setOnClickListener(
                (v) -> {
                    setVisibility(View.GONE);
                    onClose();
                });
        this.setOnClickListener(
                (v) -> {
                    RongASRSettingsDialog settingsDialog =
                            RongASRSettingsDialog.newInstance(this.settings);
                    settingsDialog.setOnDismissListener(
                            (dialog) -> {
                                setSettingsResult(settingsDialog.getSettings());
                            });
                    settingsDialog.show(
                            ((FragmentActivity) getContext()).getSupportFragmentManager(),
                            "RongASRSettingsDialog");
                });
        RongCallClient.getInstance().setASRListener(this);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != VISIBLE) {
            onClose();
        }
    }

    private void onClose() {
        ISubtitleViewCallback callback1 = callback;
        if (callback1 != null) {
            callback1.onClose();
        }
    }

    public void setSubtitleViewCallback(ISubtitleViewCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onReceiveASRContent(RCRTCASRContent content) {
        Log.d(TAG, "onReceiveRealTimeSubtitle: " + content.msg);
        post(
                () -> {
                    updateAsr(content);
                });
    }

    @Override
    public void onReceiveStopASR() {
        Log.d(TAG, "onReceiveStopRealTimeSubtitle: ");
    }

    @Override
    public void onReceiveStartASR() {
        Log.d(TAG, "onReceiveStartRealTimeSubtitle: ");
    }

    @Override
    public void onReceiveRealtimeTranslationContent(RCRTCRealtimeTranslationContent content) {
        Log.d(TAG, "onReceiveRealtimeTranslationContent: " + content.msg);
        post(
                () -> {
                    updateAsr(content);
                });
    }

    private <T extends RCRTCASRContent> void updateAsr(T content) {
        if (!isAttachedToWindow()) {
            return;
        }
        ASRItemData data = dataMap.get(content.userID);
        if (data != null) {
            data.update(content);
            return;
        }
        ASRItemData oldData = null;
        if (dataMap.size() >= MAX_COUNT) {
            for (ASRItemData item : dataMap.values()) {
                if (oldData == null) {
                    oldData = item;
                } else if (item.getUpdateTime() < oldData.getUpdateTime()) {
                    oldData = item;
                }
            }
            dataMap.remove(oldData.userId);
        }

        if (oldData != null) {
            data = new ASRItemData(oldData.getView(), content);
        } else {
            data = new ASRItemData(createItemView(), content);
        }
        dataMap.put(content.userID, data);
    }

    private RongASRItemView createItemView() {
        RongASRItemView itemView = new RongASRItemView(getContext());
        LayoutParams params =
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = CallKitUtils.dp2px(16, getContext());
        itemView.setLayoutParams(params);
        itemView.setId(View.generateViewId());
        itemView.setDisplayModel(settings.getDisplayModel());

        // 请求重新布局，确保高度变化时向上扩展
        if (binding.rcVoipAsrContainer.getChildCount() == 1) {
            // 记录添加子视图前的高度
            //            int oldHeight = getMeasuredHeight();
            // 添加子视图
            binding.rcVoipAsrContainer.addView(itemView);
            // 使用post确保在下一个布局周期中调整位置
            //            post(() -> adjustViewPosition(oldHeight));
            post(() -> adjustViewPosition());
        } else {
            binding.rcVoipAsrContainer.addView(itemView);
        }
        return itemView;
    }

    //    /**
    //     * 调整视图位置，确保向上扩展
    //     */
    //    private void adjustViewPosition(int oldHeight) {
    private void adjustViewPosition() {
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        if (layoutParams instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams frameParams = (FrameLayout.LayoutParams) layoutParams;
            // 强制重新测量，确保获取准确的新高度
            measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
            int newHeight = getMeasuredHeight();
            setMinimumHeight(newHeight);

            View parent = (View) getParent();
            int parentHeight =
                    parent.getHeight() - parent.getPaddingTop() - parent.getPaddingBottom();
            if (frameParams.topMargin + newHeight > parentHeight) {
                frameParams.topMargin = parentHeight - newHeight;
                setLayoutParams(frameParams);
            }

            // 计算高度差（向上扩展）
            //            int heightDiff = newHeight - oldHeight;
            //            // 只有当高度确实发生变化时才调整位置
            //            if (heightDiff != 0) {
            //                // 调整topMargin，使底部位置保持不变
            //                frameParams.topMargin = Math.max(frameParams.topMargin -
            // heightDiff,0);
            //                setLayoutParams(frameParams);
            //
            //                Log.d(TAG, "adjustViewPosition: oldHeight=" + oldHeight +
            //                      ", newHeight=" + newHeight +
            //                      ", heightDiff=" + heightDiff +
            //                      ", newTopMargin=" + frameParams.topMargin);
            //            }
        }
        requestLayout();
    }

    @Override
    public void onASRError(int errorCode) {
        Log.d(TAG, "onRealTimeSubtitleError: " + errorCode);
        post(
                () -> {
                    Toast.makeText(
                                    getContext(),
                                    R.string.rc_voip_subtitle_not_available,
                                    Toast.LENGTH_SHORT)
                            .show();
                    setVisibility(View.GONE);
                });
    }

    public void enableSubtitle(boolean enable) {
        setVisibility(enable ? VISIBLE : GONE);
        RongCallClient.getInstance().setEnableASR(enable);
        if (enable) {
            RongCallClient.getInstance()
                    .startASR(
                            new IRCRTCResultCallback() {
                                @Override
                                public void onSuccess() {}

                                @Override
                                public void onFailed(RTCErrorCode errorCode) {
                                    post(
                                            () -> {
                                                setVisibility(GONE);
                                            });
                                }
                            });
        }
    }

    public void destroy() {
        RongCallClient.getInstance().setASRListener(null);
    }

    public void setSettingsResult(ASRSettings newSetting) {
        Log.d(TAG, "setSettingsResult: " + (newSetting == null ? "null" : newSetting.destLanguage));
        if (newSetting == null) {
            return;
        }

        if (!TextUtils.equals(newSetting.destLanguage, this.settings.destLanguage)) {
            final String preDestLanguage = settings.destLanguage;
            boolean isStop = newSetting.isStopTranslation();
            if (isStop) {
                RongCallClient.getInstance().stopRealtimeTranslation(null);
            } else {
                RongCallClient.getInstance()
                        .startRealtimeTranslation(
                                newSetting.destLanguage,
                                new IRCRTCResultCallback() {
                                    @Override
                                    public void onSuccess() {}

                                    @Override
                                    public void onFailed(RTCErrorCode errorCode) {
                                        Log.d(TAG, "onFailed: " + errorCode);
                                        post(
                                                () -> {
                                                    settings.destLanguage = preDestLanguage;
                                                    updateDisplayModel(settings);
                                                    Toast.makeText(
                                                                    getContext(),
                                                                    R.string
                                                                            .rc_voip_asr_transolation_start_failed,
                                                                    Toast.LENGTH_SHORT)
                                                            .show();
                                                });
                                    }
                                });
            }
            updateDisplayModel(newSetting);
        } else if (this.settings.bothDisplay != newSetting.bothDisplay) {
            updateDisplayModel(newSetting);
        }

        this.settings = newSetting;
    }

    private void updateDisplayModel(ASRSettings settings) {
        int count = binding.rcVoipAsrContainer.getChildCount();
        // 使用post确保在下一个布局周期中调整位置
        for (int i = 0; i < count; i++) {
            RongASRItemView itemView = (RongASRItemView) binding.rcVoipAsrContainer.getChildAt(i);
            itemView.setDisplayModel(settings.getDisplayModel());
        }
        post(
                () -> {
                    //            measure(MeasureSpec.makeMeasureSpec(getWidth(),
                    // MeasureSpec.EXACTLY),
                    //                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
                    //            int newHeight = getMeasuredHeight();
                    //            setMinimumHeight(newHeight);
                    adjustViewPosition();
                });
    }

    public ASRSettings getSettings() {
        return settings;
    }

    public interface ISubtitleViewCallback {
        void onClose();
    }
}
