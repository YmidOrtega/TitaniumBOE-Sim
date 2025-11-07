package com.boe.simulator.server.matching;

import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchingEngineTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    private MatchingEngine matchingEngine;

    @BeforeEach
    void setUp() {
        matchingEngine = new MatchingEngine(orderRepository, tradeRepository);
    }

    private Order createOrder(String clOrdID, byte side, double price, int quantity, String symbol) {
        Order order = Order.builder()
                .clOrdID(clOrdID)
                .side(side)
                .price(new BigDecimal(price))
                .orderQty(quantity)
                .symbol(symbol)
                .ordType((byte) '2') // Limit order
                .username("testUser")
                .orderID(1L)
                .build();
        // Acknowledge the order so it becomes LIVE
        order.acknowledge();
        return order;
    }

    @Test
    void processOrder_whenNoMatch_addsOrderToBook() {
        // Arrange
        Order buyOrder = createOrder("B1", (byte) 1, 100.0, 10, "AAPL");

        // Act
        List<Trade> trades = matchingEngine.processOrder(buyOrder);

        // Assert
        assertTrue(trades.isEmpty(), "No trades should occur when the book is empty");

        OrderBook book = matchingEngine.getOrderBook("AAPL").orElseThrow();
        assertEquals(1, book.size(), "Order book should contain one order");
        assertEquals(buyOrder, book.getTopBidOrders().get(0), "The order in the book should be the one we added");

        verify(orderRepository).save(buyOrder);
    }
}
