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
 * Input bitfields (p.176) — every other bit is blank, reserved or "-" and cannot be specified:
 *   Byte 1: 0x01=ClearingFirm(4B,Alpha), 0x04=OrderQty(4B,Binary,R), 0x08=Price(8B,BinaryPrice,R),
 *           0x10=OrdType(1B), 0x20=CancelOrigOnReject(1B), 0x40=ExecInst(1B)
 *   Byte 2: 0x01=MaxFloor(4B), 0x02=StopPx(8B,BinaryPrice), 0x04=RoutingFirmID(4B,Alpha)
 *
 * R = Required. OrderQty and Price must be present on all requests;
 * Price is optional for market orders (OrdType='1').
 */
public final class ModifyOrderMessage extends ApplicationMessage {

    static final byte MESSAGE_TYPE = 0x3A;
    private static final int FIXED_SIZE = 51;

    private static final int MAX_BITFIELDS = 2;
    private static final int[][] FIELD_LENGTHS = {
        {4, 0, 4, 8, 1, 1, 1, 0},   // 0 = blank, reserved or not allowed
        {4, 8, 4, 0, 0, 0, 0, 0},
    };

    private byte matchingUnit;
    private int sequenceNumber;
    private String clOrdID;
    private String origClOrdID;
    private byte[] bitfields = new byte[0];

    private String clearingFirm;
    private int orderQty;
    private BigDecimal price;       // null = not present or 0
    private byte ordType;           // 0 = not present
    private byte cancelOrigOnReject;
    private byte execInst;
    private int maxFloor;
    private long stopPx;
    private String routingFirmID;

    private String fieldError;
    private String charsetError;

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
        msg.clOrdID        = msg.getText(buf, 20, "ClOrdID", FieldCharset.CLORDID);
        msg.origClOrdID    = msg.getText(buf, 20, "OrigClOrdID", FieldCharset.TEXT);

        int numBitfields = buf.get() & 0xFF;
        if (buf.remaining() < numBitfields) {
            msg.fieldError = "Modify Order is too short for its bitfields";
            return msg;
        }
        msg.bitfields = new byte[numBitfields];
        buf.get(msg.bitfields);

        msg.parseOptionalFields(buf);
        if (msg.fieldError == null) msg.fieldError = msg.charsetError;
        return msg;
    }

    private void parseOptionalFields(ByteBuffer buf) {
        for (int i = 0; i < bitfields.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                if ((bitfields[i] & 0xFF & (1 << bit)) == 0) continue;

                int length = i < MAX_BITFIELDS ? FIELD_LENGTHS[i][bit] : 0;
                if (length == 0) {
                    fieldError = "Bitfield " + (i + 1) + " bit " + (1 << bit) + " cannot be specified on Modify Order";
                    return;
                }
                if (buf.remaining() < length) {
                    fieldError = "Modify Order is too short for its optional fields";
                    return;
                }
                assign(i, 1 << bit, buf);
            }
        }
        fieldError = unsupportedField();
    }

    private void assign(int bitfield, int bit, ByteBuffer buf) {
        if (bitfield == 0) {
            switch (bit) {
                case 0x01 -> clearingFirm = getAlpha(buf, 4, "ClearingFirm");
                case 0x04 -> orderQty = buf.getInt();
                case 0x08 -> price = readPrice(buf);
                case 0x10 -> ordType = buf.get();
                case 0x20 -> cancelOrigOnReject = buf.get();
                case 0x40 -> execInst = buf.get();
                default -> throw new IllegalStateException("No field for bit " + bit);
            }
        } else {
            switch (bit) {
                case 0x01 -> maxFloor = buf.getInt();
                case 0x02 -> stopPx = buf.getLong();
                case 0x04 -> routingFirmID = getAlpha(buf, 4, "RoutingFirmID");
                default -> throw new IllegalStateException("No field for bit " + bit);
            }
        }
    }

    private String unsupportedField() {
        if (cancelOrigOnReject != 0 && cancelOrigOnReject != 'N' && cancelOrigOnReject != 'Y')
            return "Invalid CancelOrigOnReject '" + (char) cancelOrigOnReject + "'";
        if (execInst != 0) return "ExecInst is not supported by the simulator";
        return null;
    }

    private boolean hasBit(int bitfield, int bit) {
        return bitfield < bitfields.length && (bitfields[bitfield] & bit) != 0;
    }

    private static BigDecimal readPrice(ByteBuffer buf) {
        long raw = buf.getLong();
        return raw != 0 ? BigDecimal.valueOf(raw, 4) : null;
    }

    private String getText(ByteBuffer buf, int len, String field, FieldCharset charset) {
        byte[] bytes = new byte[len];
        buf.get(bytes);
        if (charsetError == null) charsetError = charset.check(field, bytes);
        int end = len;
        while (end > 0 && bytes[end - 1] == 0x00) end--;
        return new String(bytes, 0, end, StandardCharsets.US_ASCII).trim();
    }

    private String getAlpha(ByteBuffer buf, int len, String field) {
        return getText(buf, len, field, FieldCharset.ALPHA);
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
    public boolean cancelsOrigOnReject()  { return cancelOrigOnReject == 'Y'; }
    public String getRoutingFirmID()      { return routingFirmID; }
    public boolean hasMaxFloor()          { return hasBit(1, 0x01); }
    public int   getMaxFloor()            { return maxFloor; }
    public BigDecimal getStopPx()         { return hasBit(1, 0x02) ? BigDecimal.valueOf(stopPx, 4) : null; }
    public String getFieldError()         { return fieldError; }
    public boolean hasOrderQty()          { return hasBit(0, 0x04); }
    public boolean hasPrice()             { return hasBit(0, 0x08); }

    @Override
    public String toString() {
        return "ModifyOrder{clOrdID='" + clOrdID + "', origClOrdID='" + origClOrdID
                + "', qty=" + orderQty + ", price=" + price + '}';
    }
}
