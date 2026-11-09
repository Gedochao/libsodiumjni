package libsodiumjni;

/**
 * Thrown when a libsodium operation fails, e.g. when a sealed message can't be opened, because of
 * a wrong key pair, or a corrupted / tampered cipher.
 *
 * <p>Invalid arguments (wrong key lengths, null arrays, …) are reported with the standard
 * IllegalArgumentException / NullPointerException instead.
 */
public class SodiumException extends RuntimeException {

    public SodiumException(String message) {
        super(message);
    }
}
