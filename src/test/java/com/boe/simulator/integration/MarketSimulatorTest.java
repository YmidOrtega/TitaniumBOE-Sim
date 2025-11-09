package com.boe.simulator.integration;

import com.boe.simulator.bot.MarketSimulator;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.TradeRepositoryService;
import com.boe.simulator.server.order.OrderManager;
import com.boe.simulator.server.persistence.RocksDBManager;
import org.junit.jupiter.api.*;
import org.awaitility.Awaitility;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class MarketSimulatorTest {

    private RocksDBManager dbManager;
    private OrderManager orderManager;
    private MatchingEngine matchingEngine;
    private TradeRepositoryService tradeRepository;
    private MarketSimulator marketSimulator;

    @BeforeAll
    public void setup() throws Exception {
        // Temp database
        File tempDir = new File(System.getProperty("java.io.tmpdir"), "test-db-" + System.currentTimeMillis());
        tempDir.mkdirs();

        dbManager = RocksDBManager.getInstance(tempDir.getAbsolutePath());
        orderManager = new OrderManager(dbManager);
        matchingEngine = orderManager.getMatchingEngine();
        tradeRepository = new TradeRepositoryService(dbManager);

        marketSimulator = new MarketSimulator(orderManager, matchingEngine, tradeRepository);
    }

    @AfterAll
    public void cleanup() {
        if (marketSimulator != null) {
            marketSimulator.shutdown();
        }
        if (dbManager != null) {
            dbManager.close();
        }
    }

    @Test
    @Order(1)
    @DisplayName("Test 1: Initialize Default Bots")
    public void testInitializeDefaultBots() {
        System.out.println("========== Test 1: Initialize Default Bots ==========");

        marketSimulator.initializeDefaultBots();

        var botManager = marketSimulator.getBotManager();
        assertEquals(3, botManager.getAllBots().size(), "Should have 3 bots");

        assertNotNull(botManager.getBot("MM-001"), "Market Maker bot should exist");
        assertNotNull(botManager.getBot("TRADER-001"), "Random Trader bot should exist");
        assertNotNull(botManager.getBot("TREND-001"), "Trend Follower bot should exist");

        System.out.println("✓ PASS - 3 bots initialized\n");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Start Simulator")
    public void testStartSimulator() {
        System.out.println("========== Test 2: Start Simulator ==========");

        marketSimulator.start();
        assertTrue(marketSimulator.isRunning(), "Simulator should be running");

        var botManager = marketSimulator.getBotManager();
        assertTrue(botManager.isRunning(), "Bot manager should be running");
        assertEquals(3, botManager.getRunningBotsCount(), "All 3 bots should be running");

        System.out.println("✓ PASS - Simulator started\n");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Bots Generate Activity")
    public void testBotsGenerateActivity() {
        System.out.println("========== Test 3: Bots Generate Activity ==========");

        // Wait for bots to execute some cycles
        System.out.println("Waiting for bot activity...");
        Awaitility.await().atMost(10, TimeUnit.SECONDS).until(() -> marketSimulator.getStatistics().totalOrders() > 0);

        marketSimulator.printStatus();

        var stats = marketSimulator.getStatistics();
        assertTrue(stats.totalOrders() > 0, "Bots should have submitted orders");
        System.out.println("✓ Total orders: " + stats.totalOrders());

        System.out.println("✓ PASS - Bots are active\n");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Stop Individual Bot")
    public void testStopIndividualBot() {
        System.out.println("========== Test 4: Stop Individual Bot ==========");

        var botManager = marketSimulator.getBotManager();
        boolean stopped = botManager.stopBot("TRADER-001");

        assertTrue(stopped, "Bot should be stopped");
        assertEquals(2, botManager.getRunningBotsCount(), "Should have 2 running bots");

        var bot = botManager.getBot("TRADER-001");
        assertFalse(bot.isRunning(), "TRADER-001 should not be running");

        System.out.println("✓ PASS - Individual bot stopped\n");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Stop Simulator")
    public void testStopSimulator() {
        System.out.println("========== Test 5: Stop Simulator ==========");

        marketSimulator.stop();
        assertFalse(marketSimulator.isRunning(), "Simulator should not be running");

        var botManager = marketSimulator.getBotManager();
        assertEquals(0, botManager.getRunningBotsCount(), "No bots should be running");

        System.out.println("✓ PASS - Simulator stopped\n");
    }
}
