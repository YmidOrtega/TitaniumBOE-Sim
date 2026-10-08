package com.boe.simulator.server.order;

import com.boe.simulator.protocol.message.CancelOrderMessage;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.message.PurgeOrdersMessage;
import com.boe.simulator.protocol.message.ResetRiskMessage;
import com.boe.simulator.protocol.message.RiskResetAcknowledgmentMessage;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.TradeRepository;
import com.boe.simulator.server.session.ClientSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OrderManagerRiskTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private ClientSession session;

    private OrderManager orderManager;
    private int buyPrice = 1;

    @BeforeEach
    void setUp() {
        orderManager = new OrderManager(orderRepository, new OrderValidator(), new MatchingEngine(orderRepository, tradeRepository));
        lenient().when(session.getUsername()).thenReturn("u1");
        lenient().when(session.getSessionSubID()).thenReturn("S1");
        lenient().when(session.isAuthenticated()).thenReturn(true);
    }

    // Appends one optional field; it must be the last one in wire order
    private static byte[] withField(byte[] bytes, int bitfield, int bit, byte[] value) {
        int numberOfBitfields = bytes[35] & 0xFF;
        int fieldsStart = 36 + numberOfBitfields;
        byte[] bitfields = java.util.Arrays.copyOf(java.util.Arrays.copyOfRange(bytes, 36, fieldsStart), Math.max(bitfield, numberOfBitfields));
        bitfields[bitfield - 1] |= (byte) bit;
        ByteBuffer out = ByteBuffer.allocate(36 + bitfields.length + bytes.length - fieldsStart + value.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(bytes, 0, 35).put((byte) bitfields.length).put(bitfields).put(bytes, fieldsStart, bytes.length - fieldsStart).put(value);
        out.putShort(2, (short) (out.capacity() - 2));
        return out.array();
    }

    private NewOrderMessage order(String clOrdID, String symbol, String riskReset, int customGroupId) {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(clOrdID);
        msg.setSide((byte) '1');
        msg.setOrderQty(1);
        msg.setSymbol(symbol);
        msg.setPrice(new BigDecimal(buyPrice++));
        msg.setOrdType((byte) '2');
        msg.setCapacity((byte) 'C');
        msg.setClearingFirm("TEST");
        byte[] bytes = msg.toBytes();
        if (riskReset != null) bytes = withField(bytes, 4, 0x08, java.util.Arrays.copyOf(riskReset.getBytes(StandardCharsets.US_ASCII), 8));
        if (customGroupId != 0) bytes = withField(bytes, 7, 0x02, new byte[]{(byte) customGroupId, (byte) (customGroupId >> 8)});
        return NewOrderMessage.parse(bytes);
    }

    private OrderManager.OrderResponse submit(String clOrdID, String symbol) {
        return orderManager.processNewOrder(order(clOrdID, symbol, null, 0), session);
    }

    private CancelOrderMessage massCancel(String inst, String riskRoot) {
        CancelOrderMessage msg = new CancelOrderMessage("");
        msg.setClearingFirm("TEST");
        msg.setMassCancelInst(inst);
        if (riskRoot != null) msg.setRiskRoot(riskRoot);
        msg.setSendTime(1L);
        return CancelOrderMessage.parse(msg.toBytes());
    }

    private ResetRiskMessage resetRisk(String reset, String clearingFirm, String riskRoot, int group) {
        ResetRiskMessage msg = new ResetRiskMessage();
        msg.setRiskStatusID("R1");
        msg.setRiskReset(reset);
        msg.setClearingFirm(clearingFirm);
        msg.setRiskRoot(riskRoot);
        msg.setCustomGroupId(group);
        return ResetRiskMessage.parse(msg.toBytes());
    }

    @Test
    void efidLockout_blocksNewOrdersUntilAResetRisk() {
        assertTrue(submit("A1", "AAPL").isAcknowledged());
        assertTrue(orderManager.processCancelOrder(massCancel("FML", null), session).isMassCancelled());
        assertTrue(orderManager.findByClOrdID("A1").isEmpty());

        OrderManager.OrderResponse blocked = submit("A2", "MSFT");
        assertEquals('f', blocked.getRejectReason());

        assertEquals(RiskResetAcknowledgmentMessage.RESULT_AUTOMATIC_RESETS_DISABLED, orderManager.processResetRisk(resetRisk("F", "TEST", "", 0), "u1"),
                "EFID Risk Reset is disabled by default (p.220): F is rejected");
        assertEquals(RiskResetAcknowledgmentMessage.RESULT_SUCCESS, orderManager.processResetRisk(resetRisk("E", "TEST", "", 0), "u1"));
        assertTrue(submit("A3", "MSFT").isAcknowledged());
    }

    @Test
    void riskRootLockout_onlyBlocksThatSymbol_andARiskResetOnTheNewOrderReleasesIt() {
        orderManager.processCancelOrder(massCancel("FML", "AAPL"), session);

        assertEquals('s', submit("B1", "AAPL").getRejectReason());
        assertTrue(submit("B2", "MSFT").isAcknowledged());
        assertTrue(orderManager.processNewOrder(order("B3", "AAPL", "S", 0), session).isAcknowledged(),
                "RiskReset S on the New Order releases the symbol lockout first");
    }

    @Test
    void purgeByCustomGroupId_cancelsOnlyThatGroup_andLocksItOut() {
        orderManager.processNewOrder(order("G1", "AAPL", null, 7), session);
        orderManager.processNewOrder(order("G2", "AAPL", null, 8), session);

        PurgeOrdersMessage purge = new PurgeOrdersMessage();
        purge.setCustomGroupIds(List.of(7));
        purge.setClearingFirm("TEST");
        purge.setMassCancelInst("FSL");
        purge.setMassCancelId("P1");
        purge.setSendTime(1L);
        OrderManager.CancelResponse response = orderManager.processPurgeOrders(PurgeOrdersMessage.parse(purge.toBytes()), session);

        assertEquals(1, response.getMassCancelCount());
        assertEquals('C', response.getSubreason());
        assertTrue(orderManager.findByClOrdID("G2").isPresent());
        assertEquals('f', orderManager.processNewOrder(order("G3", "MSFT", null, 7), session).getRejectReason());
        assertTrue(orderManager.processNewOrder(order("G4", "MSFT", null, 8), session).isAcknowledged());
        assertEquals(RiskResetAcknowledgmentMessage.RESULT_SUCCESS, orderManager.processResetRisk(resetRisk("C", "TEST", "", 7), "u1"));
        assertTrue(orderManager.processNewOrder(order("G5", "MSFT", null, 7), session).isAcknowledged());
    }

    @Test
    void purgeWithRiskRootAndCustomGroupIds_isRejected() {
        PurgeOrdersMessage purge = new PurgeOrdersMessage();
        purge.setCustomGroupIds(List.of(1));
        purge.setMassCancelInst("A");
        purge.setRiskRoot("AAPL");
        purge.setSendTime(1L);

        OrderManager.CancelResponse response = orderManager.processPurgeOrders(PurgeOrdersMessage.parse(purge.toBytes()), session);

        assertTrue(response.isRejected());
        assertEquals("RiskRoot and CustomGroupID cannot both be specified", response.getRejectText());
    }

    @Test
    void purgeAcceptsAMassCancelIdWithStyleM_andRejectsAnUnknownMatchingUnit() {
        PurgeOrdersMessage purge = new PurgeOrdersMessage();
        purge.setMassCancelInst("AM");
        purge.setMassCancelId("P1");
        purge.setSendTime(1L);
        assertTrue(orderManager.processPurgeOrders(PurgeOrdersMessage.parse(purge.toBytes()), session).isMassCancelled());

        purge.setTargetMatchingUnit(2);
        assertEquals("Invalid MatchingUnit 2", orderManager.processPurgeOrders(PurgeOrdersMessage.parse(purge.toBytes()), session).getRejectText());
    }

    private PurgeOrdersMessage purgeByFirm(String clearingFirm, String inst) {
        PurgeOrdersMessage purge = new PurgeOrdersMessage();
        purge.setClearingFirm(clearingFirm);
        purge.setMassCancelInst(inst);
        purge.setSendTime(1L);
        return PurgeOrdersMessage.parse(purge.toBytes());
    }

    @Test
    void purgeClearingFirm_mustBeAllowed_andIsRequiredWithoutAnAllowedList() {
        assertEquals("ClearingFirm is required when the port has no allowed EFIDs",
                orderManager.processPurgeOrders(purgeByFirm("", "FM"), session).getRejectText());

        orderManager.setPortAttributes(orderManager.getPortAttributes().withAllowedClearingFirms(java.util.Set.of("TEST")));
        assertEquals("ClearingFirm OTHR is not allowed on this port",
                orderManager.processPurgeOrders(purgeByFirm("OTHR", "FM"), session).getRejectText());
    }

    @Test
    void blankPurgeClearingFirm_appliesToEveryAllowedEfid() {
        orderManager.setPortAttributes(orderManager.getPortAttributes().withAllowedClearingFirms(java.util.Set.of("TEST", "OTHR")));
        submit("A1", "AAPL");

        OrderManager.CancelResponse response = orderManager.processPurgeOrders(purgeByFirm("", "FML"), session);

        assertEquals(1, response.getMassCancelCount());
        assertEquals('f', submit("A2", "AAPL").getRejectReason(), "TEST is locked out");
        NewOrderMessage other = order("A3", "AAPL", null, 0);
        other.setClearingFirm("OTHR");
        assertEquals('f', orderManager.processNewOrder(NewOrderMessage.parse(other.toBytes()), session).getRejectReason(), "OTHR is locked out");
    }

    @Test
    void resetRiskResults() {
        assertEquals(RiskResetAcknowledgmentMessage.RESULT_EMPTY_RESET, orderManager.processResetRisk(resetRisk("", "TEST", "", 0), "u1"));
        assertEquals(RiskResetAcknowledgmentMessage.RESULT_EMPTY_RESET, orderManager.processResetRisk(resetRisk("X", "TEST", "", 0), "u1"));
        assertEquals(RiskResetAcknowledgmentMessage.RESULT_INVALID_RISK_ROOT, orderManager.processResetRisk(resetRisk("S", "TEST", "", 0), "u1"));
        assertEquals(RiskResetAcknowledgmentMessage.RESULT_INVALID_RISK_ROOT, orderManager.processResetRisk(resetRisk("S", "TEST", "ZZZ", 0), "u1"));
        assertEquals(RiskResetAcknowledgmentMessage.RESULT_INVALID_CLEARING_FIRM, orderManager.processResetRisk(resetRisk("E", "", "", 0), "u1"));
        ResetRiskMessage badUnit = resetRisk("E", "TEST", "", 0);
        badUnit.setTargetMatchingUnit(5);
        assertEquals(RiskResetAcknowledgmentMessage.RESULT_INVALID_MATCHING_UNIT,
                orderManager.processResetRisk(ResetRiskMessage.parse(badUnit.toBytes()), "u1"));
        assertEquals(RiskResetAcknowledgmentMessage.RESULT_SUCCESS, orderManager.processResetRisk(resetRisk("SE", "TEST", "AAPL", 0), "u1"));

        orderManager.setPortAttributes(orderManager.getPortAttributes().withEfidRiskReset(true));
        assertEquals(RiskResetAcknowledgmentMessage.RESULT_SUCCESS, orderManager.processResetRisk(resetRisk("F", "TEST", "", 0), "u1"),
                "With EFID Risk Reset enabled, F is accepted");
    }
}
