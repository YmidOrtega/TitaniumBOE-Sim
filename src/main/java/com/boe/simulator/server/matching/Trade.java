package com.boe.simulator.server.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class Trade {

    private final long tradeId;
    private final String symbol;

    // Aggressive order (taker)
    private final long buyOrderId;
    private final String buyClOrdID;
    private final String buyUsername;

    // Passive order (maker)
    private final long sellOrderId;
    private final String sellClOrdID;
    private final String sellUsername;

    // Details of the execution
    private final int quantity;
    private final BigDecimal price;
    private final Instant executionTime;

    // Metadata
    private final byte matchingUnit;
    private final String clearingFirm;

    private Trade(Builder builder) {
        this.tradeId = builder.tradeId;
        this.symbol = builder.symbol;
        this.buyOrderId = builder.buyOrderId;
        this.buyClOrdID = builder.buyClOrdID;
        this.buyUsername = builder.buyUsername;
        this.sellOrderId = builder.sellOrderId;
        this.sellClOrdID = builder.sellClOrdID;
        this.sellUsername = builder.sellUsername;
        this.quantity = builder.quantity;
        this.price = builder.price;
        this.executionTime = builder.executionTime != null ? builder.executionTime : Instant.now();
        this.matchingUnit = builder.matchingUnit;
        this.clearingFirm = builder.clearingFirm;
    }

    // Getters
    public long getTradeId() { return tradeId; }
    public String getSymbol() { return symbol; }
    public long getBuyOrderId() { return buyOrderId; }
    public String getBuyClOrdID() { return buyClOrdID; }
    public String getBuyUsername() { return buyUsername; }
    public long getSellOrderId() { return sellOrderId; }
    public String getSellClOrdID() { return sellClOrdID; }
    public String getSellUsername() { return sellUsername; }
    public int getQuantity() { return quantity; }
    public BigDecimal getPrice() { return price; }
    public Instant getExecutionTime() { return executionTime; }
    public byte getMatchingUnit() { return matchingUnit; }
    public String getClearingFirm() { return clearingFirm; }

    public BigDecimal getNotionalValue() {
        return price.multiply(BigDecimal.valueOf(quantity));
    }

    public boolean isSelfTrade() {
        return buyUsername.equals(sellUsername);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Trade trade = (Trade) o;
        return tradeId == trade.tradeId;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tradeId);
    }

    @Override
    public String toString() {
        return String.format("Trade{id=%d, symbol='%s', qty=%d, price=%s, buy=%s, sell=%s}", tradeId, symbol, quantity, price, buyClOrdID, sellClOrdID);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long tradeId;
        private String symbol;
        private long buyOrderId;
        private String buyClOrdID;
        private String buyUsername;
        private long sellOrderId;
        private String sellClOrdID;
        private String sellUsername;
        private int quantity;
        private BigDecimal price;
        private Instant executionTime;
        private byte matchingUnit;
        private String clearingFirm;

        public Builder tradeId(long tradeId) {
            this.tradeId = tradeId;
            return this;
        }

        public Builder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public Builder buyOrderId(long buyOrderId) {
            this.buyOrderId = buyOrderId;
            return this;
        }

        public Builder buyClOrdID(String buyClOrdID) {
            this.buyClOrdID = buyClOrdID;
            return this;
        }

        public Builder buyUsername(String buyUsername) {
            this.buyUsername = buyUsername;
            return this;
        }

        public Builder sellOrderId(long sellOrderId) {
            this.sellOrderId = sellOrderId;
            return this;
        }

        public Builder sellClOrdID(String sellClOrdID) {
            this.sellClOrdID = sellClOrdID;
            return this;
        }

        public Builder sellUsername(String sellUsername) {
            this.sellUsername = sellUsername;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder price(BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder executionTime(Instant executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public Builder matchingUnit(byte matchingUnit) {
            this.matchingUnit = matchingUnit;
            return this;
        }

        public Builder clearingFirm(String clearingFirm) {
            this.clearingFirm = clearingFirm;
            return this;
        }

        public Trade build() {
            if (symbol == null || symbol.isEmpty()) throw new IllegalArgumentException("Symbol is required");
            if (quantity <= 0) throw new IllegalArgumentException("Quantity must be positive");
            if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) throw new IllegalArgumentException("Price must be positive");

            return new Trade(this);
        }
    }
}