package com.boe.simulator.server.matching;

import com.boe.simulator.protocol.types.OrdType;
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
class MatchingEngineReserveStopTest {

    private static final String SYMBOL = "AAPL";

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    private MatchingEngine engine;
    private final AtomicLong ids = new AtomicLong(1);
    private final List<String> events = new ArrayList<>();
    private final List<Trade> allTrades = new ArrayList<>();

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine(orderRepository, tradeRepository);
        engine.addEventListener(new MatchingEngine.MatchingEventListener() {
            @Override
            public void onTradeExecuted(Trade trade, OrderBook book) {
                allTrades.add(trade);
            }

            @Override
            public void onOrderCancelled(Order order, byte reason, OrderBook book) {
                events.add("cancel:" + order.getClOrdID() + ":" + (char) reason);
            }

            @Override
            public void onOrderRestated(Order order, byte reason, boolean incoming, OrderBook book) {
                events.add("restated:" + order.getClOrdID() + ":" + (char) reason);
            }
        });
    }

    private Order.Builder base(String id, Side side, int qty) {
        return Order.builder().clOrdID(id).orderID(ids.getAndIncrement()).side(side).orderQty(qty)
                .symbol(SYMBOL).username("user-" + id);
    }

    private Order limit(String id, Side side, String price, int qty) {
        return live(base(id, side, qty).price(new BigDecimal(price)).ordType(OrdType.LIMIT).build());
    }

    private Order reserve(String id, Side side, String price, int qty, int maxFloor, int displayRange) {
        return live(base(id, side, qty).price(new BigDecimal(price)).ordType(OrdType.LIMIT)
                .maxFloor(maxFloor).displayRange(displayRange).build());
    }

    private Order stop(String id, Side side, String stopPx, int qty, String limitPrice) {
        Order.Builder b = base(id, side, qty).stopPx(new BigDecimal(stopPx));
        if (limitPrice != null) b.ordType(OrdType.STOP_LIMIT).price(new BigDecimal(limitPrice));
        else b.ordType(OrdType.STOP);
        return live(b.build());
    }

    private static Order live(Order o) {
        o.acknowledge();
        return o;
    }

    private OrderBook book() {
        return engine.getOrderBook(SYMBOL).orElseThrow();
    }

    @Test
    @DisplayName("Reserva: solo se publica MaxFloor y al agotarse se recarga con Order Restated L")
    void reserveDisplaysMaxFloorAndReloads() {
        Order iceberg = reserve("S1", Side.SELL, "10.00", 10, 3, 0);
        engine.processOrder(iceberg);
        assertEquals(3, book().getSnapshot(1).asks().get(0).quantity(), "Only MaxFloor is displayed");

        List<Trade> trades = engine.processOrder(limit("B1", Side.BUY, "10.00", 5));

        assertEquals(List.of(3, 2), trades.stream().map(Trade::quantity).toList());
        assertTrue(events.contains("restated:S1:L"));
        assertEquals(5, iceberg.getLeavesQty());
        assertEquals(1, book().getSnapshot(1).asks().get(0).quantity(), "The reload shows min(MaxFloor, leaves) minus the second fill");
    }

    @Test
    @DisplayName("Reserva: la parte visible de las demás órdenes del nivel va antes que la recarga")
    void displayedQuantityOfOtherOrdersTradesBeforeTheReload() {
        engine.processOrder(reserve("S1", Side.SELL, "10.00", 10, 2, 0));
        engine.processOrder(limit("S2", Side.SELL, "10.00", 5));

        List<Trade> trades = engine.processOrder(limit("B1", Side.BUY, "10.00", 6));

        assertEquals(List.of("S1:2", "S2:4"), trades.stream().map(t -> t.sellClOrdID() + ":" + t.quantity()).toList());
    }

    @Test
    @DisplayName("DisplayRange: la parte visible cae entre MaxFloor - DisplayRange y MaxFloor + DisplayRange")
    void displayRangeRandomisesTheDisplayedQuantity() {
        for (int i = 0; i < 20; i++) {
            Order iceberg = reserve("S" + i, Side.SELL, "20.00", 100, 10, 3);
            engine.processOrder(iceberg);
            assertTrue(iceberg.getDisplayQty() >= 7 && iceberg.getDisplayQty() <= 13, "displayed " + iceberg.getDisplayQty());
        }
    }

    @Test
    @DisplayName("MinQty: una IOC que no puede ejecutar el mínimo se cancela sin ejecutar nada")
    void iocWithMinQty() {
        engine.processOrder(limit("S1", Side.SELL, "10.00", 3));

        Order tooBig = live(base("B1", Side.BUY, 10).price(new BigDecimal("10.00")).ordType(OrdType.LIMIT)
                .timeInForce(TimeInForce.IOC).minQty(5).build());
        assertTrue(engine.processOrder(tooBig).isEmpty());
        assertEquals(OrderState.CANCELLED, tooBig.getState());

        Order fits = live(base("B2", Side.BUY, 10).price(new BigDecimal("10.00")).ordType(OrdType.LIMIT)
                .timeInForce(TimeInForce.IOC).minQty(3).build());
        assertEquals(1, engine.processOrder(fits).size());
        assertEquals(3, fits.getCumQty());
    }

    @Test
    @DisplayName("Stop de compra: espera fuera del libro y se elige cuando la última venta llega a StopPx")
    void buyStopElectsOnLastSaleAndTradesAsMarket() {
        engine.processOrder(limit("S1", Side.SELL, "10.00", 1));
        engine.processOrder(limit("S2", Side.SELL, "11.00", 5));
        Order buyStop = stop("BS", Side.BUY, "10.00", 5, null);

        assertTrue(engine.processOrder(buyStop).isEmpty());
        assertEquals(OrderState.LIVE, buyStop.getState());
        assertEquals(new BigDecimal("10.00"), book().getBestAsk(), "The stop is not in the book");

        engine.processOrder(limit("B1", Side.BUY, "10.00", 1));

        assertTrue(buyStop.isStopElected());
        assertEquals(OrderState.FILLED, buyStop.getState());
        assertEquals(new BigDecimal("11.00"), allTrades.get(allTrades.size() - 1).price());
    }

    @Test
    @DisplayName("Stop Limit de venta: se elige con la última venta ≤ StopPx y se queda en el libro a su precio")
    void sellStopLimitRestsAfterElection() {
        engine.processOrder(limit("B0", Side.BUY, "9.00", 1));
        Order sellStopLimit = stop("SS", Side.SELL, "9.00", 4, "9.50");
        engine.processOrder(sellStopLimit);

        engine.processOrder(limit("S1", Side.SELL, "9.00", 1));

        assertTrue(sellStopLimit.isStopElected());
        assertEquals(OrderState.LIVE, sellStopLimit.getState());
        assertEquals(new BigDecimal("9.50"), book().getBestAsk());
    }

    @Test
    @DisplayName("Stop elegido sin liquidez: el resto se cancela con N y se avisa")
    void electedStopWithoutLiquidityIsCancelled() {
        engine.processOrder(limit("S1", Side.SELL, "10.00", 1));
        Order buyStop = stop("BS", Side.BUY, "10.00", 5, null);
        engine.processOrder(buyStop);

        engine.processOrder(limit("B1", Side.BUY, "10.00", 1));

        assertEquals(OrderState.CANCELLED, buyStop.getState());
        assertTrue(events.contains("cancel:BS:N"));
    }

    @Test
    @DisplayName("Elecciones en cascada: el trade de un stop elige al siguiente")
    void electionsCascade() {
        engine.processOrder(limit("S1", Side.SELL, "10.00", 1));
        engine.processOrder(limit("S2", Side.SELL, "11.00", 1));
        engine.processOrder(limit("S3", Side.SELL, "12.00", 1));
        Order first = stop("ST1", Side.BUY, "10.00", 1, null);
        Order second = stop("ST2", Side.BUY, "11.00", 1, null);
        engine.processOrder(first);
        engine.processOrder(second);

        engine.processOrder(limit("B1", Side.BUY, "10.00", 1));

        assertEquals(OrderState.FILLED, first.getState());
        assertEquals(OrderState.FILLED, second.getState(), "ST1 traded at 11.00, which elected ST2");
    }

    @Test
    @DisplayName("Solo un trade nuevo elige: una orden sin trades no reevalúa la última venta antigua")
    void onlyANewTradeElects() {
        engine.processOrder(limit("S1", Side.SELL, "10.00", 1));
        engine.processOrder(limit("B1", Side.BUY, "10.00", 1));
        Order buyStop = stop("BS", Side.BUY, "10.00", 1, null);
        engine.processOrder(buyStop);

        engine.processOrder(limit("S2", Side.SELL, "11.00", 5));

        assertFalse(buyStop.isStopElected(), "S2 did not trade, so it is not an election trigger");
    }

    @Test
    @DisplayName("Un stop pendiente se puede cancelar y modificar")
    void pendingStopCanBeCancelledAndModified() {
        Order buyStop = stop("BS", Side.BUY, "10.00", 5, "10.50");
        engine.processOrder(buyStop);

        buyStop.modifyReserveAndStop(-1, new BigDecimal("12.00"));
        engine.modifyOrder(buyStop, "BS2", new BigDecimal("12.50"), OrdType.STOP_LIMIT, 5);
        assertTrue(book().hasStop(buyStop));
        assertEquals(new BigDecimal("12.00"), buyStop.getStopPx());

        assertTrue(engine.cancelOrder(buyStop));
        assertFalse(book().hasStop(buyStop));
    }
}
