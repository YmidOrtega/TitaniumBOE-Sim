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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MatchingEngineModifyTest {

    private static final String SYMBOL = "AAPL";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    private MatchingEngine engine;
    private AtomicLong orderIdSeq;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine(orderRepository, tradeRepository);
        orderIdSeq = new AtomicLong(1);
        recordTrades();
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

    private Order order(String clOrdID, Side side, String price, int qty) {
        return order(clOrdID, side, price, qty, "trader-" + clOrdID);
    }

    @Test
    @DisplayName("Al cambiar el precio la orden sale del nivel viejo y aparece en el nuevo")
    void repricingMovesOrderBetweenPriceLevels() {
        Order resting = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(resting);

        engine.modifyOrder(resting, "B1-R", new BigDecimal("99.00"), null, 10);

        OrderBook book = engine.getOrderBook(SYMBOL).orElseThrow();
        assertEquals(1, book.size(), "No debe quedar una orden fantasma en el nivel anterior");
        assertEquals(0, new BigDecimal("99.00").compareTo(book.getBestBid()));
        assertEquals(0, new BigDecimal("99.00").compareTo(resting.getPrice()));
        assertEquals("B1-R", resting.getClOrdID());
        assertEquals("B1", resting.getOrigClOrdID(), "El ClOrdID original se conserva");
    }

    @Test
    @DisplayName("La orden reprecio se puede cancelar: sigue localizable en el libro")
    void repricedOrderRemainsCancellable() {
        Order resting = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(resting);

        engine.modifyOrder(resting, null, new BigDecimal("99.00"), null, 10);

        assertTrue(engine.cancelOrder(resting),
                "Si el remove usara el precio nuevo antes de tiempo, la orden sería irrecuperable");
        assertTrue(engine.getOrderBook(SYMBOL).orElseThrow().isEmpty());
    }

    @Test
    @DisplayName("Subir la cantidad aplica el delta sobre leavesQty, no la reemplaza")
    void increasingQuantityAppliesDeltaToLeavesQty() {
        Order resting = order("S1", Side.SELL, "100.00", 100);
        engine.processOrder(resting);
        engine.processOrder(order("B1", Side.BUY, "100.00", 40));

        assertEquals(40, resting.getCumQty());
        assertEquals(60, resting.getLeavesQty());

        engine.modifyOrder(resting, null, null, null, 120);

        assertEquals(80, resting.getLeavesQty(),
                "delta = 120 - 100 = +20, luego leavesQty = 60 + 20");
        assertEquals(120, resting.getEffectiveOrderQty());
    }

    @Test
    @DisplayName("Bajar la cantidad reduce leavesQty en el delta")
    void decreasingQuantityAppliesNegativeDelta() {
        Order resting = order("S1", Side.SELL, "100.00", 100);
        engine.processOrder(resting);
        engine.processOrder(order("B1", Side.BUY, "100.00", 40));

        engine.modifyOrder(resting, null, null, null, 80);

        assertEquals(40, resting.getLeavesQty(), "delta = 80 - 100 = -20, luego leavesQty = 60 - 20");
    }

    @Test
    @DisplayName("Si el delta deja leavesQty <= 0 la orden se auto-cancela (spec p.77)")
    void modifyAutoCancelsWhenLeavesQtyReachesZero() {
        Order resting = order("S1", Side.SELL, "100.00", 100);
        engine.processOrder(resting);
        engine.processOrder(order("B1", Side.BUY, "100.00", 40));

        List<Trade> trades = engine.modifyOrder(resting, null, null, null, 40);

        assertTrue(trades.isEmpty());
        assertEquals(OrderState.CANCELLED, resting.getState(),
                "delta = 40 - 100 = -60, leavesQty = 60 - 60 = 0 → cancelar");
        assertTrue(engine.getOrderBook(SYMBOL).orElseThrow().isEmpty());
    }

    @Test
    @DisplayName("Reprecio agresivo: si el nuevo precio cruza el libro, ejecuta")
    void repricingIntoTheSpreadTriggersMatching() {
        engine.processOrder(order("S1", Side.SELL, "101.00", 10, "seller"));
        Order resting = order("B1", Side.BUY, "100.00", 10, "buyer");
        engine.processOrder(resting);

        List<Trade> trades = engine.modifyOrder(resting, null, new BigDecimal("101.00"), null, 10);

        assertEquals(1, trades.size(), "Al subir el bid hasta el ask, debe cruzar");
        assertEquals(0, new BigDecimal("101.00").compareTo(trades.get(0).price()));
        assertEquals(OrderState.FILLED, resting.getState());
        assertTrue(engine.getOrderBook(SYMBOL).orElseThrow().isEmpty());
    }

    @Test
    @DisplayName("Un modify que no cruza deja la orden descansando al precio nuevo")
    void nonCrossingModifyLeavesOrderResting() {
        engine.processOrder(order("S1", Side.SELL, "105.00", 10, "seller"));
        Order resting = order("B1", Side.BUY, "100.00", 10, "buyer");
        engine.processOrder(resting);

        List<Trade> trades = engine.modifyOrder(resting, null, new BigDecimal("102.00"), null, 10);

        assertTrue(trades.isEmpty());
        assertEquals(OrderState.LIVE, resting.getState());
        assertEquals(0, new BigDecimal("102.00").compareTo(
                engine.getOrderBook(SYMBOL).orElseThrow().getBestBid()));
    }

    @Test
    @DisplayName("Los shadow fields dejan intacto el precio original de la orden")
    void modifyKeepsOriginalPriceForBookRemoval() {
        Order resting = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(resting);

        engine.modifyOrder(resting, null, new BigDecimal("98.00"), null, 10);

        assertEquals(0, new BigDecimal("98.00").compareTo(resting.getPrice()),
                "getPrice() devuelve el valor efectivo");
        assertEquals(10, resting.getOrderQty(),
                "orderQty original permanece; el efectivo se consulta con getEffectiveOrderQty()");
    }

    @Test
    @DisplayName("Modificar una orden de un símbolo sin libro previo no rompe nada")
    void modifyOnUntouchedSymbolIsSafe() {
        Order never = order("B1", Side.BUY, "100.00", 10);

        List<Trade> trades = engine.modifyOrder(never, null, new BigDecimal("99.00"), null, 10);

        assertTrue(trades.isEmpty());
        assertEquals(OrderState.LIVE, never.getState());
    }

    @Test
    @DisplayName("El modify puede cambiar también el tipo de orden")
    void modifyCanChangeOrdType() {
        Order resting = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(resting);

        engine.modifyOrder(resting, null, null, OrdType.MARKET, 10);

        assertEquals(OrdType.MARKET, resting.getOrdType());
    }

    @Test
    @DisplayName("Cancelar una orden de un símbolo sin libro devuelve false")
    void cancelOnUnknownSymbolReturnsFalse() {
        Order never = order("B1", Side.BUY, "100.00", 10);

        assertTrue(!engine.cancelOrder(never));
        assertNull(engine.getOrderBook(SYMBOL).orElse(null));
    }

    @Test
    @DisplayName("Bajar solo la cantidad mantiene la prioridad temporal (p.77)")
    void reducingOnlyTheQuantityKeepsTimePriority() {
        Order first = order("B1", Side.BUY, "100.00", 10);
        Order second = order("B2", Side.BUY, "100.00", 10);
        engine.processOrder(first);
        engine.processOrder(second);

        engine.modifyOrder(first, "B1-R", new BigDecimal("100.00"), null, 6);

        OrderBook book = engine.getOrderBook(SYMBOL).orElseThrow();
        assertEquals(16, book.getTotalBidQuantity());
        assertEquals(6, first.getLeavesQty());
        assertEquals("B1-R", first.getClOrdID());

        List<Trade> trades = engine.processOrder(order("S1", Side.SELL, "100.00", 5));
        assertEquals("B1-R", trades.get(0).getBuyClOrdID(), "B1 sigue primera en la cola");
    }

    @Test
    @DisplayName("Un modify sin cambios pierde la prioridad")
    void modifyWithoutChangesLosesTimePriority() {
        Order first = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(first);
        engine.processOrder(order("B2", Side.BUY, "100.00", 10));

        engine.modifyOrder(first, "B1-R", new BigDecimal("100.00"), null, 10);

        List<Trade> trades = engine.processOrder(order("S1", Side.SELL, "100.00", 5));
        assertEquals("B2", trades.get(0).getBuyClOrdID());
    }

    @Test
    @DisplayName("Aumentar la cantidad pierde la prioridad")
    void increasingTheQuantityLosesTimePriority() {
        Order first = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(first);
        engine.processOrder(order("B2", Side.BUY, "100.00", 10));

        engine.modifyOrder(first, "B1-R", new BigDecimal("100.00"), null, 12);

        List<Trade> trades = engine.processOrder(order("S1", Side.SELL, "100.00", 5));
        assertEquals("B2", trades.get(0).getBuyClOrdID());
    }

    @Test
    @DisplayName("Bajar la cantidad y cambiar el precio pierde la prioridad")
    void reducingTheQuantityAndRepricingLosesTimePriority() {
        Order first = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(first);
        engine.processOrder(order("B2", Side.BUY, "99.00", 10));

        engine.modifyOrder(first, "B1-R", new BigDecimal("99.00"), null, 5);

        List<Trade> trades = engine.processOrder(order("S1", Side.SELL, "99.00", 5));
        assertEquals("B2", trades.get(0).getBuyClOrdID());
    }

    private Order stop(String clOrdID, String stopPx) {
        Order o = Order.builder().clOrdID(clOrdID).orderID(orderIdSeq.getAndIncrement()).side(Side.BUY)
                .orderQty(1).symbol(SYMBOL).ordType(OrdType.STOP).stopPx(new BigDecimal(stopPx))
                .username("trader-" + clOrdID).build();
        o.acknowledge();
        return o;
    }

    // A last sale at 100.00 elects every buy stop at or below 100.00; the first to trade keeps the ask
    private String firstElectedAt100() {
        engine.processOrder(order("S1", Side.SELL, "100.00", 1));
        engine.processOrder(order("S2", Side.SELL, "100.00", 1));
        engine.processOrder(order("B9", Side.BUY, "100.00", 1));
        return trades().stream().filter(t -> t.getBuyClOrdID().startsWith("ST")).findFirst().orElseThrow().getBuyClOrdID();
    }

    private final java.util.List<Trade> executed = new java.util.ArrayList<>();

    private java.util.List<Trade> trades() {
        return executed;
    }

    private void recordTrades() {
        engine.addEventListener(new MatchingEngine.MatchingEventListener() {
            @Override
            public void onTradeExecuted(Trade trade, OrderBook book) {
                executed.add(trade);
            }
        });
    }

    @Test
    @DisplayName("Cambiar solo MaxFloor mantiene la prioridad y recalcula la parte visible (p.77)")
    void changingOnlyMaxFloorKeepsTimePriority() {
        Order first = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(first);
        engine.processOrder(order("B2", Side.BUY, "100.00", 10));

        engine.modifyOrder(first, "B1-R", new BigDecimal("100.00"), null, 10, 4, null);

        assertEquals(4, first.getDisplayQty());
        List<Trade> trades = engine.processOrder(order("S1", Side.SELL, "100.00", 3));
        assertEquals("B1-R", trades.get(0).getBuyClOrdID(), "B1 sigue primera en la cola");
    }

    @Test
    @DisplayName("Bajar la cantidad y cambiar MaxFloor a la vez mantiene la prioridad")
    void reducingTheQuantityAndChangingMaxFloorKeepsTimePriority() {
        Order first = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(first);
        engine.processOrder(order("B2", Side.BUY, "100.00", 10));

        engine.modifyOrder(first, "B1-R", new BigDecimal("100.00"), null, 8, 4, null);

        assertEquals(18, engine.getOrderBook(SYMBOL).orElseThrow().getTotalBidQuantity());
        assertEquals("B1-R", engine.processOrder(order("S1", Side.SELL, "100.00", 3)).get(0).getBuyClOrdID());
    }

    @Test
    @DisplayName("Cambiar MaxFloor y el precio pierde la prioridad")
    void changingMaxFloorAndThePriceLosesTimePriority() {
        Order first = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(first);
        engine.processOrder(order("B2", Side.BUY, "101.00", 10));

        engine.modifyOrder(first, "B1-R", new BigDecimal("101.00"), null, 10, 4, null);

        assertEquals("B2", engine.processOrder(order("S1", Side.SELL, "101.00", 3)).get(0).getBuyClOrdID());
    }

    @Test
    @DisplayName("Un MaxFloor igual al actual no es un cambio: pierde la prioridad como un modify sin cambios")
    void sameMaxFloorIsNoChange() {
        Order first = order("B1", Side.BUY, "100.00", 10);
        engine.processOrder(first);
        engine.processOrder(order("B2", Side.BUY, "100.00", 10));

        engine.modifyOrder(first, "B1-R", new BigDecimal("100.00"), null, 10, 0, null);

        assertEquals("B2", engine.processOrder(order("S1", Side.SELL, "100.00", 3)).get(0).getBuyClOrdID());
    }

    @Test
    @DisplayName("Cambiar solo StopPx en un stop sin elegir mantiene la prioridad entre stops")
    void changingOnlyStopPxKeepsStopPriority() {
        Order first = stop("ST1", "99.00");
        engine.processOrder(first);
        engine.processOrder(stop("ST2", "99.00"));

        engine.modifyOrder(first, "ST1-R", null, null, 1, null, new BigDecimal("98.00"));

        assertEquals("ST1-R", firstElectedAt100());
    }

    @Test
    @DisplayName("Cambiar StopPx y subir la cantidad pierde la prioridad entre stops")
    void changingStopPxAndTheQuantityLosesStopPriority() {
        Order first = stop("ST1", "99.00");
        engine.processOrder(first);
        engine.processOrder(stop("ST2", "99.00"));

        engine.modifyOrder(first, "ST1-R", null, null, 2, null, new BigDecimal("98.00"));

        assertEquals("ST2", firstElectedAt100());
    }
}
