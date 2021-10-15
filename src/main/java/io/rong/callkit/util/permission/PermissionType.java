package io.rong.callkit.util.permission;


import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.AppOpsManagerCompat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import io.rong.common.RLog;

/**
 * @author gusd
 * @Date 2021/09/17
 * 别在多线程里面用
 */
public enum PermissionType {

    // 摄像头权限
    CameraPermission(Manifest.permission.CAMERA) {
        @Override
        public boolean checkPermission(Context context) {
            return super.checkPermission(context);
        }
    },
    // 录音权限
    AudioRecord(Manifest.permission.RECORD_AUDIO) {
        public boolean checkPermission(Context context) {
            return DeviceAdapter.getDeviceAdapter().checkRecordPermission(context);
        }
    },
    // 悬浮窗
    FloatWindow("android.settings.action.MANAGE_OVERLAY_PERMISSION") {
        private static final String TAG = "FloatWindow";
        @Override
        public boolean checkPermission(final Context context) {
            boolean result = true;
            boolean booleanValue;
            if (Build.VERSION.SDK_INT >= 23) {
                try {
                    booleanValue = (Boolean) Settings.class.getDeclaredMethod("canDrawOverlays", Context.class).invoke(null, new Object[]{context});
                    RLog.i(TAG, "isFloatWindowOpAllowed allowed: " + booleanValue);
                    return booleanValue;
                } catch (Exception e) {
                    RLog.e(TAG, String.format("getDeclaredMethod:canDrawOverlays! Error:%s, etype:%s", e.getMessage(), e.getClass().getCanonicalName()));
                    return true;
                }
            } else if (Build.VERSION.SDK_INT < 19) {
                return true;
            } else if(Build.BRAND.toLowerCase().contains("xiaomi")){
                Method method;
                Object systemService = context.getSystemService(Context.APP_OPS_SERVICE);
                try {
                    method = Class.forName("android.app.AppOpsManager").getMethod("checkOp", Integer.TYPE, Integer.TYPE, String.class);
                } catch (NoSuchMethodException e) {
                    RLog.e(TAG, String.format("NoSuchMethodException method:checkOp! Error:%s", e.getMessage()));
                    method = null;
                } catch (ClassNotFoundException e) {
                    RLog.e(TAG, "canDrawOverlays", e);
                    method = null;
                }
                if (method != null) {
                    try {
                        Integer tmp = (Integer) method.invoke(systemService, new Object[]{24, context.getApplicationInfo().uid, context.getPackageName()});
                        result = tmp != null && tmp == 0;
                    } catch (Exception e) {
                        RLog.e(TAG, String.format("call checkOp failed: %s etype:%s", e.getMessage(), e.getClass().getCanonicalName()));
                    }
                }
                RLog.i(TAG, "isFloatWindowOpAllowed allowed: " + result);
                return result;
            }
            return true;
        }

        @Override
        public void gotoSettingPage(Context context) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            }
        }
    },
    // 悬浮通知
    FloatNotification("FloatNotificationPermission") {
        @Override
        @Deprecated
        public boolean checkPermission(Context context) {
            return false;
        }

        public boolean checkPermission(Context context, String channelId) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return false;
            }
            NotificationManager mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = mNotificationManager.getNotificationChannel(channelId);//CHANNEL_ID是自己定义的渠道ID
            return channel.getImportance() == NotificationManager.IMPORTANCE_HIGH;
        }

        @Deprecated
        @Override
        public void gotoSettingPage(Context context) {
            super.gotoSettingPage(context);
        }

        public void gotoSettingPage(Context context, String channelId) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                return;
            }
            Intent intent = new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS);
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
            intent.putExtra(Settings.EXTRA_CHANNEL_ID, channelId);
            context.startActivity(intent);
        }
    },
    // 通知锁屏显示
    DisplayLockScreen("DisplayLockScreen") {
        @Override
        public boolean checkPermission(Context context) {
            return DeviceAdapter.getDeviceAdapter().checkLockScreenDisplayPermission(context);
        }

        @Override
        public void gotoSettingPage(Context context) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                Intent intent = new Intent();
                intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                intent.putExtra("app_package", context.getPackageName());
                intent.putExtra("app_uid", context.getApplicationInfo().uid);
                context.startActivity(intent);
            } else if (android.os.Build.VERSION.SDK_INT == Build.VERSION_CODES.KITKAT) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.setData(Uri.parse("package:" + context.getPackageName()));
                context.startActivity(intent);
            }
        }
    };


    private static final Map<String, PermissionType> permissionMap = new HashMap<>();

    static {
        for (PermissionType value : PermissionType.values()) {
            permissionMap.put(value.mPermissionName, value);
        }
    }

    @Nullable
    public static PermissionType gerPermissionByName(String permissionName) {
        return permissionMap.get(permissionName);
    }


    private final String mPermissionName;
    // 是否为必要权限
    private boolean isNecessary;

    PermissionType(@NonNull String permissionName) {
        this.mPermissionName = permissionName;
    }

    @NonNull
    public String getPermissionName() {
        return mPermissionName;
    }

    /**
     * 跳转到对应的设置页面
     */
    public void gotoSettingPage(Context context) {
        // 默认跳转到权限设置界面
        DeviceAdapter.getDeviceAdapter().gotoAppPermissionSettingPage(context);
    }

    public boolean checkPermission(Context context) {
        String opStr = AppOpsManagerCompat.permissionToOp(mPermissionName);
        if (opStr == null && Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return context != null && context.checkCallingOrSelfPermission(mPermissionName) == PackageManager.PERMISSION_GRANTED;
    }

    public boolean isNecessary() {
        return isNecessary;
    }

    public void setNecessary(boolean isNecessary) {
        this.isNecessary = isNecessary;
    }


}
