package io.rong.callkit;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.AttributeSet;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.RelativeLayout;
import cn.rongcloud.rtc.api.stream.RCRTCVideoView;
import cn.rongcloud.rtc.utils.FinLog;

/** Created by Administrator on 2017/3/30. */
public class ContainerLayout extends RelativeLayout {
    private final String TAG = ContainerLayout.class.getSimpleName();
    private Context context;
    private static boolean isNeedFillScrren = true;
    SurfaceView currentView;

    public ContainerLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        this.context = context;
    }

    public void addView(final SurfaceView videoView) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        this.screenWidth = wm.getDefaultDisplay().getWidth();
        this.screenHeight = wm.getDefaultDisplay().getHeight();
        FinLog.d(
                TAG,
                "---xx-- add view "
                        + videoView.toString()
                        + " Height: "
                        + ((RCRTCVideoView) videoView).rotatedFrameHeight
                        + " Width: "
                        + ((RCRTCVideoView) videoView).rotatedFrameWidth);
        super.addView(videoView, getBigContainerParams((RCRTCVideoView) videoView));
        currentView = videoView;
        ((RCRTCVideoView) videoView)
                .setOnSizeChangedListener(
                        new RCRTCVideoView.OnSizeChangedListener() {
                            @Override
                            public void onChanged(RCRTCVideoView.Size size) {
                                try {
                                    ContainerLayout.this.removeAllViews();
                                    FinLog.d(
                                            TAG,
                                            "---xx-- change view "
                                                    + videoView.toString()
                                                    + " Height: "
                                                    + ((RCRTCVideoView) videoView)
                                                            .rotatedFrameHeight
                                                    + " Width: "
                                                    + ((RCRTCVideoView) videoView)
                                                            .rotatedFrameWidth);
                                    ContainerLayout.this.addView(
                                            videoView,
                                            getBigContainerParams((RCRTCVideoView) videoView));
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }
                        });
    }

    @NonNull
    private LayoutParams getBigContainerParams(RCRTCVideoView videoView) {
        LayoutParams layoutParams = null;
        if (!isNeedFillScrren) {
            if (screenHeight > screenWidth) { // V
                int layoutParamsHeight =
                        (videoView.rotatedFrameHeight == 0 || videoView.rotatedFrameWidth == 0)
                                ? ViewGroup.LayoutParams.WRAP_CONTENT
                                : screenWidth
                                        * videoView.rotatedFrameHeight
                                        / videoView.rotatedFrameWidth;
                layoutParams = new LayoutParams(screenWidth, layoutParamsHeight);
            } else {
                int layoutParamsWidth =
                        (videoView.rotatedFrameWidth == 0 || videoView.rotatedFrameHeight == 0)
                                ? ViewGroup.LayoutParams.WRAP_CONTENT
                                : (screenWidth
                                                        * videoView.rotatedFrameWidth
                                                        / videoView.rotatedFrameHeight
                                                > screenWidth
                                        ? screenWidth
                                        : screenHeight
                                                * videoView.rotatedFrameWidth
                                                / videoView.rotatedFrameHeight);
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

    @Override
    public void removeAllViews() {
        if (currentView != null) ((RCRTCVideoView) currentView).setOnSizeChangedListener(null);
        super.removeAllViews();
    }

    private int screenWidth;
    private int screenHeight;
}
