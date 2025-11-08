package com.boe.simulator.api.websocket.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = OrderBookUpdateMessage.class, name = "orderbook"),
        @JsonSubTypes.Type(value = TradeUpdateMessage.class, name = "trade"),
        @JsonSubTypes.Type(value = OrderStatusUpdateMessage.class, name = "order_status")
})
public abstract class WebSocketMessage {

    @JsonIgnore
    private final String type;

    private final long timestamp;

    protected WebSocketMessage(String type) {
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }

    public String getType() {
        return type;
    }

    public long getTimestamp() {
        return timestamp;
    }
}