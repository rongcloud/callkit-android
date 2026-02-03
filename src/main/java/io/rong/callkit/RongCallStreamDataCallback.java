package io.rong.callkit;

import android.util.Log;
import cn.rongcloud.rtc.api.callback.IRCRTCStreamDataCallback;
import cn.rongcloud.rtc.base.RTCErrorCode;

/** Created by RongCloud on 2025/12/15. */
public abstract class RongCallStreamDataCallback implements IRCRTCStreamDataCallback<String> {

    static final String TAG = "StreamDataCallbackImpl";
    boolean isReleased = false;
    boolean isCompleted = false;
    RTCErrorCode errorCode = null;
    private IRCRTCStreamDataCallback<String> callback;
    public final String taskId;

    public RongCallStreamDataCallback(String taskId) {
        this.callback = null;
        this.taskId = taskId;
    }

    public RongCallStreamDataCallback(String taskId, IRCRTCStreamDataCallback<String> callback) {
        this.callback = callback;
        this.taskId = taskId;
    }

    public void setCallback(IRCRTCStreamDataCallback<String> callback) {
        this.callback = callback;
        if (errorCode != null) {
            callback.onFailed(errorCode);
        } else if (isCompleted) {
            callback.onComplete();
        }
    }

    public final void release() {
        Log.d(TAG, "[" + this.hashCode() + "]StreamDataCallbackImpl release");
        isReleased = true;
    }

    @Override
    public final void onComplete() {
        Log.d(TAG, "[" + this.hashCode() + "]onComplete");
        this.isCompleted = true;
        if (callback != null) {
            callback.onComplete();
        }
        if (!isReleased) {
            onDataComplete();
        }
    }

    protected abstract void onDataComplete();

    @Override
    public final void onDataReceived(String data) {
        Log.d(TAG, "[" + this.hashCode() + "]onDataReceived: " + data);
        if (callback != null) {
            callback.onDataReceived(data);
        }
        if (!isReleased) {
            onData(data);
        }
    }

    protected abstract void onData(String data);

    @Override
    public final void onFailed(RTCErrorCode errorCode) {
        Log.d(TAG, "[" + this.hashCode() + "]onFailed: " + errorCode);
        this.errorCode = errorCode;
        if (callback != null) {
            callback.onFailed(errorCode);
        }
        if (!isReleased) {
            onError(errorCode);
        }
    }

    protected abstract void onError(RTCErrorCode errorCode);
}
