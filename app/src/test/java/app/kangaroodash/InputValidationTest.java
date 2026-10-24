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

    @Test public void acceptsValidCompressedAndUncompressedGeneratorPoints() {
        String x = "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798";
        String y = "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8";
        assertTrue(MainActivity.isPublicKeyHex("02" + x));
        assertTrue(MainActivity.isPublicKeyHex("03" + x));
        assertTrue(MainActivity.isPublicKeyHex("04" + x + y));
    }

    @Test public void acceptsUppercasePublicKeyHex() {
        String x = "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798";
        String y = "483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8";
        assertTrue(MainActivity.isPublicKeyHex("02" + x));
        assertTrue(MainActivity.isPublicKeyHex("04" + x + y));
    }

    @Test public void rejectsHexEncodedValuesThatAreNotCurvePoints() {
        assertFalse(MainActivity.isPublicKeyHex("02" + repeat('f', 64)));
        assertFalse(MainActivity.isPublicKeyHex("04" + repeat('0', 128)));
        assertFalse(MainActivity.isPublicKeyHex("04" + repeat('f', 64) + repeat('0', 64)));
    }

    @Test public void rejectsCoordinatesAtOrAboveFieldPrime() {
        String p = "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F";
        assertFalse(MainActivity.isPublicKeyHex("02" + p));
        assertFalse(MainActivity.isPublicKeyHex("04" + p + repeat('0', 64)));
    }

    @Test public void rejectsWrongPublicKeyPrefixOrLength() {
        assertFalse(MainActivity.isPublicKeyHex("04" + repeat('a', 64)));
        assertFalse(MainActivity.isPublicKeyHex("02" + repeat('a', 128)));
        assertFalse(MainActivity.isPublicKeyHex("05" + repeat('a', 128)));
    }

    @Test public void acceptsStartValuesWithin256BitHexBounds() {
        assertTrue(MainActivity.isValidStartValue("0"));
        assertTrue(MainActivity.isValidStartValue("80000000000000000000000000000000000"));
        assertTrue(MainActivity.isValidStartValue(repeat('f', 64)));
        assertTrue(MainActivity.isValidStartValue(repeat('0', 64)));
    }

    @Test public void rejectsEmptyInvalidOrOversizedStartValues() {
        assertFalse(MainActivity.isValidStartValue(null));
        assertFalse(MainActivity.isValidStartValue(""));
        assertFalse(MainActivity.isValidStartValue("0x1234"));
        assertFalse(MainActivity.isValidStartValue("12 34"));
        assertFalse(MainActivity.isValidStartValue(repeat('a', 65)));
    }

    private static String repeat(char c, int count) {
        StringBuilder value = new StringBuilder(count);
        for (int i = 0; i < count; i++) value.append(c);
        return value.toString();
    }
}
