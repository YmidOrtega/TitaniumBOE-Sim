package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class OrderRejectedMessage {
    private static final byte MESSAGE_TYPE = 0x26;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    // Header
    private byte matchingUnit;
    private int sequenceNumber;

    // Required fields
    private long transactTime;
    private String clOrdID;           // 20 bytes
    private byte orderRejectReason;   // Reject reason code
    private String text;              // 60 bytes - Human-readable text

    // Reject reason codes
    public static final byte REASON_DUPLICATE_CLORDID = (byte) 'D';
    public static final byte REASON_INVALID_SYMBOL = (byte) 'S';
    public static final byte REASON_INVALID_PRICE = (byte) 'P';
    public static final byte REASON_INVALID_QUANTITY = (byte) 'Q';
    public static final byte REASON_MISSING_REQUIRED_FIELD = (byte) 'M';
    public static final byte REASON_UNAUTHORIZED = (byte) 'U';
    public static final byte REASON_UNKNOWN_ERROR = (byte) 'X';
    public static final byte REASON_INVALID_CAPACITY = (byte) 'C';
    public static final byte REASON_RATE_LIMIT_EXCEEDED = (byte) 'R';
    public static final byte REASON_SESSION_NOT_AUTHENTICATED = (byte) 'A';

    public OrderRejectedMessage() {
    }

    public OrderRejectedMessage(String clOrdID, byte reason, String text) {
        this.clOrdID = clOrdID;
        this.orderRejectReason = reason;
        this.text = text;
        this.transactTime = System.nanoTime();
    }

    public byte[] toBytes() {
        int totalSize = 2 + 2 + 1 + 1 + 4 + 8 + 20 + 1 + 60;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // StartOfMessage
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);

        // MessageLength
        buffer.putShort((short)(totalSize - 2));

        // MessageType
        buffer.put(MESSAGE_TYPE);

        // MatchingUnit
        buffer.put(matchingUnit);

        // SequenceNumber
        buffer.putInt(sequenceNumber);

        // TransactTime
        buffer.putLong(transactTime);

        // ClOrdID (20 bytes)
        buffer.put(toFixedLengthBytes(clOrdID, 20));

        // OrderRejectReason
        buffer.put(orderRejectReason);

        // Text (60 bytes)
        buffer.put(toFixedLengthBytes(text, 60));

        return buffer.array();
    }

    private byte[] toFixedLengthBytes(String str, int length) {
        byte[] result = new byte[length];
        Arrays.fill(result, (byte) 0x20);

        if (str != null && !str.isEmpty()) {
            byte[] strBytes = str.getBytes(StandardCharsets.US_ASCII);
            int copyLength = Math.min(strBytes.length, length);
            System.arraycopy(strBytes, 0, result, 0, copyLength);
        }

        return result;
    }

    public static OrderRejectedMessage fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        buffer.position(10);

        long transactTime = buffer.getLong();

        byte[] clOrdIDBytes = new byte[20];
        buffer.get(clOrdIDBytes);
        String clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();

        byte reason = buffer.get();

        byte[] textBytes = new byte[60];
        buffer.get(textBytes);
        String text = new String(textBytes, StandardCharsets.US_ASCII).trim();

        OrderRejectedMessage msg = new OrderRejectedMessage(clOrdID, reason, text);
        msg.transactTime = transactTime;

        return msg;
    }


    // Setters
    public void setMatchingUnit(byte matchingUnit) {
        this.matchingUnit = matchingUnit;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    // Getters
    public String getClOrdID() { return clOrdID; }
    public byte getOrderRejectReason() { return orderRejectReason; }
    public String getText() { return text; }

    @Override
    public String toString() {
        return "OrderRejected{" +
                "clOrdID='" + clOrdID + '\'' +
                ", reason=" + (char)orderRejectReason +
                ", text='" + text + '\'' +
                '}';
    }
}