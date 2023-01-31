package io.rong.callkit.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallSession;
import io.rong.common.RLog;

public class RTCPhoneStateReceiver extends BroadcastReceiver {

    private static final String TAG = "RTCPhoneStateReceiver";
    // 21以上会回调两次(状态值一样)
    private static String twice = "";

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (("android.intent.action.PHONE_STATE").equals(action)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            RLog.i(TAG, "state :" + state + " , twice : " + twice);
            if (!TextUtils.isEmpty(state) && !twice.equals(state)) {
                twice = state;
                if (RongCallClient.getInstance() == null) {
                    return;
                }
                RongCallSession session = RongCallClient.getInstance().getCallSession();
                if (session != null
                        && (twice.equals(TelephonyManager.EXTRA_STATE_RINGING)
                                || twice.equals(TelephonyManager.EXTRA_STATE_OFFHOOK))) {
                    RongCallClient.getInstance().hangUpCall();
                } else {
                    RLog.i(TAG, "onReceive->session = null.");
                }
            }
        }
    }
}
