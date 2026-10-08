package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Login Response (0x24) — Table 19 (p.53), spec v2.11.90.
 *
 *   [0]  StartOfMessage              2B
 *   [2]  MessageLength               2B
 *   [4]  MessageType                 1B = 0x24
 *   [5]  MatchingUnit                1B (always 0, session message)
 *   [6]  SequenceNumber              4B (always 0, session message)
 *   [10] LoginResponseStatus         1B Alphanumeric
 *   [11] LoginResponseText          60B Text
 *   [71] NoUnspecifiedUnitReplay     1B Binary (echoed from the Login Request)
 *   [72] LastReceivedSequenceNumber  4B Binary
 *   [76] NumberOfUnits               1B Binary (0 for unsuccessful logins)
 *        (UnitNumber 1B + UnitSequence 4B) × NumberOfUnits
 *        NumberOfParamGroups         1B + parameter groups (echoed from the Login Request)
 */
public final class LoginResponseMessage extends SessionMessage {
    private static final byte MESSAGE_TYPE = 0x24;

    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    private static final int LOGIN_RESPONSE_TEXT_SIZE = 60;
    private static final int FIXED_PAYLOAD_SIZE = 1 + 1 + 4 + 1 + LOGIN_RESPONSE_TEXT_SIZE + 1 + 4 + 1;
    private static final int UNIT_PAIR_SIZE = 5;

    private byte loginResponseStatus;
    private String loginResponseText;
    private boolean noUnspecifiedUnitReplay;
    private int lastReceivedSequenceNumber;
    private Map<Integer, Integer> unitSequences;
    private int numberOfParamGroups;
    private byte[] paramGroupBytes;
    private byte matchingUnit;
    private int sequenceNumber;

    // LoginResponseStatus values per spec v2.11.90 Table 19
    public static final byte STATUS_ACCEPTED          = 'A';
    public static final byte STATUS_NOT_AUTHORIZED    = 'N';
    public static final byte STATUS_SESSION_DISABLED  = 'D';
    public static final byte STATUS_SESSION_IN_USE    = 'B';
    public static final byte STATUS_INVALID_SESSION   = 'S';
    public static final byte STATUS_SEQUENCE_AHEAD    = 'Q';
    public static final byte STATUS_INVALID_UNIT      = 'I';
    public static final byte STATUS_INVALID_BITFIELD  = 'F';
    public static final byte STATUS_INVALID_STRUCTURE = 'M';

    public LoginResponseMessage(byte[] messageData) {
        if (messageData == null || messageData.length < 4) throw new IllegalArgumentException("Invalid message data");
        if (messageData[0] != START_OF_MESSAGE_1 || messageData[1] != START_OF_MESSAGE_2) throw new IllegalArgumentException("Invalid start of message marker");

        parseMessage(messageData);
    }

    public LoginResponseMessage(byte loginResponseStatus, String loginResponseText, int lastReceivedSequenceNumber,
                                Map<Integer, Integer> unitSequences) {
        this(loginResponseStatus, loginResponseText, lastReceivedSequenceNumber, unitSequences, false, 0, new byte[0]);
    }

    public LoginResponseMessage(byte loginResponseStatus, String loginResponseText, int lastReceivedSequenceNumber,
                                Map<Integer, Integer> unitSequences, boolean noUnspecifiedUnitReplay,
                                int numberOfParamGroups, byte[] paramGroupBytes) {
        this.loginResponseStatus = loginResponseStatus;
        this.loginResponseText = loginResponseText != null ? loginResponseText : "";
        this.noUnspecifiedUnitReplay = noUnspecifiedUnitReplay;
        this.lastReceivedSequenceNumber = lastReceivedSequenceNumber;
        this.unitSequences = Collections.unmodifiableMap(new LinkedHashMap<>(unitSequences));
        this.numberOfParamGroups = numberOfParamGroups;
        this.paramGroupBytes = paramGroupBytes != null ? paramGroupBytes.clone() : new byte[0];
        this.matchingUnit = 0;
        this.sequenceNumber = 0;
    }

    private void parseMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        buffer.position(2);

        int messageLength = buffer.getShort() & 0xFFFF;
        int minMessageLength = 2 + FIXED_PAYLOAD_SIZE;

        if (messageLength < minMessageLength) throw new IllegalArgumentException("Message too short: got " + messageLength + ", expected at least " + minMessageLength);
        if (data.length < 2 + messageLength) throw new IllegalArgumentException("Message truncated: got " + data.length + " bytes, MessageLength says " + (2 + messageLength));

