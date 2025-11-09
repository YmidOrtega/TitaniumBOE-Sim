package com.boe.simulator.client.interactive;

import java.util.ArrayList;
import java.util.List;

public class CommandParser {

    public static ParsedCommand parse(String input) {
        if (input == null || input.isBlank()) return new ParsedCommand("", new String[0]);

        input = input.trim();

        // Split by spaces, but respect quotes
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (char c : input.toCharArray()) {
            if (c == '"') inQuotes = !inQuotes;
            else if (c == ' ' && !inQuotes) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
            } else current.append(c);
        }

        if (current.length() > 0) tokens.add(current.toString());

        if (tokens.isEmpty()) return new ParsedCommand("", new String[0]);

        String commandName = tokens.get(0).toLowerCase();
        String[] args = tokens.size() > 1
                ? tokens.subList(1, tokens.size()).toArray(new String[0])
                : new String[0];

        return new ParsedCommand(commandName, args);
    }

    public record ParsedCommand(String commandName, String[] args) {}
}