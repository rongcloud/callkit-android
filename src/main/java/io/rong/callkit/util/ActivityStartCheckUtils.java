package io.rong.callkit.util;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;

public class ActivityStartCheckUtils {
    public interface ActivityStartResultCallback {
        void onStartActivityResult(boolean isActivityStarted);
    }

    private static final int TIME_DELAY = 1000;
    private static ActivityStartCheckUtils sInstance;
    private boolean mPostDelayIsRunning;
    private String mClassName;
    private Handler mHandler = new Handler();
    private Activity topActivity;
    private Context mAppContext;
    private ActivityStartResultCallback activityStartResultCallback;

    private ActivityStartCheckUtils() {
    }

    public static ActivityStartCheckUtils getInstance() {
        if (sInstance == null) {
            synchronized (ActivityStartCheckUtils.class) {
                if (sInstance == null) {
                    sInstance = new ActivityStartCheckUtils();
                }
            }
        }
        return sInstance;
    }

    public void registerActivityLifecycleCallbacks(Context context) {

        mAppContext = context.getApplicationContext();
        Application application = (Application) mAppContext;

        if (application == null)
            return;

        application.registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle savedInstanceState) {

            }

            @Override
            public void onActivityStarted(Activity activity) {

            }

            @Override
            public void onActivityResumed(Activity activity) {
                topActivity = activity;
            }

            @Override
            public void onActivityPaused(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {
                if (topActivity == activity) {
                    topActivity = null;
                }
            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle outState) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        });
    }

    public String getTopActivity() {
        if (topActivity == null) {
            return null;
        }

        return topActivity.getClass().getSuperclass().getSimpleName();
    }

    public void startActivity(Context context, Intent intent, String className, ActivityStartResultCallback callback) {
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

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            mPostDelayIsRunning = false;
            boolean isOnTop = isActivityOnTop();
            if (activityStartResultCallback != null) {
                activityStartResultCallback.onStartActivityResult(isOnTop);
            }
        }
    };
}