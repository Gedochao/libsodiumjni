package libsodiumjni;

/**
 * A public / secret key pair.
 *
 * <p>The arrays passed to the constructor and returned by the getters are copies, so that a key
 * pair can't be modified once created.
 */
public final class KeyPair {

  private final byte[] pubKey;
  private final byte[] secKey;

  public KeyPair(byte[] pubKey, byte[] secKey) {
    this.pubKey = pubKey.clone();
    this.secKey = secKey.clone();
  }

  public byte[] getPubKey() {
    return pubKey.clone();
  }
  public byte[] getSecKey() {
    return secKey.clone();
  }
}
