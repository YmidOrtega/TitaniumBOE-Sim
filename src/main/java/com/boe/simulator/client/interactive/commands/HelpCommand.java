package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.CommandExecutor;
import com.boe.simulator.client.interactive.SessionContext;

public class HelpCommand implements Command {

    private final CommandExecutor executor;

    public HelpCommand(CommandExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void execute(SessionContext context, String[] args) {
        if (args.length > 0) {
            // Show help for a specific command
            String cmdName = args[0].toLowerCase();
            Command cmd = executor.getCommand(cmdName);

            if (cmd != null) {
                System.out.println("\n" + cmd.getName().toUpperCase());
                System.out.println("  " + cmd.getDescription());
                System.out.println("\nUsage:");
                System.out.println("  " + cmd.getUsage());
                System.out.println();
            } else System.out.println("Unknown command: " + cmdName);
        } else {
            // Show all commands
            System.out.println("\n╔════════════════════════════════════════════════════════╗");
            System.out.println("║              Available Commands                        ║");
            System.out.println("╠════════════════════════════════════════════════════════╣");

            executor.getAllCommands().forEach(cmd -> {
                System.out.printf("║ %-15s - %-36s ║%n",
                        cmd.getName(),
                        truncate(cmd.getDescription(), 36));
            });

            System.out.println("╚════════════════════════════════════════════════════════╝");
            System.out.println("\nType 'help <command>' for detailed information");
            System.out.println();
        }
    }

    private String truncate(String str, int maxLen) {
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getUsage() {
        return "help [command]";
    }

    @Override
    public String getDescription() {
        return "Show available commands";
    }
}