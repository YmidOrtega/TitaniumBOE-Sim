package com.boe.simulator.protocol.message;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
