package com.boe.simulator.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.connection.ClientConnectionHandler;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.metrics.HealthMetrics;
import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.repository.SessionRepository;
import com.boe.simulator.server.persistence.repository.StatisticsRepository;
import com.boe.simulator.server.persistence.service.SessionRepositoryService;
import com.boe.simulator.server.persistence.service.StatisticsGeneratorService;
import com.boe.simulator.server.persistence.service.StatisticsRepositoryService;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.ClientSessionManager;

public class CboeServer {
    private static final Logger LOGGER = Logger.getLogger(CboeServer.class.getName());

    private final ServerConfiguration config;
    private final ExecutorService clientExecutor;
    private final AtomicBoolean running;
    private final AtomicInteger activeConnections;
    private final RocksDBManager dbManager;
    private final AuthenticationService authService;
    private final ClientSessionManager sessionManager;
    private final ErrorHandler errorHandler;
    private final RateLimiter rateLimiter;
    private final HealthMetrics healthMetrics;
    private final StatisticsGeneratorService statisticsGenerator;

    private ServerSocket serverSocket;
    private Thread acceptorThread;

    public CboeServer(ServerConfiguration config) {
        this.config = config;
        this.clientExecutor = Executors.newFixedThreadPool(config.getMaxConnections());
        this.running = new AtomicBoolean(false);
        this.activeConnections = new AtomicInteger(0);
        this.dbManager = RocksDBManager.getInstance("./data/cboe_server");

        LOGGER.info("Initializing persistence layer...");

        SessionRepository sessionRepo = new SessionRepositoryService(dbManager);
        StatisticsRepository statsRepo = new StatisticsRepositoryService(dbManager);

        this.authService = new AuthenticationService(dbManager);
        this.sessionManager = new ClientSessionManager(sessionRepo);
        this.errorHandler = new ErrorHandler();
        this.rateLimiter = new RateLimiter(100, Duration.ofMinutes(1));
        this.healthMetrics = new HealthMetrics();

        this.statisticsGenerator = new StatisticsGeneratorService(
            sessionRepo,
            statsRepo,
            sessionManager,
            errorHandler
        );

        // Configure logging
        LOGGER.setLevel(config.getLogLevel());
        LOGGER.log(Level.INFO, "CboeServer initialized with configuration: {0}", config);
        LOGGER.log(Level.INFO, "Database: {0}", dbManager.getDbPath());
        LOGGER.log(Level.INFO, "RocksDB initialized at: {0}", dbManager.getDbPath());
        LOGGER.log(Level.INFO, "Users in database: {0}", authService.getUserCount());
        LOGGER.log(Level.INFO, "Session persistence: ENABLED");
    }

    public void start() throws IOException {
        if (running.get()) {
            LOGGER.warning("Server is already running");
            return;
        }

        LOGGER.info("Starting CBOE Server...");

        // Create server socket
        serverSocket = new ServerSocket(config.getPort());
        serverSocket.setSoTimeout(1000); // 1 second timeout for accept()
        running.set(true);

        // Start acceptor thread
        acceptorThread = new Thread(this::acceptConnections, "ServerAcceptor");
        acceptorThread.start();

        LOGGER.log(Level.INFO, "✓ CBOE Server started successfully on {0}:{1}",new Object[]{
            config.getHost(), config.getPort()
        });
        LOGGER.log(Level.INFO, "✓ Ready to accept connections (max: {0})", config.getMaxConnections());

        statisticsGenerator.start();
        LOGGER.info("✓ Statistics generator started");
    }

    private void acceptConnections() {
        LOGGER.info("Acceptor thread started, waiting for connections...");

        while (running.get()) {
            try {
                // Accept new connection (with timeout)
                Socket clientSocket = serverSocket.accept();

                // Check connection limit
                if (activeConnections.get() >= config.getMaxConnections()) {
                    LOGGER.log(Level.WARNING, "Connection limit reached ({0}), rejecting connection from {1}", new Object[]{
                        config.getMaxConnections(), clientSocket.getRemoteSocketAddress()
                    });
                    clientSocket.close();
                    continue;
                }

                // Configure socket
                clientSocket.setSoTimeout(config.getConnectionTimeout());
                clientSocket.setTcpNoDelay(true);

                int connectionId = activeConnections.incrementAndGet();
                LOGGER.log(Level.INFO, "✓ New connection accepted [ID: {0}] from {1} (Active: {2}/{3})", new Object[]{
                    connectionId, clientSocket.getRemoteSocketAddress(), activeConnections.get(), config.getMaxConnections()
                });

                clientExecutor.submit(() -> handleClient(clientSocket, connectionId));

            } catch (SocketTimeoutException e) {
            } catch (IOException e) {
                if (running.get()) LOGGER.log(Level.SEVERE, "Error accepting connection", e);
            }
        }

        LOGGER.info("Acceptor thread stopped");
    }

