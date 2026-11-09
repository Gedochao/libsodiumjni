package libsodiumjni;

import java.util.Objects;

import libsodiumjni.internal.SodiumApi;

public final class Sodium {

    private static volatile boolean initialized = false;

    public static void init() {
        if (!initialized) {
            synchronized (Sodium.class) {
                if (!initialized) {
                    // sodium_init returns 0 if it initialized libsodium, 1 if libsodium was already
                    // initialized (e.g. by another library in this process), and -1 on failure
                    int res = SodiumApi.sodium_init();
                    if (res < 0) {
                        throw new RuntimeException("Cannot initialize libsodium (sodium_init returned " + res + ")");
                    }
                    initialized = true;
                }
            }
        }
    }


    public static KeyPair keyPair() {
        byte[] pubKey = new byte[SodiumApi.crypto_box_public_key_bytes()];
        byte[] secKey = new byte[SodiumApi.crypto_box_secret_key_bytes()];
        int res = SodiumApi.crypto_box_keypair(pubKey, secKey);
        if (res != 0) {
            throw new RuntimeException("Failed to generate key pair");
        }
        return new KeyPair(pubKey, secKey);
    }

    private static void checkKeyLength(String name, byte[] key, int expectedLength) {
        if (key.length != expectedLength) {
            throw new IllegalArgumentException(
                    "Invalid " + name + " length (" + key.length + ", expected " + expectedLength + ")");
        }
    }

    public static byte[] seal(byte[] message, byte[] pubKey) {
        Objects.requireNonNull(message, "message is null");
        Objects.requireNonNull(pubKey, "public key is null");
        checkKeyLength("public key", pubKey, SodiumApi.crypto_box_public_key_bytes());
        byte[] cipher = new byte[SodiumApi.crypto_box_seal_bytes() + message.length];
        int res = SodiumApi.crypto_box_seal(cipher, message, pubKey);
        if (res != 0) {
            throw new RuntimeException("Failed to seal message");
        }
        return cipher;
    }

    public static byte[] sealOpen(byte[] cipher, byte[] pubKey, byte[] secretKey) {
        Objects.requireNonNull(cipher, "cipher is null");
        Objects.requireNonNull(pubKey, "public key is null");
        Objects.requireNonNull(secretKey, "secret key is null");
        checkKeyLength("public key", pubKey, SodiumApi.crypto_box_public_key_bytes());
        checkKeyLength("secret key", secretKey, SodiumApi.crypto_box_secret_key_bytes());
        if (cipher.length < SodiumApi.crypto_box_seal_bytes()) {
            throw new RuntimeException(
                    "Invalid encrypted message length (" + cipher.length + ", should be > " + SodiumApi.crypto_box_seal_bytes() + ")");
        }
        byte[] message = new byte[cipher.length - SodiumApi.crypto_box_seal_bytes()];
        int res = SodiumApi.crypto_box_seal_open(message, cipher, pubKey, secretKey);
        if (res != 0) {
            throw new RuntimeException("Failed to open seal");
        }
        return message;
    }
}
