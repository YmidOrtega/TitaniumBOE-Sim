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
class MatchingEnginePriorityTest {

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
    @DisplayName("Dentro de un nivel de precio se ejecuta primero la orden que llegó antes (FIFO)")
    void executesOldestOrderFirstWithinSamePriceLevel() {
        Order first = order("S1", Side.SELL, "100.00", 10);
        Order second = order("S2", Side.SELL, "100.00", 10);
        engine.processOrder(first);
        engine.processOrder(second);

        List<Trade> trades = engine.processOrder(order("B1", Side.BUY, "100.00", 10));

        assertEquals(1, trades.size());
        assertEquals("S1", trades.get(0).sellClOrdID(),
                "La contrapartida debe ser la orden que llegó primero al nivel");
        assertEquals(OrderState.FILLED, first.getState());
        assertEquals(OrderState.LIVE, second.getState());
    }

    @Test
    @DisplayName("Entre niveles distintos cruza primero contra el mejor precio")
    void matchesBestPriceLevelFirst() {
        Order expensive = order("S_HIGH", Side.SELL, "101.00", 10);
        Order cheap = order("S_LOW", Side.SELL, "100.00", 10);
        engine.processOrder(expensive);
        engine.processOrder(cheap);

        List<Trade> trades = engine.processOrder(order("B1", Side.BUY, "101.00", 10));

        assertEquals(1, trades.size());
        assertEquals("S_LOW", trades.get(0).sellClOrdID(),
                "Un comprador debe cruzar primero contra el ask más barato");
    }

    @Test
    @DisplayName("El precio de ejecución es el de la orden pasiva, no el de la agresiva")
    void executionPriceIsThePassiveOrderPrice() {
        engine.processOrder(order("S1", Side.SELL, "100.00", 10));

        List<Trade> trades = engine.processOrder(order("B1", Side.BUY, "105.00", 10));

        assertEquals(1, trades.size());
        assertEquals(0, new BigDecimal("100.00").compareTo(trades.get(0).price()),
                "El comprador agresivo obtiene mejora de precio: paga el ask en reposo");
    }

    @Test
    @DisplayName("Una orden agresiva barre varios niveles hasta completarse")
    void sweepsMultipleLevelsUntilFilled() {
        engine.processOrder(order("S1", Side.SELL, "100.00", 5));
        engine.processOrder(order("S2", Side.SELL, "101.00", 5));

        List<Trade> trades = engine.processOrder(order("B1", Side.BUY, "101.00", 10));

        assertEquals(2, trades.size());
        assertEquals(0, new BigDecimal("100.00").compareTo(trades.get(0).price()));
        assertEquals(0, new BigDecimal("101.00").compareTo(trades.get(1).price()));
        assertTrue(engine.getOrderBook(SYMBOL).orElseThrow().isEmpty(),
                "El libro queda vacío tras consumir ambos niveles");
    }

    @Test
    @DisplayName("Un fill parcial deja la orden viva en el libro con el remanente")
    void partialFillLeavesRemainderResting() {
        engine.processOrder(order("S1", Side.SELL, "100.00", 4));

        Order aggressive = order("B1", Side.BUY, "100.00", 10);
        List<Trade> trades = engine.processOrder(aggressive);

        assertEquals(1, trades.size());
        assertEquals(4, trades.get(0).quantity());
        assertEquals(4, aggressive.getCumQty());
        assertEquals(6, aggressive.getLeavesQty());
        assertEquals(OrderState.PARTIALLY_FILLED, aggressive.getState());

        OrderBook book = engine.getOrderBook(SYMBOL).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(book.getBestBid()),
                "El remanente descansa en el lado bid");
    }

    @Test
    @DisplayName("La orden pasiva completada sale del libro")
    void filledPassiveOrderIsRemovedFromBook() {
        Order passive = order("S1", Side.SELL, "100.00", 10);
        engine.processOrder(passive);

        engine.processOrder(order("B1", Side.BUY, "100.00", 10));

        assertEquals(OrderState.FILLED, passive.getState());
        assertTrue(engine.getOrderBook(SYMBOL).orElseThrow().isEmpty());
    }

    @Test
    @DisplayName("Una orden LIMIT que no cruza no genera trades y queda en el libro")
    void nonCrossingLimitOrderRests() {
        engine.processOrder(order("S1", Side.SELL, "101.00", 10));

        List<Trade> trades = engine.processOrder(order("B1", Side.BUY, "100.00", 10));

        assertTrue(trades.isEmpty(), "Bid 100 no cruza contra ask 101");
        OrderBook book = engine.getOrderBook(SYMBOL).orElseThrow();
        assertEquals(2, book.size());
        assertEquals(0, new BigDecimal("1.00").compareTo(book.getSpread()));
    }

    @Test
    @DisplayName("Una orden MARKET cruza contra el mejor precio disponible")
    void marketOrderMatchesRegardlessOfPrice() {
        engine.processOrder(order("S1", Side.SELL, "100.00", 10));

        Order market = Order.builder()
                .clOrdID("B_MKT")
                .orderID(orderIdSeq.getAndIncrement())
                .side(Side.BUY)
                .orderQty(10)
                .symbol(SYMBOL)
                .ordType(OrdType.MARKET)
                .username("buyer")
                .build();
        market.acknowledge();

        List<Trade> trades = engine.processOrder(market);

        assertEquals(1, trades.size());
        assertEquals(0, new BigDecimal("100.00").compareTo(trades.get(0).price()));
    }

    @Test
    @DisplayName("El matching de un símbolo no toca el libro de otro")
    void ordersOnDifferentSymbolsDoNotCross() {
        Order aaplSell = order("S1", Side.SELL, "100.00", 10);
        engine.processOrder(aaplSell);

        Order spxBuy = Order.builder()
                .clOrdID("B1")
                .orderID(orderIdSeq.getAndIncrement())
                .side(Side.BUY)
                .price(new BigDecimal("100.00"))
                .orderQty(10)
                .symbol("SPX")
                .ordType(OrdType.LIMIT)
                .username("buyer")
                .build();
        spxBuy.acknowledge();

        List<Trade> trades = engine.processOrder(spxBuy);

        assertTrue(trades.isEmpty(), "Cada símbolo tiene su propio libro");
        assertEquals(OrderState.LIVE, aaplSell.getState());
        assertEquals(1, engine.getOrderBook(SYMBOL).orElseThrow().size());
        assertEquals(1, engine.getOrderBook("SPX").orElseThrow().size());
    }

    @Test
    @DisplayName("El libro registra el precio del último trade")
    void lastTradePriceIsRecorded() {
        engine.processOrder(order("S1", Side.SELL, "100.00", 10));
        engine.processOrder(order("B1", Side.BUY, "100.00", 10));

        assertEquals(0, new BigDecimal("100.00").compareTo(
                engine.getOrderBook(SYMBOL).orElseThrow().getLastTradePrice()));
    }
}
