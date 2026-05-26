package com.boe.simulator.server.order;

import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.message.OrderRejectedMessage;
import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.session.ClientSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderManagerTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderValidator orderValidator;
    @Mock
    private MatchingEngine matchingEngine;
    @Mock
    private ClientSession clientSession;

    private OrderManager orderManager;

    @BeforeEach
    void setUp() {
        orderManager = new OrderManager(orderRepository, orderValidator, matchingEngine);
        lenient().when(clientSession.isAuthenticated()).thenReturn(true);
        lenient().when(clientSession.getUsername()).thenReturn("testUser");
        lenient().when(clientSession.getSessionSubID()).thenReturn("testSession");
    }

    // Helper: builds a spec-compliant NewOrder wire message and parses it.
    // Bitfield layout per spec v2.11.90 Table 28:
    //   bf1 bits: 2=Price, 3=OrdType
    //   bf2 bits: 0=Symbol, 6=Capacity
    //   bf4 bits: 0=MaturityDate, 1=StrikePrice, 2=PutOrCall, 4=OpenClose
    private NewOrderMessage buildNewOrderMessage(String clOrdID, byte side, int orderQty, String symbol, byte ordType, BigDecimal price, byte capacity, byte openClose, String maturityDate, BigDecimal strikePrice, byte putOrCall) {
        byte bf1 = 0, bf2 = 0, bf3 = 0, bf4 = 0;

        if (price != null)                            bf1 |= 0x04;
        if (ordType != 0)                             bf1 |= 0x08;
        if (symbol != null && !symbol.isEmpty())      bf2 |= 0x01;
        if (capacity != 0)                            bf2 |= 0x40;
        if (maturityDate != null && !maturityDate.isEmpty()) bf4 |= 0x01;
        if (strikePrice != null)                      bf4 |= 0x02;
        if (putOrCall != 0)                           bf4 |= 0x04;
        if (openClose != 0)                           bf4 |= 0x10;

        int numberOfBitfields = 0;
        if (bf4 != 0)      numberOfBitfields = 4;
        else if (bf3 != 0) numberOfBitfields = 3;
        else if (bf2 != 0) numberOfBitfields = 2;
        else if (bf1 != 0) numberOfBitfields = 1;

        byte[] bitfields = new byte[numberOfBitfields];
        if (numberOfBitfields > 0) bitfields[0] = bf1;
        if (numberOfBitfields > 1) bitfields[1] = bf2;
        if (numberOfBitfields > 2) bitfields[2] = bf3;
        if (numberOfBitfields > 3) bitfields[3] = bf4;

        int baseSize = 2 + 2 + 1 + 1 + 4 + 20 + 1 + 4 + 1;
        int optionalSize = 0;
        if ((bf1 & 0x04) != 0) optionalSize += 8;
        if ((bf1 & 0x08) != 0) optionalSize += 1;
        if ((bf2 & 0x01) != 0) optionalSize += 8;
        if ((bf2 & 0x40) != 0) optionalSize += 1;
        if ((bf4 & 0x01) != 0) optionalSize += 4;
        if ((bf4 & 0x02) != 0) optionalSize += 8;
        if ((bf4 & 0x04) != 0) optionalSize += 1;
        if ((bf4 & 0x10) != 0) optionalSize += 1;

        int totalSize = baseSize + numberOfBitfields + optionalSize;
        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 0xBA);
        buffer.put((byte) 0xBA);
        buffer.putShort((short)(totalSize - 2));
        buffer.put((byte) 0x38);
        buffer.put((byte) 0);
        buffer.putInt(0);

        // ClOrdID: NUL-padded (Text field per spec)
        byte[] clOrdIDBytes = new byte[20];
        if (clOrdID != null) {
            byte[] srcBytes = clOrdID.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(srcBytes, 0, clOrdIDBytes, 0, Math.min(srcBytes.length, 20));
        }
        buffer.put(clOrdIDBytes);
        buffer.put(side);
        buffer.putInt(orderQty);
        buffer.put((byte) numberOfBitfields);
        if (bitfields.length > 0) buffer.put(bitfields);

        if ((bf1 & 0x04) != 0) buffer.put(BinaryPrice.fromPrice(price).toBytes());
        if ((bf1 & 0x08) != 0) buffer.put(ordType);
        if ((bf2 & 0x01) != 0) {
            byte[] symbolBytes = new byte[8];
            Arrays.fill(symbolBytes, (byte) 0x20);
            if (symbol != null) {
                byte[] srcBytes = symbol.getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(srcBytes, 0, symbolBytes, 0, Math.min(srcBytes.length, 8));
            }
            buffer.put(symbolBytes);
        }
        if ((bf2 & 0x40) != 0) buffer.put(capacity);
        if ((bf4 & 0x01) != 0) {
            LocalDate epoch = LocalDate.of(1970, 1, 1);
            LocalDate matDate = LocalDate.parse(maturityDate, java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd"));
            buffer.putInt((int) java.time.temporal.ChronoUnit.DAYS.between(epoch, matDate));
        }
        if ((bf4 & 0x02) != 0) buffer.put(BinaryPrice.fromPrice(strikePrice).toBytes());
        if ((bf4 & 0x04) != 0) buffer.put(putOrCall);
        if ((bf4 & 0x10) != 0) buffer.put(openClose);

        return NewOrderMessage.parse(buffer.array());
    }

    private NewOrderMessage createNewOrderMessage(String clOrdID, int side, double price, int quantity, String symbol) {
        // Default values for other fields not directly controlled by this helper
        byte ordType = (byte) '2'; // Limit order
        byte capacity = (byte) 'A';
        byte openClose = (byte) 0;
        String maturityDate = null;
        BigDecimal strikePrice = null;
        byte putOrCall = (byte) 0;

        return buildNewOrderMessage(clOrdID, (byte) side, quantity, symbol, ordType, new BigDecimal(price), capacity, openClose, maturityDate, strikePrice, putOrCall);
    }

    @Test
    void processNewOrder_whenValid_isAcknowledged() {
        // Arrange
        NewOrderMessage message = createNewOrderMessage("CLORD1", 1, 100.0, 10, "AAPL");
        when(orderValidator.validateNewOrder(any(NewOrderMessage.class)))
                .thenReturn(OrderValidator.ValidationResult.valid());
        when(matchingEngine.processOrder(any(Order.class)))
                .thenReturn(Collections.emptyList());

        // Act
        OrderManager.OrderResponse response = orderManager.processNewOrder(message, clientSession);

        // Assert
        assertTrue(response.isAcknowledged(), "Order should be acknowledged");
        assertNotNull(response.getOrder(), "Acknowledged order should not be null");
        assertEquals(1, orderManager.getTotalOrdersAccepted(), "Total accepted orders should be 1");
        assertEquals(0, orderManager.getTotalOrdersRejected(), "Total rejected orders should be 0");

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(matchingEngine).processOrder(orderCaptor.capture());
        verify(orderRepository).saveAsync(orderCaptor.getValue());

        Order capturedOrder = orderCaptor.getValue();
        assertEquals(message.getClOrdID(), capturedOrder.getClOrdID());
        assertEquals(message.getSymbol(), capturedOrder.getSymbol());
        assertEquals(OrderState.LIVE, capturedOrder.getState());
        assertTrue(orderManager.findByClOrdID(message.getClOrdID()).isPresent());
    }

    @Test
    void processNewOrder_whenValidationFails_isRejected() {
        // Arrange
        NewOrderMessage message = createNewOrderMessage("CLORD2", 1, 100.0, 10, "GOOG");
        String errorMessage = "Invalid quantity";
        when(orderValidator.validateNewOrder(any(NewOrderMessage.class)))
                .thenReturn(OrderValidator.ValidationResult.invalid(errorMessage));

        // Act
        OrderManager.OrderResponse response = orderManager.processNewOrder(message, clientSession);

        // Assert
        assertTrue(response.isRejected(), "Order should be rejected");
        assertEquals(OrderRejectedMessage.REASON_MISSING_REQUIRED_FIELD, response.getRejectReason());
        assertEquals(errorMessage, response.getRejectText());
        assertEquals(0, orderManager.getTotalOrdersAccepted(), "Total accepted orders should be 0");
        assertEquals(1, orderManager.getTotalOrdersRejected(), "Total rejected orders should be 1");

        verify(matchingEngine, never()).processOrder(any(Order.class));
        verify(orderRepository, never()).saveAsync(any(Order.class));
        assertTrue(orderManager.findByClOrdID(message.getClOrdID()).isEmpty());
    }

    @Test
    void processNewOrder_whenDuplicateClOrdID_isRejected() {
        // Arrange
        NewOrderMessage message = createNewOrderMessage("CLORD3", 1, 100.0, 10, "MSFT");
        when(orderValidator.validateNewOrder(any(NewOrderMessage.class)))
                .thenReturn(OrderValidator.ValidationResult.valid());
        when(matchingEngine.processOrder(any(Order.class)))
                .thenReturn(Collections.emptyList());

        // First order accepted
        orderManager.processNewOrder(message, clientSession);

        // Act - send same order again
        OrderManager.OrderResponse response = orderManager.processNewOrder(message, clientSession);

        // Assert
        assertTrue(response.isRejected(), "Duplicate order should be rejected");
        assertEquals(OrderRejectedMessage.REASON_DUPLICATE_CLORDID, response.getRejectReason());
        assertTrue(response.getRejectText().contains("Duplicate ClOrdID"));
        assertEquals(1, orderManager.getTotalOrdersAccepted(), "Total accepted orders should be 1");
        assertEquals(1, orderManager.getTotalOrdersRejected(), "Total rejected orders should be 1");

        // Verify matchingEngine.processOrder was called only once for the first order
        verify(matchingEngine, times(1)).processOrder(any(Order.class));
        // Verify orderRepository.saveAsync was called only once for the first order
        verify(orderRepository, times(1)).saveAsync(any(Order.class));
    }

    @Test
    void processCancelOrder_whenOrderExistsAndCancellable_isCancelled() {
        // Arrange
        NewOrderMessage newOrderMsg = createNewOrderMessage("CLORD4", 1, 100.0, 10, "AMZN");
        when(orderValidator.validateNewOrder(any(NewOrderMessage.class)))
                .thenReturn(OrderValidator.ValidationResult.valid());
        when(matchingEngine.processOrder(any(Order.class)))
                .thenReturn(Collections.emptyList());
        orderManager.processNewOrder(newOrderMsg, clientSession);

        Order orderToCancel = orderManager.findByClOrdID("CLORD4").orElseThrow();
        when(matchingEngine.cancelOrder(any(Order.class))).thenReturn(true);

        // Act
        OrderManager.CancelResponse response = orderManager.processCancelOrder("CLORD4", "testUser");

        // Assert
        assertTrue(response.isCancelled(), "Order should be cancelled");
        assertEquals(1, orderManager.getTotalOrdersCancelled(), "Total cancelled orders should be 1");
        assertEquals(OrderState.CANCELLED, orderToCancel.getState());
        verify(matchingEngine).cancelOrder(orderToCancel);
        verify(orderRepository, times(2)).saveAsync(orderToCancel); // Once for new, once for cancel
        assertTrue(orderManager.findByClOrdID("CLORD4").isEmpty());
    }

    @Test
    void processCancelOrder_whenOrderNotFound_isRejected() {
        // Arrange
        // No order is placed

        // Act
        OrderManager.CancelResponse response = orderManager.processCancelOrder("NONEXISTENT", "testUser");

        // Assert
        assertTrue(response.isRejected(), "Cancel should be rejected");
        assertTrue(response.getRejectText().contains("Order not found"));
        assertEquals(0, orderManager.getTotalOrdersCancelled(), "Total cancelled orders should be 0");
        verify(matchingEngine, never()).cancelOrder(any(Order.class));
    }

    @Test
    void processCancelOrder_whenUnauthorizedUser_isRejected() {
        // Arrange
        NewOrderMessage newOrderMsg = createNewOrderMessage("CLORD5", 1, 100.0, 10, "NFLX");
        when(orderValidator.validateNewOrder(any(NewOrderMessage.class)))
                .thenReturn(OrderValidator.ValidationResult.valid());
        when(matchingEngine.processOrder(any(Order.class)))
                .thenReturn(Collections.emptyList());
        orderManager.processNewOrder(newOrderMsg, clientSession);

        // Act
        OrderManager.CancelResponse response = orderManager.processCancelOrder("CLORD5", "anotherUser");

        // Assert
        assertTrue(response.isRejected(), "Cancel should be rejected");
        assertTrue(response.getRejectText().contains("Unauthorized"));
        assertEquals(0, orderManager.getTotalOrdersCancelled(), "Total cancelled orders should be 0");
        verify(matchingEngine, never()).cancelOrder(any(Order.class));
    }

    @Test
    void processCancelOrder_whenOrderNotCancellable_isRejected() {
        // Arrange - First create and fill an order
        NewOrderMessage newOrderMsg = createNewOrderMessage("CLORD6", 1, 100.0, 10, "TSLA");
        when(orderValidator.validateNewOrder(any(NewOrderMessage.class)))
                .thenReturn(OrderValidator.ValidationResult.valid());
        when(matchingEngine.processOrder(any(Order.class)))
                .thenAnswer(invocation -> {
                    Order order = invocation.getArgument(0);
                    // Simulate immediate fill
                    order.fill(10, BigDecimal.valueOf(100.0));
                    return Collections.emptyList();
                });
        
        // Create the order
        orderManager.processNewOrder(newOrderMsg, clientSession);

        // Act - Try to cancel the filled order
        OrderManager.CancelResponse response = orderManager.processCancelOrder("CLORD6", "testUser");

        // Assert
        assertTrue(response.isRejected(), "Cancel should be rejected");
        assertTrue(response.getRejectText().contains("not cancellable"), 
            "Reject text was: " + response.getRejectText());
        assertEquals(0, orderManager.getTotalOrdersCancelled(), "Total cancelled orders should be 0");
    }
}
