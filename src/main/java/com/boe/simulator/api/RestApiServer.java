package com.boe.simulator.api;

import com.boe.simulator.api.controller.*;
import com.boe.simulator.api.middleware.AuthenticationFilter;
import com.boe.simulator.api.middleware.CorsFilter;
import com.boe.simulator.api.middleware.ErrorHandler;
import com.boe.simulator.api.service.*;
import com.boe.simulator.api.websocket.WebSocketHandler;
import com.boe.simulator.api.websocket.WebSocketService;
import com.boe.simulator.bot.MarketSimulator;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.TradeRepository;
import com.boe.simulator.server.order.OrderManager;
import com.boe.simulator.server.order.OrderRepository;
import io.javalin.Javalin;
import io.javalin.http.ContentType;

import java.util.logging.Level;
import java.util.logging.Logger;

public class RestApiServer {
    private static final Logger LOGGER = Logger.getLogger(RestApiServer.class.getName());

    private final int port;
    private final OrderManager orderManager;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final AuthenticationService authService;
    private final MatchingEngine matchingEngine;
    private final WebSocketService webSocketService;
    private final MarketSimulator marketSimulator;

    private Javalin app;
    private boolean running = false;

    public RestApiServer(
            int port,
            OrderManager orderManager,
            OrderRepository orderRepository,
            TradeRepository tradeRepository,
            AuthenticationService authService,
            MatchingEngine matchingEngine,
            MarketSimulator marketSimulator
    ) {
        this.port = port;
        this.orderManager = orderManager;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.authService = authService;
        this.matchingEngine = matchingEngine;
        this.marketSimulator = marketSimulator;
        this.webSocketService = new WebSocketService();
    }

    public void start() {
        if (running) {
            LOGGER.warning("REST API Server is already running");
            return;
        }

        LOGGER.log(Level.INFO, "Starting REST API Server on port {0}...", port);

        // Seed market data
        MarketDataSeeder seeder = new MarketDataSeeder(matchingEngine, orderRepository);
        seeder.seedMarket();

        // Create services
        OrderService orderService = new OrderService(orderManager, orderRepository);
        PositionService positionService = new PositionService(tradeRepository);
        TradeService tradeService = new TradeService(tradeRepository);
        SymbolService symbolService = new SymbolService();

        // Create controllers
        OrderController orderController = new OrderController(orderService);
        PositionController positionController = new PositionController(positionService);
        TradeController tradeController = new TradeController(tradeService);
        SymbolController symbolController = new SymbolController(matchingEngine, symbolService);
        BotController botController = new BotController(marketSimulator); // ✅ N

        // Create an authentication filter
        AuthenticationFilter authFilter = new AuthenticationFilter(authService);

        // Configure WebSocket service in OrderManager and MatchingEngine
        orderManager.setWebSocketService(webSocketService);
        matchingEngine.setWebSocketService(webSocketService);

        WebSocketHandler wsHandler = new WebSocketHandler(webSocketService);

        // Create Javalin app
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.http.defaultContentType = ContentType.JSON;
        });

        // Configure CORS
        app.before(new CorsFilter());

        // Configure error handlers
        ErrorHandler.configure(app);

        // Public routes
        app.get("/api/health", ctx -> {
            ctx.json(new HealthResponse(
                    "healthy",
                    System.currentTimeMillis(),
                    orderManager.getActiveOrderCount(),
                    matchingEngine.getTotalMatches()
            ));
        });

        app.ws("/ws/feed", wsHandler::configure);

        app.get("/api/symbols", symbolController::getSymbols);

        // Protected routes
        app.before("/api/orders*", authFilter);
        app.before("/api/positions*", authFilter);
        app.before("/api/trades*", authFilter);

        // Order endpoints
        app.post("/api/orders", orderController::submitOrder);
        app.get("/api/orders/active", orderController::getActiveOrders);
        app.get("/api/orders/{clOrdID}", orderController::getOrder);
        app.delete("/api/orders/{clOrdID}", orderController::cancelOrder);

        // Position endpoints
        app.get("/api/positions", positionController::getPositions);
        app.get("/api/positions/{symbol}", positionController::getPosition);

        // Trade endpoints
        app.get("/api/trades/recent", tradeController::getRecentTrades);
        app.get("/api/trades/symbol/{symbol}", tradeController::getTradesBySymbol);
        app.get("/api/trades/my", tradeController::getUserTrades);

        // Start server
        app.start(port);
        running = true;

        // Bot management endpoints
        app.get("/api/simulator/status", botController::getSimulatorStatus);
        app.get("/api/simulator/bots", botController::getAllBots);
        app.get("/api/simulator/bots/{botId}", botController::getBot);
        app.post("/api/simulator/bots/{botId}/start", botController::startBot);
        app.post("/api/simulator/bots/{botId}/stop", botController::stopBot);
        app.post("/api/simulator/start", botController::startSimulator);
        app.post("/api/simulator/stop", botController::stopSimulator);

        LOGGER.log(Level.INFO, "✓ REST API Server started successfully on http://localhost:{0}", port);
        LOGGER.log(Level.INFO, "✓ WebSocket available at ws://localhost:{0}/ws/feed", port);
        printEndpoints();
    }

    public void stop() {
        if (!running) {
            LOGGER.warning("REST API Server is not running");
            return;
        }

        LOGGER.info("Stopping REST API Server...");

        if (app != null) app.stop();

        running = false;
        LOGGER.info("✓ REST API Server stopped");
    }

    public boolean isRunning() {
        return running;
    }

    private void printEndpoints() {
        LOGGER.info("========== REST API Endpoints ==========");
        LOGGER.info("PUBLIC:");
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/health", port);
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/symbols", port);
        LOGGER.info("");
        LOGGER.info("AUTHENTICATED (Basic Auth):");
        LOGGER.log(Level.INFO, "  POST   http://localhost:{0}/api/orders", port);
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/orders/active", port);
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/orders/{{clOrdID}}", port);
        LOGGER.log(Level.INFO, "  DELETE http://localhost:{0}/api/orders/{{clOrdID}}", port);
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/positions", port);
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/positions/{{symbol}}", port);
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/trades/recent", port);
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/trades/symbol/{{symbol}}", port);
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/trades/my", port);
        LOGGER.info("");
        LOGGER.info("WEBSOCKET:");
        LOGGER.log(Level.INFO, "  WS     ws://localhost:{0}/ws/feed", port);
        LOGGER.info("");
        LOGGER.info("SIMULATOR (Admin):");
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/simulator/status", port);
        LOGGER.log(Level.INFO, "  GET    http://localhost:{0}/api/simulator/bots", port);
        LOGGER.log(Level.INFO, "  POST   http://localhost:{0}/api/simulator/start", port);
        LOGGER.log(Level.INFO, "  POST   http://localhost:{0}/api/simulator/stop", port);
        LOGGER.info("========================================");
    }

    public WebSocketService getWebSocketService() {
        return webSocketService;
    }

    private record HealthResponse(
            String status,
            long timestamp,
            int activeOrders,
            long totalMatches
    ) {}
}