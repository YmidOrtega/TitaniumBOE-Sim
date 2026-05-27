package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BinaryPrice;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

/**
 * Trade Cancel or Correct — Table 104 (p.148), spec v2.11.90
 * Used to relay a trade which has been cancelled (busted) or corrected.
 *
 * Fixed layout (94 bytes minimum):
 *   [0]   StartOfMessage       2B
 *   [2]   MessageLength        2B
 *   [4]   MessageType          1B  = 0x2D
 *   [5]   MatchingUnit         1B
 *   [6]   SequenceNumber       4B
 *   [10]  TransactionTime      8B
 *   [18]  ClOrdID              20B Text
 *   [38]  OrderID              8B  Binary
 *   [46]  ExecRefID            8B  Binary (ExecID of the fill being cancelled/corrected)
 *   [54]  Side                 1B  Alphanumeric ('1'=Buy, '2'=Sell)
 *   [55]  BaseLiquidityIndicator 1B Alphanumeric
 *   [56]  ClearingFirm         4B  Alpha
 *   [60]  ClearingAccount      4B  Text
 *   [64]  LastShares           4B  Binary
 *   [68]  LastPx               8B  Binary Price
 *   [76]  CorrectedPrice       8B  Binary Price (0 for cancel/bust)
 *   [84]  OrigTime             8B  DateTime
 *   [92]  ReservedInternal     1B
 *   [93]  NumberOfReturnBitfields 1B
 *   [94]  ReturnBitfield¹…ᴺ   NB
 *         Optional fields…
 */
public final class TradeCancelOrCorrectMessage extends ApplicationMessage {
    private static final byte MESSAGE_TYPE = 0x2D;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    private static final int FIXED_SIZE = 94;

    private byte matchingUnit;
    private int sequenceNumber;

    private long transactTime;
    private String clOrdID;
    private long orderID;
    private long execRefID;
    private byte side;
    private byte baseLiquidityIndicator;
    private String clearingFirm;
    private String clearingAccount;
    private int lastShares;
    private BigDecimal lastPx;
    private BigDecimal correctedPrice;
    private long origTime;

    private int numberOfBitfields;
    private byte[] bitfields;

    public TradeCancelOrCorrectMessage() {}

    /** Cancel/bust: correctedPrice = 0 */
    public static TradeCancelOrCorrectMessage cancel(
            String clOrdID, long orderID, long execRefID,
            byte side, byte baseLiquidityIndicator,
            String clearingFirm, String clearingAccount,
            int lastShares, BigDecimal lastPx, Instant origTime,
            byte matchingUnit, int sequenceNumber) {
        return build(clOrdID, orderID, execRefID, side, baseLiquidityIndicator,
                clearingFirm, clearingAccount, lastShares, lastPx, BigDecimal.ZERO,
                origTime, matchingUnit, sequenceNumber);
    }

    /** Correct: correctedPrice != 0 */
    public static TradeCancelOrCorrectMessage correct(
            String clOrdID, long orderID, long execRefID,
            byte side, byte baseLiquidityIndicator,
            String clearingFirm, String clearingAccount,
            int lastShares, BigDecimal lastPx, BigDecimal correctedPrice,
            Instant origTime, byte matchingUnit, int sequenceNumber) {
        return build(clOrdID, orderID, execRefID, side, baseLiquidityIndicator,
                clearingFirm, clearingAccount, lastShares, lastPx, correctedPrice,
                origTime, matchingUnit, sequenceNumber);
    }

    private static TradeCancelOrCorrectMessage build(
            String clOrdID, long orderID, long execRefID,
            byte side, byte baseLiquidityIndicator,
            String clearingFirm, String clearingAccount,
            int lastShares, BigDecimal lastPx, BigDecimal correctedPrice,
            Instant origTime, byte matchingUnit, int sequenceNumber) {
        TradeCancelOrCorrectMessage msg = new TradeCancelOrCorrectMessage();
        msg.transactTime = System.nanoTime();
        msg.clOrdID = clOrdID;
        msg.orderID = orderID;
        msg.execRefID = execRefID;
        msg.side = side;
        msg.baseLiquidityIndicator = baseLiquidityIndicator;
        msg.clearingFirm = clearingFirm;
        msg.clearingAccount = clearingAccount;
        msg.lastShares = lastShares;
        msg.lastPx = lastPx;
        msg.correctedPrice = correctedPrice != null ? correctedPrice : BigDecimal.ZERO;
        msg.origTime = origTime != null ? origTime.toEpochMilli() * 1_000_000L : 0L;
        msg.matchingUnit = matchingUnit;
        msg.sequenceNumber = sequenceNumber;
        msg.numberOfBitfields = 0;
        msg.bitfields = new byte[0];
        return msg;
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
        buf.putLong(execRefID);
        buf.put(side);
        buf.put(baseLiquidityIndicator);
        putAlpha(buf, clearingFirm, 4);
        putText(buf, clearingAccount, 4);
        buf.putInt(lastShares);
        BinaryPrice.fromPrice(lastPx).putInto(buf);
        BinaryPrice.fromPrice(correctedPrice).putInto(buf);
        buf.putLong(origTime);
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

    private static void putAlpha(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        java.util.Arrays.fill(bytes, (byte) 0x20);
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
    public long getExecRefID() { return execRefID; }
    public int getLastShares() { return lastShares; }
    public BigDecimal getLastPx() { return lastPx; }
    public BigDecimal getCorrectedPrice() { return correctedPrice; }

    @Override
    public String toString() {
        return "TradeCancelOrCorrect{clOrdID='" + clOrdID + "', orderID=" + orderID
                + ", execRefID=" + execRefID + ", lastShares=" + lastShares
                + ", lastPx=" + lastPx + ", correctedPrice=" + correctedPrice + '}';
    }
}
