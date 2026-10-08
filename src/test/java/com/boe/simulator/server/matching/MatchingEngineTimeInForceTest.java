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
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class MatchingEngineTimeInForceTest {

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
    }

    private Order order(String clOrdID, Side side, String price, int qty, TimeInForce tif) {
        Order o = Order.builder()
                .clOrdID(clOrdID)
                .orderID(orderIdSeq.getAndIncrement())
                .side(side)
                .price(price != null ? new BigDecimal(price) : null)
                .orderQty(qty)
                .symbol(SYMBOL)
                .ordType(price != null ? OrdType.LIMIT : OrdType.MARKET)
                .timeInForce(tif)
                .username("trader-" + clOrdID)
                .build();
        o.acknowledge();
        return o;
    }

    private OrderBook book() {
        return engine.getOrderBook(SYMBOL).orElseThrow();
    }

    @Test
    @DisplayName("IOC: ejecuta lo que puede y cancela el resto sin dejarlo en el libro")
    void iocCancelsTheUnfilledRemainder() {
        engine.processOrder(order("S1", Side.SELL, "100.00", 4, TimeInForce.DAY));

        Order ioc = order("B1", Side.BUY, "100.00", 10, TimeInForce.IOC);
        List<Trade> trades = engine.processOrder(ioc);

        assertEquals(1, trades.size());
        assertEquals(4, ioc.getCumQty());
        assertEquals(OrderState.CANCELLED, ioc.getState());
        assertNull(book().getBestBid(), "El resto de una IOC no descansa en el libro");
    }

    @Test
    @DisplayName("IOC sin contrapartida: se cancela entera")
    void iocWithoutLiquidityIsCancelled() {
        Order ioc = order("B1", Side.BUY, "100.00", 10, TimeInForce.IOC);

        assertTrue(engine.processOrder(ioc).isEmpty());
        assertEquals(OrderState.CANCELLED, ioc.getState());
        assertNull(book().getBestBid());
    }

    @Test
    @DisplayName("FOK sin liquidez suficiente: se cancela sin ejecutar nada y el libro no cambia")
    void fokWithoutEnoughLiquidityIsCancelledWithoutTrading() {
        Order resting = order("S1", Side.SELL, "100.00", 5, TimeInForce.DAY);
        engine.processOrder(resting);
        engine.processOrder(order("S2", Side.SELL, "101.00", 5, TimeInForce.DAY));

        Order fok = order("B1", Side.BUY, "100.00", 8, TimeInForce.FOK);

        assertTrue(engine.processOrder(fok).isEmpty());
        assertEquals(OrderState.CANCELLED, fok.getState());
        assertEquals(0, fok.getCumQty());
        assertEquals(5, resting.getLeavesQty(), "La orden en reposo no se toca");
        assertEquals(10, book().getTotalAskQuantity());
    }

    @Test
    @DisplayName("FOK con liquidez suficiente en varios niveles: se ejecuta entera")
    void fokWithEnoughLiquidityAcrossLevelsIsFullyFilled() {
        engine.processOrder(order("S1", Side.SELL, "100.00", 5, TimeInForce.DAY));
        engine.processOrder(order("S2", Side.SELL, "101.00", 5, TimeInForce.DAY));

        Order fok = order("B1", Side.BUY, "101.00", 8, TimeInForce.FOK);
        List<Trade> trades = engine.processOrder(fok);

        assertEquals(2, trades.size());
        assertEquals(OrderState.FILLED, fok.getState());
    }

    @Test
    @DisplayName("FOK: no cuenta las órdenes propias que la prevención de autocruce no dejaría ejecutar")
    void fokIgnoresOwnOrdersWhenSelfTradeIsPrevented() {
        Order own = Order.builder()
                .clOrdID("S1").orderID(orderIdSeq.getAndIncrement()).side(Side.SELL)
                .price(new BigDecimal("100.00")).orderQty(10).symbol(SYMBOL)
                .username("trader-B1").build();
        own.acknowledge();
        engine.processOrder(own);

        Order fok = order("B1", Side.BUY, "100.00", 10, TimeInForce.FOK);

        assertTrue(engine.processOrder(fok).isEmpty());
        assertEquals(OrderState.CANCELLED, fok.getState());
        assertEquals(OrderState.LIVE, own.getState());
    }

    @Test
    @DisplayName("Orden a mercado: es IOC implícita y el resto se cancela")
    void marketOrderIsImplicitlyIoc() {
        engine.processOrder(order("S1", Side.SELL, "100.00", 3, TimeInForce.DAY));

        Order market = order("B1", Side.BUY, null, 10, TimeInForce.DAY);
        List<Trade> trades = engine.processOrder(market);

        assertEquals(1, trades.size());
        assertEquals(OrderState.CANCELLED, market.getState());
        assertEquals(7, market.getLeavesQty());
    }

    @Test
    @DisplayName("Day: el resto sin ejecutar sigue en el libro")
    void dayOrderRestsInTheBook() {
        Order day = order("B1", Side.BUY, "100.00", 10, TimeInForce.DAY);

        engine.processOrder(day);

        assertEquals(OrderState.LIVE, day.getState());
        assertEquals(new BigDecimal("100.00"), book().getBestBid());
    }

    @Test
    @DisplayName("El agresor del trade es la orden que entra, sea compra o venta")
    void aggressorSideIsTheIncomingOrder() {
        engine.processOrder(order("B1", Side.BUY, "100.00", 5, TimeInForce.DAY));
        List<Trade> sellAggressor = engine.processOrder(order("S1", Side.SELL, "100.00", 5, TimeInForce.DAY));

        engine.processOrder(order("S2", Side.SELL, "100.00", 5, TimeInForce.DAY));
        List<Trade> buyAggressor = engine.processOrder(order("B2", Side.BUY, "100.00", 5, TimeInForce.DAY));

        assertEquals(Side.SELL, sellAggressor.get(0).getAggressorSide());
        assertEquals(Side.BUY, buyAggressor.get(0).getAggressorSide());
    }
}
