package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;

/**
 * Negotiated Return Bitfields from a LoginRequest 0x81 parameter group.
 *
 * Wire format per group (spec Table 4, p.18):
 *   ParamGroupLength(2B LE, includes itself) + ParamGroupType=0x81(1B)
 *   + MessageType(1B) + NumberOfReturnBitfields(1B) + ReturnBitfield₁…N(1B each)
 *
 * {@link #maskFor(byte)} returns the client-negotiated bitfield mask for a
 * given outbound message type, or {@code null} when the client did not negotiate
 * that type.
 */
public final class ReturnBitfields {
    private static final byte PARAM_GROUP_TYPE = (byte) 0x81;
    private static final int MIN_GROUP_LEN = 5; // 2(len)+1(type)+1(msgType)+1(numBF)

    private final Map<Byte, byte[]> negotiated;

    private ReturnBitfields(Map<Byte, byte[]> negotiated) {
        this.negotiated = negotiated;
    }

    public static ReturnBitfields empty() {
        return new ReturnBitfields(Map.of());
    }

    /**
     * Parses {@code numberOfGroups} parameter groups from {@code buf}.
     * The buffer must be positioned immediately after the NumberOfParamGroups byte.
     */
    public static ReturnBitfields parse(int numberOfGroups, ByteBuffer buf) {
        if (numberOfGroups == 0) return empty();

        Map<Byte, byte[]> result = new HashMap<>();
        for (int i = 0; i < numberOfGroups; i++) {
            if (buf.remaining() < MIN_GROUP_LEN) break;

            int groupLen = buf.getShort() & 0xFFFF;
            if (groupLen < MIN_GROUP_LEN || buf.remaining() < groupLen - 2) break;

            byte groupType = buf.get();
            if (groupType == PARAM_GROUP_TYPE) {
                byte msgType = buf.get();
                int numBF = buf.get() & 0xFF;
                if (numBF > groupLen - 5) break; // malformed
                byte[] bfs = new byte[numBF];
                if (numBF > 0) buf.get(bfs);
                result.put(msgType, bfs);
            } else {
                buf.position(buf.position() + groupLen - 3);
            }
        }
        return new ReturnBitfields(Map.copyOf(result));
    }

    public int entryCount() {
        return negotiated.size();
    }

    public int serializedSize() {
        int size = 0;
        for (byte[] mask : negotiated.values()) {
            size += MIN_GROUP_LEN + mask.length;
        }
        return size;
    }

    public void writeTo(ByteBuffer buf) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (Map.Entry<Byte, byte[]> entry : negotiated.entrySet()) {
            byte[] mask = entry.getValue();
            buf.putShort((short) (MIN_GROUP_LEN + mask.length));
            buf.put(PARAM_GROUP_TYPE);
            buf.put(entry.getKey());
            buf.put((byte) mask.length);
            if (mask.length > 0) buf.put(mask);
        }
    }

    /**
     * Returns the client-negotiated bitfield mask for {@code messageType},
     * or {@code null} when no negotiation was received for that type.
     * A {@code null} result means the server should include all available fields.
     */
    public byte[] maskFor(byte messageType) {
        return negotiated.get(messageType);
    }

    public boolean isEmpty() {
        return negotiated.isEmpty();
    }

    @Override
    public String toString() {
        return "ReturnBitfields{entries=" + negotiated.size() + "}";
    }
}
