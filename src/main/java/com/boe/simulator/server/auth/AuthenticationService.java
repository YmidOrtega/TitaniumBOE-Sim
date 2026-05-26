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
            createUser(System.getenv().getOrDefault("DEMO_USER_1", "TRD1"),  System.getenv().getOrDefault("DEMO_PASS_1", "Pass1234!"));
            createUser(System.getenv().getOrDefault("DEMO_USER_2", "TRD2"),  System.getenv().getOrDefault("DEMO_PASS_2", "Pass5678!"));
            createUser(System.getenv().getOrDefault("DEMO_ADMIN",  "ADMN"),  System.getenv().getOrDefault("DEMO_ADMIN_PASS", "Admin999!"));
            LOGGER.log(Level.INFO, "Seeded {0} demo users (override with DEMO_USER_1/DEMO_PASS_1 env vars)", userRepository.count());
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

    public boolean authenticateForRest(String username, String password) {
        if (username == null || username.isBlank()) return false;
        if (password == null || password.isBlank()) return false;
        var userOpt = userRepository.findByUsername(username);
        if (userOpt.isEmpty()) return false;
        PersistedUser user = userOpt.get();
        if (!user.active()) return false;
        return PasswordHasher.verify(password, user.passwordHash());
    }

    public boolean userExists(String username) {
        return userRepository.findByUsername(username).isPresent();
    }

    public long getUserCount() {
        return userRepository.count();
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }
}