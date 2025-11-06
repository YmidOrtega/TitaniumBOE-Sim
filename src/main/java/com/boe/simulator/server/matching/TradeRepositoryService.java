package com.boe.simulator.server.matching;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.util.SerializationUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TradeRepositoryService implements TradeRepository {
    private static final Logger LOGGER = Logger.getLogger(TradeRepositoryService.class.getName());

    private final RocksDBManager dbManager;
    private final SerializationUtil serializer;

    private static final String CF_TRADES = RocksDBManager.CF_MESSAGES;

    public TradeRepositoryService(RocksDBManager dbManager) {
        this.dbManager = dbManager;
        this.serializer = SerializationUtil.getInstance();
        LOGGER.info("TradeRepositoryService initialized");
    }

    @Override
    public void save(Trade trade) {
        try {
            String key = buildKey(trade.getTradeId());
            PersistedTrade persistedTrade = PersistedTrade.fromTrade(trade);
            byte[] value = serializer.serialize(persistedTrade);

            dbManager.put(CF_TRADES, key.getBytes(), value);

            saveIndexes(trade);

            LOGGER.log(Level.FINE, "Saved trade: {0}", trade.getTradeId());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save trade: " + trade.getTradeId(), e);
            throw new RuntimeException("Failed to save trade", e);
        }
    }

    private void saveIndexes(Trade trade) throws Exception {
        byte[] tradeIdBytes = String.valueOf(trade.getTradeId()).getBytes();

        // Symbol index
        String symbolKey = String.format("trade-symbol:%s:%d",
                trade.getSymbol(), trade.getExecutionTime().toEpochMilli());
        dbManager.put(CF_TRADES, symbolKey.getBytes(), tradeIdBytes);

        // Buy user index
        String buyUserKey = String.format("trade-user:%s:%d",
                trade.getBuyUsername(), trade.getExecutionTime().toEpochMilli());
        dbManager.put(CF_TRADES, buyUserKey.getBytes(), tradeIdBytes);

        // Sell user index
        String sellUserKey = String.format("trade-user:%s:%d",
                trade.getSellUsername(), trade.getExecutionTime().toEpochMilli());
        dbManager.put(CF_TRADES, sellUserKey.getBytes(), tradeIdBytes);

        // Date index
        String date = trade.getExecutionTime().toString().substring(0, 10);
        String dateKey = String.format("trade-date:%s:%d",
                date, trade.getExecutionTime().toEpochMilli());
        dbManager.put(CF_TRADES, dateKey.getBytes(), tradeIdBytes);
    }

    @Override
    public Optional<Trade> findById(long tradeId) {
        try {
            String key = buildKey(tradeId);
            byte[] data = dbManager.get(CF_TRADES, key.getBytes());

            if (data == null) return Optional.empty();

            PersistedTrade persistedTrade = serializer.deserialize(data, PersistedTrade.class);
            return Optional.of(persistedTrade.toTrade());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find trade: " + tradeId, e);
            return Optional.empty();
        }
    }

    @Override
    public List<Trade> findBySymbol(String symbol) {
        try {
            String prefix = String.format("trade-symbol:%s:", symbol);
            return findByIndexPrefix(prefix);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find trades by symbol: " + symbol, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Trade> findBySymbol(String symbol, Instant start, Instant end) {
        return findBySymbol(symbol).stream()
                .filter(t -> !t.getExecutionTime().isBefore(start)
                        && !t.getExecutionTime().isAfter(end))
                .collect(Collectors.toList());
    }

    @Override
    public List<Trade> findByUsername(String username) {
        try {
            String prefix = String.format("trade-user:%s:", username);
            return findByIndexPrefix(prefix);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find trades by username: " + username, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<Trade> findByOrderId(long orderId) {
        return findAll().stream()
                .filter(t -> t.getBuyOrderId() == orderId || t.getSellOrderId() == orderId)
                .collect(Collectors.toList());
    }

    @Override
    public List<Trade> findByDateRange(Instant start, Instant end) {
        return findAll().stream()
                .filter(t -> !t.getExecutionTime().isBefore(start)
                        && !t.getExecutionTime().isAfter(end))
                .sorted(Comparator.comparing(Trade::getExecutionTime))
                .collect(Collectors.toList());
    }

    @Override
    public List<Trade> findLatest(int limit) {
        return findAll().stream()
                .sorted(Comparator.comparing(Trade::getExecutionTime).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<Trade> findLatestBySymbol(String symbol, int limit) {
        return findBySymbol(symbol).stream()
                .sorted(Comparator.comparing(Trade::getExecutionTime).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public long getTotalVolumeBySymbol(String symbol) {
        return findBySymbol(symbol).stream()
                .mapToLong(Trade::getQuantity)
                .sum();
    }

    @Override
    public BigDecimal getTotalNotionalBySymbol(String symbol) {
        return findBySymbol(symbol).stream()
                .map(Trade::getNotionalValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public long count() {
        return findAll().size();
    }

    @Override
    public long countBySymbol(String symbol) {
        return findBySymbol(symbol).size();
    }

    @Override
    public int deleteOlderThan(Instant cutoffDate) {
        List<Trade> oldTrades = findAll().stream()
                .filter(t -> t.getExecutionTime().isBefore(cutoffDate))
                .toList();

        int deleted = 0;
        for (Trade trade : oldTrades) {
            try {
                String key = buildKey(trade.getTradeId());
                dbManager.delete(CF_TRADES, key.getBytes());
                deleted++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to delete trade: " + trade.getTradeId(), e);
            }
        }

        LOGGER.log(Level.INFO, "Deleted {0} trades older than {1}", new Object[]{
                deleted,
                cutoffDate
        });
        return deleted;
    }

    @Override
    public List<Trade> search(TradeSearchCriteria criteria) {
        List<Trade> results = findAll();

        if (criteria.symbol() != null) {
            results = results.stream()
                    .filter(t -> criteria.symbol().equals(t.getSymbol()))
                    .collect(Collectors.toList());
        }

        if (criteria.username() != null) {
            results = results.stream()
                    .filter(t -> criteria.username().equals(t.getBuyUsername())
                            || criteria.username().equals(t.getSellUsername()))
                    .collect(Collectors.toList());
        }

        if (criteria.orderId() != null) {
            results = results.stream()
                    .filter(t -> t.getBuyOrderId() == criteria.orderId()
                            || t.getSellOrderId() == criteria.orderId())
                    .collect(Collectors.toList());
        }

        if (criteria.minPrice() != null) {
            results = results.stream()
                    .filter(t -> t.getPrice().compareTo(criteria.minPrice()) >= 0)
                    .collect(Collectors.toList());
        }

        if (criteria.maxPrice() != null) {
            results = results.stream()
                    .filter(t -> t.getPrice().compareTo(criteria.maxPrice()) <= 0)
                    .collect(Collectors.toList());
        }

        if (criteria.minQuantity() != null) {
            results = results.stream()
                    .filter(t -> t.getQuantity() >= criteria.minQuantity())
                    .collect(Collectors.toList());
        }

        if (criteria.maxQuantity() != null) {
            results = results.stream()
                    .filter(t -> t.getQuantity() <= criteria.maxQuantity())
                    .collect(Collectors.toList());
        }

        if (criteria.startDate() != null) {
            results = results.stream()
                    .filter(t -> !t.getExecutionTime().isBefore(criteria.startDate()))
                    .collect(Collectors.toList());
        }

        if (criteria.endDate() != null) {
            results = results.stream()
                    .filter(t -> !t.getExecutionTime().isAfter(criteria.endDate()))
                    .collect(Collectors.toList());
        }

        results = results.stream()
                .sorted(Comparator.comparing(Trade::getExecutionTime).reversed())
                .collect(Collectors.toList());

        if (criteria.limit() != null && criteria.limit() > 0) {
            results = results.stream()
                    .limit(criteria.limit())
                    .collect(Collectors.toList());
        }

        return results;
    }

    private List<Trade> findAll() {
        try {
            List<Trade> trades = new ArrayList<>();
            List<byte[]> keys = dbManager.getKeysWithPrefix(CF_TRADES, "trade:".getBytes());

            for (byte[] key : keys) {
                byte[] data = dbManager.get(CF_TRADES, key);
                if (data != null) {
                    PersistedTrade persistedTrade = serializer.deserialize(data, PersistedTrade.class);
                    trades.add(persistedTrade.toTrade());
                }
            }

            return trades;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find all trades", e);
            return new ArrayList<>();
        }
    }

    private List<Trade> findByIndexPrefix(String prefix) throws Exception {
        List<Trade> trades = new ArrayList<>();
        List<byte[]> indexKeys = dbManager.getKeysWithPrefix(CF_TRADES, prefix.getBytes());

        for (byte[] indexKey : indexKeys) {
            byte[] tradeIdBytes = dbManager.get(CF_TRADES, indexKey);
            if (tradeIdBytes != null) {
                long tradeId = Long.parseLong(new String(tradeIdBytes));
                Optional<Trade> tradeOpt = findById(tradeId);
                tradeOpt.ifPresent(trades::add);
            }
        }

        return trades.stream()
                .sorted(Comparator.comparing(Trade::getExecutionTime).reversed())
                .collect(Collectors.toList());
    }

    private String buildKey(long tradeId) {
        return "trade:" + tradeId;
    }

    private record PersistedTrade(
            @JsonProperty("tradeId") long tradeId,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("buyOrderId") long buyOrderId,
            @JsonProperty("buyClOrdID") String buyClOrdID,
            @JsonProperty("buyUsername") String buyUsername,
            @JsonProperty("sellOrderId") long sellOrderId,
            @JsonProperty("sellClOrdID") String sellClOrdID,
            @JsonProperty("sellUsername") String sellUsername,
            @JsonProperty("quantity") int quantity,
            @JsonProperty("price") String price,
            @JsonProperty("executionTime") String executionTime,
            @JsonProperty("matchingUnit") byte matchingUnit,
            @JsonProperty("clearingFirm") String clearingFirm
    ) {
        @JsonCreator
        public PersistedTrade {}

        static PersistedTrade fromTrade(Trade trade) {
            return new PersistedTrade(
                    trade.getTradeId(),
                    trade.getSymbol(),
                    trade.getBuyOrderId(),
                    trade.getBuyClOrdID(),
                    trade.getBuyUsername(),
                    trade.getSellOrderId(),
                    trade.getSellClOrdID(),
                    trade.getSellUsername(),
                    trade.getQuantity(),
                    trade.getPrice().toString(),
                    trade.getExecutionTime().toString(),
                    trade.getMatchingUnit(),
                    trade.getClearingFirm()
            );
        }

        Trade toTrade() {
            return Trade.builder()
                    .tradeId(tradeId)
                    .symbol(symbol)
                    .buyOrderId(buyOrderId)
                    .buyClOrdID(buyClOrdID)
                    .buyUsername(buyUsername)
                    .sellOrderId(sellOrderId)
                    .sellClOrdID(sellClOrdID)
                    .sellUsername(sellUsername)
                    .quantity(quantity)
                    .price(new BigDecimal(price))
                    .executionTime(Instant.parse(executionTime))
                    .matchingUnit(matchingUnit)
                    .clearingFirm(clearingFirm)
                    .build();
        }
    }
}