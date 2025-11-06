package com.boe.simulator.api.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SymbolService {
    private final Map<String, SymbolInfo> availableSymbols;

    public SymbolService() {
        this.availableSymbols = new ConcurrentHashMap<>();
        initializeDefaultSymbols();
    }

    private void initializeDefaultSymbols() {
        // Símbolos comunes para trading
        addSymbol("AAPL", "Apple Inc.", "NASDAQ", BigDecimal.valueOf(150.00));
        addSymbol("MSFT", "Microsoft Corporation", "NASDAQ", BigDecimal.valueOf(380.00));
        addSymbol("GOOGL", "Alphabet Inc.", "NASDAQ", BigDecimal.valueOf(140.00));
        addSymbol("AMZN", "Amazon.com Inc.", "NASDAQ", BigDecimal.valueOf(175.00));
        addSymbol("TSLA", "Tesla Inc.", "NASDAQ", BigDecimal.valueOf(250.00));
        addSymbol("META", "Meta Platforms Inc.", "NASDAQ", BigDecimal.valueOf(485.00));
        addSymbol("NVDA", "NVIDIA Corporation", "NASDAQ", BigDecimal.valueOf(500.00));
        addSymbol("AMD", "Advanced Micro Devices", "NASDAQ", BigDecimal.valueOf(145.00));
        addSymbol("NFLX", "Netflix Inc.", "NASDAQ", BigDecimal.valueOf(650.00));
        addSymbol("DIS", "Walt Disney Company", "NYSE", BigDecimal.valueOf(95.00));
    }

    private void addSymbol(String symbol, String name, String exchange, BigDecimal referencePrice) {
        availableSymbols.put(symbol, new SymbolInfo(symbol, name, exchange, referencePrice));
    }

    public List<SymbolInfo> getAllSymbols() {
        return List.copyOf(availableSymbols.values());
    }

    public SymbolInfo getSymbol(String symbol) {
        return availableSymbols.get(symbol);
    }

    public boolean isValidSymbol(String symbol) {
        return availableSymbols.containsKey(symbol);
    }

    public record SymbolInfo(
            String symbol,
            String name,
            String exchange,
            BigDecimal referencePrice
    ) {}
}