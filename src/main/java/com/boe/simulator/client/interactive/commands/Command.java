package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;

public interface Command {

    // Execute the command with given session context and arguments
    void execute(SessionContext context, String[] args) throws Exception;

    // Get command name
    String getName();

    // Get command usage
    String getUsage();

    // Get command description
    String getDescription();

    // Check if the command requires connection
    default boolean requiresConnection() {
        return false;
    }

    // Check if the command requires authentication
    default boolean requiresAuthentication() {
        return false;
    }
}