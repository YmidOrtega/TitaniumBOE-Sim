package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;

public class ClearCommand implements Command {

    @Override
    public void execute(SessionContext context, String[] args) {
        // ANSI escape code to clear the screen
        System.out.print("\033[H\033[2J");
        System.out.flush();
    }

    @Override
    public String getName() {
        return "clear";
    }

    @Override
    public String getUsage() {
        return "clear";
    }

    @Override
    public String getDescription() {
        return "Clear the screen";
    }
}