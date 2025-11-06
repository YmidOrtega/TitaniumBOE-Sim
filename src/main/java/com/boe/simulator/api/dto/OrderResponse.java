package com.boe.simulator.api.dto;

import com.boe.simulator.server.order.Order;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record OrderResponse(
        @JsonProperty("clOrdID") String clOrdID,
        @JsonProperty("orderID") long orderID,
        @JsonProperty("symbol") String symbol,
        @JsonProperty("side") String side,
        @JsonProperty("orderQty") int orderQty,
        @JsonProperty("leavesQty") int leavesQty,
        @JsonProperty("cumQty") int cumQty,
        @JsonProperty("price") BigDecimal price,
        @JsonProperty("orderType") String orderType,
        @JsonProperty("state") String state,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("lastModified") Instant lastModified
) {
    public static OrderResponse fromOrder(Order order) {
        return new OrderResponse(
                order.getClOrdID(),
                order.getOrderID(),
                order.getSymbol(),
                order.getSide() == 1 ? "BUY" : "SELL",
                order.getOrderQty(),
                order.getLeavesQty(),
                order.getCumQty(),
                order.getPrice(),
                order.getOrdType() == 1 ? "MARKET" : "LIMIT",
                order.getState().name(),
                order.getCreatedAt(),
                order.getLastModified()
        );
    }
}