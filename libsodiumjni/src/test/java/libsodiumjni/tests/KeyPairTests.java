package libsodiumjni.tests;

import static org.junit.Assert.assertArrayEquals;

import libsodiumjni.KeyPair;
import org.junit.Test;

/** KeyPair must not expose its internal arrays, so that it can't be modified from the outside */
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
}
