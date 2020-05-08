package io.rong.callkit.util;

public enum RingingMode {

    Incoming(0), Outgoing(1),Incoming_Custom(2);

    private int val;

    RingingMode(int val) {
        this.val = val;
    }
}
