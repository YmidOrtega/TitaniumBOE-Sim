package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class LoginRequestMessage extends SessionMessage {
    private static final byte MESSAGE_TYPE = 0x37;

    // Start of Message marker
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    private static final int SESSION_SUB_ID_SIZE = 4;
    private static final int USERNAME_SIZE = 4;
    private static final int PASSWORD_SIZE = 10;

    private final String sessionSubID;
    private final String username;
    private final String password;
    private final ReturnBitfields returnBitfields;
    private final UnitSequences unitSequences;
    private byte[] paramGroupBytes = new byte[0];
    private byte matchingUnit;
    private int sequenceNumber;

    // Optional parameter groups
    private final byte numberOfParamGroups;
    private int receivedParamGroups;

    public LoginRequestMessage(String username, String password) {
        this(username, password, "", (byte) 0, ReturnBitfields.empty());
    }

    public LoginRequestMessage(String username, String password, String sessionSubID) {
        this(username, password, sessionSubID, (byte) 0, ReturnBitfields.empty());
    }

    public LoginRequestMessage(String username, String password, String sessionSubID, byte matchingUnit) {
        this(username, password, sessionSubID, matchingUnit, ReturnBitfields.empty());
    }

    public LoginRequestMessage(String username, String password, String sessionSubID, byte matchingUnit, ReturnBitfields returnBitfields) {
        this(username, password, sessionSubID, matchingUnit, returnBitfields, UnitSequences.absent());
    }

    public LoginRequestMessage(String username, String password, String sessionSubID, byte matchingUnit, ReturnBitfields returnBitfields, UnitSequences unitSequences) {
        if (username == null || username.isEmpty() || username.length() > USERNAME_SIZE) throw new IllegalArgumentException("Username must be between 1 and " + USERNAME_SIZE + " characters");
        if (password == null || password.isEmpty() || password.length() > PASSWORD_SIZE) throw new IllegalArgumentException("Password must be between 1 and " + PASSWORD_SIZE + " characters");
        if (sessionSubID != null && sessionSubID.length() > SESSION_SUB_ID_SIZE) throw new IllegalArgumentException("Session Sub ID must be at most " + SESSION_SUB_ID_SIZE + " characters");

        this.username = username;
        this.password = password;
        this.sessionSubID = sessionSubID != null ? sessionSubID : "";
        this.returnBitfields = returnBitfields != null ? returnBitfields : ReturnBitfields.empty();
        this.matchingUnit = matchingUnit;
        this.sequenceNumber = 0;
        this.unitSequences = unitSequences != null ? unitSequences : UnitSequences.absent();
        this.numberOfParamGroups = (byte) (this.returnBitfields.entryCount() + (this.unitSequences.isPresent() ? 1 : 0));
        this.receivedParamGroups = this.numberOfParamGroups & 0xFF;
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        // Calculate message length:
        // Total payload (after the MessageLength field) = 1 + 1 + 4 + 4 + 4 + 10 + 1 = 25 bytes
        // MessageLength = Payload + 2 (for the MessageLength field itself)
        int payloadLength = 1 + 1 + 4 + SESSION_SUB_ID_SIZE + USERNAME_SIZE + PASSWORD_SIZE + 1
                + unitSequences.serializedSize() + returnBitfields.serializedSize();
        
        // MessageLength = Payload + 2 (the MessageLength field itself)
        int messageLength = payloadLength + 2;

        // Total length = StartOfMessage (2 bytes) + MessageLength
        int totalLength = 2 + messageLength;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Start of Message (2 bytes)
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);

        // Message Length (2 bytes) - includes itself and payload, not StartOfMessage
        buffer.putShort((short) messageLength);

        // Message Type (1 byte)
        buffer.put(MESSAGE_TYPE);

        // Matching Unit (1 byte)
        buffer.put(matchingUnit);

        // Sequence Number (4 bytes)
        buffer.putInt(sequenceNumber);

        // Session Sub ID (4 bytes, NUL-padded)
        buffer.put(toFixedLengthBytes(sessionSubID, SESSION_SUB_ID_SIZE));

        // Username (4 bytes, NUL-padded)
        buffer.put(toFixedLengthBytes(username, USERNAME_SIZE));

        // Password (10 bytes, NUL-padded)
        buffer.put(toFixedLengthBytes(password, PASSWORD_SIZE));

        // Number of Parameter Groups (1 byte)
        buffer.put(numberOfParamGroups);
        unitSequences.writeTo(buffer);
        returnBitfields.writeTo(buffer);

        return buffer.array();
    }

    // NUL-padded per BOE spec v2.11.90 (Alphanumeric / Text field types)
    private byte[] toFixedLengthBytes(String str, int length) {
        byte[] result = new byte[length];

        if (str != null && !str.isEmpty()) {
            byte[] strBytes = str.getBytes(StandardCharsets.US_ASCII);
            int copyLength = Math.min(strBytes.length, length);
            System.arraycopy(strBytes, 0, result, 0, copyLength);
        }

        return result;
    }

    public static LoginRequestMessage parseFromBytes(byte[] data) {
        if (data == null || data.length < 29) throw new IllegalArgumentException("Login Request is shorter than the 29-byte fixed part");

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // Skip StartOfMessage (2 bytes)
        buffer.position(2);

        // MessageLength (2 bytes)
        @SuppressWarnings("unused")
        int messageLength = buffer.getShort() & 0xFFFF;

        // MessageType (1 byte)
        byte messageType = buffer.get();
        if (messageType != 0x37) throw new IllegalArgumentException("Invalid message type: expected 0x37, got 0x" + String.format("%02X", messageType));

        // MatchingUnit (1 byte)
        byte matchingUnit = buffer.get();

        // SequenceNumber (4 bytes)
        int sequenceNumber = buffer.getInt();

        // SessionSubID (4 bytes)
        byte[] sessionSubIDBytes = new byte[4];
        buffer.get(sessionSubIDBytes);
        String sessionSubID = new String(sessionSubIDBytes, StandardCharsets.US_ASCII).trim();

        // Username (4 bytes)
        byte[] usernameBytes = new byte[4];
        buffer.get(usernameBytes);
        String username = new String(usernameBytes, StandardCharsets.US_ASCII).trim();

        // Password (10 bytes)
        byte[] passwordBytes = new byte[10];
        buffer.get(passwordBytes);
        String password = new String(passwordBytes, StandardCharsets.US_ASCII).trim();

        for (String error : new String[]{
                FieldCharset.ALPHANUMERIC.check("SessionSubID", sessionSubIDBytes),
                FieldCharset.ALPHANUMERIC.check("Username", usernameBytes),
                FieldCharset.ALPHANUMERIC.check("Password", passwordBytes)}) {
            if (error != null) throw new IllegalArgumentException(error);
        }

        // Create a message
        int numberOfParamGroups = 0;
        ReturnBitfields returnBitfields = ReturnBitfields.empty();
        UnitSequences unitSequences = UnitSequences.absent();
        byte[] paramGroupBytes = new byte[0];
        if (buffer.remaining() > 0) {
            numberOfParamGroups = buffer.get() & 0xFF;
            int groupsStart = buffer.position();
            validateParamGroups(numberOfParamGroups, buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN));
            paramGroupBytes = Arrays.copyOfRange(data, groupsStart, data.length);
            returnBitfields = ReturnBitfields.parse(numberOfParamGroups, buffer);
            buffer.position(groupsStart);
            unitSequences = UnitSequences.parse(numberOfParamGroups, buffer);
        }

        LoginRequestMessage msg = new LoginRequestMessage(username, password, sessionSubID, matchingUnit, returnBitfields, unitSequences);
        msg.setSequenceNumber(sequenceNumber);
        msg.paramGroupBytes = paramGroupBytes;
        msg.receivedParamGroups = numberOfParamGroups;
        return msg;
    }

    private static void validateParamGroups(int numberOfGroups, ByteBuffer buf) {
        int unitSequencesGroups = 0;
        Set<Byte> returnBitfieldTypes = new HashSet<>();
        for (int i = 1; i <= numberOfGroups; i++) {
            if (buf.remaining() < 3) throw new IllegalArgumentException("Parameter group " + i + " of " + numberOfGroups + " is missing");

            int groupStart = buf.position();
            int groupLen = buf.getShort() & 0xFFFF;
            if (groupLen < 3) throw new IllegalArgumentException("Parameter group " + i + " has invalid length " + groupLen);
            if (groupLen - 2 > buf.remaining()) throw new IllegalArgumentException("Parameter group " + i + " length " + groupLen + " exceeds the message");

            byte groupType = buf.get();
            if (groupType == UnitSequences.PARAM_GROUP_TYPE) {
                if (++unitSequencesGroups > 1) throw new IllegalArgumentException("Only one Unit Sequences parameter group may be included");
                if (groupLen < 5) throw new IllegalArgumentException("Unit Sequences group is too short");
                int noUnspecifiedUnitReplay = buf.get() & 0xFF;
                if (noUnspecifiedUnitReplay > 1) throw new IllegalArgumentException("NoUnspecifiedUnitReplay must be 0x00 or 0x01, got 0x" + String.format("%02X", noUnspecifiedUnitReplay));
                int numberOfUnits = buf.get() & 0xFF;
                if (groupLen != 5 + numberOfUnits * 5) throw new IllegalArgumentException("Unit Sequences group length " + groupLen + " does not match " + numberOfUnits + " units");
                Set<Integer> units = new HashSet<>();
                for (int u = 0; u < numberOfUnits; u++) {
                    int unit = buf.get() & 0xFF;
                    buf.getInt();
                    if (!units.add(unit)) throw new IllegalArgumentException("Unit " + unit + " appears twice in the Unit Sequences group");
                }
            } else if (groupType == (byte) 0x81) {
                if (groupLen < 5) throw new IllegalArgumentException("Return Bitfields group is too short");
                byte returnType = buf.get();
                if (!returnBitfieldTypes.add(returnType)) throw new IllegalArgumentException(String.format("Duplicate Return Bitfields group for message type 0x%02X", returnType));
                int numberOfBitfields = buf.get() & 0xFF;
                if (groupLen != 5 + numberOfBitfields) throw new IllegalArgumentException("Return Bitfields group length " + groupLen + " does not match " + numberOfBitfields + " bitfields");
            }
            buf.position(groupStart + groupLen);
        }
        if (buf.hasRemaining()) throw new IllegalArgumentException(buf.remaining() + " unexpected bytes after the parameter groups");
    }

    public void setMatchingUnit(byte matchingUnit) {
        this.matchingUnit = matchingUnit;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getSessionSubID() {
        return sessionSubID;
    }

    public byte getMatchingUnit() {
        return matchingUnit;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public ReturnBitfields getReturnBitfields() {
        return returnBitfields;
    }

    public UnitSequences getUnitSequences() {
        return unitSequences;
    }

    public int getNumberOfParamGroups() {
        return receivedParamGroups;
    }

    public byte[] getParamGroupBytes() {
        return paramGroupBytes.clone();
    }

    @Override
    public String toString() {
        return "LoginRequestMessage{" +
                "username='" + username + '\'' +
                ", sessionSubID='" + sessionSubID + '\'' +
                ", matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}
