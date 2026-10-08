package com.boe.simulator.server.order;

import com.boe.simulator.protocol.message.CancelOrderMessage;
import com.boe.simulator.protocol.message.CancelRejectedMessage;
import com.boe.simulator.protocol.message.ModifyOrderMessage;
import com.boe.simulator.protocol.message.UserModifyRejectedMessage;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.message.OrderRejectedMessage;
import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.protocol.types.TimeInForce;
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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
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
    //   bf1 bits: 2=Price, 4=OrdType
    //   bf2 bits: 0=Symbol, 6=Capacity
    //   bf4 bits: 0=MaturityDate, 1=StrikePrice, 2=PutOrCall, 4=OpenClose
    private NewOrderMessage buildNewOrderMessage(String clOrdID, byte side, int orderQty, String symbol, byte ordType, BigDecimal price, byte capacity, byte openClose, String maturityDate, BigDecimal strikePrice, byte putOrCall) {
        byte bf1 = 0, bf2 = 0, bf3 = 0, bf4 = 0;

        if (price != null)                            bf1 |= 0x04;
        if (ordType != 0)                             bf1 |= 0x10;
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
        if ((bf1 & 0x10) != 0) optionalSize += 1;
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
        if ((bf1 & 0x10) != 0) buffer.put(ordType);
        if ((bf2 & 0x01) != 0) {
            byte[] symbolBytes = new byte[8];
            if (symbol != null) {
                byte[] srcBytes = symbol.getBytes(StandardCharsets.US_ASCII);
                System.arraycopy(srcBytes, 0, symbolBytes, 0, Math.min(srcBytes.length, 8));
            }
            buffer.put(symbolBytes);
        }
        if ((bf2 & 0x40) != 0) buffer.put(capacity);
        if ((bf4 & 0x01) != 0) buffer.putInt(Integer.parseInt(maturityDate)); // Date: YYYYMMDD as integer
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
        assertEquals(CancelRejectedMessage.REASON_ORDER_NOT_FOUND, response.getRejectReason());
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
        assertEquals(CancelRejectedMessage.REASON_ORDER_NOT_FOUND, response.getRejectReason(), "Another user's order is reported as not found");
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
        assertEquals(CancelRejectedMessage.REASON_TOO_LATE_TO_CANCEL, response.getRejectReason());
        assertEquals(0, orderManager.getTotalOrdersCancelled(), "Total cancelled orders should be 0");
    }

    @Test
    void processNewOrder_whenMaxOpenOrdersReached_isRejectedWithReasonO() {
        orderManager.setMaxOpenOrdersPerSession(2);
        when(orderValidator.validateNewOrder(any(NewOrderMessage.class)))
                .thenReturn(OrderValidator.ValidationResult.valid());
        when(matchingEngine.processOrder(any(Order.class))).thenReturn(Collections.emptyList());

        assertTrue(orderManager.processNewOrder(createNewOrderMessage("OPEN1", '1', 100.0, 10, "AAPL"), clientSession).isAcknowledged());
        assertTrue(orderManager.processNewOrder(createNewOrderMessage("OPEN2", '1', 100.0, 10, "AAPL"), clientSession).isAcknowledged());

        OrderManager.OrderResponse response =
                orderManager.processNewOrder(createNewOrderMessage("OPEN3", '1', 100.0, 10, "AAPL"), clientSession);

        assertFalse(response.isAcknowledged());
        assertEquals(OrderRejectedMessage.REASON_MAX_OPEN_ORDERS_EXCEEDED, response.getRejectReason());
        verify(matchingEngine, times(2)).processOrder(any(Order.class));
    }

    @Test
    void processNewOrder_afterCancelBelowMaxOpenOrders_isAcceptedAgain() {
        orderManager.setMaxOpenOrdersPerSession(1);
        when(orderValidator.validateNewOrder(any(NewOrderMessage.class)))
                .thenReturn(OrderValidator.ValidationResult.valid());
        when(matchingEngine.processOrder(any(Order.class))).thenReturn(Collections.emptyList());

        orderManager.processNewOrder(createNewOrderMessage("OPEN1", '1', 100.0, 10, "AAPL"), clientSession);
        assertFalse(orderManager.processNewOrder(createNewOrderMessage("OPEN2", '1', 100.0, 10, "AAPL"), clientSession).isAcknowledged());

        orderManager.processCancelOrder("OPEN1", "testUser");

        assertTrue(orderManager.processNewOrder(createNewOrderMessage("OPEN3", '1', 100.0, 10, "AAPL"), clientSession).isAcknowledged());
    }

    @Test
    void processNewOrder_restOrdersDoNotCountTowardsBoeLimit() {
        orderManager.setMaxOpenOrdersPerSession(1);
        when(orderValidator.validateNewOrder(any(NewOrderMessage.class)))
                .thenReturn(OrderValidator.ValidationResult.valid());
        when(matchingEngine.processOrder(any(Order.class))).thenReturn(Collections.emptyList());

        assertTrue(orderManager.processNewOrder(createNewOrderMessage("REST1", '1', 100.0, 10, "AAPL"), "testUser").isAcknowledged());
        assertTrue(orderManager.processNewOrder(createNewOrderMessage("REST2", '1', 100.0, 10, "AAPL"), "testUser").isAcknowledged());

        assertTrue(orderManager.processNewOrder(createNewOrderMessage("BOE1", '1', 100.0, 10, "AAPL"), clientSession).isAcknowledged());
    }

    @Test
    void processNewOrder_whenAnUnsupportedOptionalFieldIsSet_isRejectedWithZ() {
        NewOrderMessage message = NewOrderMessage.parse(new byte[]{
                (byte) 0xBA, (byte) 0xBA, 0x2A, 0x00, 0x38, 0x00, 0x01, 0x00, 0x00, 0x00,
                'M', 'Q', '1', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                '1', 0x0A, 0x00, 0x00, 0x00,
                0x01, 0x40, 0x05, 0x00, 0x00, 0x00, 0x00, 0x00   // Bitfield 1 = MinQty (4 bytes)
        });

        OrderManager.OrderResponse response = orderManager.processNewOrder(message, clientSession);

        assertFalse(response.isAcknowledged());
        assertEquals(OrderRejectedMessage.REASON_UNFORESEEN, response.getRejectReason());
        assertEquals("MinQty is not supported by the simulator", response.getRejectText());
        verifyNoInteractions(orderValidator);
        verify(matchingEngine, never()).processOrder(any(Order.class));
    }

    // ========== Mass Cancel ==========

    private void placeOrder(String clOrdID, String symbol, String clearingFirm) {
        lenient().when(orderValidator.validateNewOrder(any(NewOrderMessage.class)))
                .thenReturn(OrderValidator.ValidationResult.valid());
        lenient().when(matchingEngine.processOrder(any(Order.class))).thenReturn(Collections.emptyList());
        NewOrderMessage message = createNewOrderMessage(clOrdID, '1', 100.0, 10, symbol);
        message.setClearingFirm(clearingFirm);
        assertTrue(orderManager.processNewOrder(message, clientSession).isAcknowledged());
    }

    private static CancelOrderMessage massCancel(String inst, String clearingFirm, String riskRoot, String massCancelId) {
        CancelOrderMessage message = new CancelOrderMessage("");
        if (clearingFirm != null) message.setClearingFirm(clearingFirm);
        if (riskRoot != null) message.setRiskRoot(riskRoot);
        if (massCancelId != null) message.setMassCancelId(massCancelId);
        if (inst != null) message.setMassCancelInst(inst);
        message.setSendTime(1L);
        return CancelOrderMessage.parse(message.toBytes());
    }

    private List<String> cancelledIds(OrderManager.CancelResponse response) {
        return response.getMassCancelledOrders().stream().map(Order::getClOrdID).sorted().toList();
    }

    @Test
    void massCancel_firmFilterA_cancelsAllTheUsersOrders() {
        placeOrder("A1", "AAPL", "TEST");
        placeOrder("A2", "MSFT", "OTHR");

        OrderManager.CancelResponse response = orderManager.processCancelOrder(massCancel("A", null, null, null), clientSession);

        assertTrue(response.isMassCancelled());
        assertEquals(List.of("A1", "A2"), cancelledIds(response));
        assertEquals('M', response.getAckStyle(), "Acknowledgement Style defaults to M");
        assertEquals(2, orderManager.getTotalOrdersCancelled());
    }

    @Test
    void massCancel_firmFilterF_onlyCancelsThatClearingFirm() {
        placeOrder("F1", "AAPL", "TEST");
        placeOrder("F2", "AAPL", "OTHR");

        OrderManager.CancelResponse response = orderManager.processCancelOrder(massCancel("F", "TEST", null, null), clientSession);

        assertEquals(List.of("F1"), cancelledIds(response));
        assertTrue(orderManager.findByClOrdID("F2").isPresent());
    }

    @Test
    void massCancel_riskRoot_isAppliedEvenWithFirmFilterA() {
        placeOrder("R1", "MSFT", "TEST");
        placeOrder("R2", "AAPL", "TEST");

        OrderManager.CancelResponse response = orderManager.processCancelOrder(massCancel("A", null, "MSFT", null), clientSession);

        assertEquals(List.of("R1"), cancelledIds(response));
    }

    @Test
    void massCancel_specTable38FiltersWithoutLockout_cancelsOnlyFirmAndRoot() {
        placeOrder("T1", "MSFT", "TEST");
        placeOrder("T2", "AAPL", "TEST");
        placeOrder("T3", "MSFT", "OTHR");

        OrderManager.CancelResponse response = orderManager.processCancelOrder(massCancel("FSNB", "TEST", "MSFT", "ABC123"), clientSession);

        assertEquals(List.of("T1"), cancelledIds(response));
        assertEquals('S', response.getAckStyle());
        assertEquals("ABC123", response.getMassCancelId());
        assertEquals(1, response.getMassCancelCount());
    }

    @Test
    void massCancel_complexOnlyFilter_cancelsNothing() {
        placeOrder("C1", "AAPL", "TEST");

        OrderManager.CancelResponse response = orderManager.processCancelOrder(massCancel("AMNC", null, null, null), clientSession);

        assertTrue(response.isMassCancelled());
        assertEquals(0, response.getMassCancelCount());
        assertTrue(orderManager.findByClOrdID("C1").isPresent());
    }

    @Test
    void massCancel_doesNotTouchOtherUsersOrders() {
        placeOrder("U1", "AAPL", "TEST");
        ClientSession other = mock(ClientSession.class);
        lenient().when(other.isAuthenticated()).thenReturn(true);
        lenient().when(other.getUsername()).thenReturn("otherUser");
        lenient().when(other.getSessionSubID()).thenReturn("otherSession");

        assertEquals(0, orderManager.processCancelOrder(massCancel("A", null, null, null), other).getMassCancelCount());
        assertTrue(orderManager.findByClOrdID("U1").isPresent());
    }

    @Test
    void invalidMassCancels_areRejectedWithZ() {
        assertMassCancelRejected(massCancel(null, null, null, null), "MassCancelInst is required for a mass cancel");
        assertMassCancelRejected(massCancel("X", null, null, null), "Invalid Clearing Firm Filter 'X' in MassCancelInst");
        assertMassCancelRejected(massCancel("F", null, null, null), "ClearingFirm is required with Clearing Firm Filter F");
        assertMassCancelRejected(massCancel("AA", null, null, "ID1"), "Acknowledgement Style A is only valid on Purge Orders");
        assertMassCancelRejected(massCancel("AI", null, null, "ID1"), "Acknowledgement Style I is only valid on Purge Orders");
        assertMassCancelRejected(massCancel("AQ", null, null, null), "Invalid Acknowledgement Style 'Q' in MassCancelInst");
        assertMassCancelRejected(massCancel("AS", null, null, null), "MassCancelID is required with Acknowledgement Style S");
        assertMassCancelRejected(massCancel("AB", null, null, null), "MassCancelID is required with Acknowledgement Style B");
        assertMassCancelRejected(massCancel("AM", null, null, "ID1"), "MassCancelID must be blank with Acknowledgement Style M");
        assertMassCancelRejected(massCancel("AS", null, null, "ID1 "), "MassCancelID must not end in a space");
        assertMassCancelRejected(massCancel("AMX", null, null, null), "Invalid Lockout Instruction 'X' in MassCancelInst");
        assertMassCancelRejected(massCancel("AML", null, null, null), "Lockout is not supported by the simulator");
        assertMassCancelRejected(massCancel("AMNX", null, null, null), "Invalid Instrument Type Filter 'X' in MassCancelInst");
        assertMassCancelRejected(massCancel("AMNBX", null, null, null), "Invalid GTC Order Filter 'X' in MassCancelInst");
    }

    private void assertMassCancelRejected(CancelOrderMessage message, String text) {
        OrderManager.CancelResponse response = orderManager.processCancelOrder(message, clientSession);
        assertTrue(response.isRejected(), text);
        assertEquals(CancelRejectedMessage.REASON_UNFORESEEN, response.getRejectReason());
        assertEquals(text, response.getRejectText());
    }

    @Test
    void cancelWithAStructureError_isRejectedWithZ() {
        placeOrder("S1", "AAPL", "TEST");
        CancelOrderMessage withoutSendTime = CancelOrderMessage.parse(new CancelOrderMessage("S1").toBytes());

        OrderManager.CancelResponse response = orderManager.processCancelOrder(withoutSendTime, clientSession);

        assertTrue(response.isRejected());
        assertEquals(CancelRejectedMessage.REASON_UNFORESEEN, response.getRejectReason());
        assertEquals("SendTime is required on Cancel Order", response.getRejectText());
        assertTrue(orderManager.findByClOrdID("S1").isPresent());
    }

    // ========== Modify Order ==========

    private static ModifyOrderMessage modify(String clOrdID, String origClOrdID, int qty, String price, byte ordType, byte cancelOrigOnReject) {
        byte bf1 = 0x04;
        int size = 4;
        if (price != null) { bf1 |= 0x08; size += 8; }
        if (ordType != 0) { bf1 |= 0x10; size += 1; }
        if (cancelOrigOnReject != 0) { bf1 |= 0x20; size += 1; }
        ByteBuffer buf = ByteBuffer.allocate(52 + size).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) (50 + size)).put((byte) 0x3A).put((byte) 0).putInt(0);
        buf.put(java.util.Arrays.copyOf(clOrdID.getBytes(StandardCharsets.US_ASCII), 20));
        buf.put(java.util.Arrays.copyOf(origClOrdID.getBytes(StandardCharsets.US_ASCII), 20));
        buf.put((byte) 1).put(bf1).putInt(qty);
        if (price != null) buf.put(BinaryPrice.fromPrice(new BigDecimal(price)).toBytes());
        if (ordType != 0) buf.put(ordType);
        if (cancelOrigOnReject != 0) buf.put(cancelOrigOnReject);
        return ModifyOrderMessage.parse(buf.array());
    }

    private static ModifyOrderMessage modify(String clOrdID, String origClOrdID, int qty, String price) {
        return modify(clOrdID, origClOrdID, qty, price, (byte) 0, (byte) 0);
    }

    private void engineAppliesModifications() {
        lenient().when(matchingEngine.modifyOrder(any(Order.class), anyString(), any(), any(), anyInt())).thenAnswer(inv -> {
            Order o = inv.getArgument(0);
            int qty = inv.getArgument(4);
            o.modify(inv.getArgument(1), inv.getArgument(2), inv.getArgument(3), qty, o.getLeavesQty() + qty - o.getEffectiveOrderQty());
            return List.of();
        });
    }

    @Test
    void chainedModifies_leaveNoStaleClOrdIDInTheCache() {
        engineAppliesModifications();
        placeOrder("A", "AAPL", "TEST");

        assertTrue(orderManager.processModifyOrder(modify("B", "A", 10, "100.00"), clientSession).isModified());
        assertTrue(orderManager.processModifyOrder(modify("C", "B", 10, "99.00"), clientSession).isModified());

        assertTrue(orderManager.findByClOrdID("A").isEmpty());
        assertTrue(orderManager.findByClOrdID("B").isEmpty(), "The intermediate ClOrdID must not stay live");
        assertEquals(CancelRejectedMessage.REASON_ORDER_NOT_FOUND, orderManager.processCancelOrder("B", "testUser").getRejectReason());
        assertTrue(orderManager.processCancelOrder("C", "testUser").isCancelled());
    }

    @Test
    void modifyToTheClOrdIDOfAnotherLiveOrder_isRejectedWithD() {
        placeOrder("A", "AAPL", "TEST");
        placeOrder("OTHER", "AAPL", "TEST");

        OrderManager.ModifyResponse response = orderManager.processModifyOrder(modify("OTHER", "A", 10, "100.00"), clientSession);

        assertTrue(response.isRejected());
        assertEquals(UserModifyRejectedMessage.REASON_DUPLICATE_CLORDID, response.getRejectReason());
        assertEquals("Duplicate ClOrdID: OTHER", response.getRejectText());
        verify(matchingEngine, never()).modifyOrder(any(Order.class), anyString(), any(), any(), anyInt());
    }

    @Test
    void reusingTheClOrdID_isOnlyAllowedForAQuantityReduction() {
        engineAppliesModifications();
        placeOrder("A", "AAPL", "TEST");

        assertTrue(orderManager.processModifyOrder(modify("A", "A", 8, "100.00"), clientSession).isModified());

        OrderManager.ModifyResponse repriced = orderManager.processModifyOrder(modify("A", "A", 6, "99.00"), clientSession);
        assertEquals(UserModifyRejectedMessage.REASON_DUPLICATE_CLORDID, repriced.getRejectReason());
        assertEquals("ClOrdID can only be reused when the Modify only reduces OrderQty", repriced.getRejectText());
        assertTrue(orderManager.findByClOrdID("A").isPresent());
    }

    @Test
    void modifyOfAnotherUsersOrder_isReportedAsNotFound() {
        placeOrder("A", "AAPL", "TEST");
        ClientSession other = mock(ClientSession.class);
        lenient().when(other.getUsername()).thenReturn("otherUser");
        lenient().when(other.getSessionSubID()).thenReturn("otherSession");

        OrderManager.ModifyResponse response = orderManager.processModifyOrder(modify("B", "A", 10, "100.00"), other);

        assertEquals(UserModifyRejectedMessage.REASON_NOT_FOUND, response.getRejectReason());
        assertEquals("Order not found or already terminated", response.getRejectText());
    }

    @Test
    void invalidModifies_areRejectedWithZ_withoutReachingTheEngine() {
        placeOrder("A", "AAPL", "TEST");

        assertModifyRejected(modify("B", "A", 10, null), "Price is required in Modify Order for limit orders");
        assertModifyRejected(modify("B", "A", 10, "100.00", (byte) '3', (byte) 0), "Stop and Stop Limit orders are not supported by the simulator");
        assertModifyRejected(modify("B", "A", 10, "100.00", (byte) '9', (byte) 0), "Invalid OrdType: 0x39");
        assertModifyRejected(modify("B", "A", 1_000_000, "100.00"), "OrderQty must be between 0 and 999,999");
        assertModifyRejected(modify("", "A", 10, "100.00"), "ClOrdID is required in Modify Order");
        verify(matchingEngine, never()).modifyOrder(any(Order.class), anyString(), any(), any(), anyInt());
    }

    private void assertModifyRejected(ModifyOrderMessage message, String text) {
        OrderManager.ModifyResponse response = orderManager.processModifyOrder(message, clientSession);
        assertTrue(response.isRejected(), text);
        assertEquals(UserModifyRejectedMessage.REASON_UNFORESEEN, response.getRejectReason());
        assertEquals(text, response.getRejectText());
        assertFalse(response.cancelledOriginal());
    }

    @Test
    void marketModify_doesNotNeedAPrice() {
        engineAppliesModifications();
        placeOrder("A", "AAPL", "TEST");

        assertTrue(orderManager.processModifyOrder(modify("B", "A", 10, null, (byte) '1', (byte) 0), clientSession).isModified());
    }

    @Test
    void rejectedModifyWithCancelOrigOnReject_cancelsTheOriginalOrder() {
        when(matchingEngine.cancelOrder(any(Order.class))).thenReturn(true);
        placeOrder("A", "AAPL", "TEST");

        OrderManager.ModifyResponse response =
                orderManager.processModifyOrder(modify("B", "A", 10, null, (byte) 0, (byte) 'Y'), clientSession);

        assertTrue(response.isRejected());
        assertTrue(response.cancelledOriginal());
        assertEquals("A", response.getOrder().getClOrdID());
        assertEquals(OrderState.CANCELLED, response.getOrder().getState());
        assertTrue(orderManager.findByClOrdID("A").isEmpty());
    }

    @Test
    void rejectedModifyWithoutCancelOrigOnReject_leavesTheOriginalOrder() {
        placeOrder("A", "AAPL", "TEST");

        OrderManager.ModifyResponse response =
                orderManager.processModifyOrder(modify("B", "A", 10, null, (byte) 0, (byte) 'N'), clientSession);

        assertFalse(response.cancelledOriginal());
        assertTrue(orderManager.findByClOrdID("A").isPresent());
        verify(matchingEngine, never()).cancelOrder(any(Order.class));
    }

    @Test
    void modify1296_isRejected() {
        engineAppliesModifications();
        placeOrder("M0", "AAPL", "TEST");
        for (int i = 1; i <= OrderManager.MAX_MODIFICATIONS_PER_ORDER; i++) {
            assertTrue(orderManager.processModifyOrder(modify("M" + i, "M" + (i - 1), 10, "100.00"), clientSession).isModified(), "Modify " + i);
        }

        OrderManager.ModifyResponse response = orderManager.processModifyOrder(modify("MX", "M1295", 10, "100.00"), clientSession);

        assertEquals(UserModifyRejectedMessage.REASON_UNFORESEEN, response.getRejectReason());
        assertEquals("Maximum of 1,295 modifications reached; the order can only be cancelled", response.getRejectText());
        assertTrue(orderManager.findByClOrdID("M1295").isPresent(), "The order can still be cancelled");
    }

    @Test
    void processNewOrder_timeInForceTheSimulatorCannotHonor_isRejectedWithZ() {
        String[][] cases = {{"1", "GTC"}, {"2", "AT_OPEN"}, {"6", "GTD"}, {"7", "AT_CLOSE"}};
        for (String[] c : cases) {
            NewOrderMessage message = createNewOrderMessage("TIF" + c[0], 1, 100.0, 10, "AAPL");
            message.setTimeInForce((byte) c[0].charAt(0));

            OrderManager.OrderResponse response = orderManager.processNewOrder(message, clientSession);

            assertTrue(response.isRejected());
            assertEquals(OrderRejectedMessage.REASON_UNFORESEEN, response.getRejectReason());
            assertEquals("TimeInForce " + c[1] + " is not supported by the simulator", response.getRejectText());
        }
        verify(matchingEngine, never()).processOrder(any(Order.class));
    }

    @Test
    void processNewOrder_unknownTimeInForce_isRejectedWithZ() {
        NewOrderMessage message = createNewOrderMessage("TIF5", 1, 100.0, 10, "AAPL");
        message.setTimeInForce((byte) '5');

        OrderManager.OrderResponse response = orderManager.processNewOrder(message, clientSession);

        assertEquals(OrderRejectedMessage.REASON_UNFORESEEN, response.getRejectReason());
        assertEquals("Invalid TimeInForce '5'", response.getRejectText());
    }

    @Test
    void processNewOrder_iocCancelledByTheEngine_isAcknowledgedAndLeavesTheActiveOrders() {
        NewOrderMessage message = createNewOrderMessage("IOC1", 1, 100.0, 10, "AAPL");
        message.setTimeInForce((byte) '3');
        when(orderValidator.validateNewOrder(any(NewOrderMessage.class))).thenReturn(OrderValidator.ValidationResult.valid());
        when(matchingEngine.processOrder(any(Order.class))).thenAnswer(inv -> {
            inv.<Order>getArgument(0).cancel();
            return Collections.emptyList();
        });

        OrderManager.OrderResponse response = orderManager.processNewOrder(message, clientSession);

        assertTrue(response.isAcknowledged());
        assertEquals(TimeInForce.IOC, response.getOrder().getTimeInForce());
        assertEquals(OrderState.CANCELLED, response.getOrder().getState());
        assertTrue(orderManager.findByClOrdID("IOC1").isEmpty());
        assertEquals(1, orderManager.getTotalOrdersCancelled());
    }
}
