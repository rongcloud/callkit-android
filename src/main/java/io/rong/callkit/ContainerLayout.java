package io.rong.callkit;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import androidx.annotation.NonNull;
import cn.rongcloud.rtc.api.stream.view.RCRTCBaseView;
import cn.rongcloud.rtc.utils.FinLog;

/** Created by Administrator on 2017/3/30. */
public class ContainerLayout extends RelativeLayout {
    private final String TAG = ContainerLayout.class.getSimpleName();
    private Context context;
    private static boolean isNeedFillScrren = true;
    View currentView;

    public ContainerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    @Override
    public void addView(final View videoView) {
        if (!(videoView instanceof RCRTCBaseView)) {
            super.addView(videoView);
            return;
        }
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.screenWidth = wm.getDefaultDisplay().getWidth();
        this.screenHeight = wm.getDefaultDisplay().getHeight();
        FinLog.d(
                TAG,
                "---xx-- add view "
                        + videoView.toString()
                        + " Height: "
                        + ((RCRTCBaseView) videoView).getRotatedFrameHeight()
                        + " Width: "
                        + ((RCRTCBaseView) videoView).getRotatedFrameWidth());
        super.addView(videoView, getBigContainerParams((RCRTCBaseView) videoView));
        currentView = videoView;
    }

    @NonNull
    private LayoutParams getBigContainerParams(RCRTCBaseView videoView) {
        LayoutParams layoutParams = null;
        if (!isNeedFillScrren) {
            if (screenHeight > screenWidth) { // V
                int layoutParamsHeight =
                        (videoView.getRotatedFrameHeight() == 0
                                        || videoView.getRotatedFrameWidth() == 0)
                                ? ViewGroup.LayoutParams.WRAP_CONTENT
                                : screenWidth
                                        * videoView.getRotatedFrameHeight()
                                        / videoView.getRotatedFrameWidth();
                layoutParams = new LayoutParams(screenWidth, layoutParamsHeight);
            } else {
                int layoutParamsWidth =
                        (videoView.getRotatedFrameWidth() == 0
                                        || videoView.getRotatedFrameHeight() == 0)
                                ? ViewGroup.LayoutParams.WRAP_CONTENT
                                : (screenWidth
                                                        * videoView.getRotatedFrameWidth()
                                                        / videoView.getRotatedFrameHeight()
                                                > screenWidth
                                        ? screenWidth
                                        : screenHeight
                                                * videoView.getRotatedFrameWidth()
                                                / videoView.getRotatedFrameHeight());
                layoutParams = new LayoutParams(layoutParamsWidth, screenHeight);
            }
        } else {
            layoutParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        }
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        return layoutParams;
    }

    public void setIsNeedFillScrren(boolean isNeed) {
        isNeedFillScrren = isNeed;
    }

    private int screenWidth;
    private int screenHeight;
}
