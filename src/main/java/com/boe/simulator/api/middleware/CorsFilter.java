package com.boe.simulator.api.middleware;

import java.util.logging.Level;
import java.util.logging.Logger;

import io.javalin.http.Context;
import io.javalin.http.Handler;

public class CorsFilter implements Handler {
    private static final Logger LOGGER = Logger.getLogger(CorsFilter.class.getName());
    private final String allowedOrigins;
    
    public CorsFilter() {
        // Read allowed origins from environment variable
        // For production: set ALLOWED_ORIGINS="https://yourdomain.com"
        // For development: defaults to common local dev servers
        this.allowedOrigins = System.getenv().getOrDefault(
            "ALLOWED_ORIGINS",
            "http://localhost:3000,http://localhost:5173,http://localhost:8080"
        );
        
        if (allowedOrigins.contains("*")) LOGGER.warning("⚠️  CORS configured with wildcard (*) - NOT recommended for production");
        else LOGGER.log(Level.INFO, "CORS configured for origins: {0}", allowedOrigins);
        
    }
    
    @Override
    public void handle(Context ctx) {
        String origin = ctx.header("Origin");
        
        // Check if origin is in allowed list
        if (origin != null && isOriginAllowed(origin)) ctx.header("Access-Control-Allow-Origin", origin);
        else if (allowedOrigins.equals("*")) ctx.header("Access-Control-Allow-Origin", "*"); // Fallback to wildcard only if explicitly configured
        else {
            // For development convenience, allow if no Origin header (e.g., Postman)
            if (origin == null) ctx.header("Access-Control-Allow-Origin", allowedOrigins.split(",")[0]);
            
        }
        
        ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
        ctx.header("Access-Control-Allow-Headers", "Content-Type, Authorization");
        ctx.header("Access-Control-Allow-Credentials", "true");
        ctx.header("Access-Control-Max-Age", "3600");

        if ("OPTIONS".equalsIgnoreCase(ctx.method().name())) ctx.status(204);
    }
    
    private boolean isOriginAllowed(String origin) {
        if (allowedOrigins.equals("*")) return true;
        
        String[] allowed = allowedOrigins.split(",");
        for (String allowedOrigin : allowed) {
            if (allowedOrigin.trim().equalsIgnoreCase(origin)) return true;
        }
        return false;
    }
}