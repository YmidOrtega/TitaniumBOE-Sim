package com.boe.simulator.server.config;

import java.util.Set;

import com.boe.simulator.protocol.types.PreventMatch;

/**
 * BOE port attributes (p.218-221) the simulator models, with the spec defaults.
 */
public record PortAttributes(
        CancelScope cancelOnDisconnect,
        int maximumOrderSize,
        int portOrderRateThreshold,
        int symbolOrderRateThreshold,
        PreventMatch defaultMtp,
        boolean doneForDayRestatements,
        boolean carriedOrderRestatements,
        boolean cancelOnReject,
        boolean efidRiskReset,
        Set<String> allowedClearingFirms,
        String defaultAccount,
        String defaultClearingFirm,
        String defaultClearingOptionalData
) {
    public enum CancelScope { ALL, DAY, NONE }

    public static final PortAttributes SPEC_DEFAULTS = new PortAttributes(
            CancelScope.ALL, 25_000, 5_000, 5_000, null, false, false, false, false, Set.of(), null, null, null);

    public PortAttributes {
        allowedClearingFirms = allowedClearingFirms == null ? Set.of() : Set.copyOf(allowedClearingFirms);
    }

    /** An empty set means every clearing firm is allowed ("All EFIDS"). */
    public boolean allowsClearingFirm(String clearingFirm) {
        return allowedClearingFirms.isEmpty() || allowedClearingFirms.contains(clearingFirm);
    }

    public PortAttributes withCancelOnDisconnect(CancelScope v) { return new PortAttributes(v, maximumOrderSize, portOrderRateThreshold, symbolOrderRateThreshold, defaultMtp, doneForDayRestatements, carriedOrderRestatements, cancelOnReject, efidRiskReset, allowedClearingFirms, defaultAccount, defaultClearingFirm, defaultClearingOptionalData); }
    public PortAttributes withMaximumOrderSize(int v) { return new PortAttributes(cancelOnDisconnect, v, portOrderRateThreshold, symbolOrderRateThreshold, defaultMtp, doneForDayRestatements, carriedOrderRestatements, cancelOnReject, efidRiskReset, allowedClearingFirms, defaultAccount, defaultClearingFirm, defaultClearingOptionalData); }
    public PortAttributes withOrderRateThresholds(int port, int symbol) { return new PortAttributes(cancelOnDisconnect, maximumOrderSize, port, symbol, defaultMtp, doneForDayRestatements, carriedOrderRestatements, cancelOnReject, efidRiskReset, allowedClearingFirms, defaultAccount, defaultClearingFirm, defaultClearingOptionalData); }
    public PortAttributes withDefaultMtp(PreventMatch v) { return new PortAttributes(cancelOnDisconnect, maximumOrderSize, portOrderRateThreshold, symbolOrderRateThreshold, v, doneForDayRestatements, carriedOrderRestatements, cancelOnReject, efidRiskReset, allowedClearingFirms, defaultAccount, defaultClearingFirm, defaultClearingOptionalData); }
    public PortAttributes withRestatements(boolean doneForDay, boolean carried) { return new PortAttributes(cancelOnDisconnect, maximumOrderSize, portOrderRateThreshold, symbolOrderRateThreshold, defaultMtp, doneForDay, carried, cancelOnReject, efidRiskReset, allowedClearingFirms, defaultAccount, defaultClearingFirm, defaultClearingOptionalData); }
    public PortAttributes withCancelOnReject(boolean v) { return new PortAttributes(cancelOnDisconnect, maximumOrderSize, portOrderRateThreshold, symbolOrderRateThreshold, defaultMtp, doneForDayRestatements, carriedOrderRestatements, v, efidRiskReset, allowedClearingFirms, defaultAccount, defaultClearingFirm, defaultClearingOptionalData); }
    public PortAttributes withEfidRiskReset(boolean v) { return new PortAttributes(cancelOnDisconnect, maximumOrderSize, portOrderRateThreshold, symbolOrderRateThreshold, defaultMtp, doneForDayRestatements, carriedOrderRestatements, cancelOnReject, v, allowedClearingFirms, defaultAccount, defaultClearingFirm, defaultClearingOptionalData); }
    public PortAttributes withAllowedClearingFirms(Set<String> v) { return new PortAttributes(cancelOnDisconnect, maximumOrderSize, portOrderRateThreshold, symbolOrderRateThreshold, defaultMtp, doneForDayRestatements, carriedOrderRestatements, cancelOnReject, efidRiskReset, v, defaultAccount, defaultClearingFirm, defaultClearingOptionalData); }
    public PortAttributes withDefaults(String account, String clearingFirm, String clearingOptionalData) { return new PortAttributes(cancelOnDisconnect, maximumOrderSize, portOrderRateThreshold, symbolOrderRateThreshold, defaultMtp, doneForDayRestatements, carriedOrderRestatements, cancelOnReject, efidRiskReset, allowedClearingFirms, account, clearingFirm, clearingOptionalData); }
}
