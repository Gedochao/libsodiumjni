package libsodiumjni.tests;

import libsodiumjni.Sodium;
import libsodiumjni.internal.LoadLibrary;

/**
 * Run in a child JVM with a large heap by SodiumInputValidationTests: seals a message so large that
 * the sealed message can't fit in a Java array. Prints "OK" if Sodium.seal rejects it with an
 * IllegalArgumentException, "SKIP" if the message itself can't be allocated here.
 */
public class HugeMessageChild {

    public static void main(String[] args) throws Exception {
        LoadLibrary.initializeFromResources();
        Sodium.init();
        byte[] pk = Sodium.keyPair().getPubKey();

        byte[] message;
        try {
            // crypto_box_seal_bytes() (48) + message.length exceeds Integer.MAX_VALUE by 1
            message = new byte[Integer.MAX_VALUE - 47];
        } catch (OutOfMemoryError e) {
            System.out.println("SKIP: " + e);
            return;
        }

        try {
            Sodium.seal(message, pk);
            System.out.println("FAIL: no exception");
        } catch (IllegalArgumentException e) {
            System.out.println("OK: " + e.getMessage());
        } catch (Throwable t) {
            System.out.println("FAIL: " + t);
        }
    }
}
