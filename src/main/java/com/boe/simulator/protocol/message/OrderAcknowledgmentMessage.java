package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.server.order.Order;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Order Acknowledgment — Table 66 (p.111), spec v2.11.90
 *
 * Fixed header (48 bytes):
 *   [0]  StartOfMessage    2B
 *   [2]  MessageLength     2B
 *   [4]  MessageType       1B  = 0x25
 *   [5]  MatchingUnit      1B
 *   [6]  SequenceNumber    4B
 *   [10] TransactionTime   8B  DateTime
 *   [18] ClOrdID           20B Text (NUL-padded)
 *   [38] OrderID           8B  Binary
 *   [46] ReservedInternal  1B  (always 0x00)
 *   [47] NumberOfReturnBitfields 1B
 *   [48] ReturnBitfield¹…ᴺ  NB
 *        Optional fields…
 *
 * Bitfield byte → bit → field (p.180):
 *   Byte 1: 0x01=Side, 0x04=Price, 0x10=OrdType
 *   Byte 2: 0x01=Symbol, 0x40=Capacity
 *   Byte 3: 0x01=Account, 0x02=ClearingFirm, 0x04=ClearingAccount, 0x40=OrderQty
 *   Byte 4: 0x01=MaturityDate, 0x02=StrikePrice, 0x04=PutOrCall, 0x08=OpenClose
 */
public final class OrderAcknowledgmentMessage extends ApplicationMessage {
    private static final byte MESSAGE_TYPE = 0x25;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    private static final int FIXED_SIZE = 48; // before bitfields/optional

    // Bitfield byte indices (0-based)
    private static final int BF_PRICE        = 0; // byte 1, bit 0x04
    private static final int BF_SIDE         = 0; // byte 1, bit 0x01
    private static final int BF_ORDTYPE      = 0; // byte 1, bit 0x10
    private static final int BF_SYMBOL       = 1; // byte 2, bit 0x01
    private static final int BF_CAPACITY     = 1; // byte 2, bit 0x40
    private static final int BF_ACCOUNT      = 2; // byte 3, bit 0x01
    private static final int BF_CLEARING_FIRM   = 2; // byte 3, bit 0x02
    private static final int BF_CLEARING_ACCT   = 2; // byte 3, bit 0x04
    private static final int BF_ORDER_QTY    = 2; // byte 3, bit 0x40
    private static final int BF_MATURITY     = 3; // byte 4, bit 0x01
    private static final int BF_STRIKE       = 3; // byte 4, bit 0x02
    private static final int BF_PUT_OR_CALL  = 3; // byte 4, bit 0x04
    private static final int BF_OPEN_CLOSE   = 3; // byte 4, bit 0x08

    // Header
    private byte matchingUnit;
    private int sequenceNumber;

    // Required fields
    private long transactTime;
    private String clOrdID;
    private long orderID;

    // Optional fields (present based on bitfields)
    private int numberOfBitfields;
    private byte[] bitfields;

    private byte side;
    private BigDecimal price;
    private byte ordType;
    private String symbol;
    private byte capacity;
    private String account;
    private String clearingFirm;
    private String clearingAccount;
    private int orderQty;
    private Instant maturityDate;
    private BigDecimal strikePrice;
    private byte putOrCall;
    private byte openClose;

    public OrderAcknowledgmentMessage() {}

    public static OrderAcknowledgmentMessage fromOrder(Order order, byte matchingUnit, int sequenceNumber) {
        OrderAcknowledgmentMessage msg = new OrderAcknowledgmentMessage();

        msg.matchingUnit = matchingUnit;
        msg.sequenceNumber = sequenceNumber;
        msg.transactTime = System.nanoTime();
        msg.clOrdID = order.getClOrdID();
        msg.orderID = order.getOrderID();

        msg.side = order.getSide().wireValue();
        msg.price = order.getPrice();
        msg.ordType = order.getOrdType().wireValue();
        msg.symbol = order.getSymbol();
        msg.capacity = order.getCapacity() != null ? order.getCapacity().wireValue() : 0;
        msg.account = order.getAccount();
        msg.clearingFirm = order.getClearingFirm();
        msg.clearingAccount = order.getClearingAccount();
        msg.orderQty = order.getOrderQty();
        msg.maturityDate = order.getMaturityDate();
        msg.strikePrice = order.getStrikePrice();
        msg.putOrCall = order.getPutOrCall() != null ? order.getPutOrCall().wireValue() : 0;
        msg.openClose = order.getOpenClose() != null ? order.getOpenClose().wireValue() : 0;

        msg.setupBitfields();
        return msg;
    }

