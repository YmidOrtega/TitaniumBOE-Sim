package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.BoeClient;
import com.boe.simulator.client.interactive.SessionContext;
import com.boe.simulator.client.interactive.notification.RealtimeMessageListener;

public class ConnectCommand implements Command {

    @Override
    public void execute(SessionContext context, String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: " + getUsage());
            return;
        }

        String host = args[0];
        int port = Integer.parseInt(args[1]);

        // Prompt for credentials
        System.out.print("Username: ");
        String username = System.console() != null
                ? System.console().readLine()
                : new java.util.Scanner(System.in).nextLine();

        System.out.print("Password: ");
        String password = System.console() != null
                ? new String(System.console().readPassword())
                : new java.util.Scanner(System.in).nextLine();

        System.out.printf("Connecting to %s:%d as %s...%n", host, port, username);

        BoeClient client = BoeClient.create(host, port, username, password);

        // Register realtime listener
        RealtimeMessageListener realtimeListener = new RealtimeMessageListener(
                context.getNotificationManager()
        );
        client.getConnectionHandler().setMessageListener(realtimeListener);

        // Start a notification manager
        context.getNotificationManager().start();

        // Connect (includes automatic login)
        client.connect().get();

        context.connect(host, port, client);
        context.authenticate(username);
        context.set("password", password);

        System.out.println("✓ Connected and authenticated successfully");
        System.out.println("✓ Real-time notifications enabled");
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