package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FieldCharsetTest {

    private static byte[] padded(String s, int len) {
        byte[] b = new byte[len];
        byte[] src = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, b, 0, src.length);
        return b;
    }

    @Test
    void alpha_allowsOnlyLetters() {
        assertNull(FieldCharset.ALPHA.check("ClearingFirm", padded("TeSt", 4)));
        assertEquals("Invalid character 0x31 in ClearingFirm (A-Z, a-z)",
                FieldCharset.ALPHA.check("ClearingFirm", padded("TS1", 4)));
        assertFalse(FieldCharset.ALPHA.allows((byte) ' '));
    }

    @Test
    void alphanumeric_allowsLettersAndDigits() {
        assertNull(FieldCharset.ALPHANUMERIC.check("Symbol", padded("Ab12", 8)));
        assertEquals("Invalid character 0x2E in Symbol (A-Z, a-z, 0-9)",
                FieldCharset.ALPHANUMERIC.check("Symbol", padded("BRK.B", 8)));
    }

    @Test
    void text_allowsPrintableAsciiOnly() {
        assertNull(FieldCharset.TEXT.check("Account", padded("acct 1-~", 16)));
        assertFalse(FieldCharset.TEXT.allows((byte) 0x09));
        assertFalse(FieldCharset.TEXT.allows((byte) 0x7F));
        assertFalse(FieldCharset.TEXT.allows((byte) 0xC3));
    }

    @Test
    void clOrdID_excludesSpaceAndTheFiveForbiddenSymbols() {
        assertTrue(FieldCharset.CLORDID.allows((byte) '!'));
        assertTrue(FieldCharset.CLORDID.allows((byte) '~'));
        for (char c : new char[]{' ', ',', ';', '|', '@', '"'}) {
            assertFalse(FieldCharset.CLORDID.allows((byte) c), "'" + c + "' is not allowed in ClOrdID");
        }
    }

    @Test
    void anyByteAfterTheNulPadding_isRejected() {
        byte[] raw = padded("AB", 4);
        raw[3] = 'C';

        assertEquals("ClearingFirm must be NUL (0x00) filled on the right",
                FieldCharset.ALPHA.check("ClearingFirm", raw));
    }

    @Test
    void emptyValue_isAccepted() {
        assertNull(FieldCharset.TEXT.check("OrigClOrdID", new byte[20]));
    }
}