    private void setupBitfields() {
        numberOfBitfields = 4;
        bitfields = new byte[4];

        // Byte 1
        bitfields[BF_SIDE] |= 0x01;                             // Side always echoed
        if (price != null) bitfields[BF_PRICE] |= 0x04;
        bitfields[BF_ORDTYPE] |= 0x10;                          // OrdType always echoed

        // Byte 2
        if (symbol != null && !symbol.isBlank()) bitfields[BF_SYMBOL] |= 0x01;
        if (capacity != 0) bitfields[BF_CAPACITY] |= 0x40;

        // Byte 3
        if (account != null && !account.isBlank()) bitfields[BF_ACCOUNT] |= 0x01;
        if (clearingFirm != null && !clearingFirm.isBlank()) bitfields[BF_CLEARING_FIRM] |= 0x02;
        if (clearingAccount != null && !clearingAccount.isBlank()) bitfields[BF_CLEARING_ACCT] |= 0x04;
        bitfields[BF_ORDER_QTY] |= 0x40;                        // OrderQty always echoed

        // Byte 4
        if (maturityDate != null) bitfields[BF_MATURITY] |= 0x01;
        if (strikePrice != null) bitfields[BF_STRIKE] |= 0x02;
        if (putOrCall != 0) bitfields[BF_PUT_OR_CALL] |= 0x04;
        if (openClose != 0) bitfields[BF_OPEN_CLOSE] |= 0x08;
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
        buf.putLong(orderID);
        buf.put((byte) 0x00);              // ReservedInternal
        buf.put((byte) numberOfBitfields);
        buf.put(bitfields, 0, numberOfBitfields);

        writeOptional(buf);
        return buf.array();
    }

    private void writeOptional(ByteBuffer buf) {
        // Byte 1 fields (ascending bit order)
        if ((bitfields[0] & 0x01) != 0) buf.put(side);
        if ((bitfields[0] & 0x04) != 0) BinaryPrice.fromPrice(price).putInto(buf);
        if ((bitfields[0] & 0x10) != 0) buf.put(ordType);

        // Byte 2 fields
        if ((bitfields[1] & 0x01) != 0) putAlpha(buf, symbol, 8);
        if ((bitfields[1] & 0x40) != 0) buf.put(capacity);

        // Byte 3 fields
        if ((bitfields[2] & 0x01) != 0) putText(buf, account, 16);
        if ((bitfields[2] & 0x02) != 0) putAlpha(buf, clearingFirm, 4);
        if ((bitfields[2] & 0x04) != 0) putText(buf, clearingAccount, 4);
        if ((bitfields[2] & 0x40) != 0) buf.putInt(orderQty);

        // Byte 4 fields
        if ((bitfields[3] & 0x01) != 0) buf.putInt(toYYYYMMDD(maturityDate));
        if ((bitfields[3] & 0x02) != 0) BinaryPrice.fromPrice(strikePrice).putInto(buf);
        if ((bitfields[3] & 0x04) != 0) buf.put(putOrCall);
        if ((bitfields[3] & 0x08) != 0) buf.put(openClose);
    }

    private int optionalSize() {
        int size = 0;
        if ((bitfields[0] & 0x01) != 0) size += 1;  // Side
        if ((bitfields[0] & 0x04) != 0) size += 8;  // Price
        if ((bitfields[0] & 0x10) != 0) size += 1;  // OrdType
        if ((bitfields[1] & 0x01) != 0) size += 8;  // Symbol
        if ((bitfields[1] & 0x40) != 0) size += 1;  // Capacity
        if ((bitfields[2] & 0x01) != 0) size += 16; // Account
        if ((bitfields[2] & 0x02) != 0) size += 4;  // ClearingFirm
        if ((bitfields[2] & 0x04) != 0) size += 4;  // ClearingAccount
        if ((bitfields[2] & 0x40) != 0) size += 4;  // OrderQty
        if ((bitfields[3] & 0x01) != 0) size += 4;  // MaturityDate
        if ((bitfields[3] & 0x02) != 0) size += 8;  // StrikePrice
        if ((bitfields[3] & 0x04) != 0) size += 1;  // PutOrCall
        if ((bitfields[3] & 0x08) != 0) size += 1;  // OpenClose
        return size;
    }

