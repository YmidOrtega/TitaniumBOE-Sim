package com.boe.simulator.protocol.message;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Optional fields a member may request in Return Bitfields — Table 133 (p.179) and the List of
 * Optional Fields (p.196), spec v2.11.90. Byte is 1-based; fields go on the wire in byte, then bit order.
 */
public enum ReturnField {
    SIDE("Side", 1, 0x01, 1, Kind.TEXT),
    PRICE("Price", 1, 0x04, 8, Kind.PRICE),
    EXEC_INST("ExecInst", 1, 0x08, 1, Kind.TEXT),
    ORD_TYPE("OrdType", 1, 0x10, 1, Kind.TEXT),
    TIME_IN_FORCE("TimeInForce", 1, 0x20, 1, Kind.TEXT),
    MIN_QTY("MinQty", 1, 0x40, 4, Kind.BINARY),
    SYMBOL("Symbol", 2, 0x01, 8, Kind.TEXT),
    CAPACITY("Capacity", 2, 0x40, 1, Kind.TEXT),
    CONTRA_TRADER("ContraTrader", 2, 0x80, 4, Kind.TEXT),
    ACCOUNT("Account", 3, 0x01, 16, Kind.TEXT),
    CLEARING_FIRM("ClearingFirm", 3, 0x02, 4, Kind.TEXT),
    CLEARING_ACCOUNT("ClearingAccount", 3, 0x04, 4, Kind.TEXT),
    DISPLAY_INDICATOR("DisplayIndicator", 3, 0x08, 1, Kind.TEXT),
    MAX_FLOOR("MaxFloor", 3, 0x10, 4, Kind.BINARY),
    ORDER_QTY("OrderQty", 3, 0x40, 4, Kind.BINARY),
    PREVENT_MATCH("PreventMatch", 3, 0x80, 3, Kind.TEXT),
    MATURITY_DATE("MaturityDate", 4, 0x01, 4, Kind.DATE),
    STRIKE_PRICE("StrikePrice", 4, 0x02, 8, Kind.PRICE),
    PUT_OR_CALL("PutOrCall", 4, 0x04, 1, Kind.TEXT),
    OPEN_CLOSE("OpenClose", 4, 0x08, 1, Kind.TEXT),
    CORRECTED_SIZE("CorrectedSize", 4, 0x20, 4, Kind.BINARY),
    ORIG_CL_ORD_ID("OrigClOrdID", 5, 0x01, 20, Kind.TEXT),
    LEAVES_QTY("LeavesQty", 5, 0x02, 4, Kind.BINARY),
    LAST_SHARES("LastShares", 5, 0x04, 4, Kind.BINARY),
    LAST_PX("LastPx", 5, 0x08, 8, Kind.PRICE),
    DISPLAY_PRICE("DisplayPrice", 5, 0x10, 8, Kind.PRICE),
    WORKING_PRICE("WorkingPrice", 5, 0x20, 8, Kind.PRICE),
    BASE_LIQUIDITY_INDICATOR("BaseLiquidityIndicator", 5, 0x40, 1, Kind.TEXT),
    EXPIRE_TIME("ExpireTime", 5, 0x80, 8, Kind.DATE_TIME),
    SECONDARY_ORDER_ID("SecondaryOrderID", 6, 0x01, 8, Kind.BINARY),
    CONTRA_CAPACITY("ContraCapacity", 6, 0x04, 1, Kind.TEXT),
    ATTRIBUTED_QUOTE("AttributedQuote", 6, 0x08, 1, Kind.TEXT),
    SUB_LIQUIDITY_INDICATOR("SubLiquidityIndicator", 7, 0x01, 1, Kind.TEXT),
    FEE_CODE("FeeCode", 8, 0x01, 2, Kind.TEXT),
    ECHO_TEXT("EchoText", 8, 0x02, 64, Kind.TEXT),
    STOP_PX("StopPx", 8, 0x04, 8, Kind.PRICE),
    ROUTING_INST("RoutingInst", 8, 0x08, 4, Kind.TEXT),
    ROUT_STRATEGY("RoutStrategy", 8, 0x10, 6, Kind.TEXT),
    ROUTE_DELIVERY_METHOD("RouteDeliveryMethod", 8, 0x20, 3, Kind.TEXT),
    EX_DESTINATION("ExDestination", 8, 0x40, 1, Kind.TEXT),
    MARKETING_FEE_CODE("MarketingFeeCode", 9, 0x01, 2, Kind.TEXT),
    TARGET_PARTY_ID("TargetPartyID", 9, 0x02, 4, Kind.TEXT),
    AUCTION_ID("AuctionId", 9, 0x04, 8, Kind.BINARY),
    CMTA_NUMBER("CmtaNumber", 9, 0x20, 4, Kind.BINARY),
    CROSS_TYPE("CrossType", 9, 0x40, 1, Kind.TEXT),
    CROSS_PRIORITIZATION("CrossPrioritization", 9, 0x80, 1, Kind.TEXT),
    CROSS_ID("CrossId", 10, 0x01, 20, Kind.TEXT),
    ALLOC_QTY("AllocQty", 10, 0x02, 4, Kind.BINARY),
    GIVE_UP_FIRM_ID("GiveUpFirmID", 10, 0x04, 4, Kind.TEXT),
    ROUTING_FIRM_ID("RoutingFirmID", 10, 0x08, 4, Kind.TEXT),
    CROSS_EXCLUSION_INDICATOR("CrossExclusionIndicator", 10, 0x20, 1, Kind.TEXT),
    TRADE_DATE("TradeDate", 12, 0x08, 4, Kind.DATE),
    CLEARING_OPTIONAL_DATA("ClearingOptionalData", 12, 0x80, 16, Kind.TEXT),
    CUM_QTY("CumQty", 13, 0x01, 4, Kind.BINARY),
    DAY_ORDER_QTY("DayOrderQty", 13, 0x02, 4, Kind.BINARY),
    DAY_CUM_QTY("DayCumQty", 13, 0x04, 4, Kind.BINARY),
    AVG_PX("AvgPx", 13, 0x08, 8, Kind.PRICE),
    DAY_AVG_PX("DayAvgPx", 13, 0x10, 8, Kind.PRICE),
    DRILL_THRU_PROTECTION("DrillThruProtection", 13, 0x40, 8, Kind.PRICE),
    MULTILEG_REPORTING_TYPE("MultilegReportingType", 13, 0x80, 1, Kind.TEXT),
    SECONDARY_EXEC_ID("SecondaryExecId", 14, 0x10, 8, Kind.BINARY),
    EQUITY_PARTY_ID("EquityPartyId", 15, 0x02, 4, Kind.TEXT),
    MASS_CANCEL_ID("MassCancelId", 15, 0x08, 20, Kind.TEXT),
    CLIENT_ID_ATTR("ClientIDAttr", 15, 0x80, 4, Kind.TEXT),
    FREQUENT_TRADER_ID("FrequentTraderID", 16, 0x01, 6, Kind.TEXT),
    SESSION_ELIGIBILITY("SessionEligibility", 16, 0x02, 1, Kind.TEXT),
    COMBO_ORDER("ComboOrder", 16, 0x04, 1, Kind.TEXT),
    COMPRESSION("Compression", 16, 0x08, 1, Kind.TEXT),
    FLOOR_DESTINATION("FloorDestination", 16, 0x10, 4, Kind.TEXT),
    FLOOR_ROUTING_INST("FloorRoutingInst", 16, 0x20, 1, Kind.TEXT),
    MULTI_CLASS_SPRD("MultiClassSprd", 16, 0x40, 1, Kind.TEXT),
    ORDER_ORIGIN("OrderOrigin", 16, 0x80, 3, Kind.TEXT),
    PRICE_TYPE("PriceType", 17, 0x01, 1, Kind.TEXT),
    STRATEGY_ID("StrategyID", 17, 0x02, 1, Kind.TEXT),
    TRADE_THROUGH_ALERT_TYPE("TradeThroughAlertType", 17, 0x08, 1, Kind.TEXT),
    SENDER_LOCATION_ID("SenderLocationID", 17, 0x10, 1, Kind.TEXT),
    FLOOR_TRADER_ACRONYM("FloorTraderAcronym", 17, 0x20, 3, Kind.TEXT),
    EXEC_LEG_CFI_CODE("ExecLegCFICode", 17, 0x40, 6, Kind.TEXT),
    CROSS_INITIATOR("CrossInitiator", 18, 0x02, 4, Kind.TEXT),
    SUBREASON("Subreason", 18, 0x04, 1, Kind.TEXT),
    HELD("Held", 18, 0x20, 1, Kind.TEXT),
    FLOOR_TRADE_TIME("FloorTradeTime", 19, 0x01, 8, Kind.DATE_TIME),
    EQUITY_EX_DESTINATION("EquityExDestination", 19, 0x02, 1, Kind.TEXT),
    CROSS_ON_BEHALF_OF_ID("CrossOnBehalfOfID", 19, 0x04, 4, Kind.TEXT);

