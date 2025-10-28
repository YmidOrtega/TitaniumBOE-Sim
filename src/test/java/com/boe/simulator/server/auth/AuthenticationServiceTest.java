package com.boe.simulator.server.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationServiceTest {

    private AuthenticationService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthenticationService();
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
    void addUser_shouldAddNewUser() {
        // Act
        authService.addUser("newuser", "newpass");

        // Assert
        AuthenticationResult result = authService.authenticate("newuser", "newpass", "sub1");
        assertTrue(result.isAccepted());
    }

    @Test
    void removeUser_shouldRemoveUser() {
        // Arrange
        authService.addUser("newuser", "newpass");

        // Act
        authService.removeUser("newuser");

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