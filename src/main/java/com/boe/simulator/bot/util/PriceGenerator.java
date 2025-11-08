package com.boe.simulator.bot.util;

import java.math.BigDecimal;
import java.util.Map;

public class PriceGenerator {

    private static final Map<String, BigDecimal> DEFAULT_PRICES = Map.of(
            "AAPL", new BigDecimal("150.00"),
            "MSFT", new BigDecimal("380.00"),
            "GOOGL", new BigDecimal("140.00"),
            "AMZN", new BigDecimal("175.00"),
            "META", new BigDecimal("485.00"),
            "TSLA", new BigDecimal("250.00"),
            "NVDA", new BigDecimal("500.00"),
            "NFLX", new BigDecimal("650.00"),
            "AMD", new BigDecimal("145.00"),
            "DIS", new BigDecimal("95.00")
    );

    public static BigDecimal getDefaultPrice(String symbol) {
        return DEFAULT_PRICES.getOrDefault(symbol, new BigDecimal("100.00"));
    }
}