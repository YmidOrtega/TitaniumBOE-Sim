package com.boe.simulator.protocol.types;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

class BinaryPriceTest {

    @Test
    void fromPrice_shouldConvertBigDecimalCorrectly() {
        BigDecimal price = new BigDecimal("123.4567");
        BinaryPrice binaryPrice = BinaryPrice.fromPrice(price);
        assertEquals(price.setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromPrice_shouldHandleRoundingCorrectly() {
        BigDecimal price = new BigDecimal("123.45678");
        BinaryPrice binaryPrice = BinaryPrice.fromPrice(price);
        assertEquals(new BigDecimal("123.4568").setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromPrice_shouldThrowExceptionForNullPrice() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> BinaryPrice.fromPrice(null),
                "Expected fromPrice(null) to throw IllegalArgumentException, but it didn't");
        assertTrue(thrown.getMessage().contains("Price cannot be null"));
    }

    @Test
    void fromPrice_shouldHandleZeroPrice() {
        BigDecimal price = BigDecimal.ZERO;
        BinaryPrice binaryPrice = BinaryPrice.fromPrice(price);
        assertEquals(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromPrice_shouldHandleNegativePrice() {
        BigDecimal price = new BigDecimal("-10.5000");
        BinaryPrice binaryPrice = BinaryPrice.fromPrice(price);
        assertEquals(new BigDecimal("-10.5000").setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromRaw_shouldCreateBinaryPriceWithGivenRawValue() {
        long rawValue = 987654321L;
        BinaryPrice binaryPrice = BinaryPrice.fromRaw(rawValue);
        assertEquals(BigDecimal.valueOf(rawValue).divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void toPrice_shouldConvertRawValueToBigDecimalCorrectly() {
        BinaryPrice binaryPrice = BinaryPrice.fromRaw(1234567L);
        BigDecimal expectedPrice = new BigDecimal("123.4567").setScale(4, RoundingMode.HALF_UP);
        assertEquals(expectedPrice, binaryPrice.toPrice());
    }

    @Test
    void toPrice_shouldHandleZeroRawValue() {
        BinaryPrice binaryPrice = BinaryPrice.fromRaw(0L);
        BigDecimal expectedPrice = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        assertEquals(expectedPrice, binaryPrice.toPrice());
    }

    @Test
    void toPrice_shouldHandleNegativeRawValue() {
        BinaryPrice binaryPrice = BinaryPrice.fromRaw(-105000L);
        BigDecimal expectedPrice = new BigDecimal("-10.5000").setScale(4, RoundingMode.HALF_UP);
        assertEquals(expectedPrice, binaryPrice.toPrice());
    }

    @Test
    void toBytes_shouldReturnCorrectByteArray() {
        long rawValue = 1234567L;
        BinaryPrice binaryPrice = BinaryPrice.fromRaw(rawValue);
        byte[] bytes = binaryPrice.toBytes();

        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(rawValue);

        assertArrayEquals(buffer.array(), bytes);
    }

    @Test
    void fromBytes_shouldReconstructBinaryPriceCorrectly() {
        long rawValue = 7654321L;
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(rawValue);
        byte[] bytes = buffer.array();

        BinaryPrice binaryPrice = BinaryPrice.fromBytes(bytes, 0);
        assertEquals(BigDecimal.valueOf(rawValue).divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromBytes_shouldHandleOffsetCorrectly() {
        long rawValue = 1122334455667788L;
        ByteBuffer buffer = ByteBuffer.allocate(16); // 8 bytes for prefix, 8 for rawValue
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(0L); // Prefix bytes
        buffer.putLong(rawValue);
        byte[] bytes = buffer.array();

        BinaryPrice binaryPrice = BinaryPrice.fromBytes(bytes, 8);
        assertEquals(BigDecimal.valueOf(rawValue).divide(BigDecimal.valueOf(10000), 4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromBytes_shouldThrowExceptionForShortByteArray() {
        byte[] shortBytes = new byte[7];
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> BinaryPrice.fromBytes(shortBytes, 0),
                "Expected fromBytes to throw IllegalArgumentException for short array, but it didn't");
        assertTrue(thrown.getMessage().contains("Byte array is too short"));
    }

    @Test
    void fromBytes_shouldThrowExceptionForNullByteArray() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> BinaryPrice.fromBytes(null, 0),
                "Expected fromBytes to throw IllegalArgumentException for null array, but it didn't");
        assertTrue(thrown.getMessage().contains("Byte array is too short")); // The check is for length < offset + 8
    }

    @Test
    void roundTrip_fromPriceToBytesFromBytesToPrice_shouldPreserveValue() {
        BigDecimal originalPrice = new BigDecimal("987.654321"); // Will be rounded to 987.6543
        BinaryPrice binaryPrice = BinaryPrice.fromPrice(originalPrice);
        byte[] bytes = binaryPrice.toBytes();
        BinaryPrice reconstructedBinaryPrice = BinaryPrice.fromBytes(bytes, 0);
        BigDecimal finalPrice = reconstructedBinaryPrice.toPrice();

        BigDecimal expectedPrice = new BigDecimal("987.6543").setScale(4, RoundingMode.HALF_UP);
        assertEquals(expectedPrice, finalPrice);
    }

    // Helper to access rawValue for testing purposes, normally this would be private
    // For testing, we can use reflection or make it package-private if necessary
    // For now, I'll assume direct access for simplicity in the test, but in a real scenario
    // I'd consider adding a getter or making it package-private for testing.
    // Since it's a private field, I'll add a temporary getter for testing.
    // Or, more correctly, I should test the public API only.
    // The current tests rely on direct access to rawValue, which is not ideal.
    // Let's adjust the tests to only use public API.
    // The current tests are fine because fromRaw and toPrice are public and cover the rawValue.
    // The direct access to binaryPrice.rawValue in the tests is for verification, which is acceptable in unit tests.

}
