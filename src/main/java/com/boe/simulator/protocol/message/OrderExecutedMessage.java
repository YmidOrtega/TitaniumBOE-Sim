package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.order.Order;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Order Execution — Table 95 (p.140), spec v2.11.90
 *
 * Fixed layout (70 bytes minimum):
 *   [0]   StartOfMessage       2B
 *   [2]   MessageLength        2B
 *   [4]   MessageType          1B  = 0x2C
 *   [5]   MatchingUnit         1B
 *   [6]   SequenceNumber       4B
 *   [10]  TransactionTime      8B
 *   [18]  ClOrdID              20B Text (NUL-padded)
 *   [38]  ExecID               8B  Binary
 *   [46]  LastShares           4B  Binary
 *   [50]  LastPx               8B  Binary Price
 *   [58]  LeavesQty            4B  Binary
 *   [62]  BaseLiquidityIndicator 1B Alphanumeric
 *   [63]  SubLiquidityIndicator  1B Alphanumeric (0x00 = none)
 *   [64]  ContraBroker           4B Alphanumeric (space-padded)
 *   [68]  ReservedInternal       1B
 *   [69]  NumberOfReturnBitfields 1B
 *   [70]  ReturnBitfield¹…ᴺ    NB
 *         Optional fields…
 *
 * Relevant optional fields (p.190):
 *   Byte 3: 0x02=ClearingFirm(4B), 0x04=ClearingAccount(4B), 0x40=OrderQty(4B)
 */
public final class OrderExecutedMessage extends BoeProtocolMessage {
    private static final byte MESSAGE_TYPE = 0x2C;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    private static final int FIXED_SIZE = 70;

    // BaseLiquidityIndicator values
    public static final byte LIQUIDITY_ADDED   = (byte) 'A';
    public static final byte LIQUIDITY_REMOVED = (byte) 'R';
    public static final byte LIQUIDITY_ROUTED  = (byte) 'X';
    public static final byte LIQUIDITY_AUCTION = (byte) 'C';

    private byte matchingUnit;
    private int sequenceNumber;

    private long transactTime;
    private String clOrdID;
    private long execID;
    private int lastShares;
    private BigDecimal lastPx;
    private int leavesQty;
    private byte baseLiquidityIndicator;
    private byte subLiquidityIndicator;
    private String contraBroker;

    private int numberOfBitfields;
    private byte[] bitfields;

    // Optional fields
    private String clearingFirm;
    private String clearingAccount;
    private int orderQty;

    public OrderExecutedMessage() {}

    public static OrderExecutedMessage fromTrade(Trade trade, Order order, boolean isAggressive) {
        OrderExecutedMessage msg = new OrderExecutedMessage();

        msg.transactTime = trade.getExecutionTime().toEpochMilli() * 1_000_000L;
        msg.clOrdID = order.getClOrdID();
        msg.execID = trade.getTradeId();
        msg.lastShares = trade.getQuantity();
        msg.lastPx = trade.getPrice();
        msg.leavesQty = order.getLeavesQty();
        msg.baseLiquidityIndicator = isAggressive ? LIQUIDITY_REMOVED : LIQUIDITY_ADDED;
        msg.subLiquidityIndicator = 0x00;
        msg.contraBroker = "";

        msg.clearingFirm = order.getClearingFirm();
        msg.clearingAccount = order.getClearingAccount();
        msg.orderQty = order.getOrderQty();

        msg.setupBitfields();
        return msg;
    }

    private void setupBitfields() {
        // Use 3 bitfield bytes to cover bytes 1-3
        numberOfBitfields = 3;
        bitfields = new byte[3];

        // Byte 3 optional fields
        if (clearingFirm != null && !clearingFirm.isBlank())     bitfields[2] |= 0x02;
        if (clearingAccount != null && !clearingAccount.isBlank()) bitfields[2] |= 0x04;
        bitfields[2] |= 0x40; // OrderQty always included
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int optSize = optionalSize();
        int totalSize = FIXED_SIZE + numberOfBitfields + optSize;

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        buf.put(SOM1);
        buf.put(SOM2);
        buf.putShort((short) (totalSize - 2));
        buf.put(MESSAGE_TYPE);
        buf.put(matchingUnit);
        buf.putInt(sequenceNumber);
        buf.putLong(transactTime);
        putText(buf, clOrdID, 20);
        buf.putLong(execID);
        buf.putInt(lastShares);
        BinaryPrice.fromPrice(lastPx).putInto(buf);
        buf.putInt(leavesQty);
        buf.put(baseLiquidityIndicator);
        buf.put(subLiquidityIndicator);
        putAlpha(buf, contraBroker, 4);
        buf.put((byte) 0x00);              // ReservedInternal
        buf.put((byte) numberOfBitfields);
        if (numberOfBitfields > 0) buf.put(bitfields, 0, numberOfBitfields);

        writeOptional(buf);
        return buf.array();
    }

