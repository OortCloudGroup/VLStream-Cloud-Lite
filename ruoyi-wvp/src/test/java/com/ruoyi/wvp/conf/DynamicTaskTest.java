package com.ruoyi.wvp.conf;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class DynamicTaskTest {

    private DynamicTask dynamicTask;

    @Before
    public void createScheduler() {
        dynamicTask = new DynamicTask();
        dynamicTask.DynamicTask();
    }

    @After
    public void closeScheduler() {
        dynamicTask.destroy();
    }

    @Test
    public void destroyCancelsScheduledTasks() {
        dynamicTask.startDelay("destroy-test", () -> { }, 60000);
        assertTrue(dynamicTask.contains("destroy-test"));
        dynamicTask.destroy();
        assertFalse(dynamicTask.contains("destroy-test"));
        assertTrue(dynamicTask.getAllKeys().isEmpty());
        dynamicTask.startDelay("after-destroy", () -> { }, 10);
        assertFalse(dynamicTask.contains("after-destroy"));
    }

    @Test
    public void completedHeartbeatTimeoutCanBeRearmedWithoutWaitingForCleanup() throws Exception {
        String key = "zlm-keepalive-local";
        AtomicBoolean online = new AtomicBoolean(true);
        AtomicInteger expirations = new AtomicInteger();
        Runnable timeout = () -> {
            online.set(false);
            expirations.incrementAndGet();
        };
        // Exercise the real 20-second watchdog mechanism with a scaled-down delay.
        dynamicTask.startDelay(key, timeout, 25);
        ScheduledFuture<?> first = future(key);
        first.get(2, TimeUnit.SECONDS);
        assertFalse(online.get());
        assertTrue(first.isDone());
        assertTrue(dynamicTask.contains(key));

        online.set(true);
        dynamicTask.startDelay(key, timeout, 25);
        ScheduledFuture<?> recovered = future(key);
        assertNotSame(first, recovered);
        recovered.get(2, TimeUnit.SECONDS);
        assertFalse(online.get());
        assertEquals(2, expirations.get());
    }

    @Test
    public void renewalReplacesThePendingTimeout() throws Exception {
        AtomicInteger oldCalls = new AtomicInteger();
        AtomicInteger newCalls = new AtomicInteger();
        dynamicTask.startDelay("renew", oldCalls::incrementAndGet, 60000);
        ScheduledFuture<?> old = future("renew");
        dynamicTask.startDelay("renew", newCalls::incrementAndGet, 25);
        future("renew").get(2, TimeUnit.SECONDS);
        assertTrue(old.isCancelled());
        assertEquals(0, oldCalls.get());
        assertEquals(1, newCalls.get());
    }

    @Test
    public void stopRemovesCompletedAndCancelledTasks() throws Exception {
        dynamicTask.startDelay("completed", () -> { }, 0);
        future("completed").get(2, TimeUnit.SECONDS);
        assertFalse(dynamicTask.stop("completed"));
        assertFalse(dynamicTask.contains("completed"));
        assertNull(dynamicTask.get("completed"));

        dynamicTask.startDelay("cancelled", () -> { }, 60000);
        future("cancelled").cancel(false);
        assertFalse(dynamicTask.stop("cancelled"));
        assertFalse(dynamicTask.contains("cancelled"));
        assertNull(dynamicTask.get("cancelled"));
    }

    @Test
    public void cancelledTimeoutCanBeRearmed() throws Exception {
        dynamicTask.startDelay("cancelled", () -> { }, 60000);
        ScheduledFuture<?> cancelled = future("cancelled");
        cancelled.cancel(false);
        dynamicTask.startDelay("cancelled", () -> { }, 25);
        ScheduledFuture<?> replacement = future("cancelled");
        assertNotSame(cancelled, replacement);
        replacement.get(2, TimeUnit.SECONDS);
    }

    @Test
    public void runningCronIsKeptAndCancelledCronCanBeRearmed() throws Exception {
        Runnable original = () -> { };
        CountDownLatch replacementRan = new CountDownLatch(1);
        dynamicTask.startCron("cron", original, 60000);
        ScheduledFuture<?> active = future("cron");
        dynamicTask.startCron("cron", replacementRan::countDown, 25);
        assertSame(active, future("cron"));
        assertSame(original, dynamicTask.get("cron"));

        active.cancel(false);
        dynamicTask.startCron("cron", replacementRan::countDown, 25);
        assertNotSame(active, future("cron"));
        assertTrue(replacementRan.await(2, TimeUnit.SECONDS));
        assertTrue(dynamicTask.stop("cron"));
    }

    @Test
    public void exceptionallyCompletedCronCanBeRearmed() throws Exception {
        Field schedulerField = DynamicTask.class.getDeclaredField("threadPoolTaskScheduler");
        schedulerField.setAccessible(true);
        ThreadPoolTaskScheduler scheduler = (ThreadPoolTaskScheduler) schedulerField.get(dynamicTask);
        // Production cron errors are suppressed; propagate only in this isolated fixture.
        scheduler.setErrorHandler(error -> { throw new IllegalStateException("fixture cron failure", error); });
        dynamicTask.startCron("failed-cron", () -> { throw new IllegalStateException("fixture"); }, 25);
        ScheduledFuture<?> failed = future("failed-cron");
        try {
            failed.get(2, TimeUnit.SECONDS);
            fail("The fixture cron should complete exceptionally");
        } catch (ExecutionException expected) {
            assertTrue(failed.isDone());
        }
        CountDownLatch recovered = new CountDownLatch(1);
        dynamicTask.startCron("failed-cron", recovered::countDown, 25);
        assertNotSame(failed, future("failed-cron"));
        assertTrue(recovered.await(2, TimeUnit.SECONDS));
        dynamicTask.stop("failed-cron");
    }

    @Test
    public void anOldRunningCallbackFinishingDoesNotRemoveItsReplacement() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        dynamicTask.startDelay("running", () -> {
            started.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Callback was not released");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                finished.countDown();
            }
        }, 0);
        try {
            assertTrue(started.await(2, TimeUnit.SECONDS));
            Runnable replacement = () -> { };
            dynamicTask.startDelay("running", replacement, 60000);
            ScheduledFuture<?> renewed = future("running");
            release.countDown();
            assertTrue(finished.await(2, TimeUnit.SECONDS));
            dynamicTask.execute();
            assertSame(renewed, future("running"));
            assertSame(replacement, dynamicTask.get("running"));
            assertTrue(dynamicTask.isAlive("running"));
        } finally {
            release.countDown();
        }
    }

    @Test
    public void cleanupCannotRemoveATimerRearmedConcurrently() throws Exception {
        dynamicTask.startDelay("cleanup", () -> { }, 0);
        future("cleanup").get(2, TimeUnit.SECONDS);
        CountDownLatch inspected = new CountDownLatch(1);
        CountDownLatch releaseInspection = new CountDownLatch(1);
        ScheduledFuture<?> old = new PausingCompletedFuture(future("cleanup"), inspected, releaseInspection);
        futures().put("cleanup", old);
        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            Future<?> cleanup = callers.submit(dynamicTask::execute);
            assertTrue(inspected.await(2, TimeUnit.SECONDS));
            CountDownLatch renewalStarted = new CountDownLatch(1);
            Runnable replacement = () -> { };
            Future<?> renewal = callers.submit(() -> {
                renewalStarted.countDown();
                dynamicTask.startDelay("cleanup", replacement, 60000);
            });
            assertTrue(renewalStarted.await(2, TimeUnit.SECONDS));
            releaseInspection.countDown();
            cleanup.get(2, TimeUnit.SECONDS);
            renewal.get(2, TimeUnit.SECONDS);
            assertNotSame(old, future("cleanup"));
            assertSame(replacement, dynamicTask.get("cleanup"));
            assertTrue(dynamicTask.isAlive("cleanup"));
        } finally {
            releaseInspection.countDown();
            callers.shutdownNow();
        }
    }

    private ScheduledFuture<?> future(String key) throws Exception {
        return futures().get(key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, ScheduledFuture<?>> futures() throws Exception {
        Field field = DynamicTask.class.getDeclaredField("futureMap");
        field.setAccessible(true);
        return (Map<String, ScheduledFuture<?>>) field.get(dynamicTask);
    }

    private static class PausingCompletedFuture implements ScheduledFuture<Object> {
        private final ScheduledFuture<?> delegate;
        private final CountDownLatch inspected;
        private final CountDownLatch release;
        private final AtomicBoolean firstInspection = new AtomicBoolean(true);

        PausingCompletedFuture(ScheduledFuture<?> delegate, CountDownLatch inspected, CountDownLatch release) {
            this.delegate = delegate;
            this.inspected = inspected;
            this.release = release;
        }

        @Override public boolean isDone() {
            if (firstInspection.compareAndSet(true, false)) {
                inspected.countDown();
                try {
                    if (!release.await(2, TimeUnit.SECONDS)) throw new AssertionError("Cleanup was not released");
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(interrupted);
                }
            }
            return delegate.isDone();
        }

        @Override public boolean cancel(boolean interrupt) { return delegate.cancel(interrupt); }
        @Override public boolean isCancelled() { return delegate.isCancelled(); }
        @Override public long getDelay(TimeUnit unit) { return delegate.getDelay(unit); }
        @Override public int compareTo(Delayed other) { return delegate.compareTo(other); }
        @Override public Object get() throws InterruptedException, ExecutionException { return delegate.get(); }
        @Override public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
    }
}
