package com.boe.simulator.protocol.message;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Modify Order — Table 39 (p.77), spec v2.11.90
 * Member→Cboe inbound message. MessageType = 0x3A.
 *
 * Fixed layout (51 bytes minimum):
 *   [0]   StartOfMessage               2B Binary
 *   [2]   MessageLength                2B Binary
 *   [4]   MessageType                  1B = 0x3A
 *   [5]   MatchingUnit                 1B (always 0 inbound)
 *   [6]   SequenceNumber               4B Binary LE
 *   [10]  ClOrdID                      20B Text NUL-padded (new ClOrdID after modification)
 *   [30]  OrigClOrdID                  20B Text NUL-padded (ClOrdID of order to replace)
 *   [50]  NumberOfModifyOrderBitfields 1B
 *   [51]  ModifyOrderBitfield¹         1B  (if NumberOfBitfields > 0)
 *         Optional fields…
 *
 * Bitfield map (p.176):
 *   Byte 1: 0x01=ClearingFirm(4B,Alpha), 0x04=OrderQty(4B,Binary,R),
 *           0x08=Price(8B,BinaryPrice,R), 0x10=OrdType(1B),
 *           0x20=CancelOrigOnReject(1B), 0x40=ExecInst(1B), 0x80=Side(1B)
 *   Byte 2: 0x01=MaxFloor(4B), 0x02=StopPx(8B,BinaryPrice),
 *           0x04=RoutingFirmID(4B,Alpha), 0x08=ManualOrderIndicator(1B),
 *           0x10=OperatorId(4B,Alpha), 0x20=FrequentTraderID(20B), 0x80=LocateBroker(8B)
 *
 * R = Required. OrderQty and Price must be present on all requests;
 * Price is optional for market orders (OrdType='1').
 */
public final class ModifyOrderMessage extends ApplicationMessage {

    static final byte MESSAGE_TYPE = 0x3A;
    private static final int FIXED_SIZE = 51;

    private byte matchingUnit;
    private int sequenceNumber;
    private String clOrdID;
    private String origClOrdID;

    // Optional fields parsed from bitfields
    private String clearingFirm;
    private int orderQty;           // 0 = not present
    private BigDecimal price;       // null = not present
    private byte ordType;           // 0 = not present
    private byte cancelOrigOnReject;
    private byte side;

    private ModifyOrderMessage() {}

    public static ModifyOrderMessage parse(byte[] data) {
        if (data == null || data.length < FIXED_SIZE) {
            throw new IllegalArgumentException(
                    "ModifyOrder message too short: " + (data == null ? 0 : data.length));
        }

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        buf.getShort();  // StartOfMessage
        buf.getShort();  // MessageLength
        buf.get();       // MessageType

        ModifyOrderMessage msg = new ModifyOrderMessage();
        msg.matchingUnit   = buf.get();
        msg.sequenceNumber = buf.getInt();
        msg.clOrdID        = getText(buf, 20);
        msg.origClOrdID    = getText(buf, 20);

        int numBitfields = buf.get() & 0xFF;
        byte[] bitfields = new byte[numBitfields];
        for (int i = 0; i < numBitfields && buf.hasRemaining(); i++) {
            bitfields[i] = buf.get();
        }

        // Byte 1 optional fields
        if (numBitfields >= 1) {
            byte bf1 = bitfields[0];
            if ((bf1 & 0x01) != 0) msg.clearingFirm      = getAlpha(buf, 4);
            if ((bf1 & 0x04) != 0) msg.orderQty           = buf.getInt();
            if ((bf1 & 0x08) != 0) msg.price              = readPrice(buf);
            if ((bf1 & 0x10) != 0) msg.ordType             = buf.get();
            if ((bf1 & 0x20) != 0) msg.cancelOrigOnReject  = buf.get();
            if ((bf1 & 0x40) != 0) buf.get();              // ExecInst — discard
            if ((bf1 & 0x80) != 0) msg.side                = buf.get();
        }

        // Byte 2 optional fields — parse sizes only, fields not used by simulator
        if (numBitfields >= 2) {
            byte bf2 = bitfields[1];
            if ((bf2 & 0x01) != 0) buf.getInt();                      // MaxFloor
            if ((bf2 & 0x02) != 0) buf.getLong();                     // StopPx
            if ((bf2 & 0x04) != 0) buf.getInt();                      // RoutingFirmID
            if ((bf2 & 0x08) != 0) buf.get();                         // ManualOrderIndicator
            if ((bf2 & 0x10) != 0) buf.getInt();                      // OperatorId
            if ((bf2 & 0x20) != 0) buf.position(buf.position() + 20); // FrequentTraderID
            if ((bf2 & 0x80) != 0) buf.getLong();                     // LocateBroker
        }

        return msg;
    }

    private static BigDecimal readPrice(ByteBuffer buf) {
        long raw = buf.getLong();
        return raw != 0 ? BigDecimal.valueOf(raw, 4) : null;
    }

    private static String getText(ByteBuffer buf, int len) {
        byte[] bytes = new byte[len];
        buf.get(bytes);
        int end = len;
        while (end > 0 && bytes[end - 1] == 0x00) end--;
        return new String(bytes, 0, end, StandardCharsets.US_ASCII).trim();
    }

    private static String getAlpha(ByteBuffer buf, int len) {
        byte[] bytes = new byte[len];
        buf.get(bytes);
        int end = len;
        while (end > 0 && (bytes[end - 1] == 0x00 || bytes[end - 1] == 0x20)) end--;
        return new String(bytes, 0, end, StandardCharsets.US_ASCII).trim();
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        throw new UnsupportedOperationException("ModifyOrderMessage is inbound-only");
    }

    public byte  getMatchingUnit()        { return matchingUnit; }
    public int   getSequenceNumber()      { return sequenceNumber; }
    public String getClOrdID()            { return clOrdID; }
    public String getOrigClOrdID()        { return origClOrdID; }
    public String getClearingFirm()       { return clearingFirm; }
    public int   getOrderQty()            { return orderQty; }
    public BigDecimal getPrice()          { return price; }
    public byte  getOrdType()             { return ordType; }
    public byte  getCancelOrigOnReject()  { return cancelOrigOnReject; }
    public byte  getSide()                { return side; }
    public boolean hasOrderQty()          { return orderQty > 0; }
    public boolean hasPrice()             { return price != null; }

    @Override
    public String toString() {
        return "ModifyOrder{clOrdID='" + clOrdID + "', origClOrdID='" + origClOrdID
                + "', qty=" + orderQty + ", price=" + price + '}';
    }
}
