package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;

public class ExitCommand implements Command {

    @Override
    public void execute(SessionContext context, String[] args) {
        System.out.println("Disconnecting...");

        if (context.isConnected()) context.disconnect();

        System.out.println("✓ Goodbye!");
        System.exit(0);
    }

    @Override
    public String getName() {
        return "exit";
    }

    @Override
    public String getUsage() {
        return "exit";
    }

    @Override
    public String getDescription() {
        return "Exit the CLI client";
    }
}