package com.boe.simulator.server.matching;

import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderRepository;
import com.boe.simulator.server.order.OrderState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MatchingEngineSelfTradeTest {

    private static final String SYMBOL = "AAPL";
    private static final String SAME_USER = "trader-1";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    private AtomicLong orderIdSeq;

    @BeforeEach
    void setUp() {
        orderIdSeq = new AtomicLong(1);
    }

    private Order order(String clOrdID, Side side, String price, int qty, String username) {
        Order o = Order.builder()
                .clOrdID(clOrdID)
                .orderID(orderIdSeq.getAndIncrement())
                .side(side)
                .price(new BigDecimal(price))
                .orderQty(qty)
                .symbol(SYMBOL)
                .ordType(OrdType.LIMIT)
                .username(username)
                .build();
        o.acknowledge();
        return o;
    }

    @Test
    @DisplayName("Con prevención activa, cruzarse consigo mismo cancela la orden pasiva y no genera trade")
    void preventsSelfTradeByCancellingThePassiveOrder() {
        MatchingEngine engine = new MatchingEngine(orderRepository, tradeRepository);
        Order passive = order("S1", Side.SELL, "100.00", 10, SAME_USER);
        engine.processOrder(passive);

        Order aggressive = order("B1", Side.BUY, "100.00", 10, SAME_USER);
        List<Trade> trades = engine.processOrder(aggressive);

        assertTrue(trades.isEmpty(), "Un wash trade es manipulación de mercado: no debe ejecutarse");
        assertEquals(OrderState.CANCELLED, passive.getState(),
                "La política es cancelar la pasiva (cancel oldest)");
        assertEquals(10, aggressive.getLeavesQty(),
                "La agresiva conserva su cantidad y queda descansando");
        assertEquals(OrderState.LIVE, aggressive.getState());
    }

    @Test
    @DisplayName("Tras cancelar la pasiva propia, la agresiva sigue buscando contrapartida ajena")
    void continuesMatchingAgainstOtherUsersAfterSelfTradePrevention() {
        MatchingEngine engine = new MatchingEngine(orderRepository, tradeRepository);
        Order ownPassive = order("S_OWN", Side.SELL, "100.00", 10, SAME_USER);
        Order otherPassive = order("S_OTHER", Side.SELL, "100.50", 10, "trader-2");
        engine.processOrder(ownPassive);
        engine.processOrder(otherPassive);

        List<Trade> trades = engine.processOrder(order("B1", Side.BUY, "100.50", 10, SAME_USER));

        assertEquals(1, trades.size(), "Debe cruzar contra el vendedor ajeno");
        assertEquals("S_OTHER", trades.get(0).sellClOrdID());
        assertEquals(OrderState.CANCELLED, ownPassive.getState());
        assertEquals(OrderState.FILLED, otherPassive.getState());
    }

    @Test
    @DisplayName("Con allowSelfTrade activado el cruce sí se ejecuta")
    void allowsSelfTradeWhenExplicitlyEnabled() {
        MatchingEngine engine = new MatchingEngine(orderRepository, tradeRepository, true);
        Order passive = order("S1", Side.SELL, "100.00", 10, SAME_USER);
        engine.processOrder(passive);

        List<Trade> trades = engine.processOrder(order("B1", Side.BUY, "100.00", 10, SAME_USER));

        assertEquals(1, trades.size());
        assertTrue(trades.get(0).isSelfTrade());
        assertEquals(OrderState.FILLED, passive.getState());
    }

    @Test
    @DisplayName("Usuarios distintos al mismo precio cruzan con normalidad")
    void differentUsersMatchNormally() {
        MatchingEngine engine = new MatchingEngine(orderRepository, tradeRepository);
        engine.processOrder(order("S1", Side.SELL, "100.00", 10, "trader-1"));

        List<Trade> trades = engine.processOrder(order("B1", Side.BUY, "100.00", 10, "trader-2"));

        assertEquals(1, trades.size());
        assertTrue(!trades.get(0).isSelfTrade());
    }
}
