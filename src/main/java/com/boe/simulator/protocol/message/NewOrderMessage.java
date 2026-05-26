package com.boe.simulator.protocol.message;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import com.boe.simulator.protocol.types.BinaryPrice;

/**
 * New Order (0x38) — Member to Cboe.
 * Wire layout per spec v2.11.90 Table 27/28.
 *
 * Bitfield layout (bitfields are 0-indexed here, i.e. bitfields[0]=Bitfield1):
 *   Bitfield 1 (index 0):
 *     bit 0 = ClearingFirm      (4 bytes Alphanumeric)
 *     bit 1 = ClearingAccount   (4 bytes Alphanumeric)
 *     bit 2 = Price             (8 bytes Binary Price)
 *     bit 3 = OrdType           (1 byte Alphanumeric)
 *     bit 4 = TimeInForce       (1 byte Alphanumeric)
 *   Bitfield 2 (index 1):
 *     bit 0 = Symbol            (8 bytes Alphanumeric)
 *     bit 6 = Capacity          (1 byte Alphanumeric)
 *     bit 7 = RoutingInst       (4 bytes Alphanumeric)
 *   Bitfield 3 (index 2):
 *     bit 0 = Account           (16 bytes Alphanumeric)
 *   Bitfield 4 (index 3):
 *     bit 0 = MaturityDate      (4 bytes Binary)
 *     bit 1 = StrikePrice       (8 bytes Binary Price)
 *     bit 2 = PutOrCall         (1 byte Alphanumeric)
 *     bit 4 = OpenClose         (1 byte Alphanumeric)
 *
 * Optional fields appear in order: first bitfield first, lowest bit first.
 * Required optional fields (must always be enabled via bitfield):
 *   Symbol, Price (for limit orders), Capacity.
 */
public final class NewOrderMessage extends BoeProtocolMessage {
    private static final byte MESSAGE_TYPE = 0x38;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

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

        msg.side = buffer.get();
        msg.orderQty = buffer.getInt();

        msg.numberOfBitfields = buffer.get() & 0xFF;
        msg.bitfields = new byte[msg.numberOfBitfields];
        buffer.get(msg.bitfields);

        msg.parseOptionalFields(buffer);
        return msg;
    }

    private void parseOptionalFields(ByteBuffer buffer) {
        if (numberOfBitfields == 0) return;

        // --- Bitfield 1 (index 0) ---
        if (bitfields.length >= 1) {
            byte bf1 = bitfields[0];
            if ((bf1 & 0x01) != 0) clearingFirm = readFixedString(buffer, 4);
            if ((bf1 & 0x02) != 0) clearingAccount = readFixedString(buffer, 4);
            if ((bf1 & 0x04) != 0) price = BinaryPrice.fromBytes(buffer.array(), buffer.position()).toPrice();
            if ((bf1 & 0x04) != 0) buffer.position(buffer.position() + 8);
            if ((bf1 & 0x08) != 0) ordType = buffer.get();
            if ((bf1 & 0x10) != 0) timeInForce = buffer.get();
        }

        // --- Bitfield 2 (index 1) ---
        if (bitfields.length >= 2) {
            byte bf2 = bitfields[1];
            if ((bf2 & 0x01) != 0) symbol = readFixedString(buffer, 8);
            if ((bf2 & 0x40) != 0) capacity = buffer.get();
            if ((bf2 & 0x80) != 0) {
                byte[] riBytes = new byte[4];
                buffer.get(riBytes);
                routingInst = riBytes[0];
            }
        }

        // --- Bitfield 3 (index 2) ---
        if (bitfields.length >= 3) {
            byte bf3 = bitfields[2];
            if ((bf3 & 0x01) != 0) account = readFixedString(buffer, 16);
        }

        // --- Bitfield 4 (index 3) ---
        if (bitfields.length >= 4) {
            byte bf4 = bitfields[3];
            if ((bf4 & 0x01) != 0) {
                maturityDate = Instant.ofEpochMilli((buffer.getInt() & 0xFFFFFFFFL) * 86400_000L);
            }
            if ((bf4 & 0x02) != 0) {
                strikePrice = BinaryPrice.fromBytes(buffer.array(), buffer.position()).toPrice();
                buffer.position(buffer.position() + 8);
            }
            if ((bf4 & 0x04) != 0) putOrCall = buffer.get();
            if ((bf4 & 0x10) != 0) openClose = buffer.get();
        }
    }

    private static String readFixedString(ByteBuffer buffer, int length) {
        byte[] bytes = new byte[length];
        buffer.get(bytes);
        return new String(bytes, StandardCharsets.US_ASCII).trim();
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
            if ((bf1 & 0x08) != 0) buffer.put(ordType);
            if ((bf1 & 0x10) != 0) buffer.put(timeInForce);
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
            if ((bf4 & 0x01) != 0) {
                LocalDate epoch = LocalDate.of(1970, 1, 1);
                LocalDate date = maturityDate.atZone(ZoneId.of("America/New_York")).toLocalDate();
                buffer.putInt((int) java.time.temporal.ChronoUnit.DAYS.between(epoch, date));
            }
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
            if ((bf1 & 0x08) != 0) size += 1;
            if ((bf1 & 0x10) != 0) size += 1;
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
        bitfields[0] |= 0x08;
    }

    public void setTimeInForce(byte timeInForce) {
        this.timeInForce = timeInForce;
        ensureBitfield(0);
        bitfields[0] |= 0x10;
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

    // Space-padded for Alphanumeric fields (Symbol, Account, etc.)
    private static byte[] toAlphaPaddedBytes(String str, int length) {
        byte[] result = new byte[length];
        java.util.Arrays.fill(result, (byte) 0x20);
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
