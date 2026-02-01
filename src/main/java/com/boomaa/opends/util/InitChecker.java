package com.boomaa.opends.util;

import com.boomaa.opends.data.holders.Protocol;
import com.boomaa.opends.data.holders.Remote;

public class InitChecker {
    // rioUDP, rioTCP, fmsUDP, fmsTCP
    private final boolean[] states = new boolean[4];
    private long rioLastSeenMs;

    public boolean get(Remote remote, Protocol protocol) {
        return states[(remote.ordinal() * 2) + protocol.ordinal()];
    }

    public void set(boolean value, Remote remote, Protocol protocol) {
        states[(remote.ordinal() * 2) + protocol.ordinal()] = value;
        if (remote == Remote.ROBO_RIO && value) {
            rioLastSeenMs = System.currentTimeMillis();
        }
    }

    public boolean isInit(Remote remote) {
        int idx = remote.ordinal() * 2;
        return states[idx] || states[idx + 1];
    }

    public void touchRio() {
        rioLastSeenMs = System.currentTimeMillis();
    }

    public boolean isRioStale(long maxAgeMs) {
        if (!isInit(Remote.ROBO_RIO)) {
            return false;
        }
        return System.currentTimeMillis() - rioLastSeenMs > maxAgeMs;
    }
}
