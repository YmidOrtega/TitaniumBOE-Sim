package com.boe.simulator.api.controller;

import com.boe.simulator.api.dto.ApiResponse;
import com.boe.simulator.api.dto.OrderRequest;
import com.boe.simulator.api.dto.OrderResponse;
import com.boe.simulator.api.service.OrderService;
import io.javalin.http.Context;
import io.javalin.openapi.*;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrderController {
    private static final Logger LOGGER = Logger.getLogger(OrderController.class.getName());

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @OpenApi(
        summary = "Submit a new order",
        description = "Crea una nueva orden de compra o venta en el sistema BOE",
        operationId = "submitOrder",
        path = "/api/orders",
        methods = HttpMethod.POST,
        tags = {"Orders"},
        security = {@OpenApiSecurity(name = "BasicAuth")},
        requestBody = @OpenApiRequestBody(
            content = {@OpenApiContent(from = OrderRequest.class)},
            required = true,
            description = "Datos de la orden a crear"
        ),
        responses = {
            @OpenApiResponse(status = "201", content = {@OpenApiContent(from = OrderResponse.class)}, description = "Orden creada exitosamente"),
            @OpenApiResponse(status = "400", content = {@OpenApiContent(from = ApiResponse.class)}, description = "Solicitud inválida"),
            @OpenApiResponse(status = "401", description = "No autenticado"),
            @OpenApiResponse(status = "422", content = {@OpenApiContent(from = ApiResponse.class)}, description = "Orden rechazada")
        }
    )
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

    @OpenApi(
        summary = "Get active orders",
        description = "Obtiene todas las órdenes activas del usuario autenticado",
        operationId = "getActiveOrders",
        path = "/api/orders/active",
        methods = HttpMethod.GET,
        tags = {"Orders"},
        security = {@OpenApiSecurity(name = "BasicAuth")},
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = OrderResponse[].class)}, description = "Lista de órdenes activas"),
            @OpenApiResponse(status = "401", description = "No autenticado")
        }
    )
    public void getActiveOrders(Context ctx) {
        String username = ctx.attribute("username");

        List<OrderResponse> orders = orderService.getActiveOrders(username);
        ctx.json(ApiResponse.success(orders));
    }

    @OpenApi(
        summary = "Get order by ID",
        description = "Obtiene los detalles de una orden específica por su ID",
        operationId = "getOrder",
        path = "/api/orders/{clOrdID}",
        methods = HttpMethod.GET,
        tags = {"Orders"},
        security = {@OpenApiSecurity(name = "BasicAuth")},
        pathParams = {@OpenApiParam(name = "clOrdID", description = "ID de la orden", required = true)},
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = OrderResponse.class)}, description = "Detalles de la orden"),
            @OpenApiResponse(status = "401", description = "No autenticado"),
            @OpenApiResponse(status = "404", content = {@OpenApiContent(from = ApiResponse.class)}, description = "Orden no encontrada")
        }
    )
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

    @OpenApi(
        summary = "Cancel an order",
        description = "Cancela una orden activa del usuario autenticado",
        operationId = "cancelOrder",
        path = "/api/orders/{clOrdID}",
        methods = HttpMethod.DELETE,
        tags = {"Orders"},
        security = {@OpenApiSecurity(name = "BasicAuth")},
        pathParams = {@OpenApiParam(name = "clOrdID", description = "ID de la orden a cancelar", required = true)},
        responses = {
            @OpenApiResponse(status = "200", content = {@OpenApiContent(from = ApiResponse.class)}, description = "Orden cancelada exitosamente"),
            @OpenApiResponse(status = "400", content = {@OpenApiContent(from = ApiResponse.class)}, description = "No se puede cancelar la orden"),
            @OpenApiResponse(status = "401", description = "No autenticado"),
            @OpenApiResponse(status = "403", content = {@OpenApiContent(from = ApiResponse.class)}, description = "No autorizado para cancelar esta orden"),
            @OpenApiResponse(status = "404", content = {@OpenApiContent(from = ApiResponse.class)}, description = "Orden no encontrada")
        }
    )
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