package io.rong.callkit.util;

import java.io.Serializable;
import java.util.HashMap;

public class CallSelectMemberSerializable implements Serializable {
    private HashMap<String, String> hashMap = new HashMap<>();

    public CallSelectMemberSerializable(HashMap<String, String> hashMap) {
        this.hashMap = hashMap;
    }

    public HashMap<String, String> getHashMap() {
        return hashMap;
    }

    public void setHashMap(HashMap<String, String> hashMap) {
        this.hashMap = hashMap;
    }
}
