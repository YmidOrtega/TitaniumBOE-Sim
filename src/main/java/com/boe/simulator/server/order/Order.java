package com.boe.simulator.server.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.boe.simulator.protocol.types.Capacity;
import com.boe.simulator.protocol.types.OpenClose;
import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.PutOrCall;
import com.boe.simulator.protocol.types.RoutingInst;
import com.boe.simulator.protocol.types.Side;

public class Order {

    // Identificadores
    private final String clOrdID;
    private final long orderID;
    private final String sessionSubID;
    private final String username;

    // Atributos básicos
    private final Side side;
    private final int orderQty;
    private int leavesQty;
    private int cumQty;
    private final BigDecimal price;
    private final OrdType ordType;

    // Symbology
    private final String symbol;
    private final Instant maturityDate;
    private final BigDecimal strikePrice;
    private final PutOrCall putOrCall;

    // Atributos de cuenta
    private final Capacity capacity;
    private final String account;
    private final String clearingFirm;
    private final String clearingAccount;
    private final OpenClose openClose;
    @SuppressWarnings("unused")
    private final byte matchingUnit;

    // Estado y timestamps
    private OrderState state;
    private final Instant createdAt;
    private Instant lastModified;

    // Routing
    private final RoutingInst routingInst;

    // Sequence tracking
    private final int receivedSequence;
    private int lastSentSequence;

    // Optional fields storage
    private final Map<String, Object> optionalFields;

    private Order(Builder builder) {
        this.clOrdID = builder.clOrdID;
        this.orderID = builder.orderID;
        this.sessionSubID = builder.sessionSubID;
        this.username = builder.username;
        this.side = builder.side;
        this.orderQty = builder.orderQty;
        this.leavesQty = builder.orderQty;
        this.cumQty = 0;
        this.price = builder.price;
        this.ordType = builder.ordType;
        this.symbol = builder.symbol;
        this.maturityDate = builder.maturityDate;
        this.strikePrice = builder.strikePrice;
        this.putOrCall = builder.putOrCall;
        this.capacity = builder.capacity;
        this.account = builder.account;
        this.clearingFirm = builder.clearingFirm;
        this.clearingAccount = builder.clearingAccount;
        this.openClose = builder.openClose;
        this.state = OrderState.PENDING_NEW;
        this.createdAt = Instant.now();
        this.lastModified = Instant.now();
        this.routingInst = builder.routingInst;
        this.receivedSequence = builder.receivedSequence;
        this.lastSentSequence = 0;
        this.optionalFields = new HashMap<>(builder.optionalFields);
        this.matchingUnit = builder.matchingUnit;
    }

    // State transitions
    public void acknowledge() {
        if (state != OrderState.PENDING_NEW) throw new IllegalStateException("Cannot acknowledge order in state: " + state);

        this.state = OrderState.LIVE;
        this.lastModified = Instant.now();
    }

    public void reject(String reason) {
        this.state = OrderState.REJECTED;
        this.lastModified = Instant.now();
        this.optionalFields.put("rejectReason", reason);
    }

    public void cancel() {
        if (!state.isCancellable()) throw new IllegalStateException("Cannot cancel order in state: " + state);

        this.state = OrderState.CANCELLED;
        this.lastModified = Instant.now();
    }

    public void fill(int qty, BigDecimal execPrice) {
        if (qty <= 0 || qty > leavesQty) throw new IllegalArgumentException("Invalid fill quantity: " + qty);

        this.cumQty += qty;
        this.leavesQty -= qty;

        if (this.leavesQty == 0) this.state = OrderState.FILLED;
        else this.state = OrderState.PARTIALLY_FILLED;

        this.lastModified = Instant.now();
    }

    public void expire() {
        if (leavesQty > 0) {
            this.state = OrderState.EXPIRED;
            this.lastModified = Instant.now();
        }
    }

    // Getters
    public String getClOrdID() { return clOrdID; }
    public long getOrderID() { return orderID; }
    public String getSessionSubID() { return sessionSubID; }
    public String getUsername() { return username; }
    public Side getSide() { return side; }
    public int getOrderQty() { return orderQty; }
    public int getLeavesQty() { return leavesQty; }
    public int getCumQty() { return cumQty; }
    public BigDecimal getPrice() { return price; }
    public OrdType getOrdType() { return ordType; }
    public String getSymbol() { return symbol; }
    public Instant getMaturityDate() { return maturityDate; }
    public BigDecimal getStrikePrice() { return strikePrice; }
    public PutOrCall getPutOrCall() { return putOrCall; }
    public Capacity getCapacity() { return capacity; }
    public String getAccount() { return account; }
    public String getClearingFirm() { return clearingFirm; }
    public String getClearingAccount() { return clearingAccount; }
    public OpenClose getOpenClose() { return openClose; }
    public OrderState getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastModified() { return lastModified; }
    public RoutingInst getRoutingInst() { return routingInst; }
    public int getReceivedSequence() { return receivedSequence; }
    public int getLastSentSequence() { return lastSentSequence; }
    public Map<String, Object> getOptionalFields() { return new HashMap<>(optionalFields); }

