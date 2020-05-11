package io.rong.callkit.util;

import android.content.Context;

import io.rong.callkit.CallFloatBoxView;
import io.rong.calllib.RongCallSession;
import io.rong.push.notification.RongNotificationInterface;

/**
 * 适配 Android 10 以上不允许后台启动 Activity 的工具类
 */
public class IncomingCallExtraHandleUtil {
    public final static int VOIP_NOTIFICATION_ID = 3000; //VoIP类型的通知消息。
    public final static int VOIP_REQUEST_CODE = 30001;

    private static RongCallSession cachedCallSession = null;
    private static boolean checkPermissions = false;

    public static void removeNotification(Context context) {
        RongNotificationInterface.removeNotification(context, VOIP_NOTIFICATION_ID);
    }

    public static RongCallSession getCallSession() {
        return cachedCallSession;
    }

    public static void cacheCallSession(RongCallSession callSession, boolean permissions) {
        cachedCallSession = callSession;
        checkPermissions = permissions;
    }

    public static boolean isCheckPermissions() {
        return checkPermissions;
    }

    public static void clear() {
        cachedCallSession = null;
        checkPermissions = false;
    }

    public static boolean needNotify() {
        return cachedCallSession != null && !CallFloatBoxView.isCallFloatBoxShown();
    }
}
