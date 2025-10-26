package com.boe.simulator;

import com.boe.simulator.connection.BoeConnectionHandler;
import com.boe.simulator.protocol.message.BoeSessionManager;
import com.boe.simulator.protocol.message.SessionState;

import java.util.Scanner;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


public class ClientServer {
    private static final Logger LOGGER = Logger.getLogger(ClientServer.class.getName());

    // Configuration - modify these values according to your BOE server
    private static final String BOE_HOST = "localhost";
    private static final int BOE_PORT = 12345;
    private static final String USERNAME = "test";
    private static final String PASSWORD = "test1234";

    public static void main(String[] args) {
        setupLogging();

        LOGGER.info("=== BOE Simulator Started ===");
        LOGGER.info("Host: " + BOE_HOST);
        LOGGER.info("Port: " + BOE_PORT);
        LOGGER.info("Username: " + USERNAME);

        // Create connection handler
        BoeConnectionHandler connectionHandler = new BoeConnectionHandler(BOE_HOST, BOE_PORT);

        // Create session manager
        BoeSessionManager sessionManager = new BoeSessionManager(connectionHandler);

        try {
            // Start interactive mode
            runInteractiveMode(sessionManager, connectionHandler);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during execution", e);
        } finally {
            // Cleanup
            cleanup(sessionManager);
        }

        LOGGER.info("=== BOE Simulator Stopped ===");
    }

    private static void runInteractiveMode(BoeSessionManager sessionManager,
                                           BoeConnectionHandler connectionHandler) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        printMenu();

        while (running) {
            System.out.print("\nEnter command: ");
            String command = scanner.nextLine().trim().toLowerCase();

            try {
                switch (command) {
                    case "1":
                    case "login":
                        handleLogin(sessionManager, connectionHandler);
                        break;

                    case "2":
                    case "logout":
                        handleLogout(sessionManager);
                        break;

                    case "3":
                    case "status":
                        handleStatus(sessionManager, connectionHandler);
                        break;

                    case "4":
                    case "send":
                        handleSendTestMessage(connectionHandler);
                        break;

                    case "5":
                    case "listener":
                        handleStartListener(connectionHandler);
                        break;

                    case "6":
                    case "stop":
                        handleStopListener(connectionHandler);
                        break;

                    case "7":
                    case "menu":
                        printMenu();
                        break;

                    case "8":
                    case "exit":
                    case "quit":
                        running = false;
                        System.out.println("Exiting...");
                        break;

                    default:
                        System.out.println("Unknown command. Type 'menu' to see available commands.");
                        break;
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error executing command: " + command, e);
                System.out.println("Error: " + e.getMessage());
            }
        }

        scanner.close();
    }

    private static void handleLogin(BoeSessionManager sessionManager,
                                    BoeConnectionHandler connectionHandler) {
        System.out.println("Attempting login...");

        sessionManager.login(USERNAME, PASSWORD)
                .thenRun(() -> {
                    System.out.println("✓ Login request sent successfully!");
                    System.out.println("  Session ID: " + sessionManager.getSessionSubID());
                    System.out.println("  State: " + sessionManager.getSessionState());
                    System.out.println("  Waiting for server response...");
                })
                .exceptionally(e -> {
                    System.err.println("✗ Login failed: " + e.getMessage());
                    return null;
                });
    }

    private static void handleLogout(BoeSessionManager sessionManager) {
        System.out.println("Attempting logout...");

        if (!sessionManager.isActive()) {
            System.out.println("Not logged in.");
            return;
        }

        sessionManager.logout()
                .thenRun(() -> System.out.println("✓ Logout request sent successfully!"))
                .exceptionally(e -> {
                    System.err.println("✗ Logout failed: " + e.getMessage());
                    return null;
                });
    }

    private static void handleStatus(BoeSessionManager sessionManager,
                                     BoeConnectionHandler connectionHandler) {
        System.out.println("\n=== Connection Status ===");
        System.out.println("Connected: " + connectionHandler.isConnected());
        System.out.println("Session State: " + sessionManager.getSessionState());
        System.out.println("Session Active: " + sessionManager.isActive());
        System.out.println("Session ID: " +
                (sessionManager.getSessionSubID() != null ? sessionManager.getSessionSubID() : "N/A"));
        System.out.println("Username: " +
                (sessionManager.getUsername() != null ? sessionManager.getUsername() : "N/A"));

        if (sessionManager.getLastHeartbeatTime() != null) {
            System.out.println("Last Heartbeat: " + sessionManager.getLastHeartbeatTime());
        }
        System.out.println("========================\n");
    }

    private static void handleSendTestMessage(BoeConnectionHandler connectionHandler) {
        System.out.println("Sending test message...");

        if (!connectionHandler.isConnected()) {
            System.out.println("Not connected. Please login first.");
            return;
        }

        byte[] testPayload = "TEST_MESSAGE".getBytes();

        connectionHandler.sendMessage(testPayload)
                .thenRun(() -> System.out.println("✓ Test message sent!"))
                .exceptionally(e -> {
                    System.err.println("✗ Failed to send message: " + e.getMessage());
                    return null;
                });
    }

    private static void handleStartListener(BoeConnectionHandler connectionHandler) {
        System.out.println("Starting message listener...");

        if (!connectionHandler.isConnected()) {
            System.out.println("Not connected. Please login first.");
            return;
        }

        connectionHandler.startListener()
                .thenRun(() -> System.out.println("✓ Listener started!"))
                .exceptionally(e -> {
                    System.err.println("✗ Failed to start listener: " + e.getMessage());
                    return null;
                });

        System.out.println("Listener running in background...");
    }

    private static void handleStopListener(BoeConnectionHandler connectionHandler) {
        System.out.println("Stopping message listener...");
        connectionHandler.stopListener();
        System.out.println("✓ Listener stop signal sent!");
    }

    private static void printMenu() {
        System.out.println("\n╔════════════════════════════════════╗");
        System.out.println("║     BOE Simulator - Main Menu      ║");
        System.out.println("╠════════════════════════════════════╣");
        System.out.println("║ 1. login    - Login to BOE server  ║");
        System.out.println("║ 2. logout   - Logout from server   ║");
        System.out.println("║ 3. status   - Show connection info ║");
        System.out.println("║ 4. send     - Send test message    ║");
        System.out.println("║ 5. listener - Start msg listener   ║");
        System.out.println("║ 6. stop     - Stop msg listener    ║");
        System.out.println("║ 7. menu     - Show this menu       ║");
        System.out.println("║ 8. exit     - Exit application     ║");
        System.out.println("╚════════════════════════════════════╝");
    }

    private static void cleanup(BoeSessionManager sessionManager) {
        try {
            LOGGER.info("Cleaning up resources...");

            if (sessionManager.isActive()) {
                LOGGER.info("Logging out...");
                sessionManager.logout();
            }

            sessionManager.shutdown();
            LOGGER.info("Cleanup complete.");

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error during cleanup", e);
        }
    }

    private static void setupLogging() {
        // Configure root logger
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);

        // Remove default handlers
        for (java.util.logging.Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }

        // Add custom console handler
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        consoleHandler.setFormatter(new SimpleFormatter());
        rootLogger.addHandler(consoleHandler);

        // Set specific loggers to different levels if needed
        Logger.getLogger("com.boe.simulator").setLevel(Level.INFO);
    }
}