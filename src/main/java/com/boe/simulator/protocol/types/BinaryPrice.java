package com.boe.simulator.protocol.types;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;

public class BinaryPrice {

    private long rawValue;

    private BinaryPrice(long rawValue) {
        this.rawValue = rawValue;
    }

    public static BinaryPrice fromPrice(BigDecimal price) {
        if(price == null) {
            throw new IllegalArgumentException("Price cannot be null");
        }
        BigDecimal bigDecimalPrice = price.multiply(BigDecimal.valueOf(10000));
        long binaryPrice = bigDecimalPrice.setScale(4, RoundingMode.HALF_UP).longValueExact();
        return new BinaryPrice(binaryPrice);
    }

    public static BinaryPrice fromRaw(long rawValue) {
        return new BinaryPrice(rawValue);
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

    public static BinaryPrice fromBytes(byte[] bytes, int offset) {
        if (bytes == null || bytes.length < offset + 8) throw new IllegalArgumentException("Byte array is too short to contain a BinaryPrice (8 bytes).");
        ByteBuffer buffer = ByteBuffer.wrap(bytes, offset, 8);
        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        long rawValue = buffer.getLong();
        return new BinaryPrice(rawValue);
    }
}
