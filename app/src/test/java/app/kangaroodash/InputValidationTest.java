package app.kangaroodash;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputValidationTest {
    private static String repeated(char value, int count) {
        StringBuilder result = new StringBuilder(count);
        for (int i = 0; i < count; i++) result.append(value);
        return result.toString();
    }

    @Test public void acceptsHexadecimalDigits() {
        assertTrue(MainActivity.isHex("0123456789abcdefABCDEF"));
    }

    @Test public void rejectsEmptyOrNullInput() {
        assertFalse(MainActivity.isHex(""));
        assertFalse(MainActivity.isHex(null));
    }

    @Test public void rejectsNonHexCharacters() {
        assertFalse(MainActivity.isHex("0x1234"));
        assertFalse(MainActivity.isHex("12 34"));
        assertFalse(MainActivity.isHex("12g4"));
    }

    @Test public void acceptsCompressedPublicKeyEncodingPrefixes() {
        assertTrue(MainActivity.isValidPublicKeyEncoding("02" + repeated('a', 64)));
        assertTrue(MainActivity.isValidPublicKeyEncoding("03" + repeated('B', 64)));
    }

    @Test public void acceptsUncompressedPublicKeyEncodingPrefix() {
        assertTrue(MainActivity.isValidPublicKeyEncoding("04" + repeated('c', 128)));
    }

    @Test public void rejectsUnsupportedPublicKeyPrefixes() {
        assertFalse(MainActivity.isValidPublicKeyEncoding("00" + repeated('a', 64)));
        assertFalse(MainActivity.isValidPublicKeyEncoding("04" + repeated('a', 64)));
        assertFalse(MainActivity.isValidPublicKeyEncoding("02" + repeated('a', 128)));
    }

    @Test public void rejectsNonHexPublicKeyAndWrongLength() {
        assertFalse(MainActivity.isValidPublicKeyEncoding("02" + repeated('g', 64)));
        assertFalse(MainActivity.isValidPublicKeyEncoding("02" + repeated('a', 62)));
        assertFalse(MainActivity.isValidPublicKeyEncoding(null));
    }
}
