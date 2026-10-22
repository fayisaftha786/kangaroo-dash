package app.kangaroodash;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BasicTest {

    @Test
    public void basicMathWorks() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void appPackageNameIsCorrect() {
        assertTrue("Package name should not be empty",
                !BasicTest.class.getPackage().getName().isEmpty());
    }
}
