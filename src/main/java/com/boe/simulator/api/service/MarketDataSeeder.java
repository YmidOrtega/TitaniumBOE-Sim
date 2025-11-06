package com.boe.simulator.api.service;

import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderManager;

import java.math.BigDecimal;
import java.util.logging.Logger;

public class MarketDataSeeder {
    private static final Logger LOGGER = Logger.getLogger(MarketDataSeeder.class.getName());

    private final OrderManager orderManager;

    public MarketDataSeeder(OrderManager orderManager) {
        this.orderManager = orderManager;
    }

    public void seedMarket() {
        LOGGER.info("Seeding market with initial orders...");

        // AAPL
        seedSymbol("AAPL", BigDecimal.valueOf(149.50), BigDecimal.valueOf(150.50), 100);

        // MSFT
        seedSymbol("MSFT", BigDecimal.valueOf(379.00), BigDecimal.valueOf(381.00), 50);

        // GOOGL
        seedSymbol("GOOGL", BigDecimal.valueOf(139.50), BigDecimal.valueOf(140.50), 75);

        LOGGER.info("Market seeding complete");
    }

    private void seedSymbol(String symbol, BigDecimal bidPrice, BigDecimal askPrice, int qty) {
        try {
            // Create buy order (bid)
            Order buyOrder = Order.builder()
                    .clOrdID("SEED-BUY-" + symbol)
                    .orderID(System.currentTimeMillis())
                    .sessionSubID("SEEDER")
                    .username("MARKET_MAKER")
                    .side((byte) 1) // BUY
                    .orderQty(qty)
                    .price(bidPrice)
                    .ordType((byte) 2) // LIMIT
                    .symbol(symbol)
                    .capacity((byte) 'M') // Market Maker
                    .account("MM-SEED")
                    .clearingFirm("SEED")
                    .clearingAccount("SEED")
                    .openClose((byte) 'N')
                    .routingInst((byte) 'B')
                    .receivedSequence(0)
                    .matchingUnit((byte) 0)
                    .build();

            buyOrder.acknowledge();
            orderManager.getMatchingEngine().processOrder(buyOrder);

            // Create sell order (ask)
            Order sellOrder = Order.builder()
                    .clOrdID("SEED-SELL-" + symbol)
                    .orderID(System.currentTimeMillis() + 1)
                    .sessionSubID("SEEDER")
                    .username("MARKET_MAKER")
                    .side((byte) 2) // SELL
                    .orderQty(qty)
                    .price(askPrice)
                    .ordType((byte) 2) // LIMIT
                    .symbol(symbol)
                    .capacity((byte) 'M') // Market Maker
                    .account("MM-SEED")
                    .clearingFirm("SEED")
                    .clearingAccount("SEED")
                    .openClose((byte) 'N')
                    .routingInst((byte) 'B')
                    .receivedSequence(0)
                    .matchingUnit((byte) 0)
                    .build();

            sellOrder.acknowledge();
            orderManager.getMatchingEngine().processOrder(sellOrder);

            LOGGER.info("Seeded " + symbol + " with bid=" + bidPrice + " ask=" + askPrice);

        } catch (Exception e) {
            LOGGER.warning("Failed to seed symbol " + symbol + ": " + e.getMessage());
        }
    }
}