package com.boe.simulator.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

public record OrderRequest(
        @JsonProperty("symbol") String symbol,
        @JsonProperty("side") String side,           // "BUY" or "SELL"
        @JsonProperty("orderQty") int orderQty,
        @JsonProperty("price") BigDecimal price,     // null for market orders
        @JsonProperty("orderType") String orderType, // "LIMIT" or "MARKET"
        @JsonProperty("account") String account,
        @JsonProperty("capacity") String capacity    // "CUSTOMER", "FIRM", etc.
) {
    public byte getSideByte() {
        return "BUY".equalsIgnoreCase(side) ? (byte) 1 : (byte) 2;
    }

    public byte getOrderTypeByte() {
        return "MARKET".equalsIgnoreCase(orderType) ? (byte) 1 : (byte) 2;
    }

    public byte getCapacityByte() {
        if (capacity == null) return 'C';
        return switch (capacity.toUpperCase()) {
            case "CUSTOMER" -> 'C';
            case "FIRM" -> 'F';
            case "MARKETMAKER" -> 'M';
            default -> 'C';
        };
    }

    public void validate() {
        if (symbol == null || symbol.isBlank()) throw new IllegalArgumentException("Symbol is required");

        if (side == null || (!side.equalsIgnoreCase("BUY") && !side.equalsIgnoreCase("SELL"))) throw new IllegalArgumentException("Side must be 'BUY' or 'SELL'");

        if (orderQty <= 0) throw new IllegalArgumentException("Order quantity must be positive");

        if ("LIMIT".equalsIgnoreCase(orderType) && price == null) throw new IllegalArgumentException("Price is required for LIMIT orders");
    }
}