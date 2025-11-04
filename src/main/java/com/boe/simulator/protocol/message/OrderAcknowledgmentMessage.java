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
import java.util.Arrays;

public class OrderAcknowledgmentMessage {
    private static final byte MESSAGE_TYPE = 0x25;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    // Header
    private byte matchingUnit;
    private int sequenceNumber;

    // Required fields
    private long transactTime;        // Timestamp in nanoseconds
    private String clOrdID;           // 20 bytes
    private long orderID;             // System-assigned order ID
    private byte side;
    private int orderQty;
    private int leavesQty;
    private byte ordType;
    private byte workingPrice;        // 'Y' or 'N'

    // Optional fields (controlled by bitfields)
    private int numberOfBitfields;
    private byte[] bitfields;

    private BigDecimal price;
    private String symbol;
    private byte capacity;
    private String account;
    private Instant maturityDate;
    private BigDecimal strikePrice;
    private byte putOrCall;
    private byte openClose;
    private String clearingFirm;
    private String clearingAccount;

    public OrderAcknowledgmentMessage() {
    }

    public static OrderAcknowledgmentMessage fromOrder(Order order, byte matchingUnit, int sequenceNumber) {
        OrderAcknowledgmentMessage msg = new OrderAcknowledgmentMessage();

        msg.matchingUnit = matchingUnit;
        msg.sequenceNumber = sequenceNumber;
        msg.transactTime = System.nanoTime();
        msg.clOrdID = order.getClOrdID();
        msg.orderID = order.getOrderID();
        msg.side = order.getSide();
        msg.orderQty = order.getOrderQty();
        msg.leavesQty = order.getLeavesQty();
        msg.ordType = order.getOrdType();
        msg.workingPrice = 'Y';

        // Copy optional fields
        msg.price = order.getPrice();
        msg.symbol = order.getSymbol();
        msg.capacity = order.getCapacity();
        msg.account = order.getAccount();
        msg.maturityDate = order.getMaturityDate();
        msg.strikePrice = order.getStrikePrice();
        msg.putOrCall = order.getPutOrCall();
        msg.openClose = order.getOpenClose();
        msg.clearingFirm = order.getClearingFirm();
        msg.clearingAccount = order.getClearingAccount();
        msg.setupBitfields();

        return msg;
    }

    private void setupBitfields() {
        // For simplicity, we'll use 3 bitfields
        numberOfBitfields = 3;
        bitfields = new byte[3];

        // Bitfield 1
        if (clearingFirm != null && !clearingFirm.isEmpty()) bitfields[0] |= 0x01;
        if (clearingAccount != null && !clearingAccount.isEmpty()) bitfields[0] |= 0x02;
        if (price != null) bitfields[0] |= 0x04;
        if (symbol != null && !symbol.isEmpty()) bitfields[0] |= 0x40;
        bitfields[0] |= (byte) 0x80; // Capacity (always present)

        // Bitfield 2
        if (account != null && !account.isEmpty()) bitfields[1] |= 0x01;
        if (openClose != 0) bitfields[1] |= (byte) 0x80;

        // Bitfield 3
        if (maturityDate != null) bitfields[2] |= 0x01;
        if (strikePrice != null) bitfields[2] |= 0x02;
        if (putOrCall != 0) bitfields[2] |= 0x04;
    }

    public byte[] toBytes() {
        // Calculate size
        int baseSize = 2 + 2 + 1 + 1 + 4 + 8 + 20 + 8 + 1 + 4 + 4 + 1 + 1 + 1;
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

        // Side
        buffer.put(side);

        // OrderQty
        buffer.putInt(orderQty);

        // LeavesQty
        buffer.putInt(leavesQty);

        // OrdType
        buffer.put(ordType);

        // WorkingPrice
        buffer.put(workingPrice);

        // NumberOfBitfields
        buffer.put((byte)numberOfBitfields);

        // Bitfields
        buffer.put(bitfields);

        // Optional fields (in order based on bitfield bits)
        writeOptionalFields(buffer);

        return buffer.array();
    }

    private void writeOptionalFields(ByteBuffer buffer) {
        // Bitfield 1
        if ((bitfields[0] & 0x01) != 0) buffer.put(toFixedLengthBytes(clearingFirm, 4)); // ClearingFirm

        if ((bitfields[0] & 0x02) != 0) buffer.put(toFixedLengthBytes(clearingAccount, 4)); // ClearingAccount

        if ((bitfields[0] & 0x04) != 0) buffer.put(BinaryPrice.fromPrice(price).toBytes()); // Price

        if ((bitfields[0] & 0x40) != 0) buffer.put(toFixedLengthBytes(symbol, 8)); // Symbol

        if ((bitfields[0] & 0x80) != 0) buffer.put(capacity); // Capacity


        // Bitfield 2
        if ((bitfields[1] & 0x01) != 0) buffer.put(toFixedLengthBytes(account, 16)); // Account

        if ((bitfields[1] & 0x80) != 0) buffer.put(openClose); // OpenClose


        // Bitfield 3
        if ((bitfields[2] & 0x01) != 0) buffer.putInt(convertMaturityDateToInt(maturityDate));

        if ((bitfields[2] & 0x02) != 0) buffer.put(BinaryPrice.fromPrice(strikePrice).toBytes()); // StrikePrice

        if ((bitfields[2] & 0x04) != 0) buffer.put(putOrCall);  // PutOrCall

    }

