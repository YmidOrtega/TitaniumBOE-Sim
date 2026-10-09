package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.protocol.types.BoeTime;
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
 *   [64]  ContraBroker           4B Alphanumeric (NUL-padded)
 *   [68]  ReservedInternal       1B
 *   [69]  NumberOfReturnBitfields 1B
 *   [70]  ReturnBitfield¹…ᴺ    NB
 *         Optional fields… (Return Bitfields Per Message, p.190)
 */
public final class OrderExecutedMessage extends ApplicationMessage {
    public static final byte MESSAGE_TYPE = 0x2C;
    private static final int FIXED_SIZE = 69; // before NumberOfReturnBitfields

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
    private ReturnFields returnFields = new ReturnFields();

    public OrderExecutedMessage() {}

    public static OrderExecutedMessage fromTrade(Trade trade, Order order, boolean isAggressive) {
        return fromTrade(trade, order, isAggressive, null);
    }

    public static OrderExecutedMessage fromTrade(Trade trade, Order order, boolean isAggressive, ReturnBitfields returnBitfields) {
        OrderExecutedMessage msg = new OrderExecutedMessage();

        msg.transactTime = BoeTime.toEpochNanos(trade.getExecutionTime());
        msg.clOrdID = order.getClOrdID();
        msg.execID = trade.getTradeId();
        msg.lastShares = trade.getQuantity();
        msg.lastPx = trade.getPrice();
        msg.leavesQty = order.getLeavesQty();
        msg.baseLiquidityIndicator = isAggressive ? LIQUIDITY_REMOVED : LIQUIDITY_ADDED;
        msg.subLiquidityIndicator = 0x00;
        msg.contraBroker = "";

        msg.returnFields = OrderReturnFields.forOrder(order)
                .put(ReturnField.LAST_SHARES, trade.getQuantity())
                .put(ReturnField.LAST_PX, trade.getPrice())
                .put(ReturnField.BASE_LIQUIDITY_INDICATOR, msg.baseLiquidityIndicator)
                .put(ReturnField.TRADE_DATE, trade.getExecutionTime())
                .select(returnBitfields, MESSAGE_TYPE);
        return msg;
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int totalSize = FIXED_SIZE + returnFields.encodedSize();
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        buf.put((byte) 0xBA).put((byte) 0xBA);
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
        putText(buf, contraBroker, 4);
        buf.put((byte) 0x00);              // ReservedInternal
        returnFields.writeTo(buf);
        return buf.array();
    }

    public static OrderExecutedMessage fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        OrderExecutedMessage msg = new OrderExecutedMessage();

        msg.matchingUnit = buf.get(5);
        msg.sequenceNumber = buf.getInt(6);
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
        msg.contraBroker = stripNul(cb);

        buf.get(); // ReservedInternal
        msg.returnFields = ReturnFields.readFrom(buf);
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

    private static String stripNul(byte[] b) {
        int end = b.length;
        while (end > 0 && b[end - 1] == 0) end--;
        return new String(b, 0, end, StandardCharsets.US_ASCII);
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
    public ReturnFields getReturnFields() { return returnFields; }
    public String getSymbol() { return (String) returnFields.get(ReturnField.SYMBOL); }
    public byte getCapacity() { return OrderAcknowledgmentMessage.firstChar(returnFields.get(ReturnField.CAPACITY)); }
    public byte[] getBitfields() { return returnFields.mask(); }
    public boolean isFilled() { return leavesQty == 0; }

    @Override
    public String toString() {
        return String.format("OrderExecution{clOrdID='%s', execID=%d, lastShares=%d @ %s, leaves=%d, liq=%c}",
                clOrdID, execID, lastShares, lastPx, leavesQty, (char) baseLiquidityIndicator);
    }
}
