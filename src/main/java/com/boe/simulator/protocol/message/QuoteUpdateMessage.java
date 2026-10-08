package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Quote Update (0x55, Table 41) and Quote Update (Short) (0x59, Table 44), spec v2.11.90.
 * Member→Cboe. Only the header and QuoteUpdateID are read.
 *
 *   [0]   StartOfMessage   2B
 *   [2]   MessageLength    2B
 *   [4]   MessageType      1B  = 0x55 / 0x59
 *   [5]   MatchingUnit     1B
 *   [6]   SequenceNumber   4B
 *   [10]  QuoteUpdateID    16B Text
 */
public final class QuoteUpdateMessage extends ApplicationMessage {
    static final byte MESSAGE_TYPE = 0x55;
    static final byte MESSAGE_TYPE_SHORT = 0x59;
    private static final int MIN_SIZE = 26;

    private final byte messageType;
    private final byte matchingUnit;
    private final int sequenceNumber;
    private final String quoteUpdateID;

    private QuoteUpdateMessage(byte messageType, byte matchingUnit, int sequenceNumber, String quoteUpdateID) {
        this.messageType = messageType;
        this.matchingUnit = matchingUnit;
        this.sequenceNumber = sequenceNumber;
        this.quoteUpdateID = quoteUpdateID;
    }

    public static QuoteUpdateMessage parse(byte[] data) {
        if (data == null || data.length < MIN_SIZE) throw new IllegalArgumentException("Quote Update is shorter than 26 bytes");
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        byte type = buf.get(4);
        if (type != MESSAGE_TYPE && type != MESSAGE_TYPE_SHORT) throw new IllegalArgumentException("Invalid message type for Quote Update");
        byte[] id = new byte[16];
        buf.position(10);
        buf.get(id);
        int end = id.length;
        while (end > 0 && id[end - 1] == 0) end--;
        return new QuoteUpdateMessage(type, buf.get(5), buf.getInt(6), new String(id, 0, end, StandardCharsets.US_ASCII));
    }

    @Override
    public byte getMessageType() { return messageType; }

    @Override
    public byte[] toBytes() {
        throw new UnsupportedOperationException("QuoteUpdateMessage is inbound-only");
    }

    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getQuoteUpdateID() { return quoteUpdateID; }
}
