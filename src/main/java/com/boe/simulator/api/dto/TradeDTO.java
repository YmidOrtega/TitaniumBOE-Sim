package com.boe.simulator.api.dto;

import com.boe.simulator.server.matching.Trade;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record TradeDTO(
        @JsonProperty("tradeId") long tradeId,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("quantity") int quantity,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("notionalValue") BigDecimal notionalValue,
        @JsonProperty("executionTime") Instant executionTime,
        @JsonProperty("buyOrderId") long buyOrderId,
        @JsonProperty("sellOrderId") long sellOrderId
) {
    public static TradeDTO fromTrade(Trade trade) {
        return new TradeDTO(
                trade.getTradeId(),
                trade.getSymbol(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getNotionalValue(),
                trade.getExecutionTime(),
                trade.getBuyOrderId(),
                trade.getSellOrderId()
        );
    }
}