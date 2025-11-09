package com.boe.simulator.client.interactive;

import com.boe.simulator.client.interactive.commands.*;

import java.util.*;

public class CommandExecutor {

    private final Map<String, Command> commands;
    private final SessionContext context;

    public CommandExecutor(SessionContext context) {
        this.context = context;
        this.commands = new HashMap<>();
        registerDefaultCommands();
    }

    private void registerDefaultCommands() {
        // Connection commands
        registerCommand(new ConnectCommand());
        registerCommand(new ExitCommand());

        // Trading commands
        registerCommand(new OrderCommand());
        registerCommand(new CancelCommand());

        // Market data commands
        registerCommand(new BookCommand());
        registerCommand(new PositionsCommand());
        registerCommand(new TradesCommand());

        // Utility commands
        registerCommand(new StatusCommand());
        registerCommand(new HelpCommand(this));
        registerCommand(new ClearCommand());
        registerCommand(new ScriptCommand(this));
    }

    public void registerCommand(Command command) {
        commands.put(command.getName().toLowerCase(), command);
    }

    public void execute(String commandLine) {
        CommandParser.ParsedCommand parsed = CommandParser.parse(commandLine);

        if (parsed.commandName().isEmpty()) return; // Empty command

        Command command = commands.get(parsed.commandName());

        if (command == null) {
            System.out.println("Unknown command: " + parsed.commandName());
            System.out.println("Type 'help' for available commands");
            return;
        }

        // Check requirements
        if (command.requiresConnection() && !context.isConnected()) {
            System.out.println("✗ This command requires an active connection");
            System.out.println("Use 'connect <host> <port>' first");
            return;
        }

        if (command.requiresAuthentication() && !context.isAuthenticated()) {
            System.out.println("✗ This command requires authentication");
            System.out.println("Use 'login <username> <password>' first");
            return;
        }

        try {
            command.execute(context, parsed.args());
        } catch (NumberFormatException e) {
            System.out.println("✗ Invalid number format: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("✗ Error executing command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public Command getCommand(String name) {
        return commands.get(name.toLowerCase());
    }

    public Collection<Command> getAllCommands() {
        return new ArrayList<>(commands.values());
    }
}