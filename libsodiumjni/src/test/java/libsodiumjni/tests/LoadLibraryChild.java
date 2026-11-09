package libsodiumjni.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import libsodiumjni.Sodium;
import libsodiumjni.internal.LoadLibrary;

/**
 * Run in a fresh JVM by LoadLibraryInitializationTests, so that the native library isn't loaded
 * yet.
 *
 * <p>Argument: scenario, "load", "null-context-class-loader", "concurrent" or "missing-resource".
 * Prints "OK" and exits with 0 on success.
 */
public class LoadLibraryChild {

    private static final int THREADS = 16;

    public static void main(String[] args) throws Exception {
        switch (args[0]) {
            case "missing-resource":
                try {
                    LoadLibrary.initializeFromResources("META-INF/native/missing/libsodiumjni.so");
                    throw new AssertionError("Expected an exception");
                } catch (IllegalStateException e) {
                    if (!e.getMessage().equals("Resource META-INF/native/missing/libsodiumjni.so not found"))
                        throw new AssertionError("Unexpected message: " + e.getMessage());
                }
                System.out.println("OK");
                return;

            case "load":
                LoadLibrary.initializeFromResources();
                break;

            case "null-context-class-loader":
                // e.g. threads created from native code, some thread pools, …
                Thread.currentThread().setContextClassLoader(null);
                LoadLibrary.initializeFromResources();
                break;

            case "concurrent":
                CyclicBarrier barrier = new CyclicBarrier(THREADS);
                List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
                List<Thread> threads = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    Thread thread = new Thread(() -> {
                        try {
                            barrier.await();
                            LoadLibrary.initializeFromResources();
                        } catch (Throwable t) {
                            failures.add(t);
                        }
                    });
                    thread.start();
                    threads.add(thread);
                }
                for (Thread thread : threads)
                    thread.join();
                if (!failures.isEmpty()) {
                    System.out.println(failures.size() + " of " + THREADS + " concurrent calls failed:");
                    failures.get(0).printStackTrace(System.out);
                    System.exit(1);
                }
                break;

            default:
                throw new IllegalArgumentException("Unknown scenario: " + args[0]);
        }

        // the library must be usable afterwards
        Sodium.init();
        Sodium.seal(new byte[] {1, 2, 3}, Sodium.keyPair().getPubKey());
        System.out.println("OK");
    }
}
