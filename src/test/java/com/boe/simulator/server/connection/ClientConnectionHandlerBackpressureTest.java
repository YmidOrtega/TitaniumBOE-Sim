package com.boe.simulator.server.connection;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.ratelimit.RateLimiter;

class ClientConnectionHandlerBackpressureTest {

    private static final byte ORDER_REJECTED = 0x26;
    private static final int PAUSE_ABOVE = 3;
    private static final int RESUME_BELOW = 1;
    private static final int PERMITS_PER_SECOND = 5; // 200ms per message after a burst of 5
    private static final int MESSAGES = 15;

    @Test
    @Timeout(20)
    void slowProcessing_pausesSocketReadsAndStillAnswersEveryMessageInOrder() throws Exception {
        ServerConfiguration config = ServerConfiguration.builder()
                .flowControl(PAUSE_ABOVE, RESUME_BELOW)
                .build();

        try (ServerSocket listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
             Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
             Socket server = listener.accept()) {

            ClientConnectionHandler handler = new ClientConnectionHandler(
                    server, 1, config, null, null, new ErrorHandler(),
                    new RateLimiter(PERMITS_PER_SECOND), null);
            Thread.ofVirtual().start(handler);

            OutputStream out = client.getOutputStream();
            for (int i = 0; i < MESSAGES; i++) out.write(newOrder("BP" + i, i + 1));
            out.flush();

            UnacknowledgedMessageGate gate = gateOf(handler);
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (!gate.isPaused() && System.nanoTime() < deadline) Thread.sleep(5);

            assertTrue(gate.isPaused(), "Reader must pause once more than " + PAUSE_ABOVE + " messages are unacknowledged");
            int readWhilePaused = handler.getSession().getMessagesReceived();
            assertTrue(readWhilePaused < MESSAGES,
                    "Paused reader must leave bytes in the TCP buffer, but read " + readWhilePaused);

            DataInputStream in = new DataInputStream(client.getInputStream());
            for (int i = 0; i < MESSAGES; i++) {
                byte[] msg = readMessage(in);
                assertEquals(ORDER_REJECTED, msg[4], "Unauthenticated orders are rejected, never dropped");
                assertEquals(0, ByteBuffer.wrap(msg, 6, 4).order(ByteOrder.LITTLE_ENDIAN).getInt(), "Order Rejected is unsequenced");
                assertEquals("BP" + i, new String(msg, 18, 20, StandardCharsets.US_ASCII).trim(), "Responses keep arrival order");
            }
            assertEquals(MESSAGES, handler.getSession().getMessagesReceived());

            handler.stop();
        }
    }

    private static byte[] newOrder(String clOrdID, int seq) {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(clOrdID);
        msg.setSide((byte) '1');
        msg.setOrderQty(1);
        msg.setSequenceNumber(seq);
        msg.setSymbol("AAPL");
        msg.setPrice(new BigDecimal("1.00"));
        msg.setOrdType((byte) '2');
        return msg.toBytes();
    }

    private static byte[] readMessage(InputStream raw) throws Exception {
        DataInputStream in = (DataInputStream) raw;
        byte[] header = new byte[4];
        in.readFully(header);
        int length = (header[2] & 0xFF) | ((header[3] & 0xFF) << 8);
        byte[] full = new byte[2 + length];
        System.arraycopy(header, 0, full, 0, 4);
        in.readFully(full, 4, length - 2);
        return full;
    }

    private static UnacknowledgedMessageGate gateOf(ClientConnectionHandler handler) throws Exception {
        Field field = ClientConnectionHandler.class.getDeclaredField("gate");
        field.setAccessible(true);
        return (UnacknowledgedMessageGate) field.get(handler);
    }
}
