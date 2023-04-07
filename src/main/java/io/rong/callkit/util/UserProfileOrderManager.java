package io.rong.callkit.util;

import android.text.TextUtils;
import android.util.Log;
import io.rong.calllib.CallUserProfile;
import io.rong.calllib.RongCallClient;
import java.util.ArrayList;
import java.util.List;

public final class UserProfileOrderManager {

    private static final String TAG = "UserProfileOrderManager";
    private final ArrayList<String> userIds;

    public UserProfileOrderManager() {
        this.userIds = new ArrayList<>();
    }

    public UserProfileOrderManager(ArrayList<String> value) {
        this.userIds = new ArrayList<>();
        if (value != null && !value.isEmpty()) {
            this.userIds.addAll(value);
        }
        ArrayList<String> userIdsTmp = new ArrayList<>();
        if (RongCallClient.getInstance() != null
                && RongCallClient.getInstance().getCallSession() != null) {
            if (RongCallClient.getInstance().getCallSession().getParticipantProfileList() != null) {
                for (CallUserProfile userProfile :
                        RongCallClient.getInstance().getCallSession().getParticipantProfileList()) {
                    if (!this.userIds.contains(userProfile.getUserId())) {
                        Log.e(TAG, "userIdsTmp.add : " + userProfile.getUserId());
                        userIdsTmp.add(userProfile.getUserId());
                    }
                }
            }
        }
        if (!userIdsTmp.isEmpty()) {
            this.userIds.addAll(userIdsTmp);
        }
    }

    public List<CallUserProfile> getSortedProfileList(
            List<CallUserProfile> participantProfileList) {
        if (this.userIds.isEmpty()) {
            //                        Log.e(TAG, "-------getSortedProfileList--->isEmpty");
            for (CallUserProfile userProfile : participantProfileList) {
                this.userIds.add(userProfile.getUserId());
            }
            return participantProfileList;
        } else {
            List<CallUserProfile> callUserProfileList = new ArrayList<>(this.userIds.size());
            for (String userId : this.userIds) {
                //                                Log.e(TAG,
                //                 "-------getSortedProfileList--->userId : "+userId);
                for (CallUserProfile callUserProfile : participantProfileList) {
                    if (TextUtils.equals(userId, callUserProfile.getUserId())) {
                        callUserProfileList.add(callUserProfile);
                    }
                }
            }
            return callUserProfileList;
        }
    }

    public ArrayList<String> getUserIds() {
        //        Log.d(TAG, "------getUserIds.start");
        //        if (userIds != null && userIds.size() >= 0) {
        //            for (String userId : userIds) {
        //                Log.d(TAG, "getUserIds.id: " + userId);
        //            }
        //        }
        return userIds;
    }

    public void exchange(String fromUserId, String toUserId) {
        int fromUserIdIndex = -1, toUserIdIndex = -1;
        for (int i = 0; i < this.userIds.size(); i++) {
            if (TextUtils.equals(userIds.get(i), fromUserId)) {
                fromUserIdIndex = i;
            }

            if (TextUtils.equals(userIds.get(i), toUserId)) {
                toUserIdIndex = i;
            }
        }

        if (fromUserIdIndex != -1) {
            try {
                userIds.set(fromUserIdIndex, toUserId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (toUserIdIndex != -1) {
            try {
                userIds.set(toUserIdIndex, fromUserId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        //        Log.e(TAG, "-----exchange----fromUserId ： "+fromUserId + ", fromUserIdIndex :
        // "+fromUserIdIndex + " ， toUserId ： "+toUserId +" ,toUserIdIndex : " +toUserIdIndex);
    }
}
