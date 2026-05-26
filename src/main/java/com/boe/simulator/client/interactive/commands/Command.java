package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;

public sealed interface Command
        permits BookCommand, CancelCommand, ClearCommand, ConnectCommand,
                ExitCommand, HelpCommand, OrderCommand, PositionsCommand,
                ScriptCommand, StatusCommand, TradesCommand {

    void execute(SessionContext context, String[] args) throws Exception;

    String getName();

    String getUsage();

    String getDescription();

    default boolean requiresConnection() {
        return false;
    }

    default boolean requiresAuthentication() {
        return false;
    }
}