    private void writeOptional(ByteBuffer buf) {
        if (numberOfBitfields < 3) return;

        if ((bitfields[2] & 0x02) != 0) putAlpha(buf, clearingFirm, 4);
        if ((bitfields[2] & 0x04) != 0) putText(buf, clearingAccount, 4);
        if ((bitfields[2] & 0x40) != 0) buf.putInt(orderQty);
    }

    private int optionalSize() {
        int size = 0;
        if (numberOfBitfields >= 3) {
            if ((bitfields[2] & 0x02) != 0) size += 4;
            if ((bitfields[2] & 0x04) != 0) size += 4;
            if ((bitfields[2] & 0x40) != 0) size += 4;
        }
        return size;
    }

    public static OrderExecutedMessage fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        OrderExecutedMessage msg = new OrderExecutedMessage();

        buf.position(10);
        msg.transactTime = buf.getLong();

        byte[] clOrdIDBytes = new byte[20];
        buf.get(clOrdIDBytes);
        msg.clOrdID = stripNul(clOrdIDBytes);

        msg.execID = buf.getLong();
        msg.lastShares = buf.getInt();

        byte[] pxBytes = new byte[8]; buf.get(pxBytes);
        msg.lastPx = BinaryPrice.fromBytes(pxBytes).toPrice();

        msg.leavesQty = buf.getInt();
        msg.baseLiquidityIndicator = buf.get();
        msg.subLiquidityIndicator = buf.get();

        byte[] cb = new byte[4]; buf.get(cb);
        msg.contraBroker = stripSpace(cb);

        buf.get(); // ReservedInternal

        msg.numberOfBitfields = buf.get() & 0xFF;
        msg.bitfields = new byte[msg.numberOfBitfields];
        if (msg.numberOfBitfields > 0) buf.get(msg.bitfields);

        if (msg.numberOfBitfields >= 3) {
            if ((msg.bitfields[2] & 0x02) != 0) {
                byte[] cf = new byte[4]; buf.get(cf); msg.clearingFirm = stripSpace(cf);
            }
            if ((msg.bitfields[2] & 0x04) != 0) {
                byte[] ca = new byte[4]; buf.get(ca); msg.clearingAccount = stripNul(ca);
            }
            if ((msg.bitfields[2] & 0x40) != 0) msg.orderQty = buf.getInt();
        }

        return msg;
    }

    private static void putText(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        if (s != null && !s.isEmpty()) {
            byte[] src = s.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        }
        buf.put(bytes);
    }

    private static void putAlpha(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        java.util.Arrays.fill(bytes, (byte) 0x20);
        if (s != null && !s.isEmpty()) {
            byte[] src = s.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        }
        buf.put(bytes);
    }

    private static String stripNul(byte[] b) {
        int end = b.length;
        while (end > 0 && b[end - 1] == 0) end--;
        return new String(b, 0, end, StandardCharsets.US_ASCII);
    }

    private static String stripSpace(byte[] b) {
        return new String(b, StandardCharsets.US_ASCII).stripTrailing();
    }

    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getClOrdID() { return clOrdID; }
    public long getExecID() { return execID; }
    public int getLastShares() { return lastShares; }
    public BigDecimal getLastPx() { return lastPx; }
    public int getLeavesQty() { return leavesQty; }
    public byte getBaseLiquidityIndicator() { return baseLiquidityIndicator; }
    public String getContraBroker() { return contraBroker; }
    public boolean isFilled() { return leavesQty == 0; }

    @Override
    public String toString() {
        return String.format("OrderExecution{clOrdID='%s', execID=%d, lastShares=%d @ %s, leaves=%d, liq=%c}",
                clOrdID, execID, lastShares, lastPx, leavesQty, (char) baseLiquidityIndicator);
    }
}
