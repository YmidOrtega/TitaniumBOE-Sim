package com.boe.simulator.integration;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.model.PersistedUser;
import com.boe.simulator.server.persistence.repository.UserRepository;
import com.boe.simulator.server.persistence.service.UserRepositoryService;
import com.boe.simulator.server.persistence.util.PasswordHasher;
import com.boe.simulator.server.persistence.util.SerializationUtil;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class PersistencePhase1Test {

    private static final String TEST_DB_PATH = "./test-data/rocksdb-test";
    private RocksDBManager dbManager;
    private UserRepository userRepository;

    public static void main(String[] args) {
        PersistencePhase1Test test = new PersistencePhase1Test();

        try {
            test.setup();
            System.out.println("🚀 Starting Persistence Phase 1 Tests...\n");

            // Run all tests
            test.testRocksDBInitialization();
            test.testPasswordHashing();
            test.testSerialization();
            test.testUserCreation();
            test.testUserRetrieval();
            test.testUserUpdate();
            test.testUserDeletion();
            test.testFindAllUsers();
            test.testFindActiveUsers();
            test.testUserExists();
            test.testUserCount();
            test.testUpdateLastLogin();
            test.testConcurrentOperations();
            test.testPasswordUpgrade();
            test.testEdgeCases();

            System.out.println("\n✅ ALL TESTS PASSED! Persistence Phase 1 is working correctly.");

        } catch (Exception e) {
            System.err.println("\n❌ TEST FAILED: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } finally {
            test.cleanup();
        }
    }

    private void setup() throws Exception {
        System.out.println("📋 Setting up test environment...");

        // Clean up any existing test data
        deleteDirectory(Path.of(TEST_DB_PATH));

        // Initialize RocksDB
        dbManager = RocksDBManager.getInstance(TEST_DB_PATH);
        userRepository = new UserRepositoryService(dbManager);

        System.out.println("✓ Test environment ready\n");
    }

    private void cleanup() {
        System.out.println("\n🧹 Cleaning up test environment...");
        if (dbManager != null) {
            dbManager.close();
        }
        try {
            deleteDirectory(Path.of(TEST_DB_PATH));
            System.out.println("✓ Cleanup complete");
        } catch (Exception e) {
            System.err.println("Warning: Failed to delete test directory: " + e.getMessage());
        }
    }

    // ==================== TEST CASES ====================

    private void testRocksDBInitialization() {
        System.out.println("Test 1: RocksDB Initialization");

        assert dbManager != null : "DBManager should not be null";
        assert dbManager.isOpen() : "Database should be open";
        assert dbManager.getDbPath().equals(TEST_DB_PATH) : "DB path mismatch";

        System.out.println("  ✓ RocksDB initialized successfully");
        System.out.println("  ✓ Database is open");
        System.out.println("  ✓ Correct database path\n");
    }

    private void testPasswordHashing() {
        System.out.println("Test 2: Password Hashing");

        String plainPassword = "TestPassword123!";

        // Test hashing
        String hash1 = PasswordHasher.hash(plainPassword);
        String hash2 = PasswordHasher.hash(plainPassword);

        assert hash1 != null : "Hash should not be null";
        assert hash1.startsWith("$2a$") : "Hash should use BCrypt format";
        assert !hash1.equals(hash2) : "Different salts should produce different hashes";

        // Test verification
        assert PasswordHasher.verify(plainPassword, hash1) : "Password verification should succeed";
        assert !PasswordHasher.verify("WrongPassword", hash1) : "Wrong password should fail verification";
        assert !PasswordHasher.verify(plainPassword, "invalid_hash") : "Invalid hash should fail verification";

        // Test null handling
        assert !PasswordHasher.verify(null, hash1) : "Null password should return false";
        assert !PasswordHasher.verify(plainPassword, null) : "Null hash should return false";

        System.out.println("  ✓ Password hashing works correctly");
        System.out.println("  ✓ Password verification works correctly");
        System.out.println("  ✓ Null handling works correctly\n");
    }

    private void testSerialization() {
        System.out.println("Test 3: Serialization");

        SerializationUtil serializer = SerializationUtil.getInstance();

        PersistedUser user = PersistedUser.create("testuser", "hashedpassword");

        // Serialize
        byte[] serialized = serializer.serialize(user);
        assert serialized != null : "Serialized data should not be null";
        assert serialized.length > 0 : "Serialized data should have content";

        // Deserialize
        PersistedUser deserialized = serializer.deserialize(serialized, PersistedUser.class);
        assert deserialized != null : "Deserialized object should not be null";
        assert deserialized.username().equals(user.username()) : "Username should match";
        assert deserialized.passwordHash().equals(user.passwordHash()) : "Password hash should match";

        System.out.println("  ✓ Object serialization works");
        System.out.println("  ✓ Object deserialization works");
        System.out.println("  ✓ Data integrity maintained\n");
    }

    private void testUserCreation() {
        System.out.println("Test 4: User Creation");

        String username = "alice";
        String password = "AlicePassword123!";
        String passwordHash = PasswordHasher.hash(password);

        PersistedUser user = PersistedUser.create(username, passwordHash);
        userRepository.save(user);

        // Verify user was saved
        Optional<PersistedUser> retrieved = userRepository.findByUsername(username);
        assert retrieved.isPresent() : "User should be found after saving";
        assert retrieved.get().username().equals(username) : "Username should match";
        assert retrieved.get().active() : "User should be active by default";

        System.out.println("  ✓ User created successfully");
        System.out.println("  ✓ User persisted to database");
        System.out.println("  ✓ User data correct\n");
    }

    private void testUserRetrieval() {
        System.out.println("Test 5: User Retrieval");

        // Create multiple users
        for (int i = 1; i <= 5; i++) {
            String username = "user" + i;
            PersistedUser user = PersistedUser.create(username, PasswordHasher.hash("password" + i));
            userRepository.save(user);
        }

        // Test retrieval
        Optional<PersistedUser> user3 = userRepository.findByUsername("user3");
        assert user3.isPresent() : "User3 should be found";
        assert user3.get().username().equals("user3") : "Username should match";

        Optional<PersistedUser> nonExistent = userRepository.findByUsername("nonexistent");
        assert nonExistent.isEmpty() : "Non-existent user should return empty Optional";

        System.out.println("  ✓ User retrieval by username works");
        System.out.println("  ✓ Non-existent user returns empty Optional");
        System.out.println("  ✓ Created 5 test users\n");
    }

    private void testUserUpdate() {
        System.out.println("Test 6: User Update");

        String username = "bobupdate";
        PersistedUser originalUser = PersistedUser.create(username, PasswordHasher.hash("password"));
        userRepository.save(originalUser);

        // Update user (deactivate)
        PersistedUser updatedUser = originalUser.deactivate();
        userRepository.save(updatedUser);

        // Verify update
        Optional<PersistedUser> retrieved = userRepository.findByUsername(username);
        assert retrieved.isPresent() : "Updated user should exist";
        assert !retrieved.get().active() : "User should be inactive";

        System.out.println("  ✓ User update works correctly");
        System.out.println("  ✓ User state changed successfully\n");
    }

    private void testUserDeletion() {
        System.out.println("Test 7: User Deletion");

        String username = "deleteme";
        PersistedUser user = PersistedUser.create(username, PasswordHasher.hash("password"));
        userRepository.save(user);

        // Verify user exists
        assert userRepository.exists(username) : "User should exist before deletion";

        // Delete user
        userRepository.delete(username);

        // Verify deletion
        assert !userRepository.exists(username) : "User should not exist after deletion";
        Optional<PersistedUser> retrieved = userRepository.findByUsername(username);
        assert retrieved.isEmpty() : "Deleted user should return empty Optional";

        System.out.println("  ✓ User deletion works correctly");
        System.out.println("  ✓ Deleted user cannot be retrieved\n");
    }

    private void testFindAllUsers() {
        System.out.println("Test 8: Find All Users");

        // Clear existing users
        userRepository.findAll().forEach(u -> userRepository.delete(u.username()));

        // Create test users
        for (int i = 1; i <= 10; i++) {
            PersistedUser user = PersistedUser.create("testuser" + i, PasswordHasher.hash("pass" + i));
            userRepository.save(user);
        }

        List<PersistedUser> allUsers = userRepository.findAll();
        assert allUsers.size() == 10 : "Should find exactly 10 users, found: " + allUsers.size();

        System.out.println("  ✓ Find all users works correctly");
        System.out.println("  ✓ Found " + allUsers.size() + " users\n");
    }

    private void testFindActiveUsers() {
        System.out.println("Test 9: Find Active Users");

        // Clear existing users
        userRepository.findAll().forEach(u -> userRepository.delete(u.username()));

        // Create mix of active and inactive users
        for (int i = 1; i <= 5; i++) {
            PersistedUser user = PersistedUser.create("active" + i, PasswordHasher.hash("pass"));
            userRepository.save(user);
        }

        for (int i = 1; i <= 3; i++) {
            PersistedUser user = PersistedUser.create("inactive" + i, PasswordHasher.hash("pass"))
                    .deactivate();
            userRepository.save(user);
        }

        List<PersistedUser> activeUsers = userRepository.findAllActive();
        assert activeUsers.size() == 5 : "Should find exactly 5 active users, found: " + activeUsers.size();
        assert activeUsers.stream().allMatch(PersistedUser::active) : "All returned users should be active";

        System.out.println("  ✓ Find active users works correctly");
        System.out.println("  ✓ Found " + activeUsers.size() + " active users\n");
    }

    private void testUserExists() {
        System.out.println("Test 10: User Exists Check");

        String existingUser = "existing";
        PersistedUser user = PersistedUser.create(existingUser, PasswordHasher.hash("password"));
        userRepository.save(user);

        assert userRepository.exists(existingUser) : "Existing user should return true";
        assert !userRepository.exists("nonexistent") : "Non-existent user should return false";

        System.out.println("  ✓ User exists check works correctly\n");
    }

    private void testUserCount() {
        System.out.println("Test 11: User Count");

        // Clear existing users
        userRepository.findAll().forEach(u -> userRepository.delete(u.username()));

        // Create known number of users
        for (int i = 1; i <= 7; i++) {
            PersistedUser user = PersistedUser.create("countuser" + i, PasswordHasher.hash("pass"));
            userRepository.save(user);
        }

        long count = userRepository.count();
        assert count == 7 : "Should count exactly 7 users, found: " + count;

        System.out.println("  ✓ User count works correctly");
        System.out.println("  ✓ Counted " + count + " users\n");
    }

    private void testUpdateLastLogin() {
        System.out.println("Test 12: Update Last Login");

        String username = "logintest";
        PersistedUser user = PersistedUser.create(username, PasswordHasher.hash("password"));
        userRepository.save(user);

        // Get original login count and time
        Optional<PersistedUser> original = userRepository.findByUsername(username);
        assert original.isPresent() : "User should exist";
        int originalLoginCount = original.get().loginCount();

        // Sleep briefly to ensure different timestamp
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Update last login
        userRepository.updateLastLogin(username);

        // Verify update
        Optional<PersistedUser> updated = userRepository.findByUsername(username);
        assert updated.isPresent() : "User should still exist";
        assert updated.get().loginCount() == originalLoginCount + 1 : "Login count should increment";
        assert updated.get().lastLogin() != null : "Last login should be set";
        assert updated.get().lastLogin().isAfter(original.get().lastLogin()) : "Last login should be updated";

        System.out.println("  ✓ Update last login works correctly");
        System.out.println("  ✓ Login count incremented");
        System.out.println("  ✓ Timestamp updated\n");
    }

    private void testConcurrentOperations() {
        System.out.println("Test 13: Concurrent Operations");

        String username = "concurrent";
        PersistedUser user = PersistedUser.create(username, PasswordHasher.hash("password"));
        userRepository.save(user);

        // Simulate concurrent reads
        Thread[] threads = new Thread[5];
        for (int i = 0; i < threads.length; i++) {
            threads[i] = new Thread(() -> {
                Optional<PersistedUser> retrieved = userRepository.findByUsername(username);
                assert retrieved.isPresent() : "Concurrent read should succeed";
            });
            threads[i].start();
        }

        // Wait for all threads
        for (Thread thread : threads) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("  ✓ Concurrent reads successful");
        System.out.println("  ✓ No race conditions detected\n");
    }

    private void testPasswordUpgrade() {
        System.out.println("Test 14: Password Upgrade Detection");

        // Create a hash with current settings
        String password = "TestPassword123!";
        String currentHash = PasswordHasher.hash(password);

        // Test upgrade detection
        boolean needsUpgrade = PasswordHasher.needsUpgrade(currentHash);
        assert !needsUpgrade : "Current hash should not need upgrade";

        // Test invalid hash
        boolean invalidNeedsUpgrade = PasswordHasher.needsUpgrade("invalid_hash");
        assert invalidNeedsUpgrade : "Invalid hash should need upgrade";

        System.out.println("  ✓ Password upgrade detection works");
        System.out.println("  ✓ Current hashes don't need upgrade");
        System.out.println("  ✓ Invalid hashes detected\n");
    }

    private void testEdgeCases() {
        System.out.println("Test 15: Edge Cases");

        // Test empty username handling
        try {
            PersistedUser.create("", "hash");
            assert false : "Should throw exception for empty username";
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test null username handling
        try {
            PersistedUser.create(null, "hash");
            assert false : "Should throw exception for null username";
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test empty password hash
        try {
            PersistedUser.create("user", "");
            assert false : "Should throw exception for empty password hash";
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // Test special characters in username
        String specialUsername = "user@test.com";
        PersistedUser specialUser = PersistedUser.create(specialUsername, PasswordHasher.hash("pass"));
        userRepository.save(specialUser);

        Optional<PersistedUser> retrieved = userRepository.findByUsername(specialUsername);
        assert retrieved.isPresent() : "Should handle special characters in username";

        System.out.println("  ✓ Empty username validation works");
        System.out.println("  ✓ Null username validation works");
        System.out.println("  ✓ Empty password validation works");
        System.out.println("  ✓ Special characters handled correctly\n");
    }

    // ==================== UTILITY METHODS ====================

    private void deleteDirectory(Path path) throws Exception {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
        }
    }
}
