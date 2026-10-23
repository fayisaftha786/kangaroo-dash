package app.kangaroodash;

import org.junit.Test;
import static org.junit.Assert.*;

public class InputValidationTest {
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

    @Test public void acceptsSupportedPublicKeyEncodingsByPrefixAndLength() {
        assertTrue(MainActivity.isPublicKeyHex("02" + repeat('a', 64)));
        assertTrue(MainActivity.isPublicKeyHex("03" + repeat('b', 64)));
        assertTrue(MainActivity.isPublicKeyHex("04" + repeat('c', 128)));
    }

    @Test public void rejectsWrongPublicKeyPrefixOrLength() {
        assertFalse(MainActivity.isPublicKeyHex("04" + repeat('a', 64)));
        assertFalse(MainActivity.isPublicKeyHex("02" + repeat('a', 128)));
        assertFalse(MainActivity.isPublicKeyHex("05" + repeat('a', 128)));
    }

    private static String repeat(char c, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int i = 0; i < count; i++) value.append(c);
        return value.toString();
    }
}
