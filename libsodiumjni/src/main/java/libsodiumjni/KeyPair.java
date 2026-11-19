package libsodiumjni;

import java.util.Arrays;

import javax.security.auth.Destroyable;

/**
 * A public / secret key pair.
 *
 * <p>The arrays passed to the constructor and returned by the getters are copies, so that a key
 * pair can't be modified once created.
 *
 * <p>Its copy of the secret key can be wiped with {@link #destroy()}, once it's not needed anymore
 * (the copies returned by {@link #getSecKey()} are the caller's to wipe).
 */
public final class KeyPair implements Destroyable {

  private final byte[] pubKey;
  private final byte[] secKey;
  private boolean destroyed = false;

  public KeyPair(byte[] pubKey, byte[] secKey) {
    this.pubKey = pubKey.clone();
    this.secKey = secKey.clone();
  }

  public byte[] getPubKey() {
    return pubKey.clone();
  }
  /** @throws IllegalStateException if the key pair was destroyed */
  public synchronized byte[] getSecKey() {
    if (destroyed) {
      throw new IllegalStateException("Key pair destroyed");
    }
    return secKey.clone();
  }

  /**
   * Wipes the secret key, which can't be retrieved afterwards. The public key is still available.
   * Can be called several times.
   */
  @Override
  public synchronized void destroy() {
    Arrays.fill(secKey, (byte) 0);
    destroyed = true;
  }

  @Override
  public synchronized boolean isDestroyed() {
    return destroyed;
  }
}
