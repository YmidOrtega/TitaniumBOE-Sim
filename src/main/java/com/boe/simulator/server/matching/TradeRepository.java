package com.boe.simulator.server.matching;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TradeRepository {

    void save(Trade trade);

    Optional<Trade> findById(long tradeId);

    List<Trade> findBySymbol(String symbol);

    List<Trade> findBySymbol(String symbol, Instant start, Instant end);

    List<Trade> findByUsername(String username);

    List<Trade> findByOrderId(long orderId);

    List<Trade> findByDateRange(Instant start, Instant end);

    List<Trade> findLatest(int limit);

    List<Trade> findLatestBySymbol(String symbol, int limit);

    long getTotalVolumeBySymbol(String symbol);

    BigDecimal getTotalNotionalBySymbol(String symbol);

    long count();

    long countBySymbol(String symbol);

    int deleteOlderThan(Instant cutoffDate);

    List<Trade> search(TradeSearchCriteria criteria);

    record TradeSearchCriteria(
            String symbol,
            String username,
            Long orderId,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            Integer minQuantity,
            Integer maxQuantity,
            Instant startDate,
            Instant endDate,
            Integer limit
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String symbol;
            private String username;
            private Long orderId;
            private BigDecimal minPrice;
            private BigDecimal maxPrice;
            private Integer minQuantity;
            private Integer maxQuantity;
            private Instant startDate;
            private Instant endDate;
            private Integer limit;

            public Builder symbol(String symbol) {
                this.symbol = symbol;
                return this;
            }

            public Builder username(String username) {
                this.username = username;
                return this;
            }

            public Builder orderId(long orderId) {
                this.orderId = orderId;
                return this;
            }

            public Builder priceRange(BigDecimal min, BigDecimal max) {
                this.minPrice = min;
                this.maxPrice = max;
                return this;
            }

            public Builder quantityRange(int min, int max) {
                this.minQuantity = min;
                this.maxQuantity = max;
                return this;
            }

            public Builder dateRange(Instant start, Instant end) {
                this.startDate = start;
                this.endDate = end;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public TradeSearchCriteria build() {
                return new TradeSearchCriteria(
                        symbol, username, orderId,
                        minPrice, maxPrice,
                        minQuantity, maxQuantity,
                        startDate, endDate, limit
                );
            }
        }
    }
}