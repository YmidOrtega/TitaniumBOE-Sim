package com.boe.simulator.api.websocket.dto;

import com.boe.simulator.server.order.Order;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

public class OrderStatusUpdateMessage extends WebSocketMessage {

    @JsonProperty("clOrdID")
    private final String clOrdID;

    @JsonProperty("orderID")
    private final long orderID;

    @JsonProperty("symbol")
    private final String symbol;

    @JsonProperty("side")
    private final String side;

    @JsonProperty("orderQty")
    private final int orderQty;

    @JsonProperty("leavesQty")
    private final int leavesQty;

    @JsonProperty("cumQty")
    private final int cumQty;

    @JsonProperty("price")
    private final BigDecimal price;

    @JsonProperty("state")
    private final String state;

    @JsonProperty("lastModified")
    private final long lastModified;

    public OrderStatusUpdateMessage(Order order) {
        super("order_status");
        this.clOrdID = order.getClOrdID();
        this.orderID = order.getOrderID();
        this.symbol = order.getSymbol();
        this.side = order.getSide() == 1 ? "BUY" : "SELL";
        this.orderQty = order.getOrderQty();
        this.leavesQty = order.getLeavesQty();
        this.cumQty = order.getCumQty();
        this.price = order.getPrice();
        this.state = order.getState().name();
        this.lastModified = order.getLastModified().toEpochMilli();
    }

    // Getters
    public String getClOrdID() { return clOrdID; }
    public long getOrderID() { return orderID; }
    public String getSymbol() { return symbol; }
    public String getSide() { return side; }
    public int getOrderQty() { return orderQty; }
    public int getLeavesQty() { return leavesQty; }
    public int getCumQty() { return cumQty; }
    public BigDecimal getPrice() { return price; }
    public String getState() { return state; }
    public long getLastModified() { return lastModified; }
}