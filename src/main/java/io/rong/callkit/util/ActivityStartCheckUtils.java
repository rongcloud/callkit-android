package io.rong.callkit.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;

import io.rong.callkit.RongCallModule;

public class ActivityStartCheckUtils {
    public interface ActivityStartResultCallback {
        void onStartActivityResult(boolean isActivityStarted);
    }

    private static final int TIME_DELAY = 3000;
    private boolean mPostDelayIsRunning;
    private String mClassName;
    private Handler mHandler = new Handler();
    private Activity topActivity;
    private Context mAppContext;
    private ActivityStartResultCallback activityStartResultCallback;

    private static class SingletonHolder {

      static ActivityStartCheckUtils sInstance = new ActivityStartCheckUtils();
    }

    private ActivityStartCheckUtils() {}

    public static ActivityStartCheckUtils getInstance() {
     return SingletonHolder.sInstance;
    }

    public void registerActivityLifecycleCallbacks(Context context) {

        mAppContext = context.getApplicationContext();
        Application application = (Application) mAppContext;

        if (application == null) return;

        application.registerActivityLifecycleCallbacks(
                new Application.ActivityLifecycleCallbacks() {
                    @Override
                    public void onActivityCreated(Activity activity, Bundle savedInstanceState) {}

                    @Override
                    public void onActivityStarted(Activity activity) {}

                    @Override
                    public void onActivityResumed(Activity activity) {
                        topActivity = activity;
                        handleIncomingCallNotify();
                    }

                    @Override
                    public void onActivityPaused(Activity activity) {}

                    @Override
                    public void onActivityStopped(Activity activity) {
                        if (topActivity == activity) {
                            topActivity = null;
                        }
                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {}

                    @Override
                    public void onActivityDestroyed(Activity activity) {}
                });
    }

    public String getTopActivity() {
        if (topActivity == null) {
            return null;
        }

        return topActivity.getClass().getSuperclass().getSimpleName();
    }

    public void startActivity(
            Context context,
            Intent intent,
            String className,
            ActivityStartResultCallback callback) {
        if (context == null || intent == null || TextUtils.isEmpty(className)) {
            return;
        }
        context.startActivity(intent);
        mClassName = className;
        if (mPostDelayIsRunning) {
            mHandler.removeCallbacks(mRunnable);
        }
        mPostDelayIsRunning = true;
        activityStartResultCallback = callback;
        mHandler.postDelayed(mRunnable, TIME_DELAY);
    }

    private boolean isActivityOnTop() {
        boolean result = false;
        String topActivityName = getTopActivity();
        if (!TextUtils.isEmpty(topActivityName)) {
            if (topActivityName.contains(mClassName)) {
                result = true;
            }
        }
        return result;
    }

    private Runnable mRunnable =
            new Runnable() {
                @Override
                public void run() {
                    mPostDelayIsRunning = false;
                    boolean isOnTop = isActivityOnTop();
                    if (activityStartResultCallback != null) {
                        activityStartResultCallback.onStartActivityResult(isOnTop);
                    }
                }
            };

    /**
     * Android 10 以上禁止后台启动 Activity
     * callKit 适配方案是后台来电时弹通知栏通知，但是如果用户不点击通知栏，
     * 通过桌面图标打开应用，需要增加一种补偿机制启动来电界面
     */
    private void handleIncomingCallNotify() {
        if (mAppContext != null && IncomingCallExtraHandleUtil.needNotify()) {
            IncomingCallExtraHandleUtil.removeNotification(mAppContext);
            mAppContext.startActivity(RongCallModule.createVoIPIntent(mAppContext,
                    IncomingCallExtraHandleUtil.getCallSession(),
                    IncomingCallExtraHandleUtil.isCheckPermissions()));
            IncomingCallExtraHandleUtil.clear();
        }
    }
}
