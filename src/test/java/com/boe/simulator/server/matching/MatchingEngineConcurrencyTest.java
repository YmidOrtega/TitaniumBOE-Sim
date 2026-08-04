package com.boe.simulator.server.matching;

import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MatchingEngineConcurrencyTest {

    private static final int TIMEOUT_SECONDS = 30;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    private MatchingEngine engine;
    private AtomicLong orderIdSeq;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine(orderRepository, tradeRepository, true);
        orderIdSeq = new AtomicLong(1);
    }

    private Order order(String clOrdID, Side side, String price, int qty, String symbol) {
        Order o = Order.builder()
                .clOrdID(clOrdID)
                .orderID(orderIdSeq.getAndIncrement())
                .side(side)
                .price(new BigDecimal(price))
                .orderQty(qty)
                .symbol(symbol)
                .ordType(OrdType.LIMIT)
                .username("trader-" + clOrdID)
                .build();
        o.acknowledge();
        return o;
    }

    @Test
    @DisplayName("Símbolos distintos se procesan en paralelo sin mezclar libros")
    void concurrentOrdersAcrossSymbolsKeepBooksIsolated() throws Exception {
        int symbols = 8;
        int ordersPerSymbol = 50;
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch done = new CountDownLatch(symbols);
            for (int s = 0; s < symbols; s++) {
                String symbol = "SYM" + s;
                pool.submit(() -> {
                    try {
                        start.await();
                        for (int i = 0; i < ordersPerSymbol; i++) {
                            engine.processOrder(order(symbol + "-B" + i, Side.BUY, "100.00", 10, symbol));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timeout procesando órdenes");
        }

        for (int s = 0; s < symbols; s++) {
            OrderBook book = engine.getOrderBook("SYM" + s).orElseThrow();
            assertEquals(ordersPerSymbol, book.size(),
                    "El libro de SYM" + s + " no debe contener órdenes de otro símbolo");
        }
    }

    @Test
    @DisplayName("Órdenes agresivas concurrentes sobre el mismo símbolo no sobre-ejecutan la liquidez")
    void concurrentAggressorsOnSameSymbolNeverOverfill() throws Exception {
        String symbol = "AAPL";
        int liquidityOrders = 100;
        int liquidityPerOrder = 10;
        int totalLiquidity = liquidityOrders * liquidityPerOrder;

        List<Order> passives = new ArrayList<>();
        for (int i = 0; i < liquidityOrders; i++) {
            Order sell = order("S" + i, Side.SELL, "100.00", liquidityPerOrder, symbol);
            passives.add(sell);
            engine.processOrder(sell);
        }

        int aggressors = 20;
        int qtyPerAggressor = 50;
        List<Order> buys = new ArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger tradedQty = new AtomicInteger();

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch done = new CountDownLatch(aggressors);
            for (int i = 0; i < aggressors; i++) {
                Order buy = order("B" + i, Side.BUY, "100.00", qtyPerAggressor, symbol);
                buys.add(buy);
                pool.submit(() -> {
                    try {
                        start.await();
                        List<Trade> trades = engine.processOrder(buy);
                        trades.forEach(t -> tradedQty.addAndGet(t.quantity()));
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timeout en el matching concurrente");
        }

        int demand = aggressors * qtyPerAggressor;
        assertEquals(Math.min(demand, totalLiquidity), tradedQty.get(),
                "La cantidad casada debe ser exactamente el mínimo entre oferta y demanda");

        for (Order buy : buys) {
            assertEquals(qtyPerAggressor, buy.getCumQty() + buy.getLeavesQty(),
                    "cumQty + leavesQty debe conservarse para " + buy.getClOrdID());
            assertTrue(buy.getCumQty() <= qtyPerAggressor,
                    "Ninguna orden puede ejecutar más de su cantidad");
        }
        for (Order sell : passives) {
            assertTrue(sell.getCumQty() <= liquidityPerOrder,
                    "Ninguna orden pasiva puede ejecutar más de su cantidad");
        }
    }

    @Test
    @DisplayName("Leer el libro mientras el motor escribe no rompe ni devuelve estado inconsistente")
    void concurrentReadersSeeConsistentBookWhileWriting() throws Exception {
        String symbol = "AAPL";
        engine.processOrder(order("SEED", Side.BUY, "100.00", 10, symbol));
        OrderBook book = engine.getOrderBook(symbol).orElseThrow();

        AtomicBoolean writing = new AtomicBoolean(true);
        AtomicReference<Throwable> readerFailure = new AtomicReference<>();
        int writes = 500;

        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch readersDone = new CountDownLatch(4);
            for (int r = 0; r < 4; r++) {
                pool.submit(() -> {
                    try {
                        while (writing.get()) {
                            BigDecimal bid = book.getBestBid();
                            if (bid != null && bid.compareTo(BigDecimal.ZERO) <= 0) {
                                throw new AssertionError("Best bid inconsistente: " + bid);
                            }
                            book.getSnapshot(5);
                            book.size();
                        }
                    } catch (Throwable t) {
                        readerFailure.compareAndSet(null, t);
                    } finally {
                        readersDone.countDown();
                    }
                });
            }

            for (int i = 0; i < writes; i++) {
                engine.processOrder(order("B" + i, Side.BUY, "99.00", 10, symbol));
            }
            writing.set(false);
            assertTrue(readersDone.await(TIMEOUT_SECONDS, TimeUnit.SECONDS), "Timeout esperando a los lectores");
        }

        assertNull(readerFailure.get(),
                () -> "Un lector falló durante la escritura concurrente: " + readerFailure.get());
        assertEquals(writes + 1, book.size());
        assertEquals(0, new BigDecimal("100.00").compareTo(book.getBestBid()),
                "El mejor bid sigue siendo el nivel más alto tras todas las escrituras");
    }
}
