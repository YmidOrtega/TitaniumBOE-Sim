package com.boe.simulator.server.order;

import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.types.BinaryPrice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderValidatorTest {

    private OrderValidator orderValidator;

    @Mock
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderValidator = new OrderValidator();
    }

    // Helper method to create NewOrderMessage with reflection for testing invalid data
    private NewOrderMessage createMessageWithReflection(String clOrdID, byte side, int orderQty, String symbol, 
                                                        byte ordType, BigDecimal price, byte capacity, byte openClose,
                                                        String maturityDate, BigDecimal strikePrice, byte putOrCall) {
        NewOrderMessage message = new NewOrderMessage();
        try {
            setField(message, "clOrdID", clOrdID);
            setField(message, "side", side);
            setField(message, "orderQty", orderQty);
            setField(message, "symbol", symbol);
            setField(message, "ordType", ordType);
            setField(message, "price", price);
            setField(message, "capacity", capacity);
            setField(message, "openClose", openClose);
            if (maturityDate != null) {
                // Convert string to Instant for maturityDate
                LocalDate matDate = LocalDate.parse(maturityDate, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
                Instant matInstant = matDate.atStartOfDay(java.time.ZoneId.of("America/New_York")).toInstant();
                setField(message, "maturityDate", matInstant);
            }
            setField(message, "strikePrice", strikePrice);
            setField(message, "putOrCall", putOrCall);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create test message", e);
        }
        return message;
    }

    private void setField(Object target, String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    // Helper method to construct NewOrderMessage via byte array for testing
    private NewOrderMessage buildNewOrderMessage(String clOrdID, byte side, int orderQty, String symbol, byte ordType, BigDecimal price, byte capacity, byte openClose, String maturityDate, BigDecimal strikePrice, byte putOrCall) {
        // This is a simplified builder for testing purposes.
        // In a real scenario, you'd have a more robust way to build these messages.
        // For now, we'll manually construct the byte array.

        // Determine bitfields based on provided optional fields
        byte bf1 = 0; // Price, OrdType, Symbol, Capacity
        byte bf2 = 0; // OpenClose
        byte bf3 = 0; // MaturityDate, StrikePrice, PutOrCall

        if (price != null) bf1 |= 0x04;
        if (ordType != 0) bf1 |= 0x08;
        if (symbol != null && !symbol.isEmpty()) bf1 |= 0x40;
        if (capacity != 0) bf1 |= 0x80;

        if (openClose != 0) bf2 |= 0x80;

        if (maturityDate != null && !maturityDate.isEmpty()) bf3 |= 0x01;
        if (strikePrice != null) bf3 |= 0x02;
        if (putOrCall != 0) bf3 |= 0x04;

        int numberOfBitfields = 0;
        if (bf1 != 0) numberOfBitfields++;
        if (bf2 != 0) numberOfBitfields++;
        if (bf3 != 0) numberOfBitfields++;

        byte[] bitfields = new byte[numberOfBitfields];
        int bfIndex = 0;
        if (bf1 != 0) bitfields[bfIndex++] = bf1;
        if (bf2 != 0) bitfields[bfIndex++] = bf2;
        if (bf3 != 0) bitfields[bfIndex++] = bf3;

        // Calculate size
        int baseSize = 2 + 2 + 1 + 1 + 4 + 20 + 1 + 4 + 1; // SOM + Length + Type + MatchingUnit + SeqNum + ClOrdID + Side + Qty + NumBitfields
        int optionalSize = 0;

        if ((bf1 & 0x04) != 0) optionalSize += 8; // Price
        if ((bf1 & 0x08) != 0) optionalSize += 1; // OrdType
        if ((bf1 & 0x40) != 0) optionalSize += 8; // Symbol
        if ((bf1 & 0x80) != 0) optionalSize += 1; // Capacity

        if ((bf2 & 0x80) != 0) optionalSize += 1; // OpenClose

        if ((bf3 & 0x01) != 0) optionalSize += 4; // MaturityDate
        if ((bf3 & 0x02) != 0) optionalSize += 8; // StrikePrice
        if ((bf3 & 0x04) != 0) optionalSize += 1; // PutOrCall

        int totalSize = baseSize + numberOfBitfields + optionalSize;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // StartOfMessage
        buffer.put((byte) 0xBA);
        buffer.put((byte) 0xBA);

        // MessageLength (excludes StartOfMessage)
        buffer.putShort((short)(totalSize - 2));

        // MessageType
        buffer.put((byte) 0x38); // NEW_ORDER

        // MatchingUnit (default to 0 for tests)
        buffer.put((byte) 0);

        // SequenceNumber (default to 0 for tests)
        buffer.putInt(0);

        // ClOrdID (20 bytes, space-padded - NO truncate for validation testing)
        byte[] clOrdIDBytes = new byte[20];
        Arrays.fill(clOrdIDBytes, (byte) 0x20);
        if (clOrdID != null) {
            byte[] srcBytes = clOrdID.getBytes(StandardCharsets.US_ASCII);
            // Don't truncate - let validation catch the error
            int copyLen = srcBytes.length;
            if (copyLen <= 20) {
                System.arraycopy(srcBytes, 0, clOrdIDBytes, 0, copyLen);
            } else {
                // For testing purposes, just copy what fits and the validator will see the full string
                System.arraycopy(srcBytes, 0, clOrdIDBytes, 0, 20);
            }
        }
        buffer.put(clOrdIDBytes);

        // Side
        buffer.put(side);

        // OrderQty
        buffer.putInt(orderQty);

        // NumberOfBitfields
        buffer.put((byte)numberOfBitfields);

        // Bitfields
        if (bitfields.length > 0) {
            buffer.put(bitfields);
        }

        // Optional fields
        if ((bf1 & 0x04) != 0) buffer.put(BinaryPrice.fromPrice(price).toBytes());
        if ((bf1 & 0x08) != 0) buffer.put(ordType);
        if ((bf1 & 0x40) != 0) {
            byte[] symbolBytes = new byte[8];
            Arrays.fill(symbolBytes, (byte) 0x20);
            if (symbol != null) {
                byte[] srcBytes = symbol.getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(srcBytes, 0, symbolBytes, 0, Math.min(srcBytes.length, 8));
            }
            buffer.put(symbolBytes);
        }
        if ((bf1 & 0x80) != 0) buffer.put(capacity);

        if ((bf2 & 0x80) != 0) buffer.put(openClose);

        if ((bf3 & 0x01) != 0) {
            LocalDate epoch = LocalDate.of(1970, 1, 1);
            LocalDate matDate = LocalDate.parse(maturityDate, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            int days = (int) java.time.temporal.ChronoUnit.DAYS.between(epoch, matDate);
            buffer.putInt(days);
        }
        if ((bf3 & 0x02) != 0) buffer.put(BinaryPrice.fromPrice(strikePrice).toBytes());
        if ((bf3 & 0x04) != 0) buffer.put(putOrCall);

        return NewOrderMessage.parse(buffer.array());
    }

    // --- validateNewOrder tests ---

    @Test
    void validateNewOrder_whenAllFieldsAreValid_returnsValidResult() {
        NewOrderMessage message = buildNewOrderMessage("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertTrue(result.isValid(), "Should be valid for a correctly formed message");
        assertNull(result.getErrorMessage());
    }

    @Test
    void validateNewOrder_whenClOrdIDIsNull_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage(null, (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("ClOrdID cannot be empty"));
    }

    @Test
    void validateNewOrder_whenClOrdIDIsEmpty_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage("", (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("ClOrdID cannot be empty"));
    }

    @Test
    void validateNewOrder_whenClOrdIDIsTooLong_returnsInvalidResult() {
        NewOrderMessage message = createMessageWithReflection("LONGLONGLONGLONGLONGCLORDID", (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("ClOrdID exceeds maximum length"));
    }

    @Test
    void validateNewOrder_whenClOrdIDHasInvalidChars_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage("CLORD;ID", (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("ClOrdID contains invalid characters"));
    }

    @Test
    void validateNewOrder_whenSideIsInvalid_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage("VALIDCLORDID1", (byte) 0, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid Side"));
    }

    @Test
    void validateNewOrder_whenOrderQtyIsTooLow_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage("VALIDCLORDID1", (byte) 1, 0, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("OrderQty must be at least 1"));
    }

    @Test
    void validateNewOrder_whenOrderQtyIsTooHigh_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage("VALIDCLORDID1", (byte) 1, 1000000, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("OrderQty exceeds system limit"));
    }

    @Test
    void validateNewOrder_whenSymbolIsNull_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage("VALIDCLORDID1", (byte) 1, 10, null, (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Symbol is required"));
    }

    @Test
    void validateNewOrder_whenSymbolIsEmpty_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage("VALIDCLORDID1", (byte) 1, 10, "", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Symbol is required"));
    }

    @Test
    void validateNewOrder_whenSymbolIsTooLong_returnsInvalidResult() {
        NewOrderMessage message = createMessageWithReflection("VALIDCLORDID1", (byte) 1, 10, "LONGSYMBOL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Symbol exceeds maximum length"));
    }

    @Test
    void validateNewOrder_whenSymbolHasInvalidChars_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage("VALIDCLORDID1", (byte) 1, 10, "APPL!", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Symbol must contain only uppercase letters and numbers"));
    }

    @Test
    void validateNewOrder_whenLimitOrderPriceIsNull_returnsInvalidResult() {
        NewOrderMessage message = createMessageWithReflection("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) 2, null, (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Price is required for limit orders"));
    }

    @Test
    void validateNewOrder_whenMarketOrderPriceIsNull_returnsValidResult() {
        NewOrderMessage message = buildNewOrderMessage("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) '1', null, (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertTrue(result.isValid(), "Market order should not require a price");
    }

    @Test
    void validateNewOrder_whenPriceIsNegative_returnsInvalidResult() {
        NewOrderMessage message = createMessageWithReflection("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) 2, new BigDecimal("-10.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Price cannot be negative"));
    }

    @Test
    void validateNewOrder_whenPriceExceedsMax_returnsInvalidResult() {
        NewOrderMessage message = createMessageWithReflection("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) 2, new BigDecimal("1000000.00"), (byte) 'C', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Price exceeds maximum value"));
    }

    @Test
    void validateNewOrder_whenCapacityIsInvalid_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'X', (byte) 0, null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid Capacity"));
    }

    @Test
    void validateNewOrder_whenOpenCloseIsInvalid_returnsInvalidResult() {
        NewOrderMessage message = buildNewOrderMessage("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 'Z', null, null, (byte) 0);
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid OpenClose"));
    }

    @Test
    void validateNewOrder_whenOptionSymbologyIsIncomplete_returnsInvalidResult() {
        NewOrderMessage message = createMessageWithReflection("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, "20251231", null, (byte) '0');
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("StrikePrice is required for option orders"));
    }

    @Test
    void validateNewOrder_whenOptionSymbologyIsComplete_returnsValidResult() {
        NewOrderMessage message = createMessageWithReflection("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, "20251231", new BigDecimal("160.00"), (byte) '0');
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertTrue(result.isValid());
    }

    @Test
    void validateNewOrder_whenStrikePriceIsNegative_returnsInvalidResult() {
        NewOrderMessage message = createMessageWithReflection("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, "20251231", new BigDecimal("-5.00"), (byte) '0');
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid StrikePrice: Price cannot be negative"));
    }

    @Test
    void validateNewOrder_whenPutOrCallIsInvalid_returnsInvalidResult() {
        NewOrderMessage message = createMessageWithReflection("VALIDCLORDID1", (byte) 1, 10, "AAPL", (byte) '2', new BigDecimal("150.00"), (byte) 'C', (byte) 0, "20251231", new BigDecimal("160.00"), (byte) '2');
        OrderValidator.ValidationResult result = orderValidator.validateNewOrder(message);
        assertFalse(result.isValid());
        assertTrue(result.getErrorMessage().contains("Invalid PutOrCall"));
    }

    // --- isDuplicateClOrdID tests ---

    @Test
    void isDuplicateClOrdID_whenClOrdIDExists_returnsTrue() {
        String clOrdID = "EXISTING_CLORDID";
        when(orderRepository.existsByClOrdID(clOrdID)).thenReturn(true);
        assertTrue(orderValidator.isDuplicateClOrdID(clOrdID, orderRepository));
    }

    @Test
    void isDuplicateClOrdID_whenClOrdIDDoesNotExist_returnsFalse() {
        String clOrdID = "NON_EXISTENT_CLORDID";
        when(orderRepository.existsByClOrdID(clOrdID)).thenReturn(false);
        assertFalse(orderValidator.isDuplicateClOrdID(clOrdID, orderRepository));
    }
}
