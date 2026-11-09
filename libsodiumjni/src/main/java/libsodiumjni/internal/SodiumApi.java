package libsodiumjni.internal;

public final class SodiumApi {

  public static native int sodium_init();

  public static native int crypto_box_public_key_bytes();
  public static native int crypto_box_secret_key_bytes();
  public static native int crypto_box_keypair(byte[] pubKey, byte[] secretKey);

  public static native int crypto_box_seal_bytes();
  /** Seals the whole message, into the first crypto_box_seal_bytes() + message.length bytes of cipher */
  public static native int crypto_box_seal(byte[] cipher, byte[] message, byte[] publicKey);

  /** Opens the whole cipher, whose length must be crypto_box_seal_bytes() + decrypted.length */
  public static native int crypto_box_seal_open(byte[] decrypted, byte[] cipher, byte[] pubKey, byte[] secretKey);

}
