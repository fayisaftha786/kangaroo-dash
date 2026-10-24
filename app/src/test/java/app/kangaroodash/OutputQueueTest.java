package app.kangaroodash;

import org.junit.Test;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.*;

public class OutputQueueTest {
    @Test public void insertsLineWhenQueueHasCapacity() {
        BlockingQueue<Object> queue = new ArrayBlockingQueue<>(1);
        AtomicBoolean keepRunning = new AtomicBoolean(true);

        assertTrue(SolverService.offerOutputLine(queue, "engine output", keepRunning::get));
        assertEquals("engine output", queue.poll());
    }

    @Test public void fullQueueDoesNotBlockOnceShutdownBegins() {
        BlockingQueue<Object> queue = new ArrayBlockingQueue<>(1);
        queue.add("already queued");
        AtomicBoolean keepRunning = new AtomicBoolean(false);

        long startedAt = System.nanoTime();
        assertFalse(SolverService.offerOutputLine(queue, "another line", keepRunning::get));
        long elapsedMs = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - startedAt);

        assertTrue("shutdown should not wait for a full queue", elapsedMs < 500);
        assertEquals(1, queue.size());
        assertEquals("already queued", queue.peek());
    }

    @Test public void interruptedThreadDoesNotBlockOnFullQueue() {
        BlockingQueue<Object> queue = new ArrayBlockingQueue<>(1);
        queue.add("already queued");
        try {
            Thread.currentThread().interrupt();
            assertFalse(SolverService.offerOutputLine(queue, "another line", () -> true));
            assertTrue("interrupt status must be preserved", Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
        assertEquals(1, queue.size());
    }
}
