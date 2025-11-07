package com.boe.simulator.api.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SymbolService {

    private final Map<String, SymbolInfo> symbols;

    public SymbolService() {
        this.symbols = new HashMap<>();
        initializeSymbols();
    }

    private void initializeSymbols() {
        // Tech stocks
        addSymbol("AAPL", "Apple Inc.", "NASDAQ", new BigDecimal("150.00"));
        addSymbol("MSFT", "Microsoft Corporation", "NASDAQ", new BigDecimal("380.00"));
        addSymbol("GOOGL", "Alphabet Inc.", "NASDAQ", new BigDecimal("140.00"));
        addSymbol("AMZN", "Amazon.com Inc.", "NASDAQ", new BigDecimal("175.00"));
        addSymbol("META", "Meta Platforms Inc.", "NASDAQ", new BigDecimal("485.00"));
        addSymbol("TSLA", "Tesla Inc.", "NASDAQ", new BigDecimal("250.00"));
        addSymbol("NVDA", "NVIDIA Corporation", "NASDAQ", new BigDecimal("500.00"));
        addSymbol("NFLX", "Netflix Inc.", "NASDAQ", new BigDecimal("650.00"));
        addSymbol("AMD", "Advanced Micro Devices", "NASDAQ", new BigDecimal("145.00"));

        // Other
        addSymbol("DIS", "Walt Disney Company", "NYSE", new BigDecimal("95.00"));
    }

    private void addSymbol(String symbol, String name, String exchange, BigDecimal referencePrice) {
        symbols.put(symbol, new SymbolInfo(symbol, name, exchange, referencePrice));
    }

    public List<SymbolInfo> getAllSymbols() {
        return new ArrayList<>(symbols.values());
    }

    public SymbolInfo getSymbol(String symbol) {
        return symbols.get(symbol);
    }

    public boolean symbolExists(String symbol) {
        return symbols.containsKey(symbol);
    }

    public void addSymbol(SymbolInfo symbolInfo) {
        symbols.put(symbolInfo.symbol(), symbolInfo);
    }

    public record SymbolInfo(
            String symbol,
            String name,
            String exchange,
            BigDecimal referencePrice
    ) {}
}