        byte messageType = buffer.get();
        if (messageType != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x24, got 0x" + String.format("%02X", messageType));

        this.matchingUnit = buffer.get();
        this.sequenceNumber = buffer.getInt();
        this.loginResponseStatus = buffer.get();

        byte[] textBytes = new byte[LOGIN_RESPONSE_TEXT_SIZE];
        buffer.get(textBytes);
        this.loginResponseText = new String(textBytes, StandardCharsets.US_ASCII).trim();

        this.noUnspecifiedUnitReplay = buffer.get() != 0;
        this.lastReceivedSequenceNumber = buffer.getInt();

        int numberOfUnits = buffer.get() & 0xFF;
        if (buffer.remaining() < numberOfUnits * UNIT_PAIR_SIZE) throw new IllegalArgumentException("Message too short for " + numberOfUnits + " unit/sequence pairs");
        Map<Integer, Integer> units = new LinkedHashMap<>();
        for (int i = 0; i < numberOfUnits; i++) {
            int unit = buffer.get() & 0xFF;
            units.put(unit, buffer.getInt());
        }
        this.unitSequences = Collections.unmodifiableMap(units);

        int end = 2 + messageLength;
        if (buffer.position() < end) {
            this.numberOfParamGroups = buffer.get() & 0xFF;
            this.paramGroupBytes = new byte[end - buffer.position()];
            buffer.get(this.paramGroupBytes);
        } else {
            this.numberOfParamGroups = 0;
            this.paramGroupBytes = new byte[0];
        }
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int payloadLength = FIXED_PAYLOAD_SIZE + unitSequences.size() * UNIT_PAIR_SIZE + 1 + paramGroupBytes.length;
        int messageLength = payloadLength + 2;
        int totalLength = 2 + messageLength;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.putShort((short) messageLength);
        buffer.put(MESSAGE_TYPE);
        buffer.put(matchingUnit);
        buffer.putInt(sequenceNumber);
        buffer.put(loginResponseStatus);
        buffer.put(toFixedLengthBytes(loginResponseText, LOGIN_RESPONSE_TEXT_SIZE));
        buffer.put((byte) (noUnspecifiedUnitReplay ? 0x01 : 0x00));
        buffer.putInt(lastReceivedSequenceNumber);
        buffer.put((byte) unitSequences.size());
        for (Map.Entry<Integer, Integer> entry : unitSequences.entrySet()) {
            buffer.put(entry.getKey().byteValue());
            buffer.putInt(entry.getValue());
        }
        buffer.put((byte) numberOfParamGroups);
        buffer.put(paramGroupBytes);

        return buffer.array();
    }

    // NUL-padded per BOE spec v2.11.90 (Text field type)
    private static byte[] toFixedLengthBytes(String str, int length) {
        byte[] result = new byte[length];
        if (str != null && !str.isEmpty()) {
            byte[] strBytes = str.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(strBytes, 0, result, 0, Math.min(strBytes.length, length));
        }
        return result;
    }

    // Getters
    public byte getLoginResponseStatus() { return loginResponseStatus; }
    public String getLoginResponseText() { return loginResponseText; }
    public boolean isNoUnspecifiedUnitReplay() { return noUnspecifiedUnitReplay; }
    public int getLastReceivedSequenceNumber() { return lastReceivedSequenceNumber; }
    public int getNumberOfUnits() { return unitSequences.size(); }
    public Map<Integer, Integer> getUnitSequences() { return unitSequences; }
    public int getNumberOfParamGroups() { return numberOfParamGroups; }
    public byte[] getParamGroupBytes() { return paramGroupBytes.clone(); }
    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }

    public boolean isAccepted() { return loginResponseStatus == STATUS_ACCEPTED; }
    public boolean isRejected() { return loginResponseStatus != STATUS_ACCEPTED; }

    // Setters
    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    @Override
    public String toString() {
        return "LoginResponseMessage{" +
                "status=" + (char) loginResponseStatus +
                ", text='" + loginResponseText + '\'' +
                ", noUnspecifiedUnitReplay=" + noUnspecifiedUnitReplay +
                ", lastReceivedSeq=" + lastReceivedSequenceNumber +
                ", units=" + unitSequences +
                ", paramGroups=" + numberOfParamGroups +
                ", matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
