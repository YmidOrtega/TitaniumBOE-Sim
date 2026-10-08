package com.boe.simulator.server.connection;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.boe.simulator.server.config.ServerConfiguration;

class ClientConnectionHandlerSequencingTest {

    private static final int THREADS = 8;
    private static final int SENDS_PER_THREAD = 2_000;

    @Test
    void sendSequenced_concurrentWriters_keepWireOrderEqualToSequenceOrder() throws Exception {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        ClientConnectionHandler handler = handlerWritingTo(wire);

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        for (int t = 0; t < THREADS; t++) {
            pool.submit(() -> {
                start.await();
                for (int i = 0; i < SENDS_PER_THREAD; i++) {
                    handler.sendSequenced(seq -> {
                        Thread.yield();
                        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(seq).array();
                    });
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));

        List<Integer> sequences = decode(wire.toByteArray());
        assertEquals(THREADS * SENDS_PER_THREAD, sequences.size());
        for (int i = 0; i < sequences.size(); i++) {
            assertEquals(i + 1, sequences.get(i), "SequenceNumber out of order on the wire at position " + i);
        }
    }

    private static ClientConnectionHandler handlerWritingTo(OutputStream out) throws Exception {
        Socket socket = mock(Socket.class);
        when(socket.getRemoteSocketAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 50000));

        ClientConnectionHandler handler = new ClientConnectionHandler(
                socket, 1, ServerConfiguration.builder().build(), null, null, null, null, null);

        Field outputStream = ClientConnectionHandler.class.getDeclaredField("outputStream");
        outputStream.setAccessible(true);
        outputStream.set(handler, out);
        return handler;
    }

    private static List<Integer> decode(byte[] bytes) {
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        List<Integer> sequences = new ArrayList<>();
        while (buf.remaining() >= 4) sequences.add(buf.getInt());
        return sequences;
    }
}
