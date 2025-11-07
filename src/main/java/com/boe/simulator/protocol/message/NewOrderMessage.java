package com.boe.simulator.protocol.message;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

import com.boe.simulator.protocol.types.BinaryPrice;

public class NewOrderMessage {
    private static final byte MESSAGE_TYPE = 0x38;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    // Required fields
    private byte matchingUnit;
    private int sequenceNumber;
    private String clOrdID;           // 20 bytes
    private byte side;                // 1=Buy, 2=Sell
    private int orderQty;

    // Bitfields
    private int numberOfBitfields;
    private byte[] bitfields;
    private BigDecimal price;
    private String symbol;            // 8 bytes
    private byte capacity;            // C, M, F, U, N, B, J
    private byte routingInst;         // B, R, P, etc
    private String account;           // 16 bytes
    private Instant maturityDate;
    private BigDecimal strikePrice;
    private byte putOrCall;           // 0=Put, 1=Call
    private byte openClose;           // O, C, N
    private String clearingFirm;      // 4 bytes
    private String clearingAccount;   // 4 bytes
    private byte ordType;             // 1=Market, 2=Limit
    private byte timeInForce;         // 0=Day, 3=IOC, 4=GTX, etc

    public NewOrderMessage() {
    }

    public static NewOrderMessage parse(byte[] data) {
        if (data == null || data.length < 36) throw new IllegalArgumentException("Invalid NewOrder message data");

        NewOrderMessage msg = new NewOrderMessage();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // Skip StartOfMessage (2 bytes)
        buffer.position(2);

        // MessageLength (2 bytes)
        int messageLength = buffer.getShort() & 0xFFFF;

        // MessageType (1 byte)
        byte messageType = buffer.get();
        if (messageType != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x38, got 0x" + String.format("%02X", messageType));

        // MatchingUnit (1 byte)
        msg.matchingUnit = buffer.get();

        // SequenceNumber (4 bytes)
        msg.sequenceNumber = buffer.getInt();

        // ClOrdID (20 bytes)
        byte[] clOrdIDBytes = new byte[20];
        buffer.get(clOrdIDBytes);
        msg.clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();

        // Side (1 byte)
        msg.side = buffer.get();

        // OrderQty (4 bytes)
        msg.orderQty = buffer.getInt();

        // NumberOfBitfields (1 byte)
        msg.numberOfBitfields = buffer.get() & 0xFF;

        // Bitfields
        msg.bitfields = new byte[msg.numberOfBitfields];
        buffer.get(msg.bitfields);

        // Parse optional fields based on bitfields
        msg.parseOptionalFields(buffer);

        return msg;
    }

    private void parseOptionalFields(ByteBuffer buffer) {
        if (numberOfBitfields == 0) return;

        // Bitfield 1
        if (bitfields.length >= 1) {
            byte bf1 = bitfields[0];

            // Bit 0: ClearingFirm
            if ((bf1 & 0x01) != 0) {
                byte[] cfBytes = new byte[4];
                buffer.get(cfBytes);
                clearingFirm = new String(cfBytes, StandardCharsets.US_ASCII).trim();
            }

            // Bit 1: ClearingAccount
            if ((bf1 & 0x02) != 0) {
                byte[] caBytes = new byte[4];
                buffer.get(caBytes);
                clearingAccount = new String(caBytes, StandardCharsets.US_ASCII).trim();
            }

            // Bit 2: Price
            if ((bf1 & 0x04) != 0) {
                price = BinaryPrice.fromBytes(buffer.array(), buffer.position()).toPrice();
                buffer.position(buffer.position() + 8);
            }

            // Bit 3: OrdType
            if ((bf1 & 0x08) != 0) ordType = buffer.get();


            // Bit 4: TimeInForce
            if ((bf1 & 0x10) != 0) timeInForce = buffer.get();

            // Bit 5: (Reserved)

            // Bit 6: Symbol
            if ((bf1 & 0x40) != 0) {
                byte[] symbolBytes = new byte[8];
                buffer.get(symbolBytes);
                symbol = new String(symbolBytes, StandardCharsets.US_ASCII).trim();
            }

            // Bit 7: Capacity
            if ((bf1 & 0x80) != 0) capacity = buffer.get();

        }

        // Bitfield 2
        if (bitfields.length >= 2) {
            byte bf2 = bitfields[1];

            // Bit 0: Account
            if ((bf2 & 0x01) != 0) {
                byte[] accountBytes = new byte[16];
                buffer.get(accountBytes);
                account = new String(accountBytes, StandardCharsets.US_ASCII).trim();
            }

            // Bit 6: RoutingInst
            if ((bf2 & 0x40) != 0) {
                byte[] riBytes = new byte[4];
                buffer.get(riBytes);
                routingInst = riBytes[0];
            }

            // Bit 7: OpenClose
            if ((bf2 & 0x80) != 0) openClose = buffer.get();

        }

        // Bitfield 3
        if (bitfields.length >= 3) {
            byte bf3 = bitfields[2];

            // Bit 0: MaturityDate
            if ((bf3 & 0x01) != 0) {
                int maturityDateInt = buffer.getInt();
                maturityDate = convertMaturityDate(maturityDateInt);
            }

            // Bit 1: StrikePrice
            if ((bf3 & 0x02) != 0) {
                strikePrice = BinaryPrice.fromBytes(buffer.array(), buffer.position()).toPrice();
                buffer.position(buffer.position() + 8);
            }

            // Bit 2: PutOrCall
            if ((bf3 & 0x04) != 0) putOrCall = buffer.get();
        }
    }

    private Instant convertMaturityDate(int days) {
        LocalDate epoch = LocalDate.of(1970, 1, 1);
        LocalDate maturity = epoch.plusDays(days);
        return maturity.atStartOfDay(ZoneId.of("America/New_York")).toInstant();
    }

    public byte[] toBytes() {
        // Calculate size
        int baseSize = 2 + 2 + 1 + 1 + 4 + 20 + 1 + 4 + 1;
        int optionalSize = calculateOptionalFieldsSize();
        int totalSize = baseSize + numberOfBitfields + optionalSize;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // StartOfMessage
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);

        // MessageLength (excludes StartOfMessage)
        buffer.putShort((short)(totalSize - 2));

        // MessageType
        buffer.put(MESSAGE_TYPE);

        // MatchingUnit
        buffer.put(matchingUnit);

        // SequenceNumber
        buffer.putInt(sequenceNumber);

        // ClOrdID (20 bytes, space-padded)
        buffer.put(toFixedLengthBytes(clOrdID, 20));

        // Side
        buffer.put(side);

        // OrderQty
        buffer.putInt(orderQty);

        // NumberOfBitfields
        buffer.put((byte)numberOfBitfields);

        // Bitfields
        if (bitfields != null) {
            buffer.put(bitfields);
        }

        // Optional fields
        if (numberOfBitfields > 0 && bitfields != null) {
            // Bitfield 1
            if (bitfields.length >= 1) {
                byte bf1 = bitfields[0];

                if ((bf1 & 0x01) != 0) buffer.put(toFixedLengthBytes(clearingFirm, 4));

                if ((bf1 & 0x02) != 0) buffer.put(toFixedLengthBytes(clearingAccount, 4));

                if ((bf1 & 0x04) != 0) buffer.put(BinaryPrice.fromPrice(price).toBytes());

                if ((bf1 & 0x08) != 0) buffer.put(ordType);

                if ((bf1 & 0x10) != 0) buffer.put(timeInForce);

                if ((bf1 & 0x40) != 0) buffer.put(toFixedLengthBytes(symbol, 8));

                if ((bf1 & 0x80) != 0) buffer.put(capacity);
            }

            // Bitfield 2
            if (bitfields.length >= 2) {
                byte bf2 = bitfields[1];

                if ((bf2 & 0x01) != 0) buffer.put(toFixedLengthBytes(account, 16));

                if ((bf2 & 0x40) != 0) buffer.put(routingInst);

                if ((bf2 & 0x80) != 0) buffer.put(openClose);

            }

            // Bitfield 3
            if (bitfields.length >= 3) {
                byte bf3 = bitfields[2];

                if ((bf3 & 0x01) != 0) {
                    LocalDate epoch = LocalDate.of(1970, 1, 1);
                    LocalDate maturity = Instant.ofEpochMilli(maturityDate.toEpochMilli())
                            .atZone(ZoneId.of("America/New_York")).toLocalDate();
                    int days = epoch.until(maturity).getDays();
                    buffer.putInt(days);
                }
                if ((bf3 & 0x02) != 0) buffer.put(BinaryPrice.fromPrice(strikePrice).toBytes());

                if ((bf3 & 0x04) != 0) buffer.put(putOrCall);
            }

        }

        return buffer.array();
    }

