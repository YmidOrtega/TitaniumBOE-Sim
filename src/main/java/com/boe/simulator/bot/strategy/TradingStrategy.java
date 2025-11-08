package com.boe.simulator.bot.strategy;

import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.order.OrderManager;

public interface TradingStrategy {

    // Execute trading action for the given symbol
    void execute(String symbol);

    // Initialize strategy with required dependencies
    void initialize(OrderManager orderManager, MatchingEngine matchingEngine);

    // Cleanup resources when strategy is stopped
    default void cleanup() {
        // Default: no cleanup needed
    }

    String getStrategyName();
}