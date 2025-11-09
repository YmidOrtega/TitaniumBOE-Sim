package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;

public class StatusCommand implements Command {

    @Override
    public void execute(SessionContext context, String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║           Session Status               ║");
        System.out.println("╠════════════════════════════════════════╣");

        if (context.isConnected()) {
            System.out.printf("║ Connection: %-26s ║%n", "✓ CONNECTED");
            System.out.printf("║ Server: %-30s ║%n", context.getHost() + ":" + context.getPort());
            System.out.printf("║ Uptime: %-30s ║%n", formatUptime(context.getUptimeSeconds()));
        } else System.out.printf("║ Connection: %-26s ║%n", "✗ DISCONNECTED");


        if (context.isAuthenticated()) {
            System.out.printf("║ User: %-32s ║%n", context.getUsername());
            System.out.printf("║ Auth: %-32s ║%n", "✓ AUTHENTICATED");
        } else System.out.printf("║ Auth: %-32s ║%n", "✗ NOT AUTHENTICATED");

        System.out.println("╚════════════════════════════════════════╝");
    }

    private String formatUptime(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public String getUsage() {
        return "status";
    }

    @Override
    public String getDescription() {
        return "Show session status";
    }
}