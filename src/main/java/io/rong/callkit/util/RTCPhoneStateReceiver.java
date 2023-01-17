package io.rong.callkit.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import io.rong.calllib.RongCallClient;
import io.rong.calllib.RongCallSession;

public class RTCPhoneStateReceiver extends BroadcastReceiver {

    private static final String TAG = "RTCPhoneStateReceiver";
    // 21以上会回调两次(状态值一样)
    private static String twice = "";
    private TelephonyManager mTelephonyManager;

    public int getCallState(Context context) {
        if (context == null) {
            return -1;
        }

        if (mTelephonyManager == null) {
            mTelephonyManager =
                    (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        }
        return mTelephonyManager.getCallState();
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!("android.intent.action.PHONE_STATE").equals(action)) {
            Log.i(TAG, "action :" + action);
            return;
        }

        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);

        if (TextUtils.isEmpty(state) && TextUtils.isEmpty(twice)) {
            int callState = getCallState(context);
            if (TelephonyManager.CALL_STATE_OFFHOOK == callState) {
                state = TelephonyManager.EXTRA_STATE_OFFHOOK;
            } else if (TelephonyManager.CALL_STATE_RINGING == callState) {
                state = TelephonyManager.EXTRA_STATE_RINGING;
            } else if (TelephonyManager.CALL_STATE_IDLE == callState) {
                state = TelephonyManager.EXTRA_STATE_IDLE;
            }
        }

        Log.i(TAG, "state : " + state + " , twice : " + twice);

        if (!TextUtils.isEmpty(state) && !twice.equals(state)) {
            twice = state;
            RongCallSession callSession = RongCallClient.getInstance().getCallSession();
            if (callSession == null) {
                Log.e(TAG, "callSession is empty");
                return;
            }

            if (twice.equals(TelephonyManager.EXTRA_STATE_OFFHOOK)) {
                // ON_PHONE;
                RongCallClient.getInstance().hangUpCall();
            } else if (twice.equals(TelephonyManager.EXTRA_STATE_IDLE)) {
                // ON_PHONE_ENDED;
            }
        }
    }
}
