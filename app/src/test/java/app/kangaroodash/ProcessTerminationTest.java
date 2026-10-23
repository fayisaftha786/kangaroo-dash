package app.kangaroodash;

import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ProcessTerminationTest {
    @Test public void returnsFalseForNullOrAlreadyExitedProcess() {
        assertFalse(SolverService.terminateProcess(null));
        FakeProcess exited = new FakeProcess(true, true);
        exited.alive = false;
        assertFalse(SolverService.terminateProcess(exited));
        assertEquals(0, exited.destroyCalls);
    }

    @Test public void acceptsGracefulTermination() {
        FakeProcess process = new FakeProcess(true, true);
        assertFalse(SolverService.terminateProcess(process));
        assertEquals(1, process.destroyCalls);
        assertEquals(0, process.forceCalls);
    }

    @Test public void escalatesWhenGracefulTerminationDoesNotExit() {
        FakeProcess process = new FakeProcess(false, true);
        assertFalse(SolverService.terminateProcess(process));
        assertEquals(1, process.destroyCalls);
        assertEquals(1, process.forceCalls);
    }

    @Test public void reportsProcessThatSurvivesForcedTermination() {
        FakeProcess process = new FakeProcess(false, false);
        assertTrue(SolverService.terminateProcess(process));
        assertEquals(1, process.destroyCalls);
        assertEquals(1, process.forceCalls);
        assertTrue(process.isAlive());
    }

    @Test public void restoresPreexistingThreadInterruptFlag() {
        FakeProcess process = new FakeProcess(true, true);
        try {
            Thread.currentThread().interrupt();
            assertFalse(SolverService.terminateProcess(process));
            assertTrue("interrupt status must be restored", Thread.currentThread().isInterrupted());
        } finally {
            // Do not leak the interrupt flag into the JUnit runner.
            Thread.interrupted();
        }
    }

    private static final class FakeProcess extends Process {
        private boolean alive;
        private final boolean exitsGracefully;
        private final boolean exitsForcibly;
        int destroyCalls;
        int forceCalls;

        FakeProcess(boolean exitsGracefully, boolean exitsForcibly) {
            this.alive = true;
            this.exitsGracefully = exitsGracefully;
            this.exitsForcibly = exitsForcibly;
        }

        @Override public OutputStream getOutputStream() { return OutputStream.nullOutputStream(); }
        @Override public InputStream getInputStream() { return InputStream.nullInputStream(); }
        @Override public InputStream getErrorStream() { return InputStream.nullInputStream(); }

        @Override public int waitFor() {
            alive = false;
            return 0;
        }

        @Override public boolean waitFor(long timeout, TimeUnit unit) {
            return !alive;
        }

        @Override public int exitValue() {
            if (alive) throw new IllegalThreadStateException("process is still alive");
            return 0;
        }

        @Override public void destroy() {
            destroyCalls++;
            if (exitsGracefully) alive = false;
        }

        @Override public Process destroyForcibly() {
            forceCalls++;
            if (exitsForcibly) alive = false;
            return this;
        }

        @Override public boolean isAlive() { return alive; }
    }
}
