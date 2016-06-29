package io.rong.imkit;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import io.rong.common.RLog;

public class RongCallReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(final Context context, Intent intent) {
        RLog.d("RongCallReceiver", "intent = " + intent);

        if (intent.getAction().equals(RongVoIPIntent.RONG_INTENT_ACTION_VOIP_INIT)) {
            RongCallService.onInit(context);
        } else if (intent.getAction().equals(RongVoIPIntent.RONG_INTENT_ACTION_VOIP_UI_READY)) {
            RongCallService.onUiReady();
        } else if (intent.getAction().equals(RongVoIPIntent.RONG_INTENT_ACTION_VOIP_CONNECTED)) {
            RongCallService.onConnected();
        }
//        else if (intent.getAction().equals(Intent.ACTION_NEW_OUTGOING_CALL) && RongCallClient.getInstance() != null) {
//            RongCallSession callProfile = RongCallManager.getInstance().getCallSession();
//            RongCallClient.getInstance().hangUpCall(callProfile.getCallId());
//        } else {
//            TelephonyManager tm = (TelephonyManager)context.getSystemService(Service.TELEPHONY_SERVICE);
//            tm.listen(listener, PhoneStateListener.LISTEN_CALL_STATE);
//        }
    }

//    private PhoneStateListener listener = new PhoneStateListener() {
//        @Override
//        public void onCallStateChanged(int state, String incomingNumber) {
//            super.onCallStateChanged(state, incomingNumber);
//            if (state == TelephonyManager.CALL_STATE_RINGING && RongCallClient.getInstance() != null) {
//                RongCallSession callSession = RongCallClient.getInstance().getCallSession();
//                if (callSession != null) {
//                    RongCallClient.getInstance().hangUpCall(callSession.getCallId());
//                }
//            }
//        }
//    };
}
