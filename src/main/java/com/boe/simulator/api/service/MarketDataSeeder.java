package com.boe.simulator.api.service;

import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderRepository;

import java.math.BigDecimal;
import java.util.logging.Logger;

public class MarketDataSeeder {
    private static final Logger LOGGER = Logger.getLogger(MarketDataSeeder.class.getName());

    private final MatchingEngine matchingEngine;
    private final OrderRepository orderRepository;
    private long orderIdCounter = 900000;

    public MarketDataSeeder(MatchingEngine matchingEngine, OrderRepository orderRepository) {
        this.matchingEngine = matchingEngine;
        this.orderRepository = orderRepository;
    }

    public void seedMarket() {
        LOGGER.info("Seeding market with initial orders...");

        seedSymbol("AAPL", new BigDecimal("149.50"), new BigDecimal("150.50"), 100);
        seedSymbol("MSFT", new BigDecimal("379.00"), new BigDecimal("381.00"), 50);
        seedSymbol("GOOGL", new BigDecimal("139.50"), new BigDecimal("140.50"), 75);

        LOGGER.info("Market seeding complete");
    }

    private void seedSymbol(String symbol, BigDecimal bidPrice, BigDecimal askPrice, int quantity) {
        // Create BUY order (bid)
        Order buyOrder = Order.builder()
                .clOrdID("SEED-BUY-" + symbol)
                .orderID(orderIdCounter++)
                .sessionSubID("MARKET-MAKER")
                .username("SYSTEM")
                .side((byte) 1)
                .orderQty(quantity)
                .price(bidPrice)
                .ordType((byte) 2)
                .symbol(symbol)
                .capacity((byte) 'M')
                .account("SEED")
                .build();

        buyOrder.acknowledge();

        // Create SELL order (ask)
        Order sellOrder = Order.builder()
                .clOrdID("SEED-SELL-" + symbol)
                .orderID(orderIdCounter++)
                .sessionSubID("MARKET-MAKER")
                .username("SYSTEM")
                .side((byte) 2)
                .orderQty(quantity)
                .price(askPrice)
                .ordType((byte) 2)
                .symbol(symbol)
                .capacity((byte) 'M')
                .account("SEED")
                .build();

        sellOrder.acknowledge();

        // Save orders first
        orderRepository.save(buyOrder);
        orderRepository.save(sellOrder);

        // Add to matching engine
        matchingEngine.processOrder(buyOrder);
        matchingEngine.processOrder(sellOrder);

        LOGGER.info("Seeded " + symbol + " with bid=" + bidPrice + " ask=" + askPrice);
    }
}