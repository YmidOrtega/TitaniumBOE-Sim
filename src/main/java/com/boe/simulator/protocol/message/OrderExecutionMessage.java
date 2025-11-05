package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.server.matching.MatchingEngine;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class OrderExecutionMessage {
    private static final byte MESSAGE_TYPE = 0x30;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    // Header
    private byte matchingUnit;
    private int sequenceNumber;

    // Required fields
    private long transactTime;        // Timestamp in nanoseconds
    private String clOrdID;           // 20 bytes
    private long orderID;
    private long execID;              // Unique execution ID
    private byte execType;            // F=Fill, 4=Cancelled
    private byte side;
    private int lastShares;           // Quantity of this fill
    private BigDecimal lastPx;        // Price of this fill (8 bytes binary)
    private int leavesQty;            // Remaining quantity
    private int cumQty;               // Cumulative filled quantity

    // Optional fields
    private int numberOfBitfields;
    private byte[] bitfields;
    private String symbol;            // 8 bytes
    private byte capacity;

    // Execution types
    public static final byte EXEC_TYPE_NEW = 'E';              // New order accepted
    public static final byte EXEC_TYPE_PARTIAL_FILL = '1';    // Partial fill
    public static final byte EXEC_TYPE_FILL = 'F';            // Complete fill
    public static final byte EXEC_TYPE_CANCELLED = '4';       // Cancelled
    public static final byte EXEC_TYPE_REPLACED = '5';        // Replaced

    public OrderExecutionMessage() {
    }

    public static OrderExecutionMessage fromTrade(MatchingEngine.Trade trade, boolean isAggressor) {
        OrderExecutionMessage msg = new OrderExecutionMessage();

        var order = isAggressor ? trade.getAggressorOrder() : trade.getPassiveOrder();

        msg.transactTime = System.nanoTime();
        msg.clOrdID = order.getClOrdID();
        msg.orderID = order.getOrderID();
        msg.execID = generateExecID();
        msg.side = order.getSide();
        msg.lastShares = trade.getQuantity();
        msg.lastPx = trade.getPrice();
        msg.leavesQty = order.getLeavesQty();
        msg.cumQty = order.getCumQty();

        // Determinar tipo de ejecución
        if (order.isFilled()) msg.execType = EXEC_TYPE_FILL;
        else msg.execType = EXEC_TYPE_PARTIAL_FILL;

        // Optional fields
        msg.symbol = order.getSymbol();
        msg.capacity = order.getCapacity();

        msg.setupBitfields();

        return msg;
    }

    private void setupBitfields() {
        numberOfBitfields = 1;
        bitfields = new byte[1];

        // Bitfield 1
        if (symbol != null && !symbol.isEmpty()) bitfields[0] |= 0x40; // Bit 6: Symbol

        if (capacity != 0) bitfields[0] |= (byte) 0x80; // Bit 7: Capacity
    }

    public byte[] toBytes() {
        // Calculate size
        int baseSize = 2 + 2 + 1 + 1 + 4 + 8 + 20 + 8 + 8 + 1 + 1 + 4 + 8 + 4 + 4 + 1;
        int optionalSize = calculateOptionalSize();
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

        // TransactTime
        buffer.putLong(transactTime);

        // ClOrdID (20 bytes)
        buffer.put(toFixedLengthBytes(clOrdID, 20));

        // OrderID (8 bytes)
        buffer.putLong(orderID);

        // ExecID (8 bytes)
        buffer.putLong(execID);

        // ExecType (1 byte)
        buffer.put(execType);

        // Side (1 byte)
        buffer.put(side);

        // LastShares (4 bytes)
        buffer.putInt(lastShares);

        // LastPx (8 bytes binary price)
        buffer.put(BinaryPrice.fromPrice(lastPx).toBytes());

        // LeavesQty (4 bytes)
        buffer.putInt(leavesQty);

        // CumQty (4 bytes)
        buffer.putInt(cumQty);

        // NumberOfBitfields (1 byte)
        buffer.put((byte)numberOfBitfields);

        // Bitfields
        if (numberOfBitfields > 0) {
            buffer.put(bitfields);

            // Optional fields
            if ((bitfields[0] & 0x40) != 0) buffer.put(toFixedLengthBytes(symbol, 8));

            if ((bitfields[0] & 0x80) != 0) buffer.put(capacity);

        }

        return buffer.array();
    }

    private int calculateOptionalSize() {
        int size = 0;
        if (numberOfBitfields > 0) {
            if ((bitfields[0] & 0x40) != 0) size += 8;  // Symbol
            if ((bitfields[0] & 0x80) != 0) size += 1;  // Capacity
        }
        return size;
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

    private static long generateExecID() {
        return System.nanoTime();
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

    public long getExecID() { return execID; }

    public byte getExecType() { return execType; }

    public int getLastShares() { return lastShares; }

    public BigDecimal getLastPx() { return lastPx; }

    public int getLeavesQty() { return leavesQty; }

    public int getCumQty() { return cumQty; }

    @Override
    public String toString() {
        return String.format("OrderExecution{clOrdID='%s', execID=%d, type=%c, qty=%d @ %s, leaves=%d, cum=%d}",
                clOrdID, execID, (char)execType, lastShares, lastPx, leavesQty, cumQty);
    }
}