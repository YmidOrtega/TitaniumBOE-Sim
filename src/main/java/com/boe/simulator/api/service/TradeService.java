package com.boe.simulator.api.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import com.boe.simulator.api.dto.TradeDTO;
import com.boe.simulator.server.matching.TradeRepository;

public class TradeService {
    private final TradeRepository tradeRepository;

    public TradeService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    public List<TradeDTO> getRecentTrades(int limit) {
        return tradeRepository.findLatest(limit).stream()
                .map(TradeDTO::fromTrade)
                .collect(Collectors.toList());
    }

    public List<TradeDTO> getTradesBySymbol(String symbol, int limit) {
        return tradeRepository.findLatestBySymbol(symbol, limit).stream()
                .map(TradeDTO::fromTrade)
                .collect(Collectors.toList());
    }

    public List<TradeDTO> getUserTrades(String username, int limit) {
        Instant oneDayAgo = Instant.now().minus(1, ChronoUnit.DAYS);

        return tradeRepository.findByUsername(username).stream()
                .filter(t -> t.getExecutionTime().isAfter(oneDayAgo))
                .sorted((t1, t2) -> t2.getExecutionTime().compareTo(t1.getExecutionTime()))
                .limit(limit)
                .map(TradeDTO::fromTrade)
                .collect(Collectors.toList());
    }
}