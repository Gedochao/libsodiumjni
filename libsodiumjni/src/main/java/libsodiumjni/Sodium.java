package libsodiumjni;

import java.util.Arrays;
import java.util.Objects;

import libsodiumjni.internal.SodiumApi;

public final class Sodium {

    private static volatile boolean initialized = false;

    /** @throws SodiumException if libsodium can't be initialized */
    public static void init() {
        if (!initialized) {
            synchronized (Sodium.class) {
                if (!initialized) {
                    // sodium_init returns 0 if it initialized libsodium, 1 if libsodium was already
                    // initialized (e.g. by another library in this process), and -1 on failure
                    int res = SodiumApi.sodium_init();
                    if (res < 0) {
                        throw new SodiumException("Cannot initialize libsodium (sodium_init returned " + res + ")");
                    }
                    initialized = true;
                }
            }
        }
    }


    /** @throws SodiumException if libsodium fails to generate a key pair */
    public static KeyPair keyPair() {
        byte[] pubKey = new byte[SodiumApi.crypto_box_public_key_bytes()];
        byte[] secKey = new byte[SodiumApi.crypto_box_secret_key_bytes()];
        int res = SodiumApi.crypto_box_keypair(pubKey, secKey);
        if (res != 0) {
            throw new SodiumException("Failed to generate key pair");
        }
        KeyPair keyPair = new KeyPair(pubKey, secKey);
        // KeyPair keeps its own copy
        Arrays.fill(secKey, (byte) 0);
        return keyPair;
    }

    private static void checkKeyLength(String name, byte[] key, int expectedLength) {
        if (key.length != expectedLength) {
            throw new IllegalArgumentException(
                    "Invalid " + name + " length (" + key.length + ", expected " + expectedLength + ")");
        }
    }

    /**
     * Seals message, so that only the owner of the secret key matching pubKey can open it.
     *
     * @throws IllegalArgumentException if pubKey doesn't have the expected length
     * @throws SodiumException if libsodium fails to seal the message (e.g. invalid public key)
     */
    public static byte[] seal(byte[] message, byte[] pubKey) {
        Objects.requireNonNull(message, "message is null");
        Objects.requireNonNull(pubKey, "public key is null");
        checkKeyLength("public key", pubKey, SodiumApi.crypto_box_public_key_bytes());
        byte[] cipher = new byte[SodiumApi.crypto_box_seal_bytes() + message.length];
        int res = SodiumApi.crypto_box_seal(cipher, message, pubKey);
        if (res != 0) {
            throw new SodiumException("Failed to seal message");
        }
        return cipher;
    }

    /**
     * Opens a message sealed with {@link #seal}.
     *
     * @throws IllegalArgumentException if a key or the cipher don't have the expected length
     * @throws SodiumException if the cipher can't be opened (wrong key pair, corrupted or tampered
     *     cipher, …)
     */
    public static byte[] sealOpen(byte[] cipher, byte[] pubKey, byte[] secretKey) {
        Objects.requireNonNull(cipher, "cipher is null");
        Objects.requireNonNull(pubKey, "public key is null");
        Objects.requireNonNull(secretKey, "secret key is null");
        checkKeyLength("public key", pubKey, SodiumApi.crypto_box_public_key_bytes());
        checkKeyLength("secret key", secretKey, SodiumApi.crypto_box_secret_key_bytes());
        if (cipher.length < SodiumApi.crypto_box_seal_bytes()) {
            throw new IllegalArgumentException(
                    "Invalid encrypted message length (" + cipher.length + ", should be >= " + SodiumApi.crypto_box_seal_bytes() + ")");
        }
        byte[] message = new byte[cipher.length - SodiumApi.crypto_box_seal_bytes()];
        int res = SodiumApi.crypto_box_seal_open(message, cipher, pubKey, secretKey);
        if (res != 0) {
            throw new SodiumException("Failed to open seal");
        }
        return message;
    }
}
