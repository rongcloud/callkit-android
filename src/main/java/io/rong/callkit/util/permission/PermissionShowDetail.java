package io.rong.callkit.util.permission;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

public class PermissionShowDetail {
    private static final String VIVO = "vivo";

    public static void showPermissionDetail(Context context) {
        String manufacturer = Build.MANUFACTURER.toLowerCase();
        switch (manufacturer) {
            case VIVO:
                openVIVODetail(context);
                break;
            default:
                defaultToDetail(context);
                break;
        }
    }

    private static void defaultToDetail(Context context) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setPackage(context.getPackageName());
            Uri uri = Uri.fromParts("package", context.getPackageName(), null);
            intent.setData(uri);
            context.startActivity(intent);
        } catch (Exception e) {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            context.startActivity(intent);
        }
    }

    private static void openVIVODetail(Context context) {
        Intent localIntent;
        if (((Build.MODEL.contains("Y85")) && (!Build.MODEL.contains("Y85A")))
                || (Build.MODEL.contains("vivo Y53L"))) {
            localIntent = new Intent();
            localIntent.setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.PurviewTabActivity");
            localIntent.putExtra("packagename", context.getPackageName());
            localIntent.putExtra("tabId", "1");
            context.startActivity(localIntent);
        } else {
            localIntent = new Intent();
            localIntent.setClassName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.SoftPermissionDetailActivity");
            localIntent.setAction("secure.intent.action.softPermissionDetail");
            localIntent.putExtra("packagename", context.getPackageName());
            context.startActivity(localIntent);
        }
    }
}
