package com.boe.simulator.protocol.types;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;

public record BinaryPrice(long rawValue) {

    public static BinaryPrice fromPrice(BigDecimal price) {
        if (price == null) throw new IllegalArgumentException("Price cannot be null");
        long raw = price.setScale(4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(10000))
                        .longValueExact();
        return new BinaryPrice(raw);
    }

    public static BinaryPrice fromRaw(long rawValue) {
        return new BinaryPrice(rawValue);
    }

    public static BinaryPrice fromBytes(byte[] bytes, int offset) {
        if (bytes == null || bytes.length < offset + 8)
            throw new IllegalArgumentException("Byte array is too short to contain a BinaryPrice (8 bytes).");
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, 8);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        return new BinaryPrice(buffer.getLong());
    }

    public static BinaryPrice fromBytes(byte[] bytes) {
        return fromBytes(bytes, 0);
    }

    public BigDecimal toPrice() {
        return BigDecimal.valueOf(rawValue).divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP);
    }

    public byte[] toBytes() {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(rawValue);
        return buffer.array();
    }

    // Writes 8 bytes directly into an existing LITTLE_ENDIAN ByteBuffer — no allocation
    public void putInto(ByteBuffer buf) {
        buf.putLong(rawValue);
    }
}
