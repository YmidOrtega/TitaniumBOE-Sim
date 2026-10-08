package com.boe.simulator.protocol.message;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.protocol.types.BoeTime;

/**
 * New Order (0x38) — Member to Cboe.
 * Wire layout per spec v2.11.90 Table 27/28.
 *
 * Bitfield layout (bitfields are 0-indexed here, i.e. bitfields[0]=Bitfield1):
 *   Bitfield 1 (index 0):
 *     bit 0 = ClearingFirm      (4 bytes Alphanumeric)
 *     bit 1 = ClearingAccount   (4 bytes Alphanumeric)
 *     bit 2 = Price             (8 bytes Binary Price)
 *     bit 3 = ExecInst          (1 byte, not supported)
 *     bit 4 = OrdType           (1 byte Alphanumeric)
 *     bit 5 = TimeInForce       (1 byte Alphanumeric)
 *   Bitfield 2 (index 1):
 *     bit 0 = Symbol            (8 bytes Alphanumeric)
 *     bit 6 = Capacity          (1 byte Alphanumeric)
 *     bit 7 = RoutingInst       (4 bytes Alphanumeric)
 *   Bitfield 3 (index 2):
 *     bit 0 = Account           (16 bytes Alphanumeric)
 *   Bitfield 4 (index 3):
 *     bit 0 = MaturityDate      (4 bytes Date, YYYYMMDD)
 *     bit 1 = StrikePrice       (8 bytes Binary Price)
 *     bit 2 = PutOrCall         (1 byte Alphanumeric)
 *     bit 4 = OpenClose         (1 byte Alphanumeric)
 *
 * Optional fields appear in order: first bitfield first, lowest bit first.
 * Every field permitted by "Input Bitfields Per Message" (p.171) is listed in FIELDS with its
 * length from "List of Optional Fields" (p.196), so unsupported fields are consumed, never
 * misaligning the fields after them.
 * Required optional fields (must always be enabled via bitfield):
 *   Symbol, Price (for limit orders), Capacity.
 */
public final class NewOrderMessage extends ApplicationMessage {
    private static final byte MESSAGE_TYPE = 0x38;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    private enum Handling { READ, IGNORE, REJECT }

    private record OptionalField(String name, int length, Handling handling, String acceptedDefault) {
        static OptionalField read(String name, int length) { return new OptionalField(name, length, Handling.READ, null); }
        static OptionalField ignore(String name, int length) { return new OptionalField(name, length, Handling.IGNORE, null); }
        static OptionalField reject(String name, int length) { return new OptionalField(name, length, Handling.REJECT, null); }
        static OptionalField rejectUnless(String name, int length, String acceptedDefault) {
            return new OptionalField(name, length, Handling.REJECT, acceptedDefault);
        }
    }

