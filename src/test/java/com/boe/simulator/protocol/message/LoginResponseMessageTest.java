package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginResponseMessageTest {

    @Test
    void isAccepted_shouldReturnTrue_whenStatusIsAccepted() {
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "text", 0, Map.of(1, 0));

        assertTrue(message.isAccepted());
        assertFalse(message.isRejected());
    }

    @Test
    void isRejected_shouldReturnTrue_whenStatusIsNotAccepted() {
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_NOT_AUTHORIZED, "text", 0, Map.of());

        assertFalse(message.isAccepted());
        assertTrue(message.isRejected());
    }

    @Test
    void toBytes_shouldMatchSpecLayout_withoutParamGroups() {
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "Login OK", 1, Map.of(1, 7));

        byte[] bytes = message.toBytes();
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        // 73 fixed payload + 1 unit pair (5) + NumberOfParamGroups (1) + length field (2)
        assertEquals(81, buf.getShort(2));
        assertEquals(0x24, bytes[4]);
        assertEquals(0, bytes[5], "MatchingUnit is always 0 for session messages");
        assertEquals(0, buf.getInt(6), "SequenceNumber is always 0 for session messages");
        assertEquals('A', bytes[10]);
        assertEquals(0x00, bytes[71], "NoUnspecifiedUnitReplay is binary, not ASCII");
        assertEquals(1, buf.getInt(72));
        assertEquals(1, bytes[76], "NumberOfUnits");
        assertEquals(1, bytes[77], "UnitNumber");
        assertEquals(7, buf.getInt(78), "UnitSequence");
        assertEquals(0, bytes[82], "NumberOfParamGroups");
        assertEquals(83, bytes.length);
    }

    @Test
    void toBytes_shouldReproduceSpecExampleLengthAndUnits() {
        // Spec Table 20: 4 units, 3 echoed parameter groups (20 + 8 + 12 bytes), MessageLength = 136
        Map<Integer, Integer> units = new LinkedHashMap<>();
        units.put(1, 113_482);
        units.put(2, 0);
        units.put(3, 0);
        units.put(4, 41_337);
        byte[] echoedGroups = new byte[40];

        LoginResponseMessage message = new LoginResponseMessage(
                LoginResponseMessage.STATUS_ACCEPTED, "Accepted", 150_100, units, true, 3, echoedGroups);
        byte[] bytes = message.toBytes();
        ByteBuffer buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(136, buf.getShort(2));
        assertEquals(0x01, bytes[71]);
        assertArrayEquals(new byte[]{0x54, 0x4A, 0x02, 0x00}, Arrays.copyOfRange(bytes, 72, 76));
        assertEquals(4, bytes[76]);
        assertArrayEquals(new byte[]{0x01, 0x4A, (byte) 0xBB, 0x01, 0x00}, Arrays.copyOfRange(bytes, 77, 82));
        assertEquals(3, bytes[97], "NumberOfParamGroups echoed");
    }

    @Test
    void parse_shouldRoundTripUnitsAndEchoedGroups() {
        byte[] echoedGroups = {0x08, 0x00, (byte) 0x80, 0x01, 0x01, 0x01, 0x05, 0x00};
        LoginResponseMessage original = new LoginResponseMessage(
                LoginResponseMessage.STATUS_ACCEPTED, "Login OK", 42, Map.of(1, 5), true, 1, echoedGroups);

        LoginResponseMessage parsed = new LoginResponseMessage(original.toBytes());

        assertEquals(LoginResponseMessage.STATUS_ACCEPTED, parsed.getLoginResponseStatus());
        assertEquals("Login OK", parsed.getLoginResponseText());
        assertTrue(parsed.isNoUnspecifiedUnitReplay());
        assertEquals(42, parsed.getLastReceivedSequenceNumber());
        assertEquals(Map.of(1, 5), parsed.getUnitSequences());
        assertEquals(1, parsed.getNumberOfParamGroups());
        assertArrayEquals(echoedGroups, parsed.getParamGroupBytes());
        assertEquals(0, parsed.getMatchingUnit());
        assertEquals(0, parsed.getSequenceNumber());
    }

    @Test
    void rejectedLogin_shouldCarryNoUnits() {
        LoginResponseMessage parsed = new LoginResponseMessage(
                new LoginResponseMessage(LoginResponseMessage.STATUS_SESSION_IN_USE, "In use", 0, Map.of()).toBytes());

        assertEquals(0, parsed.getNumberOfUnits());
        assertEquals(LoginResponseMessage.STATUS_SESSION_IN_USE, parsed.getLoginResponseStatus());
    }

    @Test
    void constructor_shouldThrowException_whenByteArrayIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(null));
    }

    @Test
    void constructor_shouldThrowException_whenByteArrayIsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(new byte[]{0x01, 0x02, 0x03}));
    }

    @Test
    void constructor_shouldThrowException_whenInvalidStartOfMessage() {
        byte[] data = new byte[77];
        data[0] = (byte) 0xFF; data[1] = (byte) 0xFF;
        data[2] = 0x4B; data[3] = 0x00;
        data[4] = 0x24;
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(data));
    }

    @Test
    void constructor_shouldThrowException_whenInvalidMessageType() {
        // 0x08 is Logout, not LoginResponse (0x24)
        byte[] data = new byte[77];
        data[0] = (byte) 0xBA; data[1] = (byte) 0xBA;
        data[2] = 0x4B; data[3] = 0x00;
        data[4] = 0x08;
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(data));
    }

    @Test
    void constructor_shouldThrowException_whenUnitPairsAreMissing() {
        byte[] data = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "x", 0, Map.of()).toBytes();
        data[76] = 5; // claims 5 unit pairs that are not there
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(data));
    }
}
