package com.boe.simulator.api.websocket.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

public class OrderBookUpdateMessage extends WebSocketMessage {

    @JsonProperty("symbol")
    private final String symbol;

    @JsonProperty("bids")
    private final List<PriceLevel> bids;

    @JsonProperty("asks")
    private final List<PriceLevel> asks;

    @JsonProperty("lastTradePrice")
    private final BigDecimal lastTradePrice;

    public OrderBookUpdateMessage(String symbol, List<PriceLevel> bids, List<PriceLevel> asks, BigDecimal lastTradePrice) {
        super("orderbook");
        this.symbol = symbol;
        this.bids = bids;
        this.asks = asks;
        this.lastTradePrice = lastTradePrice;
    }

    public String getSymbol() {
        return symbol;
    }

    public List<PriceLevel> getBids() {
        return bids;
    }

    public List<PriceLevel> getAsks() {
        return asks;
    }

    public BigDecimal getLastTradePrice() {
        return lastTradePrice;
    }

    public record PriceLevel(
            @JsonProperty("price") BigDecimal price,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("orderCount") int orderCount
    ) {}
}