    // [bitfield][bit]; null = blank or reserved in the spec table, cannot be specified
    private static final OptionalField[][] FIELDS = {
        { OptionalField.read("ClearingFirm", 4), OptionalField.read("ClearingAccount", 4), OptionalField.read("Price", 8),
          OptionalField.rejectUnless("ExecInst", 1, "00"), OptionalField.read("OrdType", 1), OptionalField.read("TimeInForce", 1),
          OptionalField.reject("MinQty", 4), OptionalField.rejectUnless("MaxFloor", 4, "00000000") },
        { OptionalField.read("Symbol", 8), null, null, null, null, null,
          OptionalField.read("Capacity", 1), OptionalField.read("RoutingInst", 4) },
        { OptionalField.read("Account", 16), OptionalField.rejectUnless("DisplayIndicator", 1, "56"), null, null, null,
          OptionalField.reject("PreventMatch", 3), null, OptionalField.reject("ExpireTime", 8) },
        { OptionalField.read("MaturityDate", 4), OptionalField.read("StrikePrice", 8), OptionalField.read("PutOrCall", 1),
          OptionalField.ignore("RiskReset", 8), OptionalField.read("OpenClose", 1), OptionalField.ignore("CMTANumber", 4),
          OptionalField.reject("TargetPartyID", 4), null },
        { OptionalField.ignore("SessionEligibility", 1), OptionalField.ignore("AttributedQuote", 1), null, null, null, null, null, null },
        { OptionalField.reject("DisplayRange", 4), OptionalField.reject("StopPx", 8), OptionalField.ignore("RoutStrategy", 6),
          OptionalField.ignore("RouteDeliveryMethod", 3), OptionalField.ignore("ExDestination", 1), OptionalField.ignore("EchoText", 64),
          OptionalField.reject("AuctionId", 8), OptionalField.ignore("RoutingFirmID", 4) },
        { null, OptionalField.ignore("CustomGroupId", 2), null, null, null, null, null, null },
        { null, null, OptionalField.ignore("ClearingOptionalData", 16), OptionalField.ignore("ClientIDAttr", 4),
          OptionalField.ignore("FrequentTraderID", 6), OptionalField.ignore("Compression", 1),
          OptionalField.reject("FloorDestination", 4), OptionalField.rejectUnless("FloorRoutingInst", 1, "00,45") },
        { OptionalField.ignore("OrderOrigin", 3), OptionalField.ignore("ORS", 1), OptionalField.rejectUnless("PriceType", 1, "32"),
          null, null, null, null, null },
        { OptionalField.ignore("Held", 1), null, null, null, null, null, null, null },
    };

    // Header / fixed fields
    private byte matchingUnit;
    private int sequenceNumber;
    private String clOrdID;   // 20 bytes, Text (NUL-padded)
    private byte side;         // '1'=Buy, '2'=Sell
    private int orderQty;

    // Bitfields
    private int numberOfBitfields;
    private byte[] bitfields;

    // Optional fields
    private String clearingFirm;    // 4 bytes
    private String clearingAccount; // 4 bytes
    private BigDecimal price;       // 8 bytes Binary Price
    private byte ordType;           // '1'=Market, '2'=Limit
    private byte timeInForce;       // '0'=Day, '3'=IOC, etc.
    private String symbol;          // 8 bytes Alphanumeric
    private byte capacity;          // 'A', 'C', 'M', etc.
    private byte routingInst;       // 'R', 'P', 'B', etc. (4-byte field, first byte)
    private String account;         // 16 bytes
    private Instant maturityDate;
    private BigDecimal strikePrice; // 8 bytes Binary Price
    private byte putOrCall;         // '0'=Put, '1'=Call
    private byte openClose;         // 'O', 'C', 'N'

    private String fieldError;      // first unsupported or invalid optional field, if any
    private final Map<String, byte[]> rawFields = new LinkedHashMap<>();
    private String charsetError;

    public NewOrderMessage() {
    }

