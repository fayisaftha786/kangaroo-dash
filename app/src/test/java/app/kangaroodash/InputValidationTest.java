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
}
