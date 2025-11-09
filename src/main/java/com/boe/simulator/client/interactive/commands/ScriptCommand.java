package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.CommandExecutor;
import com.boe.simulator.client.interactive.SessionContext;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ScriptCommand implements Command {
    private static final Logger LOGGER = Logger.getLogger(ScriptCommand.class.getName());

    private final CommandExecutor executor;

    public ScriptCommand(CommandExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(SessionContext context, String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: " + getUsage());
            return;
        }

        String scriptFile = args[0];
        boolean verbose = args.length > 1 && "--verbose".equals(args[1]);

        System.out.println("Executing script: " + scriptFile);
        System.out.println("─".repeat(50));

        try (BufferedReader reader = new BufferedReader(new FileReader(scriptFile))) {
            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();

                // Skip empty lines and comments
                if (line.isEmpty() || line.startsWith("#")) continue;

                // Handle sleep command
                if (line.startsWith("sleep ")) {
                    int seconds = Integer.parseInt(line.substring(6).trim());
                    if (verbose) System.out.printf("[Line %d] Sleeping for %d seconds...%n", lineNumber, seconds);

                    Thread.sleep(seconds * 1000L);
                    continue;
                }

                // Handle echo command
                if (line.startsWith("echo ")) {
                    String message = line.substring(5).trim();
                    System.out.println(message);
                    continue;
                }

                // Execute command
                if (verbose) {
                    System.out.printf("[Line %d] > %s%n", lineNumber, line);
                }

                try {
                    executor.execute(line);
                    Thread.sleep(500); // Small delay between commands
                } catch (Exception e) {
                    System.err.printf("✗ Error on line %d: %s%n", lineNumber, e.getMessage());
                    LOGGER.log(Level.WARNING, "Script error on line " + lineNumber, e);
                }
            }

            System.out.println("─".repeat(50));
            System.out.println("✓ Script completed");

        } catch (Exception e) {
            System.err.println("✗ Failed to execute script: " + e.getMessage());
            throw e;
        }
    }

    @Override
    public String getName() {
        return "script";
    }

    @Override
    public String getUsage() {
        return "script <file.txt> [--verbose]";
    }

    @Override
    public String getDescription() {
        return "Execute commands from a script file";
    }
}