    public static NewOrderMessage parse(byte[] data) {
        if (data == null || data.length < 36) throw new IllegalArgumentException("Invalid NewOrder message data");

        NewOrderMessage msg = new NewOrderMessage();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        buffer.position(2); // skip StartOfMessage
        @SuppressWarnings("unused")
        int messageLength = buffer.getShort() & 0xFFFF;

        byte messageType = buffer.get();
        if (messageType != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x38, got 0x" + String.format("%02X", messageType));

        msg.matchingUnit = buffer.get();
        msg.sequenceNumber = buffer.getInt();

        // ClOrdID (20 bytes, Text — NUL-padded)
        byte[] clOrdIDBytes = new byte[20];
        buffer.get(clOrdIDBytes);
        msg.clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();
        msg.charsetError = FieldCharset.CLORDID.check("ClOrdID", clOrdIDBytes);

        msg.side = buffer.get();
        msg.orderQty = buffer.getInt();

        msg.numberOfBitfields = buffer.get() & 0xFF;
        msg.bitfields = new byte[msg.numberOfBitfields];
        buffer.get(msg.bitfields);

        msg.parseOptionalFields(buffer);
        if (msg.fieldError == null) msg.fieldError = msg.charsetError;
        return msg;
    }

    private void parseOptionalFields(ByteBuffer buffer) {
        for (int i = 0; i < bitfields.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                if ((bitfields[i] & (1 << bit)) == 0) continue;

                OptionalField field = i < FIELDS.length ? FIELDS[i][bit] : null;
                if (field == null) {
                    fieldError = "Bitfield " + (i + 1) + " bit " + (1 << bit) + " cannot be specified on New Order";
                    return;
                }
                if (buffer.remaining() < field.length()) {
                    fieldError = "Message too short for " + field.name();
                    return;
                }
                byte[] value = new byte[field.length()];
                buffer.get(value);
                rawFields.put(field.name(), value);

                switch (field.handling()) {
                    case READ -> assign(field.name(), value);
                    case IGNORE -> { }
                    case REJECT -> {
                        if (fieldError == null && !isAcceptedDefault(field, value)) {
                            fieldError = field.name() + " is not supported by the simulator";
                        }
                    }
                }
            }
        }
    }

    private static boolean isAcceptedDefault(OptionalField field, byte[] value) {
        if (field.acceptedDefault() == null) return false;
        StringBuilder hex = new StringBuilder();
        for (byte b : value) hex.append(String.format("%02X", b));
        for (String accepted : field.acceptedDefault().split(",")) {
            if (accepted.contentEquals(hex)) return true;
        }
        return false;
    }

    private void assign(String name, byte[] value) {
        FieldCharset charset = switch (name) {
            case "ClearingFirm" -> FieldCharset.ALPHA;
            case "Symbol" -> FieldCharset.ALPHANUMERIC;
            case "ClearingAccount", "Account", "RoutingInst" -> FieldCharset.TEXT;
            default -> null;
        };
        if (charset != null && charsetError == null) charsetError = charset.check(name, value);

        ByteBuffer v = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN);
        switch (name) {
            case "ClearingFirm" -> clearingFirm = text(value);
            case "ClearingAccount" -> clearingAccount = text(value);
            case "Price" -> price = BinaryPrice.fromBytes(value).toPrice();
            case "OrdType" -> ordType = value[0];
            case "TimeInForce" -> timeInForce = value[0];
            case "Symbol" -> symbol = text(value);
            case "Capacity" -> capacity = value[0];
            case "RoutingInst" -> routingInst = value[0];
            case "Account" -> account = text(value);
            case "MaturityDate" -> maturityDate = BoeTime.fromYyyymmdd(v.getInt());
            case "StrikePrice" -> strikePrice = BinaryPrice.fromBytes(value).toPrice();
            case "PutOrCall" -> putOrCall = value[0];
            case "OpenClose" -> openClose = value[0];
            default -> throw new IllegalStateException("No field mapping for " + name);
        }
    }

    private static String text(byte[] value) {
        return new String(value, StandardCharsets.US_ASCII).trim();
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int optionalSize = calculateOptionalFieldsSize();
        int baseSize = 2 + 2 + 1 + 1 + 4 + 20 + 1 + 4 + 1;
        int totalSize = baseSize + numberOfBitfields + optionalSize;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.putShort((short)(totalSize - 2));
        buffer.put(MESSAGE_TYPE);
        buffer.put(matchingUnit);
        buffer.putInt(sequenceNumber);
        buffer.put(toNulPaddedBytes(clOrdID, 20)); // ClOrdID is Text (NUL-padded)
        buffer.put(side);
        buffer.putInt(orderQty);
        buffer.put((byte) numberOfBitfields);
        if (bitfields != null) buffer.put(bitfields);

        writeOptionalFields(buffer);
        return buffer.array();
    }

    private void writeOptionalFields(ByteBuffer buffer) {
        if (bitfields == null || numberOfBitfields == 0) return;

        // Bitfield 1
        if (bitfields.length >= 1) {
            byte bf1 = bitfields[0];
            if ((bf1 & 0x01) != 0) buffer.put(toAlphaPaddedBytes(clearingFirm, 4));
            if ((bf1 & 0x02) != 0) buffer.put(toAlphaPaddedBytes(clearingAccount, 4));
            if ((bf1 & 0x04) != 0) BinaryPrice.fromPrice(price).putInto(buffer);
            if ((bf1 & 0x10) != 0) buffer.put(ordType);
            if ((bf1 & 0x20) != 0) buffer.put(timeInForce);
        }

        // Bitfield 2
        if (bitfields.length >= 2) {
            byte bf2 = bitfields[1];
            if ((bf2 & 0x01) != 0) buffer.put(toAlphaPaddedBytes(symbol, 8));
            if ((bf2 & 0x40) != 0) buffer.put(capacity);
            if ((bf2 & 0x80) != 0) {
                byte[] ri = new byte[4];
                ri[0] = routingInst;
                buffer.put(ri);
            }
        }

        // Bitfield 3
        if (bitfields.length >= 3) {
            byte bf3 = bitfields[2];
            if ((bf3 & 0x01) != 0) buffer.put(toAlphaPaddedBytes(account, 16));
        }

        // Bitfield 4
        if (bitfields.length >= 4) {
            byte bf4 = bitfields[3];
            if ((bf4 & 0x01) != 0) buffer.putInt(BoeTime.toYyyymmdd(maturityDate));
            if ((bf4 & 0x02) != 0) BinaryPrice.fromPrice(strikePrice).putInto(buffer);
            if ((bf4 & 0x04) != 0) buffer.put(putOrCall);
            if ((bf4 & 0x10) != 0) buffer.put(openClose);
        }
    }

    private int calculateOptionalFieldsSize() {
        if (bitfields == null || numberOfBitfields == 0) return 0;
        int size = 0;
        if (bitfields.length >= 1) {
            byte bf1 = bitfields[0];
            if ((bf1 & 0x01) != 0) size += 4;
            if ((bf1 & 0x02) != 0) size += 4;
            if ((bf1 & 0x04) != 0) size += 8;
            if ((bf1 & 0x10) != 0) size += 1;
            if ((bf1 & 0x20) != 0) size += 1;
        }
        if (bitfields.length >= 2) {
            byte bf2 = bitfields[1];
            if ((bf2 & 0x01) != 0) size += 8;
            if ((bf2 & 0x40) != 0) size += 1;
            if ((bf2 & 0x80) != 0) size += 4;
        }
        if (bitfields.length >= 3) {
            byte bf3 = bitfields[2];
            if ((bf3 & 0x01) != 0) size += 16;
        }
        if (bitfields.length >= 4) {
            byte bf4 = bitfields[3];
            if ((bf4 & 0x01) != 0) size += 4;
            if ((bf4 & 0x02) != 0) size += 8;
            if ((bf4 & 0x04) != 0) size += 1;
            if ((bf4 & 0x10) != 0) size += 1;
        }
        return size;
    }

    // Setters update bitfields at the correct spec positions

    public void setPrice(BigDecimal price) {
        this.price = price;
        ensureBitfield(0);
        bitfields[0] |= 0x04;
    }

    public void setOrdType(byte ordType) {
        this.ordType = ordType;
        ensureBitfield(0);
        bitfields[0] |= 0x10;
    }

    public void setTimeInForce(byte timeInForce) {
        this.timeInForce = timeInForce;
        ensureBitfield(0);
        bitfields[0] |= 0x20;
    }

    public void setClearingFirm(String clearingFirm) {
        this.clearingFirm = clearingFirm;
        ensureBitfield(0);
        bitfields[0] |= 0x01;
    }

    public void setClearingAccount(String clearingAccount) {
        this.clearingAccount = clearingAccount;
        ensureBitfield(0);
        bitfields[0] |= 0x02;
    }

    public void setSymbol(String symbol) {
        this.symbol = symbol;
        ensureBitfield(1);
        bitfields[1] |= 0x01;
    }

    public void setCapacity(byte capacity) {
        this.capacity = capacity;
        ensureBitfield(1);
        bitfields[1] |= 0x40;
    }

    public void setRoutingInst(byte routingInst) {
        this.routingInst = routingInst;
        ensureBitfield(1);
        bitfields[1] |= 0x80;
    }

    public void setAccount(String account) {
        this.account = account;
        ensureBitfield(2);
        bitfields[2] |= 0x01;
    }

    public void setMaturityDate(Instant maturityDate) {
        this.maturityDate = maturityDate;
        ensureBitfield(3);
        bitfields[3] |= 0x01;
    }

    public void setStrikePrice(BigDecimal strikePrice) {
        this.strikePrice = strikePrice;
        ensureBitfield(3);
        bitfields[3] |= 0x02;
    }

    public void setPutOrCall(byte putOrCall) {
        this.putOrCall = putOrCall;
        ensureBitfield(3);
        bitfields[3] |= 0x04;
    }

    public void setOpenClose(byte openClose) {
        this.openClose = openClose;
        ensureBitfield(3);
        bitfields[3] |= 0x10;
    }

    private void ensureBitfield(int index) {
        int required = index + 1;
        if (bitfields == null) {
            bitfields = new byte[required];
            numberOfBitfields = required;
        } else if (bitfields.length < required) {
            byte[] grown = new byte[required];
            System.arraycopy(bitfields, 0, grown, 0, bitfields.length);
            bitfields = grown;
            numberOfBitfields = required;
        }
    }

    // NUL-padded for Text fields (ClOrdID)
    private static byte[] toNulPaddedBytes(String str, int length) {
        byte[] result = new byte[length];
        if (str != null && !str.isEmpty()) {
            byte[] src = str.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, result, 0, Math.min(src.length, length));
        }
        return result;
    }

    // NUL-padded for Alphanumeric fields (Symbol, Account, etc.)
    private static byte[] toAlphaPaddedBytes(String str, int length) {
        byte[] result = new byte[length];
        if (str != null && !str.isEmpty()) {
            byte[] src = str.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, result, 0, Math.min(src.length, length));
        }
        return result;
    }

    // Basic setters
    public void setClOrdID(String clOrdID) { this.clOrdID = clOrdID; }
    public void setSide(byte side) { this.side = side; }
    public void setOrderQty(int orderQty) { this.orderQty = orderQty; }
    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    // Getters
    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getClOrdID() { return clOrdID; }
    public byte getSide() { return side; }
    public int getOrderQty() { return orderQty; }
    public BigDecimal getPrice() { return price; }
    public String getSymbol() { return symbol; }
    public byte getCapacity() { return capacity; }
    public byte getRoutingInst() { return routingInst; }
    public String getAccount() { return account; }
    public Instant getMaturityDate() { return maturityDate; }
    public BigDecimal getStrikePrice() { return strikePrice; }
    public byte getPutOrCall() { return putOrCall; }
    public byte getOpenClose() { return openClose; }
    public String getClearingFirm() { return clearingFirm; }
    public String getClearingAccount() { return clearingAccount; }
    public byte getOrdType() { return ordType; }
    public byte getTimeInForce() { return timeInForce; }
    public String getFieldError() { return fieldError; }
    public Map<String, byte[]> getRawFields() { return Map.copyOf(rawFields); }

    @Override
    public String toString() {
        return "NewOrderMessage{" +
                "clOrdID='" + clOrdID + '\'' +
                ", side=" + (side == (byte) '1' ? "Buy" : "Sell") +
                ", orderQty=" + orderQty +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                ", capacity=" + (char) capacity +
                ", seq=" + sequenceNumber +
                '}';
    }
}
