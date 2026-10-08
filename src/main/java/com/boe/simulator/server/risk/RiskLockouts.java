package com.boe.simulator.server.risk;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Self-imposed lockouts from Cancel Order / Purge Orders (MassCancelInst 3rd character L, p.204),
 * released by Reset Risk or the RiskReset field of a New Order (p.207).
 */
public final class RiskLockouts {

    public enum Level { EFID, RISK_ROOT, CUSTOM_GROUP }

    // Order Reason Codes (p.213)
    public static final byte REASON_EFID_OR_CUSTOM_GROUP = (byte) 'f';
    public static final byte REASON_RISK_ROOT = (byte) 's';

    private record Key(String username, String clearingFirm, Level level, String riskRoot, int customGroupId) {}

    private final Set<Key> lockouts = ConcurrentHashMap.newKeySet();

    public void lockEfid(String username, String clearingFirm) {
        lockouts.add(new Key(username, clearingFirm, Level.EFID, null, 0));
    }

    public void lockRiskRoot(String username, String clearingFirm, String riskRoot) {
        lockouts.add(new Key(username, clearingFirm, Level.RISK_ROOT, riskRoot, 0));
    }

    public void lockCustomGroup(String username, String clearingFirm, int customGroupId) {
        lockouts.add(new Key(username, clearingFirm, Level.CUSTOM_GROUP, null, customGroupId));
    }

    /** 0 when the order may trade, otherwise the reject reason. */
    public byte check(String username, String clearingFirm, String symbol, int customGroupId) {
        for (Key key : lockouts) {
            if (!key.username().equals(username) || !Objects.equals(key.clearingFirm(), clearingFirm)) continue;
            switch (key.level()) {
                case EFID -> { return REASON_EFID_OR_CUSTOM_GROUP; }
                case CUSTOM_GROUP -> { if (customGroupId != 0 && key.customGroupId() == customGroupId) return REASON_EFID_OR_CUSTOM_GROUP; }
                case RISK_ROOT -> { if (key.riskRoot().equals(symbol)) return REASON_RISK_ROOT; }
            }
        }
        return 0;
    }

    /** A blank clearing firm releases the level for every clearing firm of the user. */
    public int release(String username, String clearingFirm, Level level, String riskRoot, int customGroupId) {
        int before = lockouts.size();
        lockouts.removeIf(key -> key.username().equals(username)
                && key.level() == level
                && (clearingFirm == null || clearingFirm.isBlank() || clearingFirm.equals(key.clearingFirm()))
                && (level != Level.RISK_ROOT || key.riskRoot().equals(riskRoot))
                && (level != Level.CUSTOM_GROUP || key.customGroupId() == customGroupId));
        return before - lockouts.size();
    }

    public boolean isEmpty() {
        return lockouts.isEmpty();
    }

    public void clear() {
        lockouts.clear();
    }
}
