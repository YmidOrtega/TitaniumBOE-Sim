package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;
import com.boe.simulator.protocol.message.CancelOrderMessage;

public class CancelCommand implements Command {

    @Override
    public void execute(SessionContext context, String[] args) throws Exception {
        if (args.length < 1) {
            System.out.println("Usage: " + getUsage());
            return;
        }

        String clOrdID = args[0];

        System.out.printf("Cancelling order %s...%n", clOrdID);

        // Create CancelOrderMessage
        CancelOrderMessage cancel = new CancelOrderMessage();
        cancel.setOrigClOrdID(clOrdID);

        // Send it via connection handler
        context.getClient().getConnectionHandler()
                .sendMessageRaw(cancel.toBytes())
                .get();

        System.out.println("✓ Cancel request submitted");
        Thread.sleep(300);
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