    private int calculateOptionalFieldsSize() {
        int size = 0;

        if (numberOfBitfields == 0 || bitfields == null) return size;

        if (price != null) size += 8;

        if (symbol != null) size += 8;

        if (account != null) size += 16;

        if (clearingFirm != null) size += 4;

        if (clearingAccount != null) size += 4;

        if (maturityDate != null) size += 4;

        if (strikePrice != null) size += 8;

        if (routingInst != 0) size += 1;

        if (putOrCall != 0) size += 1;

        if (openClose != 0) size += 1;

        if (ordType != 0) size += 1;

        if (timeInForce != 0) size += 1;

        return size;
    }

    private byte[] toFixedLengthBytes(String str, int length) {
        byte[] result = new byte[length];
        Arrays.fill(result, (byte) 0x20); // Space padding

        if (str != null && !str.isEmpty()) {
            byte[] strBytes = str.getBytes(StandardCharsets.US_ASCII);
            int copyLength = Math.min(strBytes.length, length);
            System.arraycopy(strBytes, 0, result, 0, copyLength);
        }

        return result;
    }

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

    // Setters for building messages
    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    @Override
    public String toString() {
        return "NewOrderMessage{" +
                "clOrdID='" + clOrdID + '\'' +
                ", side=" + (side == 1 ? "Buy" : "Sell") +
                ", orderQty=" + orderQty +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                ", capacity=" + (char)capacity +
                ", seq=" + sequenceNumber +
                '}';
    }
}