package com.boe.simulator.server.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationResultTest {

    @Test
    void accepted_shouldCreateAcceptedResult() {
        // Act
        AuthenticationResult result = AuthenticationResult.accepted("message");

        // Assert
        assertEquals(AuthenticationResult.Status.ACCEPTED, result.getStatus());
        assertEquals("message", result.getMessage());
        assertTrue(result.isAccepted());
        assertFalse(result.isRejected());
        assertFalse(result.isSessionInUse());
    }

    @Test
    void rejected_shouldCreateRejectedResult() {
        // Act
        AuthenticationResult result = AuthenticationResult.rejected("message");

        // Assert
        assertEquals(AuthenticationResult.Status.REJECTED, result.getStatus());
        assertEquals("message", result.getMessage());
        assertFalse(result.isAccepted());
        assertTrue(result.isRejected());
        assertFalse(result.isSessionInUse());
    }

    @Test
    void sessionInUse_shouldCreateSessionInUseResult() {
        // Act
        AuthenticationResult result = AuthenticationResult.sessionInUse("message");

        // Assert
        assertEquals(AuthenticationResult.Status.SESSION_IN_USE, result.getStatus());
        assertEquals("message", result.getMessage());
        assertFalse(result.isAccepted());
        assertFalse(result.isRejected());
        assertTrue(result.isSessionInUse());
    }

    @Test
    void toLoginResponseStatusByte_shouldReturnCorrectByte() {
        // Assert
        assertEquals((byte) 'A', AuthenticationResult.accepted("").toLoginResponseStatusByte());
        assertEquals((byte) 'R', AuthenticationResult.rejected("").toLoginResponseStatusByte());
        assertEquals((byte) 'S', AuthenticationResult.sessionInUse("").toLoginResponseStatusByte());
    }
}