package com.boe.simulator.api.controller;

import com.boe.simulator.api.dto.ApiResponse;
import com.boe.simulator.api.dto.AuthResponse;
import com.boe.simulator.api.dto.LoginRequest;
import com.boe.simulator.api.dto.RegisterRequest;
import com.boe.simulator.server.auth.AuthenticationService;
import io.javalin.http.Context;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;

public class AuthController {
    private static final Logger LOGGER = Logger.getLogger(AuthController.class.getName());

    private static final Pattern USERNAME_PATTERN = Pattern.compile("[A-Za-z0-9]{1,4}");
    private static final int PASSWORD_MIN = 6;
    private static final int PASSWORD_MAX = 10;

    private final AuthenticationService authService;

    public AuthController(AuthenticationService authService) {
        this.authService = authService;
    }

    public void login(Context ctx) {
        LoginRequest req = ctx.bodyAsClass(LoginRequest.class);

        if (req.username() == null || req.username().isBlank() ||
                req.password() == null || req.password().isBlank()) {
            ctx.status(400).json(ApiResponse.error("Username and password are required"));
            return;
        }

        if (!authService.authenticateForRest(req.username(), req.password())) {
            LOGGER.log(Level.WARNING, "Failed REST login for user: {0}", req.username());
            ctx.status(401).json(ApiResponse.error("Invalid username or password"));
            return;
        }

        LOGGER.log(Level.INFO, "REST login successful for user: {0}", req.username());
        ctx.status(200).json(ApiResponse.success(new AuthResponse(req.username(), "Login successful")));
    }

    public void register(Context ctx) {
        RegisterRequest req = ctx.bodyAsClass(RegisterRequest.class);

        if (req.username() == null || req.username().isBlank()) {
            ctx.status(400).json(ApiResponse.error("Username is required"));
            return;
        }

        if (!USERNAME_PATTERN.matcher(req.username()).matches()) {
            ctx.status(400).json(ApiResponse.error("Username must be 1-4 alphanumeric characters"));
            return;
        }

        if (req.password() == null || req.password().length() < PASSWORD_MIN || req.password().length() > PASSWORD_MAX) {
            ctx.status(400).json(ApiResponse.error(
                    "Password must be between %d and %d characters".formatted(PASSWORD_MIN, PASSWORD_MAX)));
            return;
        }

        if (authService.userExists(req.username())) {
            ctx.status(409).json(ApiResponse.error("Username already taken"));
            return;
        }

        authService.createUser(req.username(), req.password());
        LOGGER.log(Level.INFO, "New user registered via REST: {0}", req.username());
        ctx.status(201).json(ApiResponse.success(new AuthResponse(req.username(), "Account created successfully")));
    }

    public void me(Context ctx) {
        String username = ctx.attribute("username");
        ctx.status(200).json(ApiResponse.success(new AuthResponse(username, "Authenticated")));
    }
}
