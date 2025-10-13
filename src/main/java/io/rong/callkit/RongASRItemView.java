package io.rong.callkit;

import android.content.Context;
import android.graphics.Paint.FontMetrics;
import android.net.Uri;
import android.os.Build;
import android.text.Layout;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cn.rongcloud.rtc.api.RCRTCASRContent;
import cn.rongcloud.rtc.api.RCRTCRealtimeTranslationContent;
import io.rong.callkit.databinding.RcVoipItemAsrBinding;
import io.rong.callkit.util.CallKitUtils;
import io.rong.imkit.userinfo.RongUserInfoManager;
import io.rong.imlib.model.UserInfo;

/** Created by RongCloud on 2025/9/8. */
public class RongASRItemView extends LinearLayout {

    private static final String TAG = "RongASRItemView";
    public static final int MAX_LINE_COUNT = 2;

    public static final int DISPLAY_MODEL_ASR = 0;
    public static final int DISPLAY_MODEL_TRANSLATION = 1;
    public static final int DISPLAY_MODEL_BOTH = 2;

    private RcVoipItemAsrBinding binding;
    private ASRItemData data;
    private int displayModel = DISPLAY_MODEL_ASR;

    public RongASRItemView(@NonNull Context context) {
        super(context);
        initView();
    }

    public RongASRItemView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public RongASRItemView(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    public RongASRItemView(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initView();
    }

    private void initView() {
        setOrientation(VERTICAL);
        binding = RcVoipItemAsrBinding.inflate(LayoutInflater.from(getContext()), this);
        setContentHeight(binding.rcAsrContent);
        setContentHeight(binding.rcTranslationContent);
    }

    private void setContentHeight(TextView textView) {
        FontMetrics fm = textView.getPaint().getFontMetrics();
        float actualLineHeight =
                isIncludeFontPadding() ? fm.bottom - fm.top : fm.descent - fm.ascent;
        float lineHeight =
                (actualLineHeight + textView.getLineSpacingExtra())
                        * textView.getLineSpacingMultiplier();
        ViewGroup.LayoutParams lp = textView.getLayoutParams();
        lp.height = (int) (lineHeight * MAX_LINE_COUNT);
        textView.setLayoutParams(lp);
    }

    private boolean isIncludeFontPadding() {
        // Oppo Find x5 手机不要保留字体内边距，否则会造成多显示半行
        return !Build.MODEL.equals("PFEM10");
    }

    public void setDisplayModel(
            @IntRange(from = DISPLAY_MODEL_ASR, to = DISPLAY_MODEL_BOTH) int displayModel) {
        Log.d(TAG, "setDisplayModel: " + displayModel);
        this.displayModel = displayModel;
        switch (displayModel) {
            case DISPLAY_MODEL_ASR:
                binding.rcAsrContent.setVisibility(VISIBLE);
                binding.rcTranslationContent.setText("");
                binding.rcTranslationContent.setVisibility(GONE);
                break;
            case DISPLAY_MODEL_TRANSLATION:
                binding.rcAsrContent.setText("");
                binding.rcAsrContent.setVisibility(GONE);
                binding.rcTranslationContent.setVisibility(VISIBLE);
                break;
            case DISPLAY_MODEL_BOTH:
                binding.rcAsrContent.setVisibility(VISIBLE);
                binding.rcTranslationContent.setVisibility(VISIBLE);
                break;
        }
    }

    private void updateView(@NonNull ASRItemData data) {
        if (this.data == null || !TextUtils.equals(this.data.userId, data.userId)) {
            updateUserInfo(data);
        }
        switch (displayModel) {
            case DISPLAY_MODEL_ASR:
                if (data.asr == null) {
                    break;
                }
                binding.rcAsrContent.setText(data.asr.msg);
                scrollToEnd(binding.rcAsrContent);
                break;
            case DISPLAY_MODEL_TRANSLATION:
                if (data.tr == null) {
                    break;
                }
                binding.rcTranslationContent.setText(data.tr.msg);
                scrollToEnd(binding.rcTranslationContent);
                break;
            case DISPLAY_MODEL_BOTH:
                if (data.asr != null) {
                    binding.rcAsrContent.setText(data.asr.msg);
                    scrollToEnd(binding.rcAsrContent);
                }
                if (data.tr != null) {
                    binding.rcTranslationContent.setText(data.tr.msg);
                    scrollToEnd(binding.rcTranslationContent);
                }
                break;
        }
        this.data = data;
    }

    private void updateUserInfo(ASRItemData data) {
        UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(data.userId);
        String userName = data.userId;
        Uri portrait = Uri.parse("");
        if (userInfo != null) {
            userName = userInfo.getName();
            portrait = userInfo.getPortraitUri();
        }
        binding.rcSubtitleUserName.setText(CallKitUtils.nickNameRestrict(userName));
        RongCallKit.getKitImageEngine()
                .loadPortrait(
                        getContext(),
                        portrait,
                        io.rong.imkit.R.drawable.rc_default_portrait,
                        binding.rcSubtitleUserPortrait);
    }

    private void scrollToEnd(TextView view) {
        Layout layout = view.getLayout();
        if (layout == null) {
            return;
        }
        int scroll = layout.getLineTop(view.getLineCount()) - view.getHeight();
        //        Log.d(TAG, "scrollToEnd: "+scroll);
        if (scroll > 0) {
            view.scrollTo(view.getScrollX(), scroll);
        } else {
            view.scrollTo(view.getScrollX(), 0);
        }
    }

    public ASRItemData getData() {
        return data;
    }

    public String getUserId() {
        return data != null ? data.userId : "";
    }

    public long getUpdateTime() {
        return data != null ? data.getUpdateTime() : 0;
    }

    static class ASRItemData<T extends RCRTCASRContent> {
        private static final String TAG = "ASRItemData";
        public final String userId;
        private long updateTime;
        private RCRTCASRContent asr;
        private RCRTCRealtimeTranslationContent tr;
        private final RongASRItemView view;

        private void updateView() {
            view.updateView(this);
        }

        public ASRItemData(RongASRItemView view, T asr) {
            if (asr instanceof RCRTCRealtimeTranslationContent) {
                tr = (RCRTCRealtimeTranslationContent) asr;
            } else {
                this.asr = asr;
            }
            this.userId = asr.userID;
            this.view = view;
            this.updateTime = asr.timeUTC;
            updateView();
        }

        public void update(T asr) {
            if (!TextUtils.equals(this.userId, asr.userID)) {
                Log.d(
                        TAG,
                        "asr update: userId mismatch, expected: "
                                + this.userId
                                + ", actual: "
                                + asr.userID);
                return;
            }
            if (asr instanceof RCRTCRealtimeTranslationContent) {
                tr = (RCRTCRealtimeTranslationContent) asr;
            } else {
                this.asr = asr;
            }
            this.updateTime = asr.timeUTC;
            updateView();
        }

        public long getUpdateTime() {
            return updateTime;
        }

        public RongASRItemView getView() {
            return view;
        }
    }
}
