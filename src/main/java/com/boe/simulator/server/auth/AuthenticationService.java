package com.boe.simulator.server.auth;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.model.PersistedUser;
import com.boe.simulator.server.persistence.repository.UserRepository;
import com.boe.simulator.server.persistence.service.UserRepositoryService;
import com.boe.simulator.server.persistence.util.PasswordHasher;

public class AuthenticationService {
    private static final Logger LOGGER = Logger.getLogger(AuthenticationService.class.getName());

    private final UserRepository userRepository;

    private final ConcurrentHashMap<String, String> activeSessions;

    public AuthenticationService(RocksDBManager dbManager) {
        this(new UserRepositoryService(dbManager));
    }

    public AuthenticationService() {
        this(RocksDBManager.getInstance());
    }

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.activeSessions = new ConcurrentHashMap<>();

        initializeDefaultUsers();

        LOGGER.info("AuthenticationService initialized with persistent storage");
    }

    private void initializeDefaultUsers() {
        long userCount = userRepository.count();

        if (userCount == 0) {
            // Check both system property (for tests) and environment variable
            String demoMode = System.getProperty("DEMO_MODE", System.getenv().getOrDefault("DEMO_MODE", "false"));
            
            if ("true".equalsIgnoreCase(demoMode)) {
                LOGGER.warning("════════════════════════════════════════════════════");
                LOGGER.warning("⚠️  DEMO MODE ENABLED - Creating sample users");
                LOGGER.warning("⚠️  These credentials are for DEMONSTRATION ONLY");
                LOGGER.warning("⚠️  DO NOT USE IN PRODUCTION ENVIRONMENTS");
                LOGGER.warning("════════════════════════════════════════════════════");
                
                // Create demo users with credentials from environment or defaults
                // NOTE: Username max 4 chars, Password max 10 chars (BOE protocol limits)
                createUser(
                    System.getenv().getOrDefault("DEMO_USER_1", "TRD1"),
                    System.getenv().getOrDefault("DEMO_PASS_1", "Pass1234!")
                );
                createUser(
                    System.getenv().getOrDefault("DEMO_USER_2", "TRD2"),
                    System.getenv().getOrDefault("DEMO_PASS_2", "Pass5678!")
                );
                createUser(
                    System.getenv().getOrDefault("DEMO_ADMIN", "ADMN"),
                    System.getenv().getOrDefault("DEMO_ADMIN_PASS", "Admin999!")
                );
                
                LOGGER.log(Level.WARNING, "Created {0} demo users for testing", userRepository.count());
                LOGGER.warning("Set DEMO_MODE=false to disable automatic user creation");
            } else {
                LOGGER.info("No users found in database.");
                LOGGER.info("To create users:");
                LOGGER.info("  1. Set DEMO_MODE=true for demo users (NOT for production)");
                LOGGER.info("  2. Use the createUser() API programmatically");
                LOGGER.info("  3. Use a proper user management system");
            }
        } else {
            LOGGER.log(Level.INFO, "Found {0} existing users in database", userCount);
        }
    }

    public void createUser(String username, String plainPassword) {
        String passwordHash = PasswordHasher.hash(plainPassword);
        PersistedUser user = PersistedUser.create(username, passwordHash);
        userRepository.save(user);

        LOGGER.log(Level.INFO, "Created new user: {0}", username);
    }

    public AuthenticationResult authenticate(String username, String password, String sessionSubID) {
        if (username == null || username.trim().isEmpty()) return AuthenticationResult.rejected("Username cannot be empty");
        if (password == null || password.trim().isEmpty()) return AuthenticationResult.rejected("Password cannot be empty");

        // Find user in database
        var userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            LOGGER.log(Level.WARNING, "Login attempt for unknown user: {0}", username);
            return AuthenticationResult.rejected("Invalid username or password");
        }

        PersistedUser user = userOpt.get();

        // Check if user is active
        if (!user.active()) {
            LOGGER.log(Level.WARNING, "Login attempt for inactive user: {0}", username);
            return AuthenticationResult.rejected("User account is inactive");
        }

        // Verify password
        if (!PasswordHasher.verify(password, user.passwordHash())) {
            LOGGER.log(Level.WARNING, "Invalid password for user: {0}", username);
            return AuthenticationResult.rejected("Invalid username or password");
        }

        // Check for duplicate session
        if (activeSessions.containsKey(username)) {
            String existingSession = activeSessions.get(username);
            LOGGER.log(Level.WARNING, "User {0} already has active session: {1}", new Object[]{username, existingSession});
            return AuthenticationResult.sessionInUse("Session already active for this user");
        }

        // Authentication successful
        activeSessions.put(username, sessionSubID);
        userRepository.updateLastLogin(username);

        LOGGER.log(Level.INFO, "User {0} authenticated successfully (session: {1})", new Object[]{username, sessionSubID});

        return AuthenticationResult.accepted("Login successful");
    }

    public void endSession(String username) {
        String sessionSubID = activeSessions.remove(username);
        if (sessionSubID != null) LOGGER.log(Level.INFO, "Ended session for user: {0} (session: {1})", new Object[]{username, sessionSubID});

    }

    public boolean hasActiveSession(String username) {
        return activeSessions.containsKey(username);
    }

    public int getActiveSessionCount() {
        return activeSessions.size();
    }

    public long getUserCount() {
        return userRepository.count();
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }
}