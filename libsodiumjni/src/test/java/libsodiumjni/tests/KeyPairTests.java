package libsodiumjni.tests;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import javax.security.auth.Destroyable;

import libsodiumjni.KeyPair;
import org.junit.Test;

/**
 * KeyPair must not expose its internal arrays, so that it can't be modified from the outside, and
 * its secret key must be wipeable (Destroyable)
 */
public class KeyPairTests {

    private static byte[] bytes(int value) {
        byte[] arr = new byte[32];
        java.util.Arrays.fill(arr, (byte) value);
        return arr;
    }

    @Test
    public void constructorCopiesArrays() {
        byte[] pub = bytes(1);
        byte[] sec = bytes(2);
        KeyPair keyPair = new KeyPair(pub, sec);
        pub[0] = 42;
        sec[0] = 42;
        assertArrayEquals(bytes(1), keyPair.getPubKey());
        assertArrayEquals(bytes(2), keyPair.getSecKey());
    }

    @Test
    public void gettersReturnCopies() {
        KeyPair keyPair = new KeyPair(bytes(1), bytes(2));
        keyPair.getPubKey()[0] = 42;
        keyPair.getSecKey()[0] = 42;
        assertArrayEquals(bytes(1), keyPair.getPubKey());
        assertArrayEquals(bytes(2), keyPair.getSecKey());
    }

    /** KeyPair's own copy of the secret key */
    private static byte[] internalSecKey(KeyPair keyPair) throws Exception {
        Field field = KeyPair.class.getDeclaredField("secKey");
        field.setAccessible(true);
        return (byte[]) field.get(keyPair);
    }

    @Test
    public void isDestroyable() {
        assertTrue(Destroyable.class.isAssignableFrom(KeyPair.class));
    }

    @Test
    public void destroyWipesTheSecretKey() throws Exception {
        KeyPair keyPair = new KeyPair(bytes(1), bytes(2));
        byte[] secKey = internalSecKey(keyPair);
        assertFalse(keyPair.isDestroyed());
        keyPair.destroy();
        assertTrue(keyPair.isDestroyed());
        assertArrayEquals(new byte[32], secKey);
    }

    @Test
    public void secretKeyIsUnavailableOnceDestroyed() throws Exception {
        KeyPair keyPair = new KeyPair(bytes(1), bytes(2));
        keyPair.destroy();
        assertThrows(IllegalStateException.class, keyPair::getSecKey);
    }

    @Test
    public void publicKeyIsStillAvailableOnceDestroyed() throws Exception {
        // the public key isn't a secret
        KeyPair keyPair = new KeyPair(bytes(1), bytes(2));
        keyPair.destroy();
        assertArrayEquals(bytes(1), keyPair.getPubKey());
    }

    @Test
    public void destroyCanBeCalledSeveralTimes() throws Exception {
        KeyPair keyPair = new KeyPair(bytes(1), bytes(2));
        keyPair.destroy();
        keyPair.destroy();
        assertTrue(keyPair.isDestroyed());
    }
}
