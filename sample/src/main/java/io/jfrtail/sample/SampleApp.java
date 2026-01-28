package io.jfrtail.sample;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class SampleApp {
    private static final Random random = new Random();
    private static final Object lock = new Object();

    public static void main(String[] args) throws Exception {
        System.out.println("SampleApp started. PID: " + ProcessHandle.current().pid());

        // Initialize JFR-Tail Library (Embedded Mode)
        // This starts the Web Dashboard on 8080 and TCP Stream on 7099
        // Initialize JFR-Tail Library (Embedded Mode)
        // Only if system property is set (to allow testing Agent Mode separately)
        if (Boolean.getBoolean("jfrtail.start")) {
            try {
                io.jfrtail.agent.api.JfrTailMonitor.getInstance().start(8080, 7099);
                System.out.println("JFR-Tail Library initialized! (Web: 8080, TCP: 7099)");
            } catch (Exception e) {
                System.err.println("Failed to init JFR-Tail: " + e.getMessage());
            }
        }

        // Thread to generate GC
        new Thread(() -> {
            List<byte[]> data = new ArrayList<>();
            while (true) {
                try {
                    data.add(new byte[1024 * 1024]); // 1MB
                    if (data.size() > 50)
                        data.clear();
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "GC-Generator").start();

        // Thread to generate Locks
        new Thread(() -> {
            while (true) {
                synchronized (lock) {
                    try {
                        Thread.sleep(random.nextInt(30));
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
        }, "Lock-Generator-1").start();

        new Thread(() -> {
            while (true) {
                synchronized (lock) {
                    try {
                        Thread.sleep(random.nextInt(30));
                    } catch (InterruptedException e) {
                        break;
                    }
                }
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                }
            }
        }, "Lock-Generator-2").start();

        // Thread to generate Exceptions
        new Thread(() -> {
            while (true) {
                try {
                    if (random.nextInt(100) < 10) {
                        throw new RuntimeException("Sample exception " + random.nextInt(1000));
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    // Ignored
                }
            }
        }, "Exception-Generator").start();

        System.out.println("Generating events... Press Ctrl+C to stop.");
        while (true) {
            Thread.sleep(1000);
        }
    }
}
