package com.boe.simulator.client.interactive;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class InteractiveCLI {
    private static final Logger LOGGER = Logger.getLogger(InteractiveCLI.class.getName());

    private final SessionContext context;
    private final CommandExecutor executor;
    private final BufferedReader reader;
    private boolean running;

    public InteractiveCLI() {
        this.context = new SessionContext();
        this.executor = new CommandExecutor(context);
        this.reader = new BufferedReader(new InputStreamReader(System.in));
        this.running = false;

        // Disable verbose logging for cleaner output
        Logger.getLogger("").setLevel(Level.WARNING);
    }

    public void start() {
        running = true;
        printBanner();
        printWelcome();

        while (running) {
            try {
                String prompt = buildPrompt();
                System.out.print(prompt);

                String line = reader.readLine();

                if (line == null) break; // EOF (Ctrl+D)

                line = line.trim();

                if (!line.isEmpty()) executor.execute(line);

            } catch (Exception e) {
                System.out.println("✗ Error: " + e.getMessage());
                LOGGER.log(Level.SEVERE, "CLI error", e);
            }
        }

        shutdown();
    }

    private void printBanner() {
        System.out.println("""
            ╔════════════════════════════════════════════════════════════╗
            ║                                                            ║
            ║         CBOE BOE Protocol - Interactive CLI Client         ║
            ║                     Version 1.0.0                          ║
            ║                                                            ║
            ╚════════════════════════════════════════════════════════════╝
            """);
    }

    private void printWelcome() {
        System.out.println("Welcome! Type 'help' for available commands.");
        System.out.println("Start by connecting: connect localhost 8081");
        System.out.println();
    }

    private String buildPrompt() {
        StringBuilder prompt = new StringBuilder();

        if (context.isConnected()) {
            prompt.append("\u001B[32m"); // Green
            prompt.append("●");
            prompt.append("\u001B[0m"); // Reset
        } else {
            prompt.append("\u001B[31m"); // Red
            prompt.append("●");
            prompt.append("\u001B[0m"); // Reset
        }

        if (context.isAuthenticated()) prompt.append(" ").append(context.getUsername());
        else prompt.append(" guest");

        prompt.append("> ");

        return prompt.toString();
    }

    private void shutdown() {
        System.out.println("\nShutting down...");

        // Stop notification manager
        context.getNotificationManager().stop();

        if (context.isConnected()) context.disconnect();

        try {
            reader.close();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error closing reader", e);
        }

        System.out.println("Goodbye!");
    }

    public static void main(String[] args) {
        InteractiveCLI cli = new InteractiveCLI();

        // Add a shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n\nReceived shutdown signal...");
        }));

        cli.start();
    }
}