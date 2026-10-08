package com.boe.simulator.protocol.message;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

class NewOrderMessageTest {

    // Fixed part: header(10) + ClOrdID(20) + Side(1) + OrderQty(4) + NumberOfBitfields(1)
    private static final int BITFIELDS_OFFSET = 36;

    private static final Instant MATURITY_2011_03_19 =
            LocalDate.of(2011, 3, 19).atStartOfDay(ZoneId.of("America/New_York")).toInstant();

    private static NewOrderMessage optionOrder() {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID("ORD1");
        msg.setSide((byte) '1');
        msg.setOrderQty(10);
        msg.setSymbol("AAPL");
        msg.setMaturityDate(MATURITY_2011_03_19);
        return msg;
    }

    @Test
    void toBytes_shouldEncodeMaturityDateAsYyyymmdd() {
        byte[] bytes = optionOrder().toBytes();

        // 4 bitfields, then Symbol (8B), then MaturityDate (4B)
        int maturityOffset = BITFIELDS_OFFSET + 4 + 8;
        byte[] maturity = Arrays.copyOfRange(bytes, maturityOffset, maturityOffset + 4);

        // Spec example: EF DB 32 01 = 20110319
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xDB, 0x32, 0x01}, maturity);
    }

    @Test
    void toBytes_shouldNulPadAlphanumericSymbol() {
        byte[] bytes = optionOrder().toBytes();

        int symbolOffset = BITFIELDS_OFFSET + 4;
        byte[] symbol = Arrays.copyOfRange(bytes, symbolOffset, symbolOffset + 8);

        assertArrayEquals(new byte[]{'A', 'A', 'P', 'L', 0x00, 0x00, 0x00, 0x00}, symbol);
    }

    @Test
    void parse_shouldRoundTripMaturityDateAndSymbol() {
        NewOrderMessage parsed = NewOrderMessage.parse(optionOrder().toBytes());

        assertEquals("AAPL", parsed.getSymbol());
        assertEquals(MATURITY_2011_03_19, parsed.getMaturityDate());
    }

    // Fixed part of the spec Table 28 example: ClOrdID ABC123, Buy, 100 contracts
    private static final String FIXED_28 = "BA BA 00 00 38 00 64 00 00 00 41 42 43 31 32 33 00 00 00 00 00 00 00 00 00 00 00 00 00 00 31 64 00 00 00";

    private static byte[] order(String bitfieldsAndFields) {
        String[] p = (FIXED_28 + " " + bitfieldsAndFields).trim().split("\\s+");
        byte[] b = new byte[p.length];
        for (int i = 0; i < p.length; i++) b[i] = (byte) Integer.parseInt(p[i], 16);
        b[2] = (byte) (b.length - 2);
        return b;
    }

    private static String zeros(int n) {
        return String.join(" ", java.util.Collections.nCopies(n, "00"));
    }

    @Test
    void specTable28Example_parsesEveryField() {
        NewOrderMessage m = NewOrderMessage.parse(order("04 04 C1 01 17 70 17 00 00 00 00 00 00 4D 53 46 54 00 00 00 00 43 52 00 00 00 "
                + "44 45 46 47 00 00 00 00 00 00 00 00 00 00 00 00 EF DB 32 01 98 AB 02 00 00 00 00 00 31 4F"));

        assertEquals("ABC123", m.getClOrdID());
        assertEquals('1', m.getSide());
        assertEquals(100, m.getOrderQty());
        assertEquals(new java.math.BigDecimal("0.6000"), m.getPrice());
        assertEquals("MSFT", m.getSymbol());
        assertEquals('C', m.getCapacity());
        assertEquals('R', m.getRoutingInst());
        assertEquals("DEFG", m.getAccount());
        assertEquals(MATURITY_2011_03_19, m.getMaturityDate());
        assertEquals(new java.math.BigDecimal("17.5000"), m.getStrikePrice());
        assertEquals('1', m.getPutOrCall());
        assertEquals('O', m.getOpenClose());
        assertNull(m.getFieldError());
    }

    @Test
    void ordTypeIsBit16_andTimeInForceBit32_ofBitfield1() {
        NewOrderMessage m = NewOrderMessage.parse(order("02 34 41 70 17 00 00 00 00 00 00 31 33 4D 53 46 54 00 00 00 00 43"));

        assertEquals('1', m.getOrdType(), "Bit 16 is OrdType (bit 8 is ExecInst)");
        assertEquals('3', m.getTimeInForce(), "Bit 32 is TimeInForce");
        assertNull(m.getFieldError());
    }

    @Test
    void encoder_putsOrdTypeAndTimeInForceAtTheSpecBits() {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID("X");
        msg.setSide((byte) '1');
        msg.setOrderQty(1);
        msg.setOrdType((byte) '1');
        msg.setTimeInForce((byte) '3');

        byte[] bytes = msg.toBytes();

        assertEquals(0x30, bytes[BITFIELDS_OFFSET] & 0xFF);
        assertEquals('1', NewOrderMessage.parse(bytes).getOrdType());
        assertEquals('3', NewOrderMessage.parse(bytes).getTimeInForce());
    }

    @Test
    void informationalField_isConsumedAndIgnored_keepingTheFollowingFieldsAligned() {
        // Bitfield 6 bit 32 = EchoText (64 bytes), then nothing; Bitfield 2 Symbol + Capacity before it
        NewOrderMessage m = NewOrderMessage.parse(order("06 00 41 00 00 00 20 4D 53 46 54 00 00 00 00 43 " + zeros(64)));

        assertEquals("MSFT", m.getSymbol());
        assertEquals('C', m.getCapacity());
        assertNull(m.getFieldError());
    }

    @Test
    void executionChangingField_isRejected_butStillConsumed() {
        // Bitfield 1 bit 64 = MinQty (4 bytes) before Symbol and Capacity
        NewOrderMessage m = NewOrderMessage.parse(order("02 40 41 05 00 00 00 4D 53 46 54 00 00 00 00 43"));

        assertEquals("MinQty is not supported by the simulator", m.getFieldError());
        assertEquals("MSFT", m.getSymbol(), "The fields after MinQty are still read correctly");
    }

    @Test
    void executionChangingFieldWithItsDefaultValue_isAccepted() {
        NewOrderMessage zero = NewOrderMessage.parse(order("02 80 41 00 00 00 00 4D 53 46 54 00 00 00 00 43"));
        NewOrderMessage five = NewOrderMessage.parse(order("02 80 41 05 00 00 00 4D 53 46 54 00 00 00 00 43"));

        assertNull(zero.getFieldError(), "MaxFloor 0 displays the whole order, the same as not sending it");
        assertEquals("MaxFloor is not supported by the simulator", five.getFieldError());
    }

    @Test
    void blankFieldInTheSpecTable_isRejected() {
        // Bitfield 2 bit 2 = SymbolSfx, blank for Cboe Options
        assertEquals("Bitfield 2 bit 2 cannot be specified on New Order",
                NewOrderMessage.parse(order("02 00 02 00 00 00 00")).getFieldError());
    }

    @Test
    void bitBeyondTheTenSpecBitfields_isRejected() {
        assertEquals("Bitfield 11 bit 1 cannot be specified on New Order",
                NewOrderMessage.parse(order("0B 00 00 00 00 00 00 00 00 00 00 01")).getFieldError());
    }

    @Test
    void clOrdIDWithAForbiddenCharacter_isRejected() {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID("ORD@1");
        msg.setSide((byte) '1');
        msg.setOrderQty(1);

        assertEquals("Invalid character 0x40 in ClOrdID (33-126 except ,;|@\")",
                NewOrderMessage.parse(msg.toBytes()).getFieldError());
    }

    @Test
    void symbolMustBeAlphanumeric() {
        NewOrderMessage m = NewOrderMessage.parse(order("02 00 41 4D 53 2D 54 00 00 00 00 43"));

        assertEquals("Invalid character 0x2D in Symbol (A-Z, a-z, 0-9)", m.getFieldError());
    }

    @Test
    void textAfterTheNulPadding_isRejected() {
        NewOrderMessage m = NewOrderMessage.parse(order("02 00 41 4D 53 00 54 00 00 00 00 43"));

        assertEquals("Symbol must be NUL (0x00) filled on the right", m.getFieldError());
    }

    @Test
    void clearingFirmMustBeAlpha() {
        NewOrderMessage m = NewOrderMessage.parse(order("02 01 41 54 45 35 54 4D 53 46 54 00 00 00 00 43"));

        assertEquals("Invalid character 0x35 in ClearingFirm (A-Z, a-z)", m.getFieldError());
    }

    @Test
    void unsupportedFieldError_takesPrecedenceOverACharsetError() {
        NewOrderMessage m = NewOrderMessage.parse(order("02 40 41 05 00 00 00 4D 53 2D 54 00 00 00 00 43"));

        assertEquals("MinQty is not supported by the simulator", m.getFieldError());
    }
}
