package com.boe.simulator.server.session;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;

import com.boe.simulator.protocol.message.ReturnBitfields;

public final class BoeSessionState {
    public static final byte MATCHING_UNIT = 1;

    @FunctionalInterface
    public interface Writer {
        void write(byte[] bytes) throws IOException;
    }

    public enum InboundCheck { ACCEPTED, UNSEQUENCED, BACKWARD }

    private final String username;
    private final String sessionSubID;
    private final ReentrantLock lock = new ReentrantLock();
    private final List<byte[]> journal = new ArrayList<>();

    private int lastProcessedInbound;
    private volatile ReturnBitfields returnBitfields = ReturnBitfields.empty();

    BoeSessionState(String username, String sessionSubID) {
        this.username = username;
        this.sessionSubID = sessionSubID;
    }

    public void sendSequenced(IntFunction<byte[]> encoder, Writer writer) throws IOException {
        lock.lock();
        try {
            byte[] bytes = encoder.apply(journal.size() + 1);
            journal.add(bytes);
            if (writer != null) writer.write(bytes);
        } finally {
            lock.unlock();
        }
    }

    public InboundCheck checkInbound(int sequenceNumber) {
        lock.lock();
        try {
            if (sequenceNumber == 0) return InboundCheck.UNSEQUENCED;
            if (Integer.compareUnsigned(sequenceNumber, lastProcessedInbound) <= 0) return InboundCheck.BACKWARD;
            lastProcessedInbound = sequenceNumber;
            return InboundCheck.ACCEPTED;
        } finally {
            lock.unlock();
        }
    }

    public List<byte[]> messagesAfter(int lastReceived) {
        lock.lock();
        try {
            if (lastReceived >= journal.size()) return List.of();
            return List.copyOf(journal.subList(Math.max(lastReceived, 0), journal.size()));
        } finally {
            lock.unlock();
        }
    }

    public void lock() { lock.lock(); }
    public void unlock() { lock.unlock(); }

    public int lastSentSequence() {
        lock.lock();
        try {
            return journal.size();
        } finally {
            lock.unlock();
        }
    }

    public int lastProcessedInbound() {
        lock.lock();
        try {
            return lastProcessedInbound;
        } finally {
            lock.unlock();
        }
    }

    public ReturnBitfields getReturnBitfields() { return returnBitfields; }
    public void setReturnBitfields(ReturnBitfields returnBitfields) {
        this.returnBitfields = returnBitfields != null ? returnBitfields : ReturnBitfields.empty();
    }

    public String getUsername() { return username; }
    public String getSessionSubID() { return sessionSubID; }
}
