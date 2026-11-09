package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.nio.charset.StandardCharsets;

import libsodiumjni.KeyPair;
import libsodiumjni.Sodium;
import libsodiumjni.internal.LoadLibrary;
import libsodiumjni.internal.SodiumApi;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;

/**
 * Checks that null arrays are rejected with a NullPointerException, rather than crashing the JVM from
 * the native code.
 */
public class SodiumNullArgumentsTests {

    private static final int KEY_BYTES = 32;
    private static final int SEAL_BYTES = 48;

    @BeforeClass
    public static void setUp() throws Exception {
        LoadLibrary.initializeFromResources();
        Sodium.init();
    }

    private static byte[] message() {
        return "Hello, libsodium!".getBytes(StandardCharsets.UTF_8);
    }

    private static void assertNpe(String expectedMessage, ThrowingRunnable runnable) {
        NullPointerException ex = assertThrows(NullPointerException.class, runnable);
        assertEquals(expectedMessage, ex.getMessage());
    }

    // Sodium (public API)

    @Test
    public void sealRejectsNullArguments() {
        KeyPair keyPair = Sodium.keyPair();
        assertNpe("message is null", () -> Sodium.seal(null, keyPair.getPubKey()));
        assertNpe("public key is null", () -> Sodium.seal(message(), null));
    }

    @Test
    public void sealOpenRejectsNullArguments() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = Sodium.seal(message(), keyPair.getPubKey());
        assertNpe("cipher is null", () -> Sodium.sealOpen(null, keyPair.getPubKey(), keyPair.getSecKey()));
        assertNpe("public key is null", () -> Sodium.sealOpen(cipher, null, keyPair.getSecKey()));
        assertNpe("secret key is null", () -> Sodium.sealOpen(cipher, keyPair.getPubKey(), null));
    }

    // SodiumApi (public native bindings, used by Sodium)

    @Test
    public void nativeKeyPairRejectsNullArrays() {
        assertNpe("public key is null", () -> SodiumApi.crypto_box_keypair(null, new byte[KEY_BYTES]));
        assertNpe("secret key is null", () -> SodiumApi.crypto_box_keypair(new byte[KEY_BYTES], null));
    }

    @Test
    public void nativeSealRejectsNullArrays() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] message = message();
        byte[] cipher = new byte[SEAL_BYTES + message.length];
        assertNpe("cipher is null", () -> SodiumApi.crypto_box_seal(null, message, keyPair.getPubKey()));
        assertNpe("message is null", () -> SodiumApi.crypto_box_seal(cipher, null, keyPair.getPubKey()));
        assertNpe("public key is null", () -> SodiumApi.crypto_box_seal(cipher, message, null));
    }

    @Test
    public void nativeSealOpenRejectsNullArrays() {
        KeyPair keyPair = Sodium.keyPair();
        byte[] cipher = Sodium.seal(message(), keyPair.getPubKey());
        byte[] decrypted = new byte[cipher.length - SEAL_BYTES];
        byte[] pk = keyPair.getPubKey();
        byte[] sk = keyPair.getSecKey();
        assertNpe("decrypted is null", () -> SodiumApi.crypto_box_seal_open(null, cipher, pk, sk));
        assertNpe("cipher is null", () -> SodiumApi.crypto_box_seal_open(decrypted, null, pk, sk));
        assertNpe("public key is null", () -> SodiumApi.crypto_box_seal_open(decrypted, cipher, null, sk));
        assertNpe("secret key is null", () -> SodiumApi.crypto_box_seal_open(decrypted, cipher, pk, null));
    }
}
