package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoeMessageFactoryTest {

    @Test
    void createMessage_shouldReturnLoginRequestMessage() {
        // Arrange
        byte[] data = new LoginRequestMessage("user", "pass", "sub1", (byte) 1).toBytes();
        BoeMessage message = new BoeMessage(data);

        // Act
        Object result = BoeMessageFactory.createMessage(message);

        // Assert
        assertTrue(result instanceof LoginRequestMessage);
    }

    @Test
    void createMessage_shouldReturnLogoutRequestMessage() {
        // Arrange
        byte[] data = new LogoutRequestMessage((byte) 1, 123).toBytes();
        BoeMessage message = new BoeMessage(data);

        // Act
        Object result = BoeMessageFactory.createMessage(message);

        // Assert
        assertTrue(result instanceof LogoutRequestMessage);
    }

    @Test
    void createMessage_shouldReturnClientHeartbeatMessage() {
        // Arrange
        byte[] data = new ClientHeartbeatMessage((byte) 1, 123).toBytes();
        BoeMessage message = new BoeMessage(data);

        // Act
        Object result = BoeMessageFactory.createMessage(message);

        // Assert
        assertTrue(result instanceof ClientHeartbeatMessage);
    }

    @Test
    void createMessage_shouldReturnServerHeartbeatMessage() {
        // Arrange
        byte[] data = new ServerHeartbeatMessage((byte) 1, 123).toBytes();
        BoeMessage message = new BoeMessage(data);

        // Act
        Object result = BoeMessageFactory.createMessage(message);

        // Assert
        assertTrue(result instanceof ServerHeartbeatMessage);
    }

    @Test
    void createMessage_shouldReturnLoginResponseMessage() {
        // Arrange
        byte[] data = new LoginResponseMessage((byte) 'A', "text", 1, 1).toBytes();
        BoeMessage message = new BoeMessage(data);

        // Act
        Object result = BoeMessageFactory.createMessage(message);

        // Assert
        assertTrue(result instanceof LoginResponseMessage);
    }

    @Test
    void createMessage_shouldReturnLogoutResponseMessage() {
        // Arrange
        byte[] data = new LogoutResponseMessage((byte) 'U', "text", 1, 1).toBytes();
        BoeMessage message = new BoeMessage(data);

        // Act
        Object result = BoeMessageFactory.createMessage(message);

        // Assert
        assertTrue(result instanceof LogoutResponseMessage);
    }

    @Test
    void createMessage_shouldReturnNull_forUnknownMessageType() {
        // Arrange
        BoeMessage message = new BoeMessage(new byte[]{(byte) 0xBA, (byte) 0xBA, 0x04, 0x00, (byte) 0xFF});

        // Act
        Object result = BoeMessageFactory.createMessage(message);

        // Assert
        assertNull(result);
    }

    @Test
    void getMessageTypeName_shouldReturnCorrectName() {
        // Assert
        assertEquals("LoginRequest", BoeMessageFactory.getMessageTypeName(BoeMessageFactory.LOGIN_REQUEST));
        assertEquals("LogoutRequest", BoeMessageFactory.getMessageTypeName(BoeMessageFactory.LOGOUT_REQUEST));
        assertEquals("ClientHeartbeat", BoeMessageFactory.getMessageTypeName(BoeMessageFactory.CLIENT_HEARTBEAT));
        assertEquals("ServerHeartbeat", BoeMessageFactory.getMessageTypeName(BoeMessageFactory.SERVER_HEARTBEAT));
        assertEquals("LoginResponse", BoeMessageFactory.getMessageTypeName(BoeMessageFactory.LOGIN_RESPONSE));
        assertEquals("LogoutResponse", BoeMessageFactory.getMessageTypeName(BoeMessageFactory.LOGOUT_RESPONSE));
        assertTrue(BoeMessageFactory.getMessageTypeName((byte) 0xFF).startsWith("Unknown"));
    }
}