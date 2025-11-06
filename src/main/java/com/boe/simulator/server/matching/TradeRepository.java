package com.boe.simulator.server.matching.repository;

import com.boe.simulator.server.matching.Trade;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TradeRepository {

    void save(Trade trade);

    Optional<Trade> findById(String tradeId);

    List<Trade> findAll();

    List<Trade> findBySymbol(String symbol);

    List<Trade> findByUser(String username);

    List<Trade> findByDateRange(Instant start, Instant end);

    List<Trade> findRecent(int limit);

    long getTotalVolume(String symbol);

    long count();

    int deleteOlderThan(Instant cutoffDate);

    void deleteAll();
}