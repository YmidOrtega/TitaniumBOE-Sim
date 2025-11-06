package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.order.Order;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class OrderExecutedMessage {
    private static final byte MESSAGE_TYPE = 0x21;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    // Header
    private byte matchingUnit;
    private int sequenceNumber;

    // Required fields
    private long transactTime;
    private String clOrdID;           // 20 bytes
    private long orderID;
    private long execID;              // Execution ID (Trade ID)
    private int lastShares;           // Cantidad ejecutada en este fill
    private int leavesQty;            // Cantidad pendiente
    private BigDecimal lastPx;        // Precio de ejecución
    private byte liquidity;           // 'A'=Added, 'R'=Removed, 'N'=None

    // Optional fields
    private int numberOfBitfields;
    private byte[] bitfields;
    private String symbol;            // 8 bytes
    private BigDecimal avgPx;         // Precio promedio
    private int cumQty;               // Cantidad acumulada

    public OrderExecutedMessage() {
    }

    public static OrderExecutedMessage fromTrade(Trade trade, Order order, boolean isAggressive) {
        OrderExecutedMessage msg = new OrderExecutedMessage();

        msg.transactTime = trade.getExecutionTime().toEpochMilli() * 1_000_000; // Convert to nanos
        msg.clOrdID = order.getClOrdID();
        msg.orderID = order.getOrderID();
        msg.execID = trade.getTradeId();
        msg.lastShares = trade.getQuantity();
        msg.leavesQty = order.getLeavesQty();
        msg.lastPx = trade.getPrice();
        msg.liquidity = isAggressive ? (byte)'R' : (byte)'A'; // Aggressive removes, passive adds

        // Optional fields
        msg.symbol = order.getSymbol();
        msg.cumQty = order.getCumQty();
        msg.avgPx = calculateAvgPx(order);

        msg.setupBitfields();

        return msg;
    }

    private static BigDecimal calculateAvgPx(Order order) {
        if (order.getCumQty() == 0) return BigDecimal.ZERO;
        return order.getPrice();
    }

    private void setupBitfields() {
        numberOfBitfields = 1;
        bitfields = new byte[1];

        // Bitfield 1
        if (symbol != null) bitfields[0] |= 0x01;
        if (avgPx != null) bitfields[0] |= 0x02;
        if (cumQty > 0) bitfields[0] |= 0x04;
    }

    public byte[] toBytes() {
        int baseSize = 2 + 2 + 1 + 1 + 4 + 8 + 20 + 8 + 8 + 4 + 4 + 8 + 1 + 1;
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

        // OrderID
        buffer.putLong(orderID);

        // ExecID
        buffer.putLong(execID);

        // LastShares
        buffer.putInt(lastShares);

        // LeavesQty
        buffer.putInt(leavesQty);

        // LastPx
        buffer.put(BinaryPrice.fromPrice(lastPx).toBytes());

        // Liquidity
        buffer.put(liquidity);

        // NumberOfBitfields
        buffer.put((byte)numberOfBitfields);

        // Bitfields
        buffer.put(bitfields);

        // Optional fields
        writeOptionalFields(buffer);

        return buffer.array();
    }

    private void writeOptionalFields(ByteBuffer buffer) {
        if ((bitfields[0] & 0x01) != 0) buffer.put(toFixedLengthBytes(symbol, 8));
        if ((bitfields[0] & 0x02) != 0) buffer.put(BinaryPrice.fromPrice(avgPx).toBytes());
        if ((bitfields[0] & 0x04) != 0) buffer.putInt(cumQty);
    }

    private int calculateOptionalSize() {
        int size = 0;
        if ((bitfields[0] & 0x01) != 0) size += 8;  // Symbol
        if ((bitfields[0] & 0x02) != 0) size += 8;  // AvgPx
        if ((bitfields[0] & 0x04) != 0) size += 4;  // CumQty
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

    public static OrderExecutedMessage fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        OrderExecutedMessage msg = new OrderExecutedMessage();

        // Skip to payload (after header)
        buffer.position(10);

        msg.transactTime = buffer.getLong();

        byte[] clOrdIDBytes = new byte[20];
        buffer.get(clOrdIDBytes);
        msg.clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();

        msg.orderID = buffer.getLong();
        msg.execID = buffer.getLong();
        msg.lastShares = buffer.getInt();
        msg.leavesQty = buffer.getInt();

        byte[] lastPxBytes = new byte[8];
        buffer.get(lastPxBytes);
        msg.lastPx = BinaryPrice.fromBytes(lastPxBytes).toPrice();

        msg.liquidity = buffer.get();

        msg.numberOfBitfields = buffer.get();
        msg.bitfields = new byte[msg.numberOfBitfields];
        buffer.get(msg.bitfields);

        // Parse optional fields
        if (msg.numberOfBitfields > 0) {
            if ((msg.bitfields[0] & 0x01) != 0) {
                byte[] symbolBytes = new byte[8];
                buffer.get(symbolBytes);
                msg.symbol = new String(symbolBytes, StandardCharsets.US_ASCII).trim();
            }
            if ((msg.bitfields[0] & 0x02) != 0) {
                byte[] avgPxBytes = new byte[8];
                buffer.get(avgPxBytes);
                msg.avgPx = BinaryPrice.fromBytes(avgPxBytes).toPrice();
            }
            if ((msg.bitfields[0] & 0x04) != 0) msg.cumQty = buffer.getInt();
        }

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
    public long getOrderID() { return orderID; }
    public long getExecID() { return execID; }
    public int getLastShares() { return lastShares; }
    public int getLeavesQty() { return leavesQty; }
    public BigDecimal getLastPx() { return lastPx; }
    public byte getLiquidity() { return liquidity; }
    public String getSymbol() { return symbol; }
    public int getCumQty() { return cumQty; }

    public boolean isFilled() {
        return leavesQty == 0;
    }

    public boolean isPartialFill() {
        return leavesQty > 0;
    }

    @Override
    public String toString() {
        return String.format("OrderExecuted{clOrdID='%s', execID=%d, lastShares=%d @ %s, leaves=%d, liq=%c}",
                clOrdID, execID, lastShares, lastPx, leavesQty, (char)liquidity);
    }
}