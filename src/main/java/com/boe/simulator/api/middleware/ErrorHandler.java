package com.boe.simulator.api.middleware;

import com.boe.simulator.api.dto.ApiResponse;
import io.javalin.http.Context;
import io.javalin.http.ExceptionHandler;
import io.javalin.http.HttpStatus;

import java.util.logging.Level;
import java.util.logging.Logger;

public class ErrorHandler {
    private static final Logger LOGGER = Logger.getLogger(ErrorHandler.class.getName());

    public static ExceptionHandler<Exception> handle() {
        return (e, ctx) -> {
            LOGGER.log(Level.SEVERE, "Unhandled exception in REST API", e);

            ctx.status(HttpStatus.INTERNAL_SERVER_ERROR);
            ctx.json(ApiResponse.error("Internal server error: " + e.getMessage()));
        };
    }

    public static ExceptionHandler<IllegalArgumentException> handleIllegalArgument() {
        return (e, ctx) -> {
            LOGGER.log(Level.WARNING, "Bad request: {0}", e.getMessage());

            ctx.status(HttpStatus.BAD_REQUEST);
            ctx.json(ApiResponse.error(e.getMessage()));
        };
    }

    public static ExceptionHandler<SecurityException> handleSecurity() {
        return (e, ctx) -> {
            LOGGER.log(Level.WARNING, "Security violation: {0}", e.getMessage());

            ctx.status(HttpStatus.FORBIDDEN);
            ctx.json(ApiResponse.error(e.getMessage()));
        };
    }

    public static void configure(io.javalin.Javalin app) {
        app.exception(Exception.class, handle());
        app.exception(IllegalArgumentException.class, handleIllegalArgument());
        app.exception(SecurityException.class, handleSecurity());
    }
}