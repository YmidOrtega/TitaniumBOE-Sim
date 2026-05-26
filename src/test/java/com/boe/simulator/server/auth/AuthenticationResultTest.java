package com.boe.simulator.server.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationResultTest {

    @Test
    void accepted_shouldCreateAcceptedResult() {
        // Act
        AuthenticationResult result = AuthenticationResult.accepted("message");

        // Assert
        assertEquals(AuthenticationResult.Status.ACCEPTED, result.status());
        assertEquals("message", result.message());
        assertTrue(result.isAccepted());
        assertFalse(result.isRejected());
        assertFalse(result.isSessionInUse());
    }

    @Test
    void rejected_shouldCreateRejectedResult() {
        // Act
        AuthenticationResult result = AuthenticationResult.rejected("message");

        // Assert
        assertEquals(AuthenticationResult.Status.REJECTED, result.status());
        assertEquals("message", result.message());
        assertFalse(result.isAccepted());
        assertTrue(result.isRejected());
        assertFalse(result.isSessionInUse());
    }

    @Test
    void sessionInUse_shouldCreateSessionInUseResult() {
        // Act
        AuthenticationResult result = AuthenticationResult.sessionInUse("message");

        // Assert
        assertEquals(AuthenticationResult.Status.SESSION_IN_USE, result.status());
        assertEquals("message", result.message());
        assertFalse(result.isAccepted());
        assertFalse(result.isRejected());
        assertTrue(result.isSessionInUse());
    }

    @Test
    void toLoginResponseStatusByte_shouldReturnCorrectByte() {
        // Per spec v2.11.90: A=Accepted, N=Not authorized, B=Session in use
        assertEquals((byte) 'A', AuthenticationResult.accepted("").toLoginResponseStatusByte());
        assertEquals((byte) 'N', AuthenticationResult.rejected("").toLoginResponseStatusByte());
        assertEquals((byte) 'B', AuthenticationResult.sessionInUse("").toLoginResponseStatusByte());
    }
}