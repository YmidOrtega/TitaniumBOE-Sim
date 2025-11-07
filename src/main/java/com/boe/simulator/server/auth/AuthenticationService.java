package com.boe.simulator.server.auth;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.model.PersistedUser;
import com.boe.simulator.server.persistence.repository.UserRepository;
import com.boe.simulator.server.persistence.service.UserRepositoryService;
import com.boe.simulator.server.persistence.util.PasswordHasher;

import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

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
            LOGGER.info("No users found in database, creating default users...");

            // Create default users
            createUser("USER", "PASS");
            createUser("TRADER1", "PASS1");
            createUser("TRADER2", "PASS2");
            createUser("ADMIN", "ADMIN123");
            createUser("TEST", "TEST");

            LOGGER.info("Created " + userRepository.count() + " default users");
        } else {
            LOGGER.info("Found " + userCount + " existing users in database");
        }
    }

    public void createUser(String username, String plainPassword) {
        String passwordHash = PasswordHasher.hash(plainPassword);
        PersistedUser user = PersistedUser.create(username, passwordHash);
        userRepository.save(user);

        LOGGER.info("Created new user: " + username);
    }

    public AuthenticationResult authenticate(String username, String password, String sessionSubID) {
        if (username == null || username.trim().isEmpty()) return AuthenticationResult.rejected("Username cannot be empty");
        if (password == null || password.trim().isEmpty()) return AuthenticationResult.rejected("Password cannot be empty");

        // Find user in database
        var userOpt = userRepository.findByUsername(username);

        if (userOpt.isEmpty()) {
            LOGGER.warning("Login attempt for unknown user: " + username);
            return AuthenticationResult.rejected("Invalid username or password");
        }

        PersistedUser user = userOpt.get();

        // Check if user is active
        if (!user.active()) {
            LOGGER.warning("Login attempt for inactive user: " + username);
            return AuthenticationResult.rejected("User account is inactive");
        }

        // Verify password
        if (!PasswordHasher.verify(password, user.passwordHash())) {
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
        userRepository.updateLastLogin(username);

        LOGGER.info("User " + username + " authenticated successfully (session: " + sessionSubID + ")");

        return AuthenticationResult.accepted("Login successful");
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

    public long getUserCount() {
        return userRepository.count();
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }
}