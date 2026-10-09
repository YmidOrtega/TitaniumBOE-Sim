package com.boe.simulator.server.order;

import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.PreventMatch;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.protocol.types.TimeInForce;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.TradeRepository;
import com.boe.simulator.server.persistence.RocksDBManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class OrderPersistenceRestartTest {

    @TempDir
    Path dataDir;

    private static void setCreatedAt(Order order, Instant createdAt) throws Exception {
        java.lang.reflect.Field field = Order.class.getDeclaredField("createdAt");
        field.setAccessible(true);
        field.set(order, createdAt);
    }

    @Test
    void gtcOrdersSurviveARestart_dayOrdersFromAPreviousDayExpire() throws Exception {
        OrderRepository repository = new OrderRepository(RocksDBManager.getInstance(dataDir.toString()));

        Order gtc = Order.builder().clOrdID("G1").orderID(5_000_000).username("u1").sessionSubID("TCP-1")
                .side(Side.BUY).orderQty(10).price(new BigDecimal("12.50")).ordType(OrdType.LIMIT).symbol("AAPL")
                .timeInForce(TimeInForce.GTC).maxFloor(4).customGroupId(9)
                .preventMatch(PreventMatch.fromBytes("NF\0".getBytes())).build();
        gtc.acknowledge();
        gtc.fill(2, new BigDecimal("12.50"));
        Order oldDay = Order.builder().clOrdID("D1").orderID(5_000_001).username("u1").sessionSubID("TCP-1")
                .side(Side.BUY).orderQty(1).price(new BigDecimal("1.00")).ordType(OrdType.LIMIT).symbol("AAPL").build();
        oldDay.acknowledge();
        setCreatedAt(oldDay, Instant.now().minus(Duration.ofDays(2)));
        repository.save(gtc);
        repository.save(oldDay);

        MatchingEngine engine = new MatchingEngine(repository, mock(TradeRepository.class));
        OrderManager restarted = new OrderManager(repository, new OrderValidator(), engine);

        Order restored = restarted.findByClOrdID("G1").orElseThrow();
        assertEquals(TimeInForce.GTC, restored.getTimeInForce());
        assertEquals(8, restored.getLeavesQty());
        assertEquals(4, restored.getMaxFloor());
        assertEquals(9, restored.getCustomGroupId());
        assertEquals('N', restored.getPreventMatch().modifier());
        assertEquals(new BigDecimal("12.5000"), restored.getAvgPx());
        assertTrue(restored.isCarried(), "Orders loaded at startup are carried orders (p.218)");
        assertEquals(new BigDecimal("12.50"), engine.getOrderBook("AAPL").orElseThrow().getBestBid(), "Back in the book");

        assertEquals(1, restarted.getActiveOrderCount(), "A Day order from a previous trading day expires instead of loading");

        assertTrue(restarted.processNewOrder(newOrder(), "u2").getOrder().getOrderID() > 5_000_001,
                "New OrderIDs continue after the highest one loaded");
    }

    private static com.boe.simulator.protocol.message.NewOrderMessage newOrder() {
        com.boe.simulator.protocol.message.NewOrderMessage msg = new com.boe.simulator.protocol.message.NewOrderMessage();
        msg.setClOrdID("N1");
        msg.setSide((byte) '2');
        msg.setOrderQty(1);
        msg.setSymbol("MSFT");
        msg.setPrice(new BigDecimal("30.00"));
        msg.setOrdType((byte) '2');
        msg.setCapacity((byte) 'C');
        return com.boe.simulator.protocol.message.NewOrderMessage.parse(msg.toBytes());
    }
}
