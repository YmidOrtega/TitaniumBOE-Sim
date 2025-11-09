package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.BoeClient;
import com.boe.simulator.client.interactive.SessionContext;
import com.boe.simulator.client.interactive.notification.NotificationTradingListener;
import com.boe.simulator.client.interactive.util.ColorOutput;

public class ConnectCommand implements Command {

    @Override
    public void execute(SessionContext context, String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: " + getUsage());
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        System.out.print("Username (max 4 chars): ");
        String username = System.console() != null ? System.console().readLine() : new java.util.Scanner(System.in).nextLine();

        if (username == null || username.isEmpty()) {
            ColorOutput.error("Username cannot be empty");
            return;
        }

        if (username.length() > 4) {
            ColorOutput.error("Username must be 4 characters or less");
            return;
        }

        System.out.print("Password: ");
        String password = System.console() != null ? new String(System.console().readPassword()) : new java.util.Scanner(System.in).nextLine();

        if (password == null || password.isEmpty()) {
            ColorOutput.error("Password cannot be empty");
            return;
        }

        System.out.printf("Connecting to %s:%d as %s...%n", host, port, username);

        try {
            BoeClient client = BoeClient.create(host, port, username, password);

            // Start a notification manager
            context.getNotificationManager().start();

            // Register trading listener for notifications
            NotificationTradingListener tradingListener =
                    new NotificationTradingListener(context.getNotificationManager());
            client.getConnectionHandler().addTradingListener(tradingListener);

            // Connect (includes automatic login)
            client.connect().get();

            // Check if authentication was successful
            if (!client.isAuthenticated()) {
                // Cleanup on authentication failure
                context.getNotificationManager().stop();
                client.getConnectionHandler().removeTradingListener(tradingListener);
                client.disconnect();
                ColorOutput.error("Authentication failed - invalid username or password");
                return;
            }

            context.connect(host, port, client);
            context.authenticate(username);
            context.set("password", password);
            context.set("tradingListener", tradingListener); // Store for cleanup

            ColorOutput.success("Connected and authenticated successfully");
            ColorOutput.info("Real-time notifications enabled");

        } catch (Exception e) {
            // Cleanup notification manager if it was started
            context.getNotificationManager().stop();
            
            // Extract the root cause message for user-friendly error
            Throwable cause = e;
            while (cause.getCause() != null && cause.getCause() != cause) {
                cause = cause.getCause();
            }
            
            String errorMessage = cause.getMessage();
            if (errorMessage == null || errorMessage.isEmpty()) {
                errorMessage = cause.getClass().getSimpleName();
            }
            
            ColorOutput.error("Connection failed: " + errorMessage);
            // Don't re-throw - just return to allow user to try again
        }
    }

    @Override
    public String getName() {
        return "connect";
    }

    @Override
    public String getUsage() {
        return "connect <host> <port>";
    }

    @Override
    public String getDescription() {
        return "Connect to CBOE server and authenticate";
    }
}