package com.boe.simulator.bot;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

public record BotConfig(
        String botId,
        String strategyType,
        Set<String> symbols,
        String username,
        Duration actionInterval,
        int minQuantity,
        int maxQuantity,
        BigDecimal priceVariation,
        boolean enabled
) {
    public static BotConfig marketMaker(String botId, Set<String> symbols, String username) {
        return new BotConfig(
                botId,
                "MARKET_MAKER",
                symbols,
                username,
                Duration.ofSeconds(5),
                50,
                200,
                new BigDecimal("0.02"), // 2% spread
                true
        );
    }

    public static BotConfig randomTrader(String botId, Set<String> symbols, String username) {
        return new BotConfig(
                botId,
                "RANDOM_TRADER",
                symbols,
                username,
                Duration.ofSeconds(3),
                10,
                100,
                new BigDecimal("0.01"), // 1% price variation
                true
        );
    }

    public static BotConfig trendFollower(String botId, Set<String> symbols, String username) {
        return new BotConfig(
                botId,
                "TREND_FOLLOWER",
                symbols,
                username,
                Duration.ofSeconds(4),
                25,
                150,
                new BigDecimal("0.005"), // 0.5% price variation
                true
        );
    }
}