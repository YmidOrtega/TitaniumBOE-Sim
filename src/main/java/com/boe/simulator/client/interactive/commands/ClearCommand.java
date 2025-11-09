package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;

public class ClearCommand implements Command {

    @Override
    public void execute(SessionContext context, String[] args) {
        try {
            // Try to detect OS and use appropriate clear method
            String os = System.getProperty("os.name").toLowerCase();
            
            if (os.contains("win")) {
                // Windows - use cls command
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                // Unix/Linux/Mac - use ANSI escape codes and also try clear command
                // Clear screen and move cursor to top-left
                System.out.print("\033[H\033[2J");
                System.out.flush();
                
                // Also print many newlines as fallback
                for (int i = 0; i < 50; i++) {
                    System.out.println();
                }
                
                // Move cursor back to top
                System.out.print("\033[H");
                System.out.flush();
            }
        } catch (Exception e) {
            // Fallback: just print many newlines
            for (int i = 0; i < 50; i++) {
                System.out.println();
            }
        }
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