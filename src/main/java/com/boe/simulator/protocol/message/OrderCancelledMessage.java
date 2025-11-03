package com.boe.simulator.protocol.message;

import com.boe.simulator.server.order.Order;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class OrderCancelledMessage {
    private static final byte MESSAGE_TYPE = 0x23;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    // Cancel reasons
    public static final byte REASON_USER_REQUESTED = (byte) 'U';
    public static final byte REASON_MASS_CANCEL = (byte) 'M';
    public static final byte REASON_TIMEOUT = (byte) 'T';
    public static final byte REASON_SUPERVISOR = (byte) 'S';

    // Header
    private byte matchingUnit;
    private int sequenceNumber;

    // Required fields
    private long transactTime;        // Timestamp in nanoseconds
    private String clOrdID;           // 20 bytes
    private long orderID;
    private byte cancelReason;
    private int leavesQty;

    // Optional fields
    private int numberOfBitfields;
    private byte[] bitfields;
    private String massCancelId;      // 20 bytes - For mass cancel acknowledgment

    public OrderCancelledMessage() {
    }

    public static OrderCancelledMessage fromOrder(Order order, byte cancelReason) {
        OrderCancelledMessage msg = new OrderCancelledMessage();
        msg.transactTime = System.nanoTime();
        msg.clOrdID = order.getClOrdID();
        msg.orderID = order.getOrderID();
        msg.cancelReason = cancelReason;
        msg.leavesQty = order.getLeavesQty();
        msg.numberOfBitfields = 0;
        return msg;
    }

    public static OrderCancelledMessage forMassCancel(String massCancelId, int totalCancelled) {
        OrderCancelledMessage msg = new OrderCancelledMessage();
        msg.transactTime = System.nanoTime();
        msg.clOrdID = "";  // Empty for mass cancel acknowledgment
        msg.orderID = 0;
        msg.cancelReason = REASON_MASS_CANCEL;
        msg.leavesQty = totalCancelled;
        msg.massCancelId = massCancelId;

        // Setup bitfields for massCancelId
        msg.numberOfBitfields = 1;
        msg.bitfields = new byte[1];
        msg.bitfields[0] = 0x01; // Bit 0 = massCancelId present

        return msg;
    }

    public byte[] toBytes() {
        int baseSize = 2 + 2 + 1 + 1 + 4 + 8 + 20 + 8 + 1 + 4 + 1; // Base fields
        int optionalSize = 0;

        if (numberOfBitfields > 0 && (bitfields[0] & 0x01) != 0) optionalSize += 20; // massCancelId

        int totalSize = baseSize + numberOfBitfields + optionalSize;

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

        // TransactTime (8 bytes)
        buffer.putLong(transactTime);

        // ClOrdID (20 bytes)
        buffer.put(toFixedLengthBytes(clOrdID, 20));

        // OrderID (8 bytes)
        buffer.putLong(orderID);

        // CancelReason (1 byte)
        buffer.put(cancelReason);

        // LeavesQty (4 bytes)
        buffer.putInt(leavesQty);

        // NumberOfBitfields
        buffer.put((byte)numberOfBitfields);

        // Bitfields
        if (numberOfBitfields > 0) {
            buffer.put(bitfields);

            // Optional: MassCancelId
            if ((bitfields[0] & 0x01) != 0 && massCancelId != null) buffer.put(toFixedLengthBytes(massCancelId, 20));
        }

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

    // Setters
    public void setMatchingUnit(byte matchingUnit) {
        this.matchingUnit = matchingUnit;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    // Getters
    public String getClOrdID() { return clOrdID; }
    public long getOrderID() { return orderID; }
    public byte getCancelReason() { return cancelReason; }
    public int getLeavesQty() { return leavesQty; }
    public String getMassCancelId() { return massCancelId; }

    @Override
    public String toString() {
        if (massCancelId != null && !massCancelId.isEmpty()) return "OrderCancelled{MASS CANCEL: massCancelId='" + massCancelId + "', count=" + leavesQty + "}";
        else return "OrderCancelled{clOrdID='" + clOrdID + "', orderID=" + orderID + ", reason=" + (char)cancelReason + ", leavesQty=" + leavesQty + "}";
    }
}