    public void setLastSentSequence(int seq) {
        this.lastSentSequence = seq;
    }

    public boolean isLive() {
        return state == OrderState.LIVE || state == OrderState.PARTIALLY_FILLED;
    }

    public boolean isFilled() {
        return state == OrderState.FILLED;
    }

    public boolean isCancelled() {
        return state == OrderState.CANCELLED;
    }

    public boolean isRejected() {
        return state == OrderState.REJECTED;
    }

    public void setLeavesQty(int leavesQty) {
        if (leavesQty < 0 || leavesQty > this.orderQty) throw new IllegalArgumentException("Invalid leavesQty: " + leavesQty);

        this.leavesQty = leavesQty;
        this.lastModified = Instant.now();
    }

    @Override
    public String toString() {
        return "Order{" +
                "clOrdID='" + clOrdID + '\'' +
                ", orderID=" + orderID +
                ", symbol='" + symbol + '\'' +
                ", side=" + side +
                ", qty=" + orderQty +
                ", price=" + price +
                ", state=" + state +
                '}';
    }

    // Builder Pattern
    public static Builder builder() {
        return new Builder();
    }

    public byte getMatchingUnit() {
        return matchingUnit;
    }

    public static class Builder {
        private String clOrdID;
        private long orderID;
        private String sessionSubID;
        private String username;
        private Side side;
        private int orderQty;
        private BigDecimal price;
        private OrdType ordType = OrdType.LIMIT;
        private String symbol;
        private Instant maturityDate;
        private BigDecimal strikePrice;
        private PutOrCall putOrCall;
        private Capacity capacity;
        private String account = "";
        private String clearingFirm = "";
        private String clearingAccount = "";
        private OpenClose openClose = OpenClose.NONE;
        private RoutingInst routingInst = RoutingInst.BOOK_ONLY;
        private int receivedSequence;
        private final Map<String, Object> optionalFields = new HashMap<>();
        private byte matchingUnit = 0;

        public Builder matchingUnit(byte matchingUnit) {
            this.matchingUnit = matchingUnit;
            return this;
        }

        public Builder clOrdID(String clOrdID) {
            this.clOrdID = clOrdID;
            return this;
        }

        public Builder orderID(long orderID) {
            this.orderID = orderID;
            return this;
        }

        public Builder sessionSubID(String sessionSubID) {
            this.sessionSubID = sessionSubID;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder side(Side side) {
            this.side = side;
            return this;
        }

        public Builder orderQty(int orderQty) {
            this.orderQty = orderQty;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder ordType(OrdType ordType) {
            this.ordType = ordType;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder maturityDate(Instant maturityDate) {
            this.maturityDate = maturityDate;
            return this;
        }

        public Builder strikePrice(BigDecimal strikePrice) {
            this.strikePrice = strikePrice;
            return this;
        }

        public Builder putOrCall(PutOrCall putOrCall) {
            this.putOrCall = putOrCall;
            return this;
        }

        public Builder capacity(Capacity capacity) {
            this.capacity = capacity;
            return this;
        }

        public Builder account(String account) {
            this.account = account;
            return this;
        }

        public Builder clearingFirm(String clearingFirm) {
            this.clearingFirm = clearingFirm;
            return this;
        }

        public Builder clearingAccount(String clearingAccount) {
            this.clearingAccount = clearingAccount;
            return this;
        }

        public Builder openClose(OpenClose openClose) {
            this.openClose = openClose;
            return this;
        }

        public Builder routingInst(RoutingInst routingInst) {
            this.routingInst = routingInst;
            return this;
        }

        public Builder receivedSequence(int receivedSequence) {
            this.receivedSequence = receivedSequence;
            return this;
        }

        public Builder optionalField(String key, Object value) {
            this.optionalFields.put(key, value);
            return this;
        }

        public Order build() {
            // Validations
            if (clOrdID == null || clOrdID.isEmpty()) throw new IllegalArgumentException("ClOrdID is required");

            if (orderQty <= 0 || orderQty > 999999) throw new IllegalArgumentException("Invalid OrderQty: " + orderQty);

            if (side == null) throw new IllegalArgumentException("Side is required");

            if (symbol == null || symbol.isEmpty()) throw new IllegalArgumentException("Symbol is required");

            if (ordType == OrdType.LIMIT && price == null) throw new IllegalArgumentException("Price is required for limit orders");

            return new Order(this);
        }
    }
}