    public static OrderAcknowledgmentMessage fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        OrderAcknowledgmentMessage msg = new OrderAcknowledgmentMessage();

        buf.position(10); // skip SOM(2)+MsgLen(2)+MsgType(1)+MatchUnit(1)+SeqNum(4)

        msg.transactTime = buf.getLong();

        byte[] clOrdIDBytes = new byte[20];
        buf.get(clOrdIDBytes);
        msg.clOrdID = stripNul(clOrdIDBytes);

        msg.orderID = buf.getLong();
        buf.get(); // ReservedInternal

        msg.numberOfBitfields = buf.get() & 0xFF;
        msg.bitfields = new byte[msg.numberOfBitfields];
        if (msg.numberOfBitfields > 0) buf.get(msg.bitfields);

        msg.readOptional(buf);
        return msg;
    }

    private void readOptional(ByteBuffer buf) {
        if (numberOfBitfields < 1) return;

        if ((bitfields[0] & 0x01) != 0) side = buf.get();
        if ((bitfields[0] & 0x04) != 0) {
            byte[] px = new byte[8]; buf.get(px); price = BinaryPrice.fromBytes(px).toPrice();
        }
        if ((bitfields[0] & 0x10) != 0) ordType = buf.get();

        if (numberOfBitfields < 2) return;
        if ((bitfields[1] & 0x01) != 0) { byte[] s = new byte[8]; buf.get(s); symbol = stripSpace(s); }
        if ((bitfields[1] & 0x40) != 0) capacity = buf.get();

        if (numberOfBitfields < 3) return;
        if ((bitfields[2] & 0x01) != 0) { byte[] a = new byte[16]; buf.get(a); account = stripNul(a); }
        if ((bitfields[2] & 0x02) != 0) { byte[] cf = new byte[4]; buf.get(cf); clearingFirm = stripSpace(cf); }
        if ((bitfields[2] & 0x04) != 0) { byte[] ca = new byte[4]; buf.get(ca); clearingAccount = stripNul(ca); }
        if ((bitfields[2] & 0x40) != 0) orderQty = buf.getInt();

        if (numberOfBitfields < 4) return;
        if ((bitfields[3] & 0x01) != 0) maturityDate = fromYYYYMMDD(buf.getInt());
        if ((bitfields[3] & 0x02) != 0) {
            byte[] sp = new byte[8]; buf.get(sp); strikePrice = BinaryPrice.fromBytes(sp).toPrice();
        }
        if ((bitfields[3] & 0x04) != 0) putOrCall = buf.get();
        if ((bitfields[3] & 0x08) != 0) openClose = buf.get();
    }

    // Text fields: NUL-padded (0x00)
    private static void putText(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        if (s != null && !s.isEmpty()) {
            byte[] src = s.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        }
        buf.put(bytes);
    }

    // Alphanumeric fields: space-padded (0x20)
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

    // MaturityDate: YYYYMMDD packed as uint32 LE (per spec example p.120)
    private static int toYYYYMMDD(Instant instant) {
        if (instant == null) return 0;
        LocalDate d = instant.atZone(ZoneId.of("America/New_York")).toLocalDate();
        return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
    }

    private static Instant fromYYYYMMDD(int yyyymmdd) {
        if (yyyymmdd == 0) return null;
        int y = yyyymmdd / 10000, m = (yyyymmdd / 100) % 100, d = yyyymmdd % 100;
        return LocalDate.of(y, m, d).atStartOfDay(ZoneId.of("America/New_York")).toInstant();
    }

    // Getters
    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getClOrdID() { return clOrdID; }
    public long getOrderID() { return orderID; }
    public byte getSide() { return side; }
    public BigDecimal getPrice() { return price; }
    public String getSymbol() { return symbol; }
    public int getOrderQty() { return orderQty; }

    @Override
    public String toString() {
        return "OrderAcknowledgment{clOrdID='" + clOrdID + "', orderID=" + orderID
                + ", symbol='" + symbol + "', price=" + price + '}';
    }
}
