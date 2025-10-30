package com.boe.simulator.server.persistence.util;

import org.mindrot.jbcrypt.BCrypt;

import java.util.logging.Logger;

public class PasswordHasher {
    private static final Logger LOGGER = Logger.getLogger(PasswordHasher.class.getName());
    private static final int DEFAULT_LOG_ROUNDS = 12;

    public static String hash(String plainPassword) {
        if (plainPassword == null || plainPassword.isBlank()) throw new IllegalArgumentException("Password cannot be null or blank");

        String hashed = BCrypt.hashpw(plainPassword, BCrypt.gensalt(DEFAULT_LOG_ROUNDS));
        LOGGER.fine("Password hashed successfully");
        return hashed;
    }

    public static boolean verify(String plainPassword, String hashedPassword) {
        if (plainPassword == null || hashedPassword == null) return false;

        try {
            boolean matches = BCrypt.checkpw(plainPassword, hashedPassword);
            LOGGER.fine("Password verification: " + (matches ? "success" : "failed"));
            return matches;
        } catch (Exception e) {
            LOGGER.warning("Password verification error: " + e.getMessage());
            return false;
        }
    }

    public static boolean needsUpgrade(String hashedPassword) {
        try {

            String[] parts = hashedPassword.split("\\$");
            if (parts.length < 3) return true;

            int rounds = Integer.parseInt(parts[2]);
            return rounds < DEFAULT_LOG_ROUNDS;
        } catch (Exception e) {
            LOGGER.warning("Failed to parse BCrypt hash: " + e.getMessage());
            return true;
        }
    }
}
