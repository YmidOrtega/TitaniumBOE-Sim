package com.boe.simulator.client.interactive.commands;

import java.math.BigDecimal;

import com.boe.simulator.client.interactive.SessionContext;
import com.boe.simulator.protocol.message.NewOrderMessage;

public class OrderCommand implements Command {

    @Override
    public void execute(SessionContext context, String[] args) {
        try {
            if (args.length < 4) {
                System.out.println("Usage: " + getUsage());
                return;
            }

            String sideStr = args[0].toUpperCase();
            String symbol = args[1].toUpperCase();
            int quantity = Integer.parseInt(args[2]);
            BigDecimal price = new BigDecimal(args[3]);

            // Validate side
            byte side;
            if ("BUY".equals(sideStr) || "B".equals(sideStr)) side = 1;
            else if ("SELL".equals(sideStr) || "S".equals(sideStr)) side = 2;
            else {
                System.out.println("✗ Invalid side. Use BUY or SELL");
                return;
            }

            String clOrdID = generateClOrdID(context);

            System.out.printf("Submitting order: %s %d %s @ %s (ID: %s)%n", sideStr, quantity, symbol, price, clOrdID);

            NewOrderMessage order = new NewOrderMessage();
            order.setClOrdID(clOrdID);
            order.setSide(side);
            order.setOrderQty(quantity);
            order.setSymbol(symbol);
            order.setPrice(price);
            order.setOrdType((byte) 2); // 2 = Limit order
            order.setCapacity((byte) 'C'); // C = Customer

            // Serialize and send
            byte[] messageBytes = order.toBytes();

            // Send it via connection handler
            context.getClient().getConnectionHandler().sendMessageRaw(messageBytes).get();

            System.out.println("✓ Order submitted, waiting for acknowledgment...");
            Thread.sleep(500);
        } catch (NumberFormatException e) {
            System.out.println("✗ Invalid number format: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("✗ Error submitting order: " + e.getMessage());
        }
    }

    private String generateClOrdID(SessionContext context) {
        long timestamp = System.currentTimeMillis() % 100000;
        return String.format("CLI-%d", timestamp);
    }

    @Override
    public String getName() {
        return "order";
    }

    @Override
    public String getUsage() {
        return "order <buy|sell> <symbol> <quantity> <price>";
    }

    @Override
    public String getDescription() {
        return "Submit a new limit order";
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