package com.boe.simulator.protocol.message;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.protocol.types.BoeTime;

/**
 * Values for the optional fields of an outbound message, written in the order the spec requires:
 * NumberOfReturnBitfields, the bitfields, then every requested field (byte, then bit order).
 * A requested field without a value is sent filled with binary zero (p.111).
 */
public final class ReturnFields {
    private final Map<ReturnField, Object> values = new EnumMap<>(ReturnField.class);
    private byte[] mask = new byte[0];

    public ReturnFields put(ReturnField field, Object value) {
        if (value != null) values.put(field, value);
        return this;
    }

    // Raw bytes echoed back from the inbound message; computed values take precedence
    public ReturnFields echo(Map<String, byte[]> rawFields) {
        if (rawFields == null) return this;
        rawFields.forEach((name, raw) -> {
            ReturnField field = ReturnField.byName(name);
            if (field != null) values.putIfAbsent(field, raw);
        });
        return this;
    }

    public Object get(ReturnField field) {
        return values.get(field);
    }

    public byte[] mask() {
        return mask.clone();
    }

    public boolean isRequested(ReturnField field) {
        int index = field.byteNumber() - 1;
        return index < mask.length && (mask[index] & field.bit()) != 0;
    }

    /** The negotiated bitfields for {@code messageType}, limited to what the spec allows for it. */
    public ReturnFields select(ReturnBitfields negotiated, byte messageType) {
        byte[] requested = negotiated != null ? negotiated.maskFor(messageType) : null;
        byte[] allowed = ReturnBitfieldRules.allowedFor(messageType);
        if (requested == null) {
            mask = new byte[0];
            return this;
        }
        mask = new byte[requested.length];
        for (int i = 0; i < requested.length; i++) {
            mask[i] = (byte) (requested[i] & (i < allowed.length ? allowed[i] : 0));
        }
        return this;
    }

    public int encodedSize() {
        int size = 1 + mask.length;
        for (ReturnField field : ReturnField.wireOrder()) {
            if (isRequested(field)) size += field.length();
        }
        return size;
    }

    public void writeTo(ByteBuffer buf) {
        buf.put((byte) mask.length);
        buf.put(mask);
        for (ReturnField field : ReturnField.wireOrder()) {
            if (isRequested(field)) buf.put(encode(field, values.get(field)));
        }
    }

    public static ReturnFields readFrom(ByteBuffer buf) {
        ReturnFields fields = new ReturnFields();
        fields.mask = new byte[buf.get() & 0xFF];
        buf.get(fields.mask);
        for (ReturnField field : ReturnField.wireOrder()) {
            if (!fields.isRequested(field)) continue;
            byte[] raw = new byte[field.length()];
            buf.get(raw);
            fields.values.put(field, decode(field, raw));
        }
        return fields;
    }

    private static byte[] encode(ReturnField field, Object value) {
        byte[] out = new byte[field.length()];
        if (value == null) return out;
        if (value instanceof byte[] raw) {
            System.arraycopy(raw, 0, out, 0, Math.min(raw.length, out.length));
            return out;
        }
        ByteBuffer buf = ByteBuffer.wrap(out).order(ByteOrder.LITTLE_ENDIAN);
        switch (field.kind()) {
            case BINARY -> putBinary(buf, ((Number) value).longValue(), field.length());
            case PRICE -> BinaryPrice.fromPrice((BigDecimal) value).putInto(buf);
            case DATE -> buf.putInt(value instanceof Instant i ? BoeTime.toYyyymmdd(i) : ((Number) value).intValue());
            case DATE_TIME -> buf.putLong(((Number) value).longValue());
            case TEXT -> {
                byte[] src = value instanceof Character c ? new byte[]{(byte) c.charValue()}
                        : value instanceof Byte b ? new byte[]{b}
                        : value.toString().getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(src, 0, out, 0, Math.min(src.length, out.length));
            }
        }
        return out;
    }

    private static void putBinary(ByteBuffer buf, long value, int length) {
        switch (length) {
            case 1 -> buf.put((byte) value);
            case 2 -> buf.putShort((short) value);
            case 4 -> buf.putInt((int) value);
            default -> buf.putLong(value);
        }
    }

    private static Object decode(ReturnField field, byte[] raw) {
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.LITTLE_ENDIAN);
        return switch (field.kind()) {
            case BINARY -> switch (raw.length) {
                case 1 -> (long) (raw[0] & 0xFF);
                case 2 -> (long) (buf.getShort() & 0xFFFF);
                case 4 -> buf.getInt() & 0xFFFFFFFFL;
                default -> buf.getLong();
            };
            case PRICE -> BinaryPrice.fromBytes(raw).toPrice();
            case DATE -> buf.getInt();
            case DATE_TIME -> buf.getLong();
            case TEXT -> {
                int end = raw.length;
                while (end > 0 && raw[end - 1] == 0) end--;
                yield new String(raw, 0, end, StandardCharsets.US_ASCII);
            }
        };
    }
}
