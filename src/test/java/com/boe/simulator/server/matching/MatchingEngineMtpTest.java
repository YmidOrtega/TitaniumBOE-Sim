package com.boe.simulator.server.matching;

import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.PreventMatch;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.protocol.types.TimeInForce;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class MatchingEngineMtpTest {

    private static final String SYMBOL = "AAPL";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    private MatchingEngine engine;
    private final AtomicLong ids = new AtomicLong(1);
    private final List<String> cancelled = new ArrayList<>();
    private final List<String> restated = new ArrayList<>();

    @BeforeEach
    void setUp() {
        engine = engine(false);
    }

    private MatchingEngine engine(boolean allowSelfTrade) {
        MatchingEngine e = new MatchingEngine(orderRepository, tradeRepository, allowSelfTrade);
        e.addEventListener(new MatchingEngine.MatchingEventListener() {
            @Override
            public void onOrderCancelled(Order order, byte reason, OrderBook book) {
                cancelled.add(order.getClOrdID() + ":" + (char) reason);
            }

            @Override
            public void onOrderRestated(Order order, byte reason, boolean incoming, OrderBook book) {
                restated.add(order.getClOrdID() + ":" + (char) reason + ":" + (incoming ? "in" : "rest"));
            }
        });
        return e;
    }

    private Order order(String id, Side side, int qty, String user, String mtp) {
        return order(id, side, qty, user, mtp, "FIRM", TimeInForce.DAY);
    }

    private Order order(String id, Side side, int qty, String user, String mtp, String clearingFirm, TimeInForce tif) {
        Order o = Order.builder().clOrdID(id).orderID(ids.getAndIncrement()).side(side)
                .price(new BigDecimal("10.00")).orderQty(qty).symbol(SYMBOL).ordType(OrdType.LIMIT)
                .username(user).clearingFirm(clearingFirm).timeInForce(tif)
                .preventMatch(mtp != null ? PreventMatch.fromBytes(mtp.getBytes()) : null).build();
        o.acknowledge();
        return o;
    }

    @Test
    @DisplayName("Defecto de puerto (O, F): la propia en reposo se cancela con V y la entrante sigue contra la ajena")
    void portDefaultCancelsTheRestingOwnOrder() {
        Order own = order("S1", Side.SELL, 5, "u1", null);
        Order other = order("S2", Side.SELL, 5, "u2", null);
        engine.processOrder(own);
        engine.processOrder(other);

        List<Trade> trades = engine.processOrder(order("B1", Side.BUY, 5, "u1", null));

        assertEquals(1, trades.size());
        assertEquals("S2", trades.get(0).sellClOrdID());
        assertEquals(OrderState.CANCELLED, own.getState());
        assertEquals((byte) 'V', own.getCancelReason());
        assertEquals(List.of("S1:V"), cancelled);
    }

    @Test
    @DisplayName("N: se cancela la entrante con V y la propia en reposo sigue en el libro")
    void cancelNewest() {
        Order own = order("S1", Side.SELL, 5, "u1", null);
        engine.processOrder(own);

        Order inbound = order("B1", Side.BUY, 5, "u1", "NF\0");
        assertTrue(engine.processOrder(inbound).isEmpty());

        assertEquals(OrderState.CANCELLED, inbound.getState());
        assertEquals((byte) 'V', inbound.getCancelReason());
        assertEquals(OrderState.LIVE, own.getState());
        assertTrue(cancelled.isEmpty(), "The inbound cancel is reported in its own response, not as unsolicited");
    }

    @Test
    @DisplayName("B: se cancelan las dos")
    void cancelBoth() {
        Order own = order("S1", Side.SELL, 5, "u1", null);
        engine.processOrder(own);
        Order inbound = order("B1", Side.BUY, 5, "u1", "BF\0");

        engine.processOrder(inbound);

        assertEquals(OrderState.CANCELLED, inbound.getState());
        assertEquals(OrderState.CANCELLED, own.getState());
    }

    @Test
    @DisplayName("S: se cancela la más pequeña; si son iguales, las dos")
    void cancelSmallest() {
        Order big = order("S1", Side.SELL, 8, "u1", null);
        engine.processOrder(big);
        Order small = order("B1", Side.BUY, 3, "u1", "SF\0");
        engine.processOrder(small);
        assertEquals(OrderState.CANCELLED, small.getState());
        assertEquals(OrderState.LIVE, big.getState());

        Order equal = order("B2", Side.BUY, 8, "u1", "SF\0");
        engine.processOrder(equal);
        assertEquals(OrderState.CANCELLED, equal.getState());
        assertEquals(OrderState.CANCELLED, big.getState());
    }

    @Test
    @DisplayName("D con la entrante mayor: se cancela la propia y la entrante baja OrderQty y LeavesQty")
    void decrementInboundWhenLarger() {
        Order own = order("S1", Side.SELL, 3, "u1", null);
        Order other = order("S2", Side.SELL, 10, "u2", null);
        engine.processOrder(own);
        engine.processOrder(other);
        Order inbound = order("B1", Side.BUY, 10, "u1", "DF\0");

        List<Trade> trades = engine.processOrder(inbound);

        assertEquals(OrderState.CANCELLED, own.getState());
        assertEquals(List.of("B1:W:in"), restated);
        assertEquals(7, inbound.getEffectiveOrderQty());
        assertEquals(7, trades.get(0).quantity(), "The decremented remainder trades against the next order");
        assertEquals(OrderState.FILLED, inbound.getState());
    }

    @Test
    @DisplayName("D con la entrante menor y la propia también D: se cancela la entrante y la propia se decrementa")
    void decrementRestingWhenLarger() {
        Order own = order("S1", Side.SELL, 10, "u1", "DF\0");
        engine.processOrder(own);
        Order inbound = order("B1", Side.BUY, 4, "u1", "DF\0");

        engine.processOrder(inbound);

        assertEquals(OrderState.CANCELLED, inbound.getState());
        assertEquals(6, own.getLeavesQty());
        assertEquals(6, own.getEffectiveOrderQty());
        assertEquals(List.of("S1:W:rest"), restated);
        assertEquals(6, engine.getOrderBook(SYMBOL).orElseThrow().getTotalAskQuantity());
    }

    @Test
    @DisplayName("D contra una propia mayor que no pide decremento: se cancelan las dos")
    void decrementAgainstLargerNonDecrementingRestingCancelsBoth() {
        Order own = order("S1", Side.SELL, 10, "u1", null);
        engine.processOrder(own);
        Order inbound = order("B1", Side.BUY, 4, "u1", "DF\0");

        engine.processOrder(inbound);

        assertEquals(OrderState.CANCELLED, inbound.getState());
        assertEquals(OrderState.CANCELLED, own.getState());
        assertTrue(restated.isEmpty());
    }

    @Test
    @DisplayName("d: solo baja LeavesQty, OrderQty se mantiene")
    void decrementLeavesOnly() {
        Order own = order("S1", Side.SELL, 10, "u1", "dF\0");
        engine.processOrder(own);

        engine.processOrder(order("B1", Side.BUY, 4, "u1", "dF\0"));

        assertEquals(6, own.getLeavesQty());
        assertEquals(10, own.getEffectiveOrderQty());
    }

    @Test
    @DisplayName("Nivel M (EFID): con distinta ClearingFirm no se previene y se ejecuta")
    void efidLevelNeedsTheSameClearingFirm() {
        engine.processOrder(order("S1", Side.SELL, 5, "u1", "OM\0", "AAAA", TimeInForce.DAY));

        List<Trade> trades = engine.processOrder(order("B1", Side.BUY, 5, "u1", "OM\0", "BBBB", TimeInForce.DAY));

        assertEquals(1, trades.size());
    }

    @Test
    @DisplayName("Trading Group distinto en las dos: no se previene")
    void differentTradingGroupsDoNotPrevent() {
        engine.processOrder(order("S1", Side.SELL, 5, "u1", "OF1"));

        assertEquals(1, engine.processOrder(order("B1", Side.BUY, 5, "u1", "OF2")).size());
    }

    @Test
    @DisplayName("Sin defecto de puerto (allowSelfTrade): la orden en reposo sin PreventMatch no se protege")
    void withoutPortDefaultBothOrdersNeedAnInstruction() {
        MatchingEngine permissive = engine(true);
        permissive.processOrder(order("S1", Side.SELL, 5, "u1", null));

        assertEquals(1, permissive.processOrder(order("B1", Side.BUY, 5, "u1", "NF\0")).size());
    }

    @Test
    @DisplayName("FOK delante de una propia con N: se cancela sin ejecutar nada")
    void fokStopsAtAPreventedOrderThatWouldCancelIt() {
        Order own = order("S1", Side.SELL, 5, "u1", null);
        Order other = order("S2", Side.SELL, 5, "u2", null);
        engine.processOrder(own);
        engine.processOrder(other);

        Order fok = order("B1", Side.BUY, 5, "u1", "NF\0", "FIRM", TimeInForce.FOK);

        assertTrue(engine.processOrder(fok).isEmpty());
        assertEquals(OrderState.CANCELLED, fok.getState());
        assertEquals(OrderState.LIVE, own.getState());
    }

    @Test
    @DisplayName("PreventMatch inválido: se rechaza al parsear")
    void invalidPreventMatchIsRejected() {
        byte[] badModifier = "XF\0".getBytes();
        byte[] badLevel = "OX\0".getBytes();
        assertThrows(IllegalArgumentException.class, () -> PreventMatch.fromBytes(badModifier));
        assertThrows(IllegalArgumentException.class, () -> PreventMatch.fromBytes(badLevel));
        assertNull(PreventMatch.fromBytes(new byte[3]));
    }
}
