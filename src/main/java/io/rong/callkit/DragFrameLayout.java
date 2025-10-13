package io.rong.callkit;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.customview.widget.ViewDragHelper;

/** Created by RongCloud on 2025/8/12. */
public class DragFrameLayout extends FrameLayout {

    private static final String TAG = "DragFrameLayout";

    // ViewDragHelper相关
    private ViewDragHelper mDragHelper;
    private View mDragView;

    public DragFrameLayout(@NonNull Context context) {
        super(context);
    }

    public DragFrameLayout(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
    }

    public DragFrameLayout(
            @NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public DragFrameLayout(
            @NonNull Context context,
            @Nullable AttributeSet attrs,
            int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public void setDragView(View view) {
        mDragView = view;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        //        Log.d(TAG, "onLayout: changed="+changed+" , left="+left+" , top="+top
        //            + " , right="+right+" , bottom="+bottom);
        super.onLayout(changed, left, top, right, bottom);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // 初始化ViewDragHelper
        mDragHelper =
                ViewDragHelper.create(
                        this,
                        1.0f,
                        new ViewDragHelper.Callback() {
                            @Override
                            public boolean tryCaptureView(@NonNull View child, int pointerId) {
                                if (mDragView == null || child == null) {
                                    return false;
                                }
                                return child == mDragView;
                            }

                            @Override
                            public void onViewPositionChanged(
                                    @NonNull View child, int left, int top, int dx, int dy) {
                                LayoutParams lp = (LayoutParams) child.getLayoutParams();
                                lp.topMargin = top - getPaddingTop();
                                child.setLayoutParams(lp);
                            }

                            @Override
                            public int clampViewPositionVertical(View child, int top, int dy) {
                                return Math.max(
                                        getPaddingTop(),
                                        Math.min(
                                                top,
                                                getHeight()
                                                        - getPaddingTop()
                                                        - getPaddingBottom()
                                                        - mDragView.getMinimumHeight()));
                            }

                            @Override
                            public int clampViewPositionHorizontal(
                                    @NonNull View child, int left, int dx) {
                                return (int) child.getX();
                            }

                            @Override
                            public int getViewHorizontalDragRange(@NonNull View child) {
                                return getMeasuredWidth() - child.getMeasuredWidth();
                            }

                            @Override
                            public int getViewVerticalDragRange(@NonNull View child) {
                                return getMeasuredHeight() - child.getMeasuredHeight();
                            }
                        });
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (mDragView == null) {
            return false;
        }
        return mDragHelper.shouldInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (mDragView == null) {
            return false;
        }
        // 将触摸事件传递给ViewDragHelper
        mDragHelper.processTouchEvent(event);
        return true;
    }
}