    private void handleClient(Socket socket, int connectionId) {
        LOGGER.log(Level.INFO, "[Connection {0}] Handler started", connectionId);
        ClientConnectionHandler handler = null;

        try {
            handler = new ClientConnectionHandler(
                    socket, connectionId, config, authService,
                    sessionManager, errorHandler, rateLimiter
            );
            sessionManager.registerHandler(handler);
            healthMetrics.updatePeakConnections(activeConnections.get());
            handler.run();

        } catch (Exception e) {
            errorHandler.handleError(connectionId, "Handler error", e);
            LOGGER.log(Level.SEVERE, "[Connection " + connectionId + "] Error in handler", e);
        } finally {
            if (handler != null) sessionManager.unregisterHandler(handler);
            
            activeConnections.decrementAndGet();
            LOGGER.log(Level.INFO, "[Connection {0}] Handler terminated (Active: {1})",new Object[]{
                connectionId, activeConnections.get()
            });
        }
    }

    public void stop() {
        if (!running.get()) {
            LOGGER.warning("Server is not running");
            return;
        }

        LOGGER.info("Stopping CBOE Server...");
        running.set(false);

        // Close server socket
        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing server socket", e);
        }

        // Wait for acceptor thread
        if (acceptorThread != null) {
            try {
                acceptorThread.join(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Interrupted while waiting for acceptor thread");
            }
        }

        LOGGER.info("✓ CBOE Server stopped");
    }

    public void shutdown() {
        LOGGER.info("======= SERVER SHUTDOWN INITIATED =======");

        if (statisticsGenerator != null) {
            LOGGER.info("Stopping statistics generator...");
            statisticsGenerator.stop();
        }

        if (statisticsGenerator != null) {
            LOGGER.info("Generating final statistics...");
            statisticsGenerator.generateCurrentDayStatistics();
        }

        LOGGER.info(healthMetrics.getHealthSummary());
        LOGGER.log(Level.INFO, "Error stats: Errors={0}, Warnings={1}, Recoveries={2}", new Object[]{
            errorHandler.getTotalErrors(), errorHandler.getTotalWarnings(), errorHandler.getTotalRecoveries()
        });
        LOGGER.log(Level.INFO, "Users in database: {0}", authService.getUserCount());
        sessionManager.printSessionSummary();

        // Stop accepting connections
        stop();

        // Disconnect all sessions
        sessionManager.disconnectAll();

        // Shutdown executor
        clientExecutor.shutdown();
        try {
            if (!clientExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                LOGGER.warning("Executor did not terminate in time, forcing shutdown...");
                clientExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            clientExecutor.shutdownNow();
        }

        LOGGER.info("Closing persistence layer...");
        if (dbManager != null) {
            dbManager.close();
            LOGGER.info("✓ Database closed successfully");
        }

        LOGGER.info("✓ CBOE Server shutdown complete");
        LOGGER.info("=========================================");
    }

    // Status methods
    public boolean isRunning() {
        return running.get();
    }

    public int getActiveConnections() {
        return activeConnections.get();
    }

    public ServerConfiguration getConfiguration() {
        return config;
    }

    public AuthenticationService getAuthService() {
        return authService;
    }

    public ClientSessionManager getSessionManager() {
        return sessionManager;
    }

    public ErrorHandler getErrorHandler() {
        return errorHandler;
    }

    public HealthMetrics getHealthMetrics() {
        return healthMetrics;
    }

    public RocksDBManager getDatabaseManager() {
        return dbManager;
    }

    public static void main(String[] args) {
        ServerConfiguration config = ServerConfiguration.builder()
                .host("0.0.0.0")
                .port(8080)
                .maxConnections(10)
                .logLevel(Level.INFO)
                .build();

        CboeServer server = new CboeServer(config);

        // Add shutdown hook for clean shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n\nShutdown signal received...");
            server.shutdown();
        }));

        try {
            // Start server
            server.start();
            System.out.printf("""
                    ╔════════════════════════════════════════════════════════════╗
                    ║         CBOE Server - Con Persistencia - RUNNING           ║
                    ╠════════════════════════════════════════════════════════════╣
                    ║  Server Address: %s:%d                              ║
                    ║  Max Connections: %d                                       ║
                    ║  Persistence: ENABLED                                      ║
                    ║  Statistics: AUTO-GENERATED                                ║
                    ║                                                            ║
                    ║  Test with: telnet localhost %d                          ║
                    ║  or use your BoeConnectionHandler client                   ║
                    ║                                                            ║
                    ║  Press Ctrl+C to stop the server                           ║
                    ╚════════════════════════════════════════════════════════════╝
                    %n""", config.getHost(), config.getPort(), config.getMaxConnections(), config.getPort());

            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleAtFixedRate(() -> {
                if (server.isRunning()) LOGGER.log(Level.INFO, "Server status - Active connections: {0}", server.getActiveConnections());
            }, 0, 10, TimeUnit.SECONDS);

            // Keep main thread alive
            while (server.isRunning()) {
                Thread.sleep(1000);
            }

            // Shutdown del scheduler al finalizar
            scheduler.shutdownNow();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Server thread interrupted", e);
            server.shutdown();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Server I/O error", e);
            server.shutdown();
        } catch (RuntimeException e) {
            LOGGER.log(Level.SEVERE, "Unexpected server runtime error", e);
            server.shutdown();
        }
    }
}