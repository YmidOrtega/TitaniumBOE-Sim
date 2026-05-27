package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Order Restated — Table 81 (p.126), spec v2.11.90
 * Sent when an order is asynchronously modified without an explicit Modify Order request.
 *
 * Fixed layout (49 bytes minimum):
 *   [0]   StartOfMessage       2B
 *   [2]   MessageLength        2B
 *   [4]   MessageType          1B  = 0x28
 *   [5]   MatchingUnit         1B
 *   [6]   SequenceNumber       4B
 *   [10]  TransactionTime      8B
 *   [18]  ClOrdID              20B Text
 *   [38]  OrderID              8B  Binary
 *   [46]  RestatementReason    1B  Alphanumeric
 *   [47]  ReservedInternal     1B
 *   [48]  NumberOfReturnBitfields 1B
 *   [49]  ReturnBitfield¹…ᴺ   NB
 *         Optional fields…
 *
 * RestatementReason values:
 *   E = Reduction of OrdQty due to Equity Leg Reject (C1 only)
 *   F = Represented on Floor (C1 only)
 *   L = Reload
 *   P = Price Sliding Reprice
 *   Q = Liquidity Updated
 *   R = Reroute
 *   S = Ship and Post (SWP)
 *   W = Wash
 */
public final class OrderRestatedMessage extends BoeProtocolMessage {
    private static final byte MESSAGE_TYPE = 0x28;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    private static final int FIXED_SIZE = 49;

    public static final byte REASON_RELOAD              = (byte) 'L';
    public static final byte REASON_PRICE_SLIDING       = (byte) 'P';
    public static final byte REASON_LIQUIDITY_UPDATED   = (byte) 'Q';
    public static final byte REASON_REROUTE             = (byte) 'R';
    public static final byte REASON_WASH                = (byte) 'W';

    private byte matchingUnit;
    private int sequenceNumber;

    private long transactTime;
    private String clOrdID;
    private long orderID;
    private byte restatementReason;

    private int numberOfBitfields;
    private byte[] bitfields;

    public OrderRestatedMessage() {}

    public OrderRestatedMessage(String clOrdID, long orderID, byte restatementReason,
                                byte matchingUnit, int sequenceNumber) {
        this.clOrdID = clOrdID;
        this.orderID = orderID;
        this.restatementReason = restatementReason;
        this.matchingUnit = matchingUnit;
        this.sequenceNumber = sequenceNumber;
        this.transactTime = System.nanoTime();
        this.numberOfBitfields = 0;
        this.bitfields = new byte[0];
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int totalSize = FIXED_SIZE + numberOfBitfields;

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        buf.put(SOM1); buf.put(SOM2);
        buf.putShort((short) (totalSize - 2));
        buf.put(MESSAGE_TYPE);
        buf.put(matchingUnit);
        buf.putInt(sequenceNumber);
        buf.putLong(transactTime);
        putText(buf, clOrdID, 20);
        buf.putLong(orderID);
        buf.put(restatementReason);
        buf.put((byte) 0x00);              // ReservedInternal
        buf.put((byte) numberOfBitfields);
        if (numberOfBitfields > 0) buf.put(bitfields, 0, numberOfBitfields);

        return buf.array();
    }

    private static void putText(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        if (s != null && !s.isEmpty()) {
            byte[] src = s.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        }
        buf.put(bytes);
    }

    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getClOrdID() { return clOrdID; }
    public long getOrderID() { return orderID; }
    public byte getRestatementReason() { return restatementReason; }

    @Override
    public String toString() {
        return "OrderRestated{clOrdID='" + clOrdID + "', orderID=" + orderID
                + ", reason=" + (char) restatementReason + '}';
    }
}
