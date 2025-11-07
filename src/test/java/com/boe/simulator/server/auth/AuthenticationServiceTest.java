package com.boe.simulator.server.auth;

import com.boe.simulator.server.persistence.model.PersistedUser;
import com.boe.simulator.server.persistence.repository.UserRepository;
import com.boe.simulator.server.persistence.util.PasswordHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    private AuthenticationService authenticationService;

    private final String TEST_USERNAME = "testUser";
    private final String TEST_PASSWORD = "testPass";
    private final String TEST_SESSION_ID = "session123";
    private final String TEST_PASSWORD_HASH = PasswordHasher.hash(TEST_PASSWORD);

    @BeforeEach
    void setUp() {
        // Mock the count method to prevent default user creation during tests
        when(userRepository.count()).thenReturn(1L); // Assume one user exists to skip default creation
        authenticationService = new AuthenticationService(userRepository);
    }

    @Test
    void authenticate_whenValidCredentialsAndNoActiveSession_returnsAccepted() {
        // Arrange
        PersistedUser user = PersistedUser.create(TEST_USERNAME, TEST_PASSWORD_HASH);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));

        // Act
        AuthenticationResult result = authenticationService.authenticate(TEST_USERNAME, TEST_PASSWORD, TEST_SESSION_ID);

        // Assert
        assertTrue(result.isAccepted());
        assertEquals("Login successful", result.getMessage());
        assertTrue(authenticationService.hasActiveSession(TEST_USERNAME));
        assertEquals(1, authenticationService.getActiveSessionCount());
        verify(userRepository).updateLastLogin(TEST_USERNAME);
    }

    @Test
    void authenticate_whenEmptyUsername_returnsRejected() {
        // Act
        AuthenticationResult result = authenticationService.authenticate("", TEST_PASSWORD, TEST_SESSION_ID);

        // Assert
        assertTrue(result.isRejected());
        assertEquals("Username cannot be empty", result.getMessage());
        assertFalse(authenticationService.hasActiveSession(TEST_USERNAME));
    }

    @Test
    void authenticate_whenUnknownUser_returnsRejected() {
        // Arrange
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.empty());

        // Act
        AuthenticationResult result = authenticationService.authenticate(TEST_USERNAME, TEST_PASSWORD, TEST_SESSION_ID);

        // Assert
        assertTrue(result.isRejected());
        assertEquals("Invalid username or password", result.getMessage());
        assertFalse(authenticationService.hasActiveSession(TEST_USERNAME));
    }

    @Test
    void authenticate_whenInactiveUser_returnsRejected() {
        // Arrange
        PersistedUser user = PersistedUser.create(TEST_USERNAME, TEST_PASSWORD_HASH).deactivate();
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));

        // Act
        AuthenticationResult result = authenticationService.authenticate(TEST_USERNAME, TEST_PASSWORD, TEST_SESSION_ID);

        // Assert
        assertTrue(result.isRejected());
        assertEquals("User account is inactive", result.getMessage());
        assertFalse(authenticationService.hasActiveSession(TEST_USERNAME));
    }

    @Test
    void authenticate_whenIncorrectPassword_returnsRejected() {
        // Arrange
        PersistedUser user = PersistedUser.create(TEST_USERNAME, TEST_PASSWORD_HASH);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));

        // Act
        AuthenticationResult result = authenticationService.authenticate(TEST_USERNAME, "wrongPass", TEST_SESSION_ID);

        // Assert
        assertTrue(result.isRejected());
        assertEquals("Invalid username or password", result.getMessage());
        assertFalse(authenticationService.hasActiveSession(TEST_USERNAME));
    }

    @Test
    void authenticate_whenUserAlreadyHasActiveSession_returnsSessionInUse() {
        // Arrange
        PersistedUser user = PersistedUser.create(TEST_USERNAME, TEST_PASSWORD_HASH);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        authenticationService.authenticate(TEST_USERNAME, TEST_PASSWORD, TEST_SESSION_ID); // First successful login

        // Act
        AuthenticationResult result = authenticationService.authenticate(TEST_USERNAME, TEST_PASSWORD, "anotherSession");

        // Assert
        assertTrue(result.isSessionInUse());
        assertEquals("Session already active for this user", result.getMessage());
        assertTrue(authenticationService.hasActiveSession(TEST_USERNAME));
        assertEquals(1, authenticationService.getActiveSessionCount()); // Still one active session
    }

    @Test
    void createUser_successfullyCreatesAndSavesUser() {
        // Arrange
        String newUsername = "newUser";
        String newPassword = "newPass";
        when(userRepository.count()).thenReturn(0L); // To trigger default user creation logic
        authenticationService = new AuthenticationService(userRepository); // Re-init to trigger default user creation

        // Act
        authenticationService.createUser(newUsername, newPassword);

        // Assert
        ArgumentCaptor<PersistedUser> userCaptor = ArgumentCaptor.forClass(PersistedUser.class);
        verify(userRepository, atLeastOnce()).save(userCaptor.capture()); // atLeastOnce because default users are created
        PersistedUser capturedUser = userCaptor.getValue();
        assertEquals(newUsername, capturedUser.username());
        assertTrue(PasswordHasher.verify(newPassword, capturedUser.passwordHash()));
    }

    @Test
    void endSession_whenSessionExists_removesSession() {
        // Arrange
        PersistedUser user = PersistedUser.create(TEST_USERNAME, TEST_PASSWORD_HASH);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        authenticationService.authenticate(TEST_USERNAME, TEST_PASSWORD, TEST_SESSION_ID); // Start session

        // Act
        authenticationService.endSession(TEST_USERNAME);

        // Assert
        assertFalse(authenticationService.hasActiveSession(TEST_USERNAME));
        assertEquals(0, authenticationService.getActiveSessionCount());
    }

    @Test
    void endSession_whenSessionDoesNotExist_doesNothing() {
        // Arrange - no session started

        // Act
        authenticationService.endSession(TEST_USERNAME);

        // Assert
        assertFalse(authenticationService.hasActiveSession(TEST_USERNAME));
        assertEquals(0, authenticationService.getActiveSessionCount());
        verify(userRepository, never()).updateLastLogin(anyString()); // Ensure no unintended interactions
    }

    @Test
    void hasActiveSession_returnsTrue_whenSessionIsActive() {
        // Arrange
        PersistedUser user = PersistedUser.create(TEST_USERNAME, TEST_PASSWORD_HASH);
        when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(user));
        authenticationService.authenticate(TEST_USERNAME, TEST_PASSWORD, TEST_SESSION_ID); // Start session

        // Act & Assert
        assertTrue(authenticationService.hasActiveSession(TEST_USERNAME));
    }

    @Test
    void hasActiveSession_returnsFalse_whenSessionIsNotActive() {
        // Arrange - no session started

        // Act & Assert
        assertFalse(authenticationService.hasActiveSession(TEST_USERNAME));
    }
}