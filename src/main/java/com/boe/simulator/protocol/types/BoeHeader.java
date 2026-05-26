package com.boe.simulator.protocol.types;

/**
 * Immutable value object for the BOEv3 12-byte message header.
 * Used by Phase 2 when the serializer is aligned to the real wire format.
 *
 * Wire layout (little-endian):
 *   [0-1]  StartOfMessage  = 0xB0E3
 *   [2-3]  MessageLength   (excludes StartOfMessage field)
 *   [4-5]  MessageType     (2 bytes)
 *   [6]    MatchingUnit    (0 = member-sent)
 *   [7]    Reserved        (must be 0)
 *   [8-11] SequenceNumber  (0 = session messages / next expected)
 */
public record BoeHeader(
        short startOfMessage,
        short messageLength,
        MessageType messageType,
        byte matchingUnit,
        byte reserved,
        int sequenceNumber
) {
    public static final short START_OF_MESSAGE = (short) 0xB0E3;
    public static final int WIRE_SIZE = 12;

    public BoeHeader {
        if (sequenceNumber < 0)
            throw new IllegalArgumentException("SequenceNumber must be >= 0");
    }

    public static BoeHeader of(MessageType type, int sequenceNumber) {
        return new BoeHeader(START_OF_MESSAGE, (short) 0, type, (byte) 0, (byte) 0, sequenceNumber);
    }

    public static BoeHeader sessionMessage(MessageType type) {
        return of(type, 0);
    }
}
