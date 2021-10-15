package io.rong.callkit.util.permission;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import androidx.core.app.AppOpsManagerCompat;

import java.lang.reflect.Method;

import io.rong.common.RLog;

/**
 * @author gusd
 * @Date 2021/09/17
 * @escription 处理系统之间的差异化问题
 */
public enum DeviceAdapter {
    defaultAdapter(),// 默认 adapter
    ///////////////////////////////////////////////////////////////////////////
    // 小米手机
    ///////////////////////////////////////////////////////////////////////////
    MiuiAdapter() {
        @Override
        public void gotoAppPermissionSettingPage(Context context) {
            try { // MIUI 8
                Intent localIntent = new Intent("miui.intent.action.APP_PERM_EDITOR");
                localIntent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity");
                localIntent.putExtra("extra_pkgname", context.getPackageName());
                context.startActivity(localIntent);
            } catch (Exception e) {
                try { // MIUI 5/6/7
                    Intent localIntent = new Intent("miui.intent.action.APP_PERM_EDITOR");
                    localIntent.setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.AppPermissionsEditorActivity");
                    localIntent.putExtra("extra_pkgname", context.getPackageName());
                    context.startActivity(localIntent);
                } catch (Exception e1) { // 否则跳转到应用详情
                    super.gotoAppPermissionSettingPage(context);
                }
            }
        }

        @Override
        public boolean checkLockScreenDisplayPermission(Context context) {
            AppOpsManager ops = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
                ops = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
            }
            try {
                int op = 10020; // >= 23
                // ops.checkOpNoThrow(op, uid, packageName)
                Method method = ops.getClass().getMethod("checkOpNoThrow", new Class[]{int.class, int.class, String.class});
                Integer result = (Integer) method.invoke(ops, op, android.os.Process.myUid(), context.getPackageName());

                return result == AppOpsManager.MODE_ALLOWED;

            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        }
    },
    ///////////////////////////////////////////////////////////////////////////
    // 魅族手机
    ///////////////////////////////////////////////////////////////////////////
    MeiZuAdapter() {
        private static final String TAG = "MeiZuAdapter";

        @Override
        public void gotoAppPermissionSettingPage(Context context) {
            try {
                Intent intent = new Intent("com.meizu.safe.security.SHOW_APPSEC");
                intent.addCategory(Intent.CATEGORY_DEFAULT);
                intent.putExtra("packageName", context.getApplicationInfo().processName);
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                super.gotoAppPermissionSettingPage(context);
            }
        }

        @Override
        public boolean checkRecordPermission(Context context) {
            return super.checkRecordPermission(context) || hasRecordPermision(context);
        }

        private boolean hasRecordPermision(Context context) {
            boolean hasPermission = false;
            int bufferSizeInBytes = AudioRecord.getMinBufferSize(44100,
                    AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT);
            if (bufferSizeInBytes < 0) {
                RLog.e(TAG, "bufferSizeInBytes = " + bufferSizeInBytes);
                return false;
            }
            AudioRecord audioRecord;
            try {
                audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, 44100,
                        AudioFormat.CHANNEL_IN_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSizeInBytes);
                audioRecord.startRecording();
                if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                    hasPermission = true;
                    audioRecord.stop();
                }
                audioRecord.release();
            } catch (Exception e) {
                RLog.e(TAG, "Audio record exception.");
            }
            return hasPermission;
        }
    },
    ///////////////////////////////////////////////////////////////////////////
    // 华为手机
    ///////////////////////////////////////////////////////////////////////////
    HuiWeiAdapter() {
        private static final String TAG = "HuiWeiAdapter";

        @Override
        public void gotoAppPermissionSettingPage(Context context) {
            try {
                Intent intent = new Intent();
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ComponentName comp = new ComponentName("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity");//华为权限管理
                intent.setComponent(comp);
                context.startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
                super.gotoAppPermissionSettingPage(context);
            }
        }
    },
    ///////////////////////////////////////////////////////////////////////////
    // oppo 手机
    ///////////////////////////////////////////////////////////////////////////
    OppoAdapter() {
        @Override
        public void gotoAppPermissionSettingPage(Context context) {
            try {
                Intent intent = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS");
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra("packageName", context.getApplicationInfo().processName);
                ComponentName comp = new ComponentName("com.coloros.securitypermission", "com.coloros.securitypermission.permission.PermissionAppAllPermissionActivity");//R11t 7.1.1 os-v3.2
                intent.setComponent(comp);
                context.startActivity(intent);
            } catch (Exception e) {
                super.gotoAppPermissionSettingPage(context);
            }
        }
    },
    ///////////////////////////////////////////////////////////////////////////
    // vivo 手机
    ///////////////////////////////////////////////////////////////////////////
    VivoAdapter() {
        @Override
        public boolean checkLockScreenDisplayPermission(Context context) {
            String packageName = context.getPackageName();
            Uri uri2 = Uri.parse("content://com.vivo.permissionmanager.provider.permission/control_locked_screen_action");
            String selection = "pkgname = ?";
            String[] selectionArgs = new String[]{packageName};
            try {
                Cursor cursor = context
                        .getContentResolver()
                        .query(uri2, null, selection, selectionArgs, null);
                if (cursor != null) {
                    if (cursor.moveToFirst()) {
                        int currentMode = cursor.getInt(cursor.getColumnIndex("currentstate"));
                        cursor.close();
                        return currentMode == 0;
                    } else {
                        cursor.close();
                        return false;
                    }
                }
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
            return false;
        }
    };

    private static final String TAG = "DeviceAdapter";

    public static DeviceAdapter getDeviceAdapter() {
        DeviceAdapter adapter = null;
        if (OSUtils.isEmui()) {
            adapter = DeviceAdapter.HuiWeiAdapter;
        } else if (OSUtils.isFlyme()) {
            adapter = DeviceAdapter.MeiZuAdapter;
        } else if (OSUtils.isMiui()) {
            adapter = DeviceAdapter.MiuiAdapter;
        } else if (OSUtils.isOppo()) {
            adapter = DeviceAdapter.OppoAdapter;
        } else if (OSUtils.isVivo()) {
            adapter = DeviceAdapter.VivoAdapter;
        } else {
            adapter = DeviceAdapter.defaultAdapter;
        }
        RLog.d(TAG, "current device adapter = " + adapter.getClass().getName());
        return adapter;
    }


    public void gotoAppPermissionSettingPage(Context context) {
        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", context.getPackageName(), null));
        context.startActivity(intent);
    }

    public boolean checkRecordPermission(Context context) {
        String opStr = AppOpsManagerCompat.permissionToOp(Manifest.permission.RECORD_AUDIO);
        if (opStr == null && Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return context != null && context.checkCallingOrSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    // 只有 小米 和 vivo 设备可检测，其他设备无法检测，默认返回 true
    public boolean checkLockScreenDisplayPermission(Context context) {
        return true;
    }

}
