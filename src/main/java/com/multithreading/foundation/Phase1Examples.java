package com.multithreading.foundation;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Phase 1 demos: thread basics, lifecycle, control, and the core concurrency problems.
 *
 * Run a single demo with JDK 21 (no compile step needed):
 *     java Phase1Examples.java 1
 *
 *  1 = thread basics          4 = lost updates (atomicity)
 *  2 = thread lifecycle       5 = check-then-act race (overselling)
 *  3 = thread control         6 = visibility bug (volatile)
 */
public class Phase1Examples {

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: java Phase1Examples.java <1-6>");
            System.out.println(" 1 thread basics | 2 lifecycle | 3 control | 4 lost updates | 5 check-then-act | 6 visibility");
            return;
        }
        switch (args[0]) {
            case "1" -> threadBasics();
            case "2" -> lifecycle();
            case "3" -> control();
            case "4" -> lostUpdates();
            case "5" -> checkThenAct();
            case "6" -> visibility();
            default -> System.out.println("Unknown demo: " + args[0]);
        }
    }

    static void log(String msg) {
        System.out.println("[" + Thread.currentThread().getName() + "] " + msg);
    }

    // ------------------------------------------------------------------
    // 1. Thread basics
    // ------------------------------------------------------------------
    static void threadBasics() throws InterruptedException {
        log("cores available: " + Runtime.getRuntime().availableProcessors());

        // Way 1: extend Thread (works, but couples the task to the thread)
        class Worker extends Thread {
            Worker() { super("worker-subclass"); }
            @Override public void run() { log("extending Thread"); }
        }
        Thread t1 = new Worker();

        // Way 2: pass a Runnable (preferred: task is separate from the thread)
        Thread t2 = new Thread(() -> log("Runnable lambda"), "worker-runnable");

        // Way 3: builder API (final in JDK 21)
        Thread t3 = Thread.ofPlatform().name("worker-builder")
                .unstarted(() -> log("builder API"));

        t1.start();
        t2.start();
        t3.start();
        t1.join();
        t2.join();
        t3.join();

        // start() vs run()
        Thread t4 = new Thread(() -> log("who runs me?"), "worker-t4");
        log("calling run() directly:");
        t4.run();                       // NO new thread: runs on main
        Thread t5 = new Thread(() -> log("who runs me?"), "worker-t5");
        log("calling start():");
        t5.start();                     // new thread
        t5.join();

        // A thread can be started only once
        try {
            t5.start();
        } catch (IllegalThreadStateException e) {
            log("second start() -> " + e);
        }
    }

    // ------------------------------------------------------------------
    // 2. Thread lifecycle: NEW, RUNNABLE, TIMED_WAITING, BLOCKED, WAITING, TERMINATED
    // ------------------------------------------------------------------
    static void lifecycle() throws Exception {
        Object lock = new Object();

        // NEW -> TIMED_WAITING -> BLOCKED -> TERMINATED
        Thread t = new Thread(() -> {
            try { Thread.sleep(300); } catch (InterruptedException e) { return; }
            synchronized (lock) { /* can only enter once main releases the monitor */ }
        }, "life");

        System.out.println("before start():          " + t.getState());   // NEW
        synchronized (lock) {
            t.start();
            Thread.sleep(100);
            System.out.println("inside Thread.sleep():   " + t.getState()); // TIMED_WAITING
            Thread.sleep(400);
            System.out.println("waiting for monitor:     " + t.getState()); // BLOCKED
        }
        t.join();
        System.out.println("after finishing:         " + t.getState());   // TERMINATED

        // WAITING: inside Object.wait()
        Thread waiter = new Thread(() -> {
            synchronized (lock) {
                try { lock.wait(); } catch (InterruptedException e) { /* ignore */ }
            }
        }, "waiter");
        waiter.start();
        Thread.sleep(200);
        System.out.println("inside Object.wait():    " + waiter.getState()); // WAITING
        synchronized (lock) { lock.notify(); }
        waiter.join();

        // RUNNABLE: actually executing (or ready to)
        Thread busy = new Thread(() -> {
            long n = 0;
            while (!Thread.currentThread().isInterrupted()) n++;
        }, "busy");
        busy.start();
        Thread.sleep(100);
        System.out.println("busy loop:               " + busy.getState()); // RUNNABLE
        busy.interrupt();
        busy.join();
    }

    // ------------------------------------------------------------------
    // 3. Thread control: join, sleep, interrupt, daemon, uncaught exceptions
    // ------------------------------------------------------------------
    static void control() throws Exception {
        // (a) interrupting a sleeping thread: sleep throws, and the flag is CLEARED
        Thread sleeper = new Thread(() -> {
            try {
                Thread.sleep(10_000);
                log("woke up normally");
            } catch (InterruptedException e) {
                log("interrupted during sleep; flag is now " + Thread.currentThread().isInterrupted());
            }
        }, "sleeper");
        sleeper.start();
        Thread.sleep(200);
        sleeper.interrupt();
        sleeper.join();

        // (b) cooperative cancellation of a busy loop by polling the flag
        Thread busy = new Thread(() -> {
            long n = 0;
            while (!Thread.currentThread().isInterrupted()) n++;
            log("busy loop saw the flag and is exiting cleanly");
        }, "busy");
        busy.start();
        Thread.sleep(200);
        busy.interrupt();
        busy.join();

        // (c) the idiom: when you catch InterruptedException and can't rethrow, RESTORE the flag
        Thread restorer = new Thread(() -> {
            try {
                Thread.sleep(10_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log("flag restored for callers up the stack: " + Thread.currentThread().isInterrupted());
            }
        }, "restorer");
        restorer.start();
        Thread.sleep(100);
        restorer.interrupt();
        restorer.join();

        // (d) join with a timeout
        Thread slow = new Thread(() -> {
            try { Thread.sleep(2_000); } catch (InterruptedException e) { /* ignore */ }
        }, "slow");
        slow.start();
        slow.join(300);
        log("after join(300ms), slow is still alive: " + slow.isAlive());
        slow.interrupt();
        slow.join();

        // (e) uncaught exceptions kill only that thread; a handler lets you observe it
        Thread bad = new Thread(() -> { throw new IllegalStateException("boom"); }, "bad");
        bad.setUncaughtExceptionHandler((th, ex) ->
                System.out.println("handler got: " + ex + " from thread " + th.getName()));
        bad.start();
        bad.join();
        log("main is unaffected by the other thread's exception");

        // (f) daemon thread: does not keep the JVM alive
        Thread daemon = new Thread(() -> {
            while (true) {
                try { Thread.sleep(100); } catch (InterruptedException e) { return; }
            }
        }, "daemon-loop");
        daemon.setDaemon(true);          // must be set BEFORE start()
        daemon.start();
        log("main ending; the JVM will exit even though the daemon loops forever");
    }

    // ------------------------------------------------------------------
    // 4. Lost updates: count++ is NOT atomic
    // ------------------------------------------------------------------
    static int plainCount;
    static int syncCount;
    static final AtomicInteger atomicCount = new AtomicInteger();
    static final Object LOCK = new Object();

    static void runInThreads(int n, Runnable r) throws InterruptedException {
        Thread[] ts = new Thread[n];
        for (int i = 0; i < n; i++) {
            ts[i] = new Thread(r, "racer-" + i);
            ts[i].start();
        }
        for (Thread t : ts) t.join();
    }

    static void lostUpdates() throws Exception {
        final int threads = 4, perThread = 1_000_000;
        final int expected = threads * perThread;

        runInThreads(threads, () -> { for (int i = 0; i < perThread; i++) plainCount++; });
        System.out.println("plain int:     " + plainCount + "  (expected " + expected + ", lost " + (expected - plainCount) + ")");

        runInThreads(threads, () -> {
            for (int i = 0; i < perThread; i++) synchronized (LOCK) { syncCount++; }
        });
        System.out.println("synchronized:  " + syncCount);

        runInThreads(threads, () -> { for (int i = 0; i < perThread; i++) atomicCount.incrementAndGet(); });
        System.out.println("AtomicInteger: " + atomicCount.get());
    }

    // ------------------------------------------------------------------
    // 5. Check-then-act race: overselling seats
    // ------------------------------------------------------------------
    static int seats;
    static final AtomicInteger sold = new AtomicInteger();

    static void checkThenAct() throws Exception {
        final int totalSeats = 100_000;

        // Broken: the check (seats > 0) and the action (seats--) are separate steps
        seats = totalSeats;
        sold.set(0);
        runInThreads(4, () -> {
            for (int i = 0; i < totalSeats; i++) {
                if (seats > 0) {           // check
                    seats--;               // act (and not atomic either)
                    sold.incrementAndGet();
                }
            }
        });
        System.out.println("unsafe: sold " + sold.get() + " of " + totalSeats + " seats"
                + (sold.get() > totalSeats ? "  <-- OVERSOLD" : "  (race did not show up this run; try again)"));

        // Fixed: check and act inside one critical section
        seats = totalSeats;
        sold.set(0);
        runInThreads(4, () -> {
            for (int i = 0; i < totalSeats; i++) {
                synchronized (LOCK) {
                    if (seats > 0) {
                        seats--;
                        sold.incrementAndGet();
                    }
                }
            }
        });
        System.out.println("safe:   sold " + sold.get() + " of " + totalSeats + " seats");
    }

    // ------------------------------------------------------------------
    // 6. Visibility: a plain flag may never be seen by another thread
    // ------------------------------------------------------------------
    static boolean stopPlain = false;
    static volatile boolean stopVolatile = false;

    static void visibility() throws Exception {
        Thread plain = new Thread(() -> {
            long n = 0;
            while (!stopPlain) n++;
            log("plain flag: saw the update after " + n + " iterations");
        }, "plain-worker");
        plain.setDaemon(true);
        plain.start();
        Thread.sleep(1_000);
        stopPlain = true;
        plain.join(2_000);
        System.out.println(plain.isAlive()
                ? "plain:    worker is STILL RUNNING -> visibility bug reproduced"
                : "plain:    worker stopped (bug did not reproduce on this JVM/run; it is JIT-dependent)");

        Thread vol = new Thread(() -> {
            long n = 0;
            while (!stopVolatile) n++;
            log("volatile flag: saw the update after " + n + " iterations");
        }, "volatile-worker");
        vol.setDaemon(true);
        vol.start();
        Thread.sleep(1_000);
        stopVolatile = true;
        vol.join(2_000);
        System.out.println(vol.isAlive()
                ? "volatile: still running (should never happen)"
                : "volatile: worker stopped, always");
    }
}
