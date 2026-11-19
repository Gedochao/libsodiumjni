package libsodiumjni.tests;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CyclicBarrier;

import libsodiumjni.Sodium;
import libsodiumjni.internal.LoadLibrary;
import libsodiumjni.internal.SodiumApi;

/**
 * Run in a fresh JVM by SodiumInitTests, so that neither libsodium nor Sodium are initialized yet.
 *
 * <p>Argument: scenario, "already-initialized", "concurrent", or "auto-init-key-pair",
 * "auto-init-seal", "auto-init-seal-open" (Sodium.init() not called, the Sodium method must
 * initialize libsodium itself). Prints "OK" and exits with 0 on success.
 */
public class SodiumInitChild {

    private static final int THREADS = 32;

    public static void main(String[] args) throws Exception {
        LoadLibrary.initializeFromResources();

        switch (args[0]) {
            case "already-initialized":
                // Something else in the process initialized libsodium first
                int res = SodiumApi.sodium_init();
                if (res != 0)
                    throw new AssertionError("Expected the first sodium_init call to return 0, got " + res);
                Sodium.init();
                break;

            case "concurrent":
                CyclicBarrier barrier = new CyclicBarrier(THREADS);
                List<Throwable> failures = Collections.synchronizedList(new ArrayList<>());
                List<Thread> threads = new ArrayList<>();
                for (int i = 0; i < THREADS; i++) {
                    Thread thread = new Thread(() -> {
                        try {
                            barrier.await();
                            Sodium.init();
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
                    System.out.println(failures.size() + " of " + THREADS + " concurrent Sodium.init() calls failed:");
                    failures.get(0).printStackTrace(System.out);
                    System.exit(1);
                }
                break;

            case "auto-init-key-pair":
                Sodium.keyPair();
                checkLibsodiumInitialized();
                break;

            case "auto-init-seal": {
                // key pair from the native method, which doesn't initialize libsodium
                byte[] pk = new byte[32];
                SodiumApi.crypto_box_keypair(pk, new byte[32]);
                Sodium.seal(new byte[] {1, 2, 3}, pk);
                checkLibsodiumInitialized();
                break;
            }

            case "auto-init-seal-open": {
                byte[] pk = new byte[32];
                byte[] sk = new byte[32];
                SodiumApi.crypto_box_keypair(pk, sk);
                byte[] cipher = new byte[48 + 3];
                SodiumApi.crypto_box_seal(cipher, new byte[] {1, 2, 3}, pk);
                Sodium.sealOpen(cipher, pk, sk);
                checkLibsodiumInitialized();
                break;
            }

            default:
                throw new IllegalArgumentException("Unknown scenario: " + args[0]);
        }

        // libsodium must be usable afterwards
        Sodium.seal(new byte[] {1, 2, 3}, Sodium.keyPair().getPubKey());
        System.out.println("OK");
    }

    /** sodium_init returns 1 if libsodium was already initialized, 0 if it initialized it */
    private static void checkLibsodiumInitialized() {
        int res = SodiumApi.sodium_init();
        if (res != 1)
            throw new AssertionError("Expected libsodium to be initialized already (sodium_init returning 1), got " + res);
    }
}
