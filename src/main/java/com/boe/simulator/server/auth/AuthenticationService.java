package com.boe.simulator.server.auth;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class AuthenticationService {
    private static final Logger LOGGER = Logger.getLogger(AuthenticationService.class.getName());

    // In-memory user database (username -> password)
    private final ConcurrentHashMap<String, String> users;

    // Track active sessions to prevent duplicates (username -> sessionSubID)
    private final ConcurrentHashMap<String, String> activeSessions;

    public AuthenticationService() {
        this.users = new ConcurrentHashMap<>();
        this.activeSessions = new ConcurrentHashMap<>();

        // Initialize with some default users
        initializeDefaultUsers();
    }

    private void initializeDefaultUsers() {
        // Add some test users
        users.put("USER", "PASS");
        users.put("TRADER1", "PASS1");
        users.put("TRADER2", "PASS2");
        users.put("ADMIN", "ADMIN123");
        users.put("TEST", "TEST");

        LOGGER.info("Initialized authentication service with " + users.size() + " users");
    }

    public AuthenticationResult authenticate(String username, String password, String sessionSubID) {
        if (username == null || username.trim().isEmpty()) {
            return AuthenticationResult.rejected("Username cannot be empty");
        }

        if (password == null || password.trim().isEmpty()) {
            return AuthenticationResult.rejected("Password cannot be empty");
        }

        // Check if user exists
        if (!users.containsKey(username)) {
            LOGGER.warning("Login attempt for unknown user: " + username);
            return AuthenticationResult.rejected("Invalid username or password");
        }

        // Check password
        String expectedPassword = users.get(username);
        if (!expectedPassword.equals(password)) {
            LOGGER.warning("Invalid password for user: " + username);
            return AuthenticationResult.rejected("Invalid username or password");
        }

        // Check for duplicate session
        if (activeSessions.containsKey(username)) {
            String existingSession = activeSessions.get(username);
            LOGGER.warning("User " + username + " already has active session: " + existingSession);
            return AuthenticationResult.sessionInUse("Session already active for this user");
        }

        // Authentication successful
        activeSessions.put(username, sessionSubID);
        LOGGER.info("User " + username + " authenticated successfully (session: " + sessionSubID + ")");

        return AuthenticationResult.accepted("Login successful");
    }

    public void addUser(String username, String password) {
        if (username == null || password == null) throw new IllegalArgumentException("Username and password cannot " +
                "be" +  " null");
        users.put(username, password);
        LOGGER.info("Added new user: " + username);
    }

    public void removeUser(String username) {
        users.remove(username);
        activeSessions.remove(username);
        LOGGER.info("Removed user: " + username);
    }

    public void endSession(String username) {
        String sessionSubID = activeSessions.remove(username);
        if (sessionSubID != null) LOGGER.info("Ended session for user: " + username + " (session: " + sessionSubID + ")");
    }

    public boolean hasActiveSession(String username) {
        return activeSessions.containsKey(username);
    }


    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public int getUserCount() {
        return users.size();
    }
}