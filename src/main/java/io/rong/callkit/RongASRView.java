package io.rong.callkit;

import android.content.Context;
import android.graphics.Paint.FontMetrics;
import android.net.Uri;
import android.text.Layout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import cn.rongcloud.rtc.api.RCRTCASRContent;
import cn.rongcloud.rtc.api.callback.IRCRTCResultCallback;
import cn.rongcloud.rtc.base.RTCErrorCode;
import io.rong.callkit.util.CallKitUtils;
import io.rong.calllib.IRongCallASRListener;
import io.rong.calllib.RongCallClient;
import io.rong.imkit.userinfo.RongUserInfoManager;
import io.rong.imlib.model.UserInfo;

/** Created by RongCloud on 2025/8/12. */
public class RongASRView extends FrameLayout implements IRongCallASRListener {

    public static final float MAX_HEIGHT = 150;
    public static final int MAX_LINE_COUNT = 2;
    private final String TAG = "RongASRView";

    private ISubtitleViewCallback callback;
    private TextView mTextview;
    private TextView mUserName;
    private ImageView mUserPortrait;

    private RCRTCASRContent mLastSubtitle;

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
        inflate(getContext(), R.layout.rc_voip_subtitle_view, this);
        findViewById(R.id.rc_subtitle_close)
                .setOnClickListener(
                        v -> {
                            this.setVisibility(GONE);
                        });
        mTextview = findViewById(R.id.rc_subtitle_content);
        mUserName = findViewById(R.id.rc_subtitle_user_name);
        mUserPortrait = findViewById(R.id.rc_subtitle_user_portrait);
        FontMetrics fontMetrics = mTextview.getPaint().getFontMetrics();
        float lineHeight =
                (fontMetrics.descent - fontMetrics.ascent + mTextview.getLineSpacingExtra())
                        * mTextview.getLineSpacingMultiplier();
        ViewGroup.LayoutParams lp = mTextview.getLayoutParams();
        lp.height = (int) (lineHeight * MAX_LINE_COUNT);
        mTextview.setLayoutParams(lp);
        RongCallClient.getInstance().setASRListener(this);
    }

    public void updateSubtitle(RCRTCASRContent subtitle) {
        if (subtitle == null) {
            return;
        }
        mLastSubtitle = subtitle;
        UserInfo userInfo = RongUserInfoManager.getInstance().getUserInfo(subtitle.userID);
        String userName = subtitle.userID;
        Uri portrait = Uri.parse("");
        if (userInfo != null) {
            userName = userInfo.getName();
            portrait = userInfo.getPortraitUri();
        }
        mUserName.setText(CallKitUtils.nickNameRestrict(userName));
        RongCallKit.getKitImageEngine()
                .loadPortrait(
                        getContext(),
                        portrait,
                        io.rong.imkit.R.drawable.rc_default_portrait,
                        mUserPortrait);
        mTextview.setText(subtitle.msg);
        scrollToEnd();
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != VISIBLE) {
            ISubtitleViewCallback callback1 = callback;
            if (callback1 != null) {
                callback1.onClose();
            }
        }
    }

    private void scrollToEnd() {
        Layout layout = mTextview.getLayout();
        if (layout == null) {
            return;
        }
        int scroll = layout.getLineTop(mTextview.getLineCount()) - mTextview.getHeight();
        //        Log.d(TAG, "scrollToEnd: "+scroll);
        if (scroll > 0) {
            mTextview.scrollTo(mTextview.getScrollX(), scroll);
        } else {
            mTextview.scrollTo(mTextview.getScrollX(), 0);
        }
    }

    public void setSubtitleViewCallback(ISubtitleViewCallback callback) {
        this.callback = callback;
    }

    @Override
    public void onReceiveASRContent(RCRTCASRContent realTimeSubtitle) {
        Log.d(TAG, "onReceiveRealTimeSubtitle: " + realTimeSubtitle.msg);
        post(
                () -> {
                    if (!isAttachedToWindow()) {
                        return;
                    }
                    updateSubtitle(realTimeSubtitle);
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
            if (mLastSubtitle != null) {
                updateSubtitle(mLastSubtitle);
            }
        }
    }

    public void destroy() {
        RongCallClient.getInstance().setASRListener(null);
    }

    public interface ISubtitleViewCallback {
        void onClose();
    }
}
