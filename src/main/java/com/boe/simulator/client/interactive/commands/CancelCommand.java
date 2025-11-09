package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;
import com.boe.simulator.protocol.message.CancelOrderMessage;

public class CancelCommand implements Command {

    @Override
    public void execute(SessionContext context, String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("Usage: " + getUsage());
                return;
            }

            String clOrdID = args[0];

            System.out.printf("Cancelling order %s...%n", clOrdID);

            CancelOrderMessage cancel = new CancelOrderMessage(clOrdID);

            // Serialize and send
            byte[] messageBytes = cancel.toBytes();

            // Send via connection handler
            context.getClient().getConnectionHandler().sendMessageRaw(messageBytes).get();

            System.out.println("✓ Cancel request submitted");
            Thread.sleep(300);
        } catch (Exception e) {
            System.out.println("✗ Error cancelling order: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "cancel";
    }

    @Override
    public String getUsage() {
        return "cancel <clOrdID>";
    }

    @Override
    public String getDescription() {
        return "Cancel an order";
    }

    @Override
    public boolean requiresConnection() {
        return true;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }
}