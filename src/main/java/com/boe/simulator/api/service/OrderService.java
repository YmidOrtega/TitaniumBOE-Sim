package com.boe.simulator.api.service;

import com.boe.simulator.api.dto.OrderRequest;
import com.boe.simulator.api.dto.OrderResponse;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderManager;
import com.boe.simulator.server.order.OrderRepository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class OrderService {
    private static final Logger LOGGER = Logger.getLogger(OrderService.class.getName());
    private static final AtomicLong SEQUENCE = new AtomicLong(1);

    private final OrderManager orderManager;
    private final OrderRepository orderRepository;

    public OrderService(OrderManager orderManager, OrderRepository orderRepository) {
        this.orderManager = orderManager;
        this.orderRepository = orderRepository;
    }

    public OrderResponse submitOrder(OrderRequest request, String username) {
        request.validate();

        LOGGER.log(Level.INFO, "REST API: Submitting order for user {0}: {1} {2} {3} @ {4}",
                new Object[]{username, request.side(), request.symbol(), request.orderQty(), request.price()});

        NewOrderMessage newOrder = createNewOrderMessage(request);

        var response = orderManager.processNewOrder(newOrder, username);

        if (response.isRejected()) throw new IllegalStateException("Order rejected: " + response.getRejectText());

        return OrderResponse.fromOrder(response.getOrder());
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

        orderManager.processCancelOrder(clOrdID, username);

        LOGGER.log(Level.INFO, "REST API: Cancelled order {0} for user {1}",
                new Object[]{clOrdID, username});
    }

    private NewOrderMessage createNewOrderMessage(OrderRequest request) {
        NewOrderMessage message = new NewOrderMessage();
        try {
            setField(message, "clOrdID", generateClOrdID());
            setField(message, "symbol", request.symbol());
            setField(message, "side", request.getSideByte());
            setField(message, "orderQty", request.orderQty());
            setField(message, "price", request.price());
            setField(message, "ordType", request.getOrderTypeByte());
            setField(message, "capacity", request.getCapacityByte());
            setField(message, "account", request.account() != null ? request.account() : "");
            setField(message, "matchingUnit", (byte) 0);
            setField(message, "sequenceNumber", 0);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to create NewOrderMessage", e);
            throw new RuntimeException("Failed to create order message", e);
        }

        return message;
    }

    private void setField(Object obj, String fieldName, Object value) throws Exception {
        var field = obj.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(obj, value);
    }

    private String generateClOrdID() {
        long seq = SEQUENCE.getAndIncrement() % 100000;
        long time = System.currentTimeMillis() % 100000;
        return String.format("R%05d-%05d", seq, time);
    }
}