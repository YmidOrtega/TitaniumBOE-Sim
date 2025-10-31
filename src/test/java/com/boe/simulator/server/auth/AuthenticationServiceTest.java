package com.boe.simulator.server.auth;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AuthenticationServiceTest {

    private AuthenticationService authService;

    @BeforeEach
    void setUp() {
        resetRocksDBManager();
        deleteDatabase();
        authService = new AuthenticationService();
    }

    @AfterEach
    @SuppressWarnings("CallToPrintStackTrace")
    void tearDown() throws NoSuchFieldException {
        try {
            java.lang.reflect.Field instanceField = com.boe.simulator.server.persistence.RocksDBManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            Object dbManagerInstance = instanceField.get(null);
            if (dbManagerInstance != null) {
                java.lang.reflect.Method closeMethod = dbManagerInstance.getClass().getDeclaredMethod("close");
                closeMethod.setAccessible(true);
                closeMethod.invoke(dbManagerInstance);
            }
        } catch (NoSuchMethodException | IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    private void resetRocksDBManager() {
        try {
            java.lang.reflect.Field instanceField = com.boe.simulator.server.persistence.RocksDBManager.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to reset RocksDBManager singleton: " + e.getMessage());
        }
    }

    private void deleteDatabase() {
        try {
            Path path = Paths.get("./data");
            if (Files.exists(path)) {
                Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .map(Path::toFile)
                    .forEach(File::delete);
            }
        } catch (IOException e) {
            fail("Failed to delete database: " + e.getMessage());   
        }
    }

    @Test
    void authenticate_shouldReturnAccepted_forValidCredentials() {
        // Act
        AuthenticationResult result = authService.authenticate("USER", "PASS", "sub1");

        // Assert
        assertTrue(result.isAccepted());
    }

    @Test
    void authenticate_shouldReturnRejected_forInvalidUsername() {
        // Act
        AuthenticationResult result = authService.authenticate("UNKNOWN", "PASS", "sub1");

        // Assert
        assertTrue(result.isRejected());
    }

    @Test
    void authenticate_shouldReturnRejected_forInvalidPassword() {
        // Act
        AuthenticationResult result = authService.authenticate("USER", "WRONG", "sub1");

        // Assert
        assertTrue(result.isRejected());
    }

    @Test
    void authenticate_shouldReturnSessionInUse_forDuplicateSession() {
        // Arrange
        authService.authenticate("USER", "PASS", "sub1");

        // Act
        AuthenticationResult result = authService.authenticate("USER", "PASS", "sub2");

        // Assert
        assertEquals(AuthenticationResult.Status.SESSION_IN_USE, result.getStatus());
    }

    @Test
    void createUser_shouldAddNewUser() {
        // Act
        authService.createUser("newuser", "newpass");

        // Assert
        AuthenticationResult result = authService.authenticate("newuser", "newpass", "sub1");
        assertTrue(result.isAccepted());
    }

    @Test
    void deleteUser_shouldRemoveUser() {
        // Arrange
        authService.createUser("newuser", "newpass");

        // Act
        authService.getUserRepository().delete("newuser");

        // Assert
        AuthenticationResult result = authService.authenticate("newuser", "newpass", "sub1");
        assertTrue(result.isRejected());
    }

    @Test
    void endSession_shouldEndUserSession() {
        // Arrange
        authService.authenticate("USER", "PASS", "sub1");
        assertTrue(authService.hasActiveSession("USER"));

        // Act
        authService.endSession("USER");

        // Assert
        assertFalse(authService.hasActiveSession("USER"));
    }
}
