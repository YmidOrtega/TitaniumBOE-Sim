package com.boe.simulator.api.controller;

import com.boe.simulator.api.dto.ApiResponse;
import com.boe.simulator.api.dto.OrderRequest;
import com.boe.simulator.api.dto.OrderResponse;
import com.boe.simulator.api.service.OrderService;
import io.javalin.http.Context;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrderController {
    private static final Logger LOGGER = Logger.getLogger(OrderController.class.getName());

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    public void submitOrder(Context ctx) {
        String username = ctx.attribute("username");
        OrderRequest request = ctx.bodyAsClass(OrderRequest.class);

        try {
            OrderResponse response = orderService.submitOrder(request, username);
            ctx.json(ApiResponse.success(response));
            ctx.status(201);
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Invalid order request: {0}", e.getMessage());
            ctx.json(ApiResponse.error(e.getMessage()));
            ctx.status(400);
        } catch (IllegalStateException e) {
            LOGGER.log(Level.WARNING, "Order rejected: {0}", e.getMessage());
            ctx.json(ApiResponse.error(e.getMessage()));
            ctx.status(422);
        }
    }

    public void getActiveOrders(Context ctx) {
        String username = ctx.attribute("username");

        List<OrderResponse> orders = orderService.getActiveOrders(username);
        ctx.json(ApiResponse.success(orders));
    }

    public void getOrder(Context ctx) {
        String username = ctx.attribute("username");
        String clOrdID = ctx.pathParam("clOrdID");

        orderService.getOrder(clOrdID, username)
                .ifPresentOrElse(
                        order -> ctx.json(ApiResponse.success(order)),
                        () -> {
                            ctx.json(ApiResponse.error("Order not found"));
                            ctx.status(404);
                        }
                );
    }

    public void cancelOrder(Context ctx) {
        String username = ctx.attribute("username");
        String clOrdID = ctx.pathParam("clOrdID");

        try {
            orderService.cancelOrder(clOrdID, username);
            ctx.json(ApiResponse.success("Order cancelled successfully"));
        } catch (IllegalArgumentException e) {
            ctx.json(ApiResponse.error(e.getMessage()));
            ctx.status(404);
        } catch (SecurityException e) {
            ctx.json(ApiResponse.error(e.getMessage()));
            ctx.status(403);
        } catch (IllegalStateException e) {
            ctx.json(ApiResponse.error(e.getMessage()));
            ctx.status(400);
        }
    }
}