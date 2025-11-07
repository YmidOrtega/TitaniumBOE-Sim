package com.boe.simulator.api.middleware;

import com.boe.simulator.api.dto.ApiResponse;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.persistence.model.PersistedUser;
import com.boe.simulator.server.persistence.repository.UserRepository;
import com.boe.simulator.server.persistence.util.PasswordHasher;
import io.javalin.http.Context;
import io.javalin.http.Handler;

import java.util.Base64;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public class AuthenticationFilter implements Handler {
    private static final Logger LOGGER = Logger.getLogger(AuthenticationFilter.class.getName());

    private final UserRepository userRepository;

    public AuthenticationFilter(AuthenticationService authService) {
        this.userRepository = (UserRepository) authService.getUserRepository();
    }

    @Override
    public void handle(Context ctx) {
        String authHeader = ctx.header("Authorization");

        if (authHeader == null || !authHeader.startsWith("Basic ")) {
            ctx.json(ApiResponse.error("Missing or invalid Authorization header"));
            ctx.status(401);
            return;
        }

        try {
            String base64Credentials = authHeader.substring(6);
            String credentials = new String(Base64.getDecoder().decode(base64Credentials));
            String[] parts = credentials.split(":", 2);

            if (parts.length != 2) {
                ctx.json(ApiResponse.error("Invalid credentials format"));
                ctx.status(401);
                return;
            }

            String username = parts[0];
            String password = parts[1];

            if (!authenticateWithoutSession(username, password)) {
                LOGGER.log(Level.WARNING, "Authentication failed for user: {0}", username);
                ctx.json(ApiResponse.error("Invalid credentials"));
                ctx.status(401);
                return;
            }

            // Store username in context
            ctx.attribute("username", username);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Authentication error", e);
            ctx.json(ApiResponse.error("Authentication error"));
            ctx.status(500);
        }
    }

    private boolean authenticateWithoutSession(String username, String password) {
        try {
            Optional<PersistedUser> userOpt = userRepository.findByUsername(username);

            if (userOpt.isEmpty()) return false;

            PersistedUser user = userOpt.get();

            if (!user.active()) return false;

            return PasswordHasher.verify(password, user.passwordHash());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error authenticating user: " + username, e);
            return false;
        }
    }
}