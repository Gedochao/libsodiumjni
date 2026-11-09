package libsodiumjni.tests;

import java.lang.reflect.Field;
import java.util.HexFormat;

import libsodiumjni.KeyPair;
import libsodiumjni.Sodium;
import libsodiumjni.internal.LoadLibrary;

/**
 * Run in a child JVM by NativeMemoryWipingTests, with free_checker preloaded.
 *
 * <p>Seals and opens the secret (so that it goes through the native code as plaintext), then leaves
 * the control marker in freed native memory, to check that free_checker actually detects leaks.
 *
 * <p>Arguments: hex-encoded secret, hex-encoded control marker
 */
public class FreeCheckerChild {

    public static void main(String[] args) throws Exception {
        byte[] secret = HexFormat.of().parseHex(args[0]);
        byte[] control = HexFormat.of().parseHex(args[1]);

        LoadLibrary.initializeFromResources();
        Sodium.init();

        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = Sodium.seal(secret, keyPair.getPubKey());
        byte[] decrypted = Sodium.sealOpen(cipher, keyPair.getPubKey(), keyPair.getSecKey());
        if (!java.util.Arrays.equals(secret, decrypted))
            throw new AssertionError("Round trip failed");

        // control: malloc'ed memory freed with the control marker still in it
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        long address = unsafe.allocateMemory(control.length);
        for (int i = 0; i < control.length; i++)
            unsafe.putByte(address + i, control[i]);
        unsafe.freeMemory(address);

        System.out.println("DONE");
    }
}
