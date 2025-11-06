package com.boe.simulator.api.middleware;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class CorsFilter implements Handler {
    @Override
    public void handle(Context ctx) {
        ctx.header("Access-Control-Allow-Origin", "*");
        ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ctx.header("Access-Control-Max-Age", "3600");

        if ("OPTIONS".equalsIgnoreCase(ctx.method().name())) {
            ctx.status(204);
        }
    }
}