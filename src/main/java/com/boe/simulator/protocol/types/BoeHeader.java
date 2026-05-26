package com.boe.simulator.protocol.types;

/**
 * Immutable value object for the BOE 10-byte message header.
 * Source: Cboe Titanium U.S. Options BOE Specification v2.11.90, Table 10 (p. 43).
 *
 * Wire layout (little-endian):
 *   [0-1]  StartOfMessage  = 0xBA 0xBA
 *   [2-3]  MessageLength   (includes this field, excludes StartOfMessage)
 *   [4]    MessageType     (1 byte)
 *   [5]    MatchingUnit    (0 for session messages; 0 for all Member→Cboe messages)
 *   [6-9]  SequenceNumber  (0 for session messages)
 */
public record BoeHeader(
        short startOfMessage,
        short messageLength,
        MessageType messageType,
        byte matchingUnit,
        int sequenceNumber
) {
    public static final short START_OF_MESSAGE = (short) 0xBABA;
    public static final int WIRE_SIZE = 10;

    public BoeHeader {
        if (sequenceNumber < 0)
            throw new IllegalArgumentException("SequenceNumber must be >= 0");
    }

    public static BoeHeader of(MessageType type, int sequenceNumber) {
        return new BoeHeader(START_OF_MESSAGE, (short) 0, type, (byte) 0, sequenceNumber);
    }

    /** Session messages always have MatchingUnit=0, SequenceNumber=0. */
    public static BoeHeader sessionMessage(MessageType type) {
        return of(type, 0);
    }
}