    public enum Kind { BINARY, PRICE, TEXT, DATE, DATE_TIME }

    private static final List<ReturnField> WIRE_ORDER = Stream.of(values())
            .sorted(Comparator.comparingInt((ReturnField f) -> f.byteNumber).thenComparingInt(f -> f.bit))
            .toList();
    private static final Map<String, ReturnField> BY_NAME = new HashMap<>();

    static {
        for (ReturnField f : values()) BY_NAME.put(f.specName.toLowerCase(Locale.ROOT), f);
    }

    private final String specName;
    private final int byteNumber;
    private final int bit;
    private final int length;
    private final Kind kind;

    ReturnField(String specName, int byteNumber, int bit, int length, Kind kind) {
        this.specName = specName;
        this.byteNumber = byteNumber;
        this.bit = bit;
        this.length = length;
        this.kind = kind;
    }

    public String specName() { return specName; }
    public int byteNumber() { return byteNumber; }
    public int bit() { return bit; }
    public int length() { return length; }
    public Kind kind() { return kind; }

    static List<ReturnField> wireOrder() { return WIRE_ORDER; }

    /** Case-insensitive, so "CMTANumber" and "MassCancelID" from the input tables match too. */
    public static ReturnField byName(String name) {
        return name == null ? null : BY_NAME.get(name.toLowerCase(Locale.ROOT));
    }
}
