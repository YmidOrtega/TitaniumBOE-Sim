package com.boe.simulator.api.service;

import com.boe.simulator.api.dto.OrderRequest;
import com.boe.simulator.api.dto.OrderResponse;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderManager;
import com.boe.simulator.server.order.OrderRepository;
import com.boe.simulator.server.session.ClientSession;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OrderService {
    private static final Logger LOGGER = Logger.getLogger(OrderService.class.getName());
    private static final AtomicLong ORDER_ID_GENERATOR = new AtomicLong(System.currentTimeMillis());

    private final OrderManager orderManager;
    private final OrderRepository orderRepository;

    public OrderService(OrderManager orderManager, OrderRepository orderRepository) {
        this.orderManager = orderManager;
        this.orderRepository = orderRepository;
    }

    public OrderResponse submitOrder(OrderRequest request, String username) {
        request.validate();

        LOGGER.log(Level.INFO, "REST API: Submitting order for user {0}: {1} {2} {3} @ {4}", new Object[]{
                username,
                request.side(),
                request.symbol(),
                request.orderQty(),
                request.price()
        });

        // Create Order directly instead of going through NewOrderMessage
        String clOrdID = generateClOrdID();
        long orderID = ORDER_ID_GENERATOR.incrementAndGet();

        Order order = Order.builder()
                .clOrdID(clOrdID)
                .orderID(orderID)
                .sessionSubID("REST-API")
                .username(username)
                .side(request.getSideByte())
                .orderQty(request.orderQty())
                .price(request.price())
                .ordType(request.getOrderTypeByte())
                .symbol(request.symbol())
                .capacity(request.getCapacityByte())
                .account(request.account() != null ? request.account() : "")
                .clearingFirm("")
                .clearingAccount("")
                .openClose((byte) 'N')
                .routingInst((byte) 'B')
                .receivedSequence(0)
                .matchingUnit((byte) 0)
                .build();

        try {
            // Acknowledge the order
            order.acknowledge();

            // Process through matching engine
            var trades = orderManager.getMatchingEngine().processOrder(order);

            LOGGER.log(Level.INFO, "REST API: Order {0} processed, {1} trades generated", new Object[]{
                    clOrdID,
                    trades.size()
            });

            return OrderResponse.fromOrder(order);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to process order from REST API", e);
            throw new IllegalStateException("Order processing failed: " + e.getMessage());
        }
    }

    public List<OrderResponse> getActiveOrders(String username) {
        return orderRepository.findByUsername(username).stream()
                .filter(Order::isLive)
                .map(OrderResponse::fromOrder)
                .collect(Collectors.toList());
    }

    public Optional<OrderResponse> getOrder(String clOrdID, String username) {
        return orderRepository.findByClOrdID(clOrdID)
                .filter(order -> order.getUsername().equals(username))
                .map(OrderResponse::fromOrder);
    }

    public void cancelOrder(String clOrdID, String username) {
        var orderOpt = orderRepository.findByClOrdID(clOrdID);

        if (orderOpt.isEmpty()) throw new IllegalArgumentException("Order not found: " + clOrdID);

        Order order = orderOpt.get();

        if (!order.getUsername().equals(username)) throw new SecurityException("Unauthorized: Order belongs to different user");
        if (!order.getState().isCancellable()) throw new IllegalStateException("Order cannot be cancelled in state: " + order.getState());

        // Cancel through matching engine
        orderManager.getMatchingEngine().cancelOrder(order);
        order.cancel();
        orderRepository.save(order);

        LOGGER.log(Level.INFO, "REST API: Cancelled order {0} for user {1}", new Object[]{
                clOrdID,
                username
        });
    }

    private String generateClOrdID() {
        long timestamp = System.currentTimeMillis() % 100000000;
        int random = (int)(Math.random() * 1000);
        return String.format("R-%d-%03d", timestamp, random);
    }
}