package com.boe.simulator.api;

import com.boe.simulator.api.controller.*;
import com.boe.simulator.api.middleware.AuthenticationFilter;
import com.boe.simulator.api.middleware.CorsFilter;
import com.boe.simulator.api.middleware.ErrorHandler;
import com.boe.simulator.api.service.*;
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

    private Javalin app;
    private boolean running = false;

    public RestApiServer(
            int port,
            OrderManager orderManager,
            OrderRepository orderRepository,
            TradeRepository tradeRepository,
            AuthenticationService authService,
            MatchingEngine matchingEngine
    ) {
        this.port = port;
        this.orderManager = orderManager;
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.authService = authService;
        this.matchingEngine = matchingEngine;
    }

    public void start() {
        if (running) {
            LOGGER.warning("REST API Server is already running");
            return;
        }

        LOGGER.log(Level.INFO, "Starting REST API Server on port {0}...", port);

        // Create services
        SymbolService symbolService = new SymbolService();
        OrderService orderService = new OrderService(orderManager, orderRepository);
        PositionService positionService = new PositionService(tradeRepository);
        TradeService tradeService = new TradeService(tradeRepository);

        // Create controllers
        OrderController orderController = new OrderController(orderService);
        PositionController positionController = new PositionController(positionService);
        TradeController tradeController = new TradeController(tradeService);
        SymbolController symbolController = new SymbolController(matchingEngine, symbolService);

        // Create authentication filter
        AuthenticationFilter authFilter = new AuthenticationFilter(authService);

        // Create Javalin app
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
            config.http.defaultContentType = ContentType.JSON;
        });

        // Configure CORS
        app.before(new CorsFilter());

        // Configure error handlers
        ErrorHandler.configure(app);

        // Public routes (no auth)
        app.get("/api/health", ctx -> {
            ctx.json(new HealthResponse(
                    "healthy",
                    System.currentTimeMillis(),
                    orderManager.getActiveOrderCount(),
                    matchingEngine.getTotalMatches()
            ));
        });

        app.get("/api/symbols", symbolController::getSymbols);
        app.get("/api/symbols/{symbol}", symbolController::getSymbol);
        app.get("/api/symbols/{symbol}/book", symbolController::getOrderBook);

        // Protected routes (require authentication)
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

        MarketDataSeeder seeder = new MarketDataSeeder(orderManager);
        seeder.seedMarket();

        // Start server
        app.start(port);
        running = true;

        LOGGER.log(Level.INFO, "✓ REST API Server started successfully on http://localhost:{0}", port);
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
        LOGGER.info("========================================");
    }

    private record HealthResponse(
            String status,
            long timestamp,
            int activeOrders,
            long totalMatches
    ) {}
}