package com.boe.simulator.server.connection;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.boe.simulator.protocol.message.ClientHeartbeatMessage;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;
import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.ClientSessionManager;

@Timeout(10)
class ClientConnectionHandlerLoginTest {

    private static final byte LOGIN_RESPONSE = 0x24;
    private static final byte LOGOUT = 0x08;

    private ServerSocket listener;
    private Socket client;
    private DataInputStream in;
    private OutputStream out;
    private AuthenticationService auth;

    @BeforeEach
    void setUp() throws Exception {
        listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        auth = mock(AuthenticationService.class);
        when(auth.authenticate(anyString(), anyString(), anyString())).thenReturn(AuthenticationResult.accepted("ok"));

        client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket server = listener.accept();
        Thread.ofVirtual().start(new ClientConnectionHandler(server, 1, ServerConfiguration.builder().build(),
                auth, new ClientSessionManager(), new ErrorHandler(), new RateLimiter(1_000), null));
        in = new DataInputStream(client.getInputStream());
        out = client.getOutputStream();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        listener.close();
    }

    @Test
    void firstMessageNotALoginRequest_isLoggedOutAndDisconnected() throws Exception {
        send(new ClientHeartbeatMessage().toBytes());

        byte[] logout = read();
        assertEquals(LOGOUT, logout[4]);
        assertEquals(LogoutResponseMessage.REASON_PROTOCOL_VIOLATION, logout[10]);
        assertEquals("Login Request must be the first message (got 0x03)", text(logout));
        assertEquals(-1, in.read());
        verifyNoInteractions(auth);
    }

    @Test
    void malformedLoginRequest_isRejectedWithMAndDisconnected() throws Exception {
        byte[] login = new LoginRequestMessage("U1", "pass", "S1").toBytes();
        login[28] = 2; // claims two parameter groups that are not there
        send(login);

        LoginResponseMessage response = new LoginResponseMessage(read());
        assertEquals(LoginResponseMessage.STATUS_INVALID_STRUCTURE, response.getLoginResponseStatus());
        assertEquals("Parameter group 1 of 2 is missing", response.getLoginResponseText());
        assertEquals(-1, in.read());
        verifyNoInteractions(auth);
    }

    @Test
    void invalidReturnBitfield_isRejectedWithFNamingByteAndBit() throws Exception {
        byte[] fixed = new LoginRequestMessage("U1", "pass", "S1").toBytes();
        byte[] group = {0x06, 0x00, (byte) 0x81, 0x25, 0x01, 0x02}; // Order Ack, byte 1 bit 2 = PegDifference (blank)
        byte[] login = new byte[fixed.length + group.length];
        System.arraycopy(fixed, 0, login, 0, fixed.length);
        System.arraycopy(group, 0, login, fixed.length, group.length);
        login[28] = 1;
        login[2] = (byte) (login.length - 2);
        send(login);

        byte[] raw = read();
        assertEquals(LOGIN_RESPONSE, raw[4]);
        LoginResponseMessage response = new LoginResponseMessage(raw);
        assertEquals(LoginResponseMessage.STATUS_INVALID_BITFIELD, response.getLoginResponseStatus());
        assertEquals("Invalid return bitfield for 0x25: byte 1 bit 2", response.getLoginResponseText());
        assertEquals(1, response.getNumberOfParamGroups(), "Parameter groups are echoed on a rejected login too");
        assertEquals(-1, in.read());
        verifyNoInteractions(auth);
    }

    @Test
    void secondLoginOnTheSameConnection_isLoggedOutAndReleasesTheFirstSession() throws Exception {
        send(new LoginRequestMessage("U1", "pass", "S1").toBytes());
        assertEquals(LoginResponseMessage.STATUS_ACCEPTED, new LoginResponseMessage(read()).getLoginResponseStatus());
        read(); // Replay Complete

        send(new LoginRequestMessage("U2", "pass", "S1").toBytes());

        byte[] logout = readSkippingHeartbeats();
        assertEquals(LOGOUT, logout[4]);
        assertEquals(LogoutResponseMessage.REASON_PROTOCOL_VIOLATION, logout[10]);
        assertEquals("Login Request already accepted on this connection", text(logout));
        assertEquals(-1, in.read());
        verify(auth, times(1)).authenticate(anyString(), anyString(), anyString());
        verify(auth, timeout(1_000).times(1)).endSession("U1");
    }

    private byte[] readSkippingHeartbeats() throws IOException {
        while (true) {
            byte[] msg = read();
            if (msg[4] != 0x09) return msg;
        }
    }

    private void send(byte[] bytes) throws IOException {
        out.write(bytes);
        out.flush();
    }

    private byte[] read() throws IOException {
        byte[] header = new byte[4];
        in.readFully(header);
        int length = (header[2] & 0xFF) | ((header[3] & 0xFF) << 8);
        byte[] full = new byte[2 + length];
        System.arraycopy(header, 0, full, 0, 4);
        in.readFully(full, 4, length - 2);
        return full;
    }

    private static String text(byte[] logout) {
        return new String(logout, 11, 60, StandardCharsets.US_ASCII).trim();
    }
}
