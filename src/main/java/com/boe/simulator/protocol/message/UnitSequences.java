package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Unit Sequences parameter group (0x80) of a Login Request — Table 12 (p.48), spec v2.11.90.
 *
 * Wire format:
 *   ParamGroupLength(2B LE, includes itself) + ParamGroupType=0x80(1B)
 *   + NoUnspecifiedUnitReplay(1B, 0x00/0x01) + NumberOfUnits(1B)
 *   + (UnitNumber(1B) + UnitSequence(4B LE)) × NumberOfUnits
 */
public final class UnitSequences {
    public static final byte PARAM_GROUP_TYPE = (byte) 0x80;
    private static final int FIXED_GROUP_LEN = 5; // 2(len)+1(type)+1(flag)+1(numUnits)
    private static final int UNIT_PAIR_LEN = 5;   // 1(unit)+4(seq)

    private final boolean present;
    private final boolean noUnspecifiedUnitReplay;
    private final Map<Integer, Integer> lastReceivedByUnit;

    private UnitSequences(boolean present, boolean noUnspecifiedUnitReplay, Map<Integer, Integer> lastReceivedByUnit) {
        this.present = present;
        this.noUnspecifiedUnitReplay = noUnspecifiedUnitReplay;
        this.lastReceivedByUnit = lastReceivedByUnit;
    }

    public static UnitSequences absent() {
        return new UnitSequences(false, false, Map.of());
    }

    public static UnitSequences of(boolean noUnspecifiedUnitReplay, Map<Integer, Integer> lastReceivedByUnit) {
        return new UnitSequences(true, noUnspecifiedUnitReplay,
                Collections.unmodifiableMap(new LinkedHashMap<>(lastReceivedByUnit)));
    }

    /**
     * Scans {@code numberOfGroups} parameter groups for the 0x80 group, skipping any other type.
     * The buffer must be positioned immediately after the NumberOfParamGroups byte.
     */
    public static UnitSequences parse(int numberOfGroups, ByteBuffer buf) {
        buf.order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < numberOfGroups; i++) {
            if (buf.remaining() < 3) break;

            int groupLen = buf.getShort() & 0xFFFF;
            if (groupLen < 3 || buf.remaining() < groupLen - 2) break;

            byte groupType = buf.get();
            if (groupType != PARAM_GROUP_TYPE) {
                buf.position(buf.position() + groupLen - 3);
                continue;
            }
            if (groupLen < FIXED_GROUP_LEN) break;

            boolean noUnspecified = buf.get() != 0;
            int numberOfUnits = buf.get() & 0xFF;
            if (groupLen < FIXED_GROUP_LEN + numberOfUnits * UNIT_PAIR_LEN) break;

            Map<Integer, Integer> units = new LinkedHashMap<>();
            for (int u = 0; u < numberOfUnits; u++) {
                int unit = buf.get() & 0xFF;
                units.put(unit, buf.getInt());
            }
            return of(noUnspecified, units);
        }
        return absent();
    }

    public int serializedSize() {
        return present ? FIXED_GROUP_LEN + lastReceivedByUnit.size() * UNIT_PAIR_LEN : 0;
    }

    public void writeTo(ByteBuffer buf) {
        if (!present) return;
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) serializedSize());
        buf.put(PARAM_GROUP_TYPE);
        buf.put((byte) (noUnspecifiedUnitReplay ? 0x01 : 0x00));
        buf.put((byte) lastReceivedByUnit.size());
        for (Map.Entry<Integer, Integer> entry : lastReceivedByUnit.entrySet()) {
            buf.put(entry.getKey().byteValue());
            buf.putInt(entry.getValue());
        }
    }

    public boolean isPresent() { return present; }
    public boolean isNoUnspecifiedUnitReplay() { return noUnspecifiedUnitReplay; }
    public Map<Integer, Integer> lastReceivedByUnit() { return lastReceivedByUnit; }

    @Override
    public String toString() {
        return present
                ? "UnitSequences{noUnspecifiedUnitReplay=" + noUnspecifiedUnitReplay + ", units=" + lastReceivedByUnit + "}"
                : "UnitSequences{absent}";
    }
}
