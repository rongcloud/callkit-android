package io.rong.callkit;

import android.text.TextUtils;
import io.rong.calllib.ReportUtil;

/** Created by weiqinxiao on 16/3/15. */
public enum RongCallAction {
    NONE(-1, ""),
    ACTION_OUTGOING_CALL(1, "ACTION_OUTGOING_CALL"),
    ACTION_INCOMING_CALL(2, "ACTION_INCOMING_CALL"),
    ACTION_ADD_MEMBER(3, "ACTION_ADD_MEMBER"),
    ACTION_RESUME_CALL(4, "ACTION_RESUME_CALL");

    int value;
    String msg;

    RongCallAction(int v, String msg) {
        this.value = v;
        this.msg = msg;
    }

    public int getValue() {
        return value;
    }

    public String getName() {
        return msg;
    }

    public static RongCallAction getAction(String msg) {
        if (TextUtils.isEmpty(msg)) {
            ReportUtil.appError(ReportUtil.TAG.INTERNAL_ERROR, "desc", "getAction().msg is empty");
            return NONE;
        }

        for (RongCallAction action : RongCallAction.values()) {
            if (TextUtils.equals(action.getName(), msg)) {
                return action;
            }
        }
        return NONE;
    }
}
