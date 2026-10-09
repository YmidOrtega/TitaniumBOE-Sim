package com.boe.simulator.server.connection;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

final class UnacknowledgedMessageGate {
    private final int pauseAbove;
    private final int resumeBelow;
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition resumed = lock.newCondition();

    private int unacknowledged;
    private boolean paused;
    private boolean closed;

    UnacknowledgedMessageGate(int pauseAbove, int resumeBelow) {
        if (resumeBelow < 1 || resumeBelow > pauseAbove)
            throw new IllegalArgumentException("Resume threshold must be between 1 and the pause threshold");
        this.pauseAbove = pauseAbove;
        this.resumeBelow = resumeBelow;
    }

    void awaitReadable() throws InterruptedException {
        lock.lock();
        try {
            while (paused && !closed) resumed.await();
        } finally {
            lock.unlock();
        }
    }

    // Returns true when this read paused the reader
    boolean onRead() {
        lock.lock();
        try {
            unacknowledged++;
            if (!paused && unacknowledged > pauseAbove) {
                paused = true;
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    // Returns true when this acknowledgment resumed the reader
    boolean onAcknowledged() {
        lock.lock();
        try {
            unacknowledged--;
            if (paused && unacknowledged < resumeBelow) {
                paused = false;
                resumed.signalAll();
                return true;
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    void close() {
        lock.lock();
        try {
            closed = true;
            resumed.signalAll();
        } finally {
            lock.unlock();
        }
    }

    int unacknowledged() {
        lock.lock();
        try {
            return unacknowledged;
        } finally {
            lock.unlock();
        }
    }

    boolean isPaused() {
        lock.lock();
        try {
            return paused;
        } finally {
            lock.unlock();
        }
    }
}
