package com.boe.simulator.api.websocket.dto;

import com.boe.simulator.server.matching.Trade;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class TradeUpdateMessage extends WebSocketMessage {

    @JsonProperty("tradeId")
    private final long tradeId;

    @JsonProperty("symbol")
    private final String symbol;

    @JsonProperty("price")
    private final BigDecimal price;

    @JsonProperty("quantity")
    private final int quantity;

    @JsonProperty("notionalValue")
    private final BigDecimal notionalValue;

    @JsonProperty("executionTime")
    private final long executionTime;

    public TradeUpdateMessage(Trade trade) {
        super("trade");
        this.tradeId = trade.getTradeId();
        this.symbol = trade.getSymbol();
        this.price = trade.getPrice();
        this.quantity = trade.getQuantity();
        this.notionalValue = trade.getNotionalValue();
        this.executionTime = trade.getExecutionTime().toEpochMilli();
    }

    // Getters
    public long getTradeId() { return tradeId; }
    public String getSymbol() { return symbol; }
    public BigDecimal getPrice() { return price; }
    public int getQuantity() { return quantity; }
    public BigDecimal getNotionalValue() { return notionalValue; }
    public long getExecutionTime() { return executionTime; }
}