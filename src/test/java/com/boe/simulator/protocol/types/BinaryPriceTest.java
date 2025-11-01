package com.boe.simulator.protocol.types;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;

class BinaryPriceTest {

    @Test
    void fromPrice_shouldConvertBigDecimalToBinaryPrice() {
        // Arrange
        BigDecimal price = new BigDecimal("123.4567");

        // Act
        BinaryPrice binaryPrice = BinaryPrice.fromPrice(price);

        // Assert
        assertEquals(price.setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromPrice_shouldRoundBigDecimalToFourDecimalPlaces() {
        // Arrange
        BigDecimal price = new BigDecimal("123.45678");
        BigDecimal expectedPrice = new BigDecimal("123.4568");

        // Act
        BinaryPrice binaryPrice = BinaryPrice.fromPrice(price);

        // Assert
        assertEquals(expectedPrice.setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromPrice_shouldThrowException_whenPriceIsNull() {
        // Arrange, Act & Assert
        Throwable thrown = assertThrows(IllegalArgumentException.class, () -> BinaryPrice.fromPrice(null));
        assertEquals(IllegalArgumentException.class, thrown.getClass());
    }

    @Test
    void fromPrice_shouldHandleZeroCorrectly() {
        // Arrange
        BigDecimal price = BigDecimal.ZERO;

        // Act
        BinaryPrice binaryPrice = BinaryPrice.fromPrice(price);

        // Assert
        assertEquals(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromPrice_shouldHandleNegativePriceCorrectly() {
        // Arrange
        BigDecimal price = new BigDecimal("-10.5000");

        // Act
        BinaryPrice binaryPrice = BinaryPrice.fromPrice(price);

        // Assert
        assertEquals(price.setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromRaw_shouldCreateBinaryPriceFromRawValue() {
        // Arrange
        long rawValue = 987654321L;
        BigDecimal expectedPrice = new BigDecimal("98765.4321");

        // Act
        BinaryPrice binaryPrice = BinaryPrice.fromRaw(rawValue);

        // Assert
        assertEquals(expectedPrice.setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void toPrice_shouldConvertRawValueToBigDecimal() {
        // Arrange
        BinaryPrice binaryPrice = BinaryPrice.fromRaw(1234567L);
        BigDecimal expectedPrice = new BigDecimal("123.4567");

        // Act
        BigDecimal actualPrice = binaryPrice.toPrice();

        // Assert
        assertEquals(expectedPrice.setScale(4, RoundingMode.HALF_UP), actualPrice);
    }

    @Test
    void toPrice_shouldHandleZeroRawValueCorrectly() {
        // Arrange
        BinaryPrice binaryPrice = BinaryPrice.fromRaw(0L);

        // Act
        BigDecimal price = binaryPrice.toPrice();

        // Assert
        assertEquals(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP), price);
    }

    @Test
    void toPrice_shouldHandleNegativeRawValueCorrectly() {
        // Arrange
        BinaryPrice binaryPrice = BinaryPrice.fromRaw(-105000L);
        BigDecimal expectedPrice = new BigDecimal("-10.5000");

        // Act
        BigDecimal actualPrice = binaryPrice.toPrice();

        // Assert
        assertEquals(expectedPrice.setScale(4, RoundingMode.HALF_UP), actualPrice);
    }

    @Test
    void toBytes_shouldReturnCorrectLittleEndianByteArray() {
        // Arrange
        long rawValue = 1234567L;
        BinaryPrice binaryPrice = BinaryPrice.fromRaw(rawValue);
        ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(rawValue);
        byte[] expectedBytes = buffer.array();

        // Act
        byte[] actualBytes = binaryPrice.toBytes();

        // Assert
        assertArrayEquals(expectedBytes, actualBytes);
    }

    @Test
    void fromBytes_shouldReconstructBinaryPriceFromByteArray() {
        // Arrange
        long rawValue = 7654321L;
        ByteBuffer buffer = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(rawValue);
        byte[] bytes = buffer.array();
        BigDecimal expectedPrice = new BigDecimal("765.4321");

        // Act
        BinaryPrice binaryPrice = BinaryPrice.fromBytes(bytes, 0);

        // Assert
        assertEquals(expectedPrice.setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromBytes_shouldHandleOffsetCorrectly() {
        // Arrange
        long rawValue = 1122334455668L;
        ByteBuffer buffer = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putLong(0L); // some other data
        buffer.putLong(rawValue);
        byte[] bytes = buffer.array();
        BigDecimal expectedPrice = new BigDecimal("112233445.5668"); // Rounded

        // Act
        BinaryPrice binaryPrice = BinaryPrice.fromBytes(bytes, 8);

        // Assert
        assertEquals(expectedPrice.setScale(4, RoundingMode.HALF_UP), binaryPrice.toPrice());
    }

    @Test
    void fromBytes_shouldThrowException_whenArrayIsTooShort() {
        // Arrange
        byte[] shortBytes = new byte[7];

        // Act & Assert
        Throwable thrown = assertThrows(IllegalArgumentException.class, () -> BinaryPrice.fromBytes(shortBytes, 0));
        assertEquals(IllegalArgumentException.class, thrown.getClass());
    }

    @Test
    void fromBytes_shouldThrowException_whenArrayIsNull() {
        // Arrange, Act & Assert
        Throwable thrown = assertThrows(IllegalArgumentException.class, () -> BinaryPrice.fromBytes(null, 0));
        assertEquals(IllegalArgumentException.class, thrown.getClass());
    }

    @Test
    void roundTripConversion_fromPriceToBytesAndBack_shouldPreserveValue() {
        // Arrange
        BigDecimal originalPrice = new BigDecimal("987.654321");
        BigDecimal expectedPrice = new BigDecimal("987.6543");

        // Act
        BinaryPrice binaryPrice = BinaryPrice.fromPrice(originalPrice);
        byte[] bytes = binaryPrice.toBytes();
        BinaryPrice reconstructedBinaryPrice = BinaryPrice.fromBytes(bytes, 0);
        BigDecimal finalPrice = reconstructedBinaryPrice.toPrice();

        // Assert
        assertEquals(expectedPrice.setScale(4, RoundingMode.HALF_UP), finalPrice);
    }
}