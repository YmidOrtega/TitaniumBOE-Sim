package com.boe.simulator.server.connection;

import java.io.DataInputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.metrics.HealthMetrics;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.ClientSessionManager;

class ClientConnectionHandlerMetricsTest {

    @Test
    @Timeout(10)
    void bytesOnTheWire_areRecordedInHealthMetrics() throws Exception {
        AuthenticationService auth = mock(AuthenticationService.class);
        when(auth.authenticate(anyString(), anyString(), anyString())).thenReturn(AuthenticationResult.accepted("ok"));
        HealthMetrics metrics = new HealthMetrics();

        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
             Socket server = listener.accept()) {

            Thread.ofVirtual().start(new ClientConnectionHandler(server, 1, ServerConfiguration.builder().build(),
                    auth, new ClientSessionManager(), new ErrorHandler(), new RateLimiter(1_000), null, metrics));

            byte[] login = new LoginRequestMessage("U1", "pass", "S1").toBytes();
            OutputStream out = client.getOutputStream();
            out.write(login);
            out.flush();

            DataInputStream in = new DataInputStream(client.getInputStream());
            long received = readMessageLength(in) + readMessageLength(in); // Login Response + Replay Complete

            long deadline = System.nanoTime() + 1_000_000_000L;
            while (metrics.getTotalBytesSent() < received && System.nanoTime() < deadline) Thread.sleep(5);

            assertEquals(login.length, metrics.getTotalBytesReceived());
            assertEquals(received, metrics.getTotalBytesSent());
        }
    }

    private static int readMessageLength(DataInputStream in) throws Exception {
        byte[] header = new byte[4];
        in.readFully(header);
        int length = (header[2] & 0xFF) | ((header[3] & 0xFF) << 8);
        in.readFully(new byte[length - 2]);
        return 2 + length;
    }
}
