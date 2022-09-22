package io.rong.callkit.util;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import androidx.annotation.NonNull;
import androidx.core.app.AppOpsManagerCompat;
import io.rong.callkit.util.permission.PermissionShowDetail;
import io.rong.callkit.util.permission.PermissionType;
import io.rong.calllib.RongCallCommon;
import io.rong.common.RLog;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** @author gusd @Date 2021/09/17 */
public class RongCallPermissionUtil {
    private static final String TAG = "RongCallPermissionUtil";

    public static void requestPermissions(
            Activity activity, PermissionType[] permissionTypes, int requestCode) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }
        final List<String> permissionsNotGranted = new ArrayList<>();
        for (PermissionType audioCallPermission : permissionTypes) {
            if (!audioCallPermission.checkPermission(activity)) {
                permissionsNotGranted.add(audioCallPermission.getPermissionName());
            }
        }
        if (!permissionsNotGranted.isEmpty()) {
            activity.requestPermissions(permissionsNotGranted.toArray(new String[0]), requestCode);
        }
    }

    ///////////////////////////////////////////////////////////////////////////
    // 音频通话权限相关
    ///////////////////////////////////////////////////////////////////////////

    /**
     * 请求音频通话所需权限
     *
     * @param activity
     */
    public static void requestAudioCallNeedPermission(Activity activity, final int requestCode) {
        requestPermissions(activity, getAudioCallPermissions(activity), requestCode);
    }

    /**
     * 获取音频通话所需权限
     *
     * @return
     */
    public static PermissionType[] getAudioCallPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && context.getApplicationInfo() != null
                && context.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.S) {
            return new PermissionType[] {
                PermissionType.AudioRecord,
                PermissionType.BluetoothConnect,
                PermissionType.BluetoothScan,
                PermissionType.BluetoothAdvertise
            };
        } else {
            return new PermissionType[] {PermissionType.AudioRecord};
        }
    }

    public static boolean checkAudioCallNeedPermission(Context context) {
        for (PermissionType audioCallPermission : getAudioCallPermissions(context)) {
            if (!audioCallPermission.checkPermission(context)) {
                return false;
            }
        }
        return true;
    }

    public static boolean checkAndRequestAudioCallPermission(
            Activity activity, final int requestCode) {
        boolean granted = checkAudioCallNeedPermission(activity);
        if (!granted) {
            requestAudioCallNeedPermission(activity, requestCode);
        }
        return granted;
    }

    ///////////////////////////////////////////////////////////////////////////
    // 视频通话权限相关
    ///////////////////////////////////////////////////////////////////////////
    public static boolean checkVideoCallNeedPermission(Context context) {
        for (PermissionType audioCallPermission : getVideoCallPermissions(context)) {
            if (!audioCallPermission.checkPermission(context)) {
                return false;
            }
        }
        return true;
    }

    public static void requestVideoCallNeedPermission(Activity activity, final int requestCode) {
        requestPermissions(activity, getVideoCallPermissions(activity), requestCode);
    }

    public static boolean checkAndRequestVideoCallPermission(
            Activity activity, final int requestCode) {
        boolean granted = checkVideoCallNeedPermission(activity);
        if (!granted) {
            requestVideoCallNeedPermission(activity, requestCode);
        }
        return granted;
    }

    /**
     * 获取视频通话所需必要权限
     *
     * @return
     */
    public static PermissionType[] getVideoCallPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && context.getApplicationInfo() != null
                && context.getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.S) {
            return new PermissionType[] {
                PermissionType.CameraPermission,
                PermissionType.AudioRecord,
                PermissionType.BluetoothConnect,
                PermissionType.BluetoothScan,
                PermissionType.BluetoothAdvertise,
            };
        } else {
            return new PermissionType[] {
                PermissionType.CameraPermission, PermissionType.AudioRecord
            };
        }
    }

    public static boolean checkAndRequestPermissionByCallType(
            Activity activity, RongCallCommon.CallMediaType type, final int requestCode) {
        if (RongCallCommon.CallMediaType.VIDEO.equals(type)) {
            return checkAndRequestVideoCallPermission(activity, requestCode);
        } else if (RongCallCommon.CallMediaType.AUDIO.equals(type)) {
            return checkAndRequestAudioCallPermission(activity, requestCode);
        }
        return false;
    }

    public static boolean checkPermissionByType(
            Context context, RongCallCommon.CallMediaType type) {
        if (RongCallCommon.CallMediaType.VIDEO.equals(type)) {
            return checkVideoCallNeedPermission(context);
        } else if (RongCallCommon.CallMediaType.AUDIO.equals(type)) {
            return checkAudioCallNeedPermission(context);
        }
        return false;
    }

    ///////////////////////////////////////////////////////////////////////////
    // 悬浮窗相关
    ///////////////////////////////////////////////////////////////////////////

    public static void requestFloatWindowNeedPermission(
            final Context context, final DialogInterface.OnClickListener listener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> permissionList = new ArrayList<>();
            permissionList.add(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
            showPermissionAlert(
                    context,
                    context.getString(io.rong.imkit.R.string.rc_permission_grant_needed)
                            + getNotGrantedPermissionMsg(context, permissionList),
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            if (listener != null) {
                                listener.onClick(dialog, which);
                            }
                            if (DialogInterface.BUTTON_POSITIVE == which) {
                                Intent intent =
                                        new Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:" + context.getPackageName()));
                                if (intent.resolveActivity(context.getPackageManager()) != null) {
                                    context.startActivity(intent);
                                }
                            }
                        }
                    });
        }
    }

    private static String getNotGrantedPermissionMsg(Context context, List<String> permissions) {
        if (permissions == null || permissions.size() == 0) {
            return "";
        }
        Set<String> permissionsValue = new HashSet<>();
        String permissionValue;
        try {
            for (String permission : permissions) {
                permissionValue =
                        context.getString(
                                context.getResources()
                                        .getIdentifier(
                                                "rc_" + permission,
                                                "string",
                                                context.getPackageName()),
                                0);
                permissionsValue.add(permissionValue);
            }
        } catch (Resources.NotFoundException e) {
            RLog.e(
                    TAG,
                    "one of the permissions is not recognized by SDK." + permissions.toString());
            return "";
        }

        StringBuilder result = new StringBuilder("(");
        for (String value : permissionsValue) {
            result.append(value).append(" ");
        }
        result = new StringBuilder(result.toString().trim() + ")");
        return result.toString();
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private static void showPermissionAlert(
            Context context, String content, DialogInterface.OnClickListener listener) {
        new AlertDialog.Builder(context)
                .setMessage(content)
                .setPositiveButton(io.rong.imkit.R.string.rc_confirm, listener)
                .setNegativeButton(io.rong.imkit.R.string.rc_cancel, listener)
                .setNeutralButton(io.rong.imkit.R.string.rc_not_prompt, listener)
                .setCancelable(false)
                .create()
                .show();
    }

    public static boolean checkFloatWindowPermission(Context context) {
        return PermissionType.FloatWindow.checkPermission(context);
    }

    public static void showRequestPermissionFailedAlter(
            final Context context, @NonNull String[] permissions, @NonNull int[] grantResults) {
        final String content = getNotGrantedPermissionMsg(context, permissions, grantResults);
        if (TextUtils.isEmpty(content)) {
            return;
        }
        DialogInterface.OnClickListener listener =
                new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case DialogInterface.BUTTON_POSITIVE:
                                PermissionShowDetail.showPermissionDetail(context);
                                break;
                            case DialogInterface.BUTTON_NEGATIVE:
                            default:
                                break;
                        }
                    }
                };
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            new AlertDialog.Builder(context, android.R.style.Theme_Material_Light_Dialog_Alert)
                    .setMessage(content)
                    .setPositiveButton(io.rong.imkit.R.string.rc_confirm, listener)
                    .setNegativeButton(io.rong.imkit.R.string.rc_cancel, listener)
                    .setCancelable(false)
                    .create()
                    .show();
        } else {
            new AlertDialog.Builder(context)
                    .setMessage(content)
                    .setPositiveButton(io.rong.imkit.R.string.rc_confirm, listener)
                    .setNegativeButton(io.rong.imkit.R.string.rc_cancel, listener)
                    .setCancelable(false)
                    .create()
                    .show();
        }
    }

    private static String getNotGrantedPermissionMsg(
            Context context, String[] permissions, int[] grantResults) {
        if (permissions == null || permissions.length == 0) {
            return "";
        }
        try {
            List<String> permissionNameList = new ArrayList<>(permissions.length);
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    String permissionName =
                            context.getString(
                                    context.getResources()
                                            .getIdentifier(
                                                    "rc_" + permissions[i],
                                                    "string",
                                                    context.getPackageName()),
                                    0);
                    if (!permissionNameList.contains(permissionName)) {
                        permissionNameList.add(permissionName);
                    }
                }
            }

            StringBuilder builder =
                    new StringBuilder(
                            context.getResources()
                                    .getString(io.rong.imkit.R.string.rc_permission_grant_needed));
            return builder.append("(")
                    .append(TextUtils.join(" ", permissionNameList))
                    .append(")")
                    .toString();
        } catch (Resources.NotFoundException e) {
            RLog.e(
                    TAG,
                    "One of the permissions is not recognized by SDK."
                            + Arrays.toString(permissions));
        }

        return "";
    }

    public static boolean checkPermissions(Context context, @NonNull String[] permissions) {
        if (permissions.length == 0) {
            return true;
        }
        for (String permission : permissions) {
            PermissionType permissionType = PermissionType.gerPermissionByName(permission);
            if (permissionType != null) {
                if (!permissionType.checkPermission(context)) {
                    return false;
                }
            } else {
                boolean result = hasPermission(context, permission);
                if (!result) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean hasPermission(Context context, String permission) {
        String opStr = AppOpsManagerCompat.permissionToOp(permission);
        if (opStr == null && Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return context != null
                && context.checkCallingOrSelfPermission(permission)
                        == PackageManager.PERMISSION_GRANTED;
    }
}