    private int calculateOptionalSize() {
        int size = 0;
        if ((bitfields[0] & 0x01) != 0) size += 4;  // ClearingFirm
        if ((bitfields[0] & 0x02) != 0) size += 4;  // ClearingAccount
        if ((bitfields[0] & 0x04) != 0) size += 8;  // Price
        if ((bitfields[0] & 0x40) != 0) size += 8;  // Symbol
        if ((bitfields[0] & 0x80) != 0) size += 1;  // Capacity
        if ((bitfields[1] & 0x01) != 0) size += 16; // Account
        if ((bitfields[1] & 0x80) != 0) size += 1;  // OpenClose
        if ((bitfields[2] & 0x01) != 0) size += 4;  // MaturityDate
        if ((bitfields[2] & 0x02) != 0) size += 8;  // StrikePrice
        if ((bitfields[2] & 0x04) != 0) size += 1;  // PutOrCall
        return size;
    }

    private int convertMaturityDateToInt(Instant instant) {
        if (instant == null) return 0;
        LocalDate maturity = instant.atZone(ZoneId.of("America/New_York")).toLocalDate();
        LocalDate epoch = LocalDate.of(1970, 1, 1);
        return (int) java.time.temporal.ChronoUnit.DAYS.between(epoch, maturity);
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

    public static OrderAcknowledgmentMessage fromBytes(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        OrderAcknowledgmentMessage msg = new OrderAcknowledgmentMessage();

        buffer.position(10);

        msg.transactTime = buffer.getLong();

        byte[] clOrdIDBytes = new byte[20];
        buffer.get(clOrdIDBytes);
        msg.clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();

        msg.orderID = buffer.getLong();
        msg.side = buffer.get();
        msg.orderQty = buffer.getInt();
        msg.leavesQty = buffer.getInt();
        msg.ordType = buffer.get();
        msg.workingPrice = buffer.get();

        msg.numberOfBitfields = buffer.get();
        msg.bitfields = new byte[msg.numberOfBitfields];
        buffer.get(msg.bitfields);

        if (msg.numberOfBitfields > 0) {
            if ((msg.bitfields[0] & 0x01) != 0) {
                byte[] clearingFirmBytes = new byte[4];
                buffer.get(clearingFirmBytes);
                msg.clearingFirm = new String(clearingFirmBytes, StandardCharsets.US_ASCII).trim();
            }

            if ((msg.bitfields[0] & 0x02) != 0) {
                byte[] clearingAccountBytes = new byte[4];
                buffer.get(clearingAccountBytes);
                msg.clearingAccount = new String(clearingAccountBytes, StandardCharsets.US_ASCII).trim();
            }

            if ((msg.bitfields[0] & 0x04) != 0) {
                byte[] priceBytes = new byte[8];
                buffer.get(priceBytes);
                msg.price = BinaryPrice.fromBytes(priceBytes).toPrice();
            }

            if ((msg.bitfields[0] & 0x40) != 0) {
                byte[] symbolBytes = new byte[8];
                buffer.get(symbolBytes);
                msg.symbol = new String(symbolBytes, StandardCharsets.US_ASCII).trim();
            }

            if ((msg.bitfields[0] & 0x80) != 0) {
                msg.capacity = buffer.get();
            }
        }

        if (msg.numberOfBitfields > 1) {
            if ((msg.bitfields[1] & 0x01) != 0) {
                byte[] accountBytes = new byte[16];
                buffer.get(accountBytes);
                msg.account = new String(accountBytes, StandardCharsets.US_ASCII).trim();
            }

            if ((msg.bitfields[1] & 0x80) != 0) {
                msg.openClose = buffer.get();
            }
        }

        if (msg.numberOfBitfields > 2) {
            if ((msg.bitfields[2] & 0x01) != 0) {
                int maturityDateInt = buffer.getInt();
                msg.maturityDate = Instant.ofEpochMilli(maturityDateInt * 86400000L);
            }

            if ((msg.bitfields[2] & 0x02) != 0) {
                byte[] strikePriceBytes = new byte[8];
                buffer.get(strikePriceBytes);
                msg.strikePrice = BinaryPrice.fromBytes(strikePriceBytes).toPrice();
            }

            if ((msg.bitfields[2] & 0x04) != 0) {
                msg.putOrCall = buffer.get();
            }
        }

        return msg;
    }


    // Getters
    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getClOrdID() { return clOrdID; }
    public long getOrderID() { return orderID; }

    @Override
    public String toString() {
        return "OrderAcknowledgment{" +
                "clOrdID='" + clOrdID + '\'' +
                ", orderID=" + orderID +
                ", side=" + (side == 1 ? "Buy" : "Sell") +
                ", qty=" + orderQty +
                ", leavesQty=" + leavesQty +
                ", symbol='" + symbol + '\'' +
                ", price=" + price +
                '}';
    }
}