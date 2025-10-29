package com.boe.simulator.connection;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.Socket;

import com.boe.simulator.server.connection.ClientConnectionHandler;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mock;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.boe.simulator.protocol.serialization.BoeMessageSerializer;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.ClientSessionManager;

class ClientConnectionHandlerTest {

    @Mock
    private Socket mockSocket;
    @Mock
    private ServerConfiguration mockConfig;
    @Mock
    private AuthenticationService mockAuthService;
    @Mock
    private ClientSessionManager mockSessionManager;
    @Mock
    private ErrorHandler mockErrorHandler;
    @Mock
    private RateLimiter mockRateLimiter;
    @Mock
    private BoeMessageSerializer mockSerializer;

    private ClientConnectionHandler clientConnectionHandler;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() throws IOException {
        closeable = MockitoAnnotations.openMocks(this);
        when(mockSocket.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mockSocket.getOutputStream()).thenReturn(System.out);
        clientConnectionHandler = new ClientConnectionHandler(mockSocket, 1, mockConfig, mockAuthService, mockSessionManager, mockErrorHandler, mockRateLimiter);
        setField(clientConnectionHandler, "serializer", mockSerializer);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to set field '" + fieldName + "': " + e.getMessage());
        }
    }
}