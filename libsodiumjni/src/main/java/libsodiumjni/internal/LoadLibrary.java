package libsodiumjni.internal;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Loads the libsodiumjni native library.
 *
 * <p>The library is loaded at most once per JVM: once it is loaded, by any of the methods here (or
 * marked as loaded with {@link #assumeInitialized()}), later calls to them do nothing, even if they
 * are passed another path.
 *
 * <p>{@link #initializeFromResources()} extracts the library to the directory given by the {@value
 * #TMPDIR_PROPERTY} system property, or, if it's not set, to the default temporary directory
 * ({@code java.io.tmpdir}). Loading libraries from a directory can be forbidden (e.g. when it's
 * mounted with noexec on Linux), in which case set that property to a directory that allows it.
 */
public final class LoadLibrary {

    /** System property: directory where the native library is extracted from the resources */
    public static final String TMPDIR_PROPERTY = "libsodiumjni.tmpdir";

    private static volatile boolean initialized = false;

    public static void initialize(String libraryPath) {
        if (!initialized) {
            synchronized (LoadLibrary.class) {
                if (!initialized) {
                    System.load(libraryPath);
                    initialized = true;
                }
            }
        }
    }

    public static String resourcePath() {
        return resourcePath(System.getProperty("os.name"), System.getProperty("os.arch"));
    }

    /** Resource path of the native library for the passed OS name and architecture (as in the "os.name" and "os.arch" Java properties) */
    public static String resourcePath(String osName0, String osArch0) {
        // "x86_64", "aarch64", or null if unsupported
        String arch;
        switch (osArch0.toLowerCase(Locale.ROOT)) {
            case "amd64":
            case "x86_64":
                arch = "x86_64";
                break;
            case "aarch64":
            case "arm64":
                arch = "aarch64";
                break;
            default:
                arch = null;
        }

        String osName = osName0.toLowerCase(Locale.ROOT);
        String dir;
        String os;
        if (osName.contains("linux")) {
            os = "Linux";
            dir = "x86_64".equals(arch) ? "linux64/libsodiumjni.so"
                    : "aarch64".equals(arch) ? "linux-aarch64/libsodiumjni.so"
                    : null;
        }
        else if (osName.contains("mac")) {
            os = "macOS";
            dir = "x86_64".equals(arch) ? "darwin/libsodiumjni.dylib"
                    : "aarch64".equals(arch) ? "arm-darwin/libsodiumjni.dylib"
                    : null;
        }
        else if (osName.contains("windows")) {
            os = "Windows";
            dir = "x86_64".equals(arch) ? "windows64/libsodiumjni.dll"
                    : "aarch64".equals(arch) ? "windows-aarch64/libsodiumjni.dll"
                    : null;
        }
        else
          throw new UnsupportedOperationException("Unrecognized or unsupported OS: " + osName);

        // Rather than falling back to another architecture's library, which can only fail to load
        if (dir == null)
            throw new UnsupportedOperationException("Unsupported architecture on " + os + ": " + osArch0);
        return "META-INF/native/" + dir;
    }

    public static String libSuffix() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("linux")) return ".so";
        else if (osName.contains("mac")) return ".dylib";
        else if (osName.contains("windows")) return ".dll";
        else
          throw new UnsupportedOperationException("Unrecognized or unsupported OS: " + osName);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    }

    public static void initializeFromResources() throws IOException {
        initializeFromResources(resourcePath());
    }

    public static void initializeFromResources(String resourcePath) throws IOException {
        if (!initialized) {
            synchronized (LoadLibrary.class) {
                if (!initialized) {
                    byte[] content = readResource(resourcePath);
                    if (isWindows())
                        loadFromWindowsCache(content);
                    else
                        loadAndDelete(content);
                    initialized = true;
                }
            }
        }
    }

    private static byte[] readResource(String resourcePath) throws IOException {
        // the context class loader can be null (e.g. threads attached from native code)
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        URL resUrl = loader == null ? null : loader.getResource(resourcePath);
        if (resUrl == null)
            resUrl = LoadLibrary.class.getClassLoader() == null
                    ? ClassLoader.getSystemResource(resourcePath)
                    : LoadLibrary.class.getClassLoader().getResource(resourcePath);
        if (resUrl == null)
            throw new IllegalStateException("Resource " + resourcePath + " not found");
        try (InputStream is = resUrl.openStream()) {
            return is.readAllBytes();
        }
    }

    /** Directory where the native library is extracted from the resources */
    private static Path extractionDir() {
        String dir = System.getProperty(TMPDIR_PROPERTY);
        if (dir == null || dir.isEmpty())
            dir = System.getProperty("java.io.tmpdir");
        return Paths.get(dir);
    }

    /**
     * System.load, with the directory the library was extracted to and how to change it in the error
     * message, if it fails (still an UnsatisfiedLinkError, with the original one as cause)
     */
    private static void loadExtracted(Path lib) {
        try {
            System.load(lib.toString());
        } catch (UnsatisfiedLinkError e) {
            UnsatisfiedLinkError error = new UnsatisfiedLinkError(
                    e.getMessage() + " (libsodiumjni extracted its native library to " + lib.getParent()
                            + ". If libraries can't be loaded from there, e.g. if it's mounted with noexec, "
                            + "set the " + TMPDIR_PROPERTY + " system property to another directory)");
            error.initCause(e);
            throw error;
        }
    }

    /**
     * Linux / macOS: a loaded library can be deleted right away (it stays mapped), so it's extracted
     * (to the temporary directory, or the {@value #TMPDIR_PROPERTY} one) to a randomly named file (not a predictable one, as the temporary directory is
     * usually shared between users), which is deleted once loaded.
     */
    private static void loadAndDelete(byte[] content) throws IOException {
        Path lib = Files.createTempFile(extractionDir(), "libsodiumjni-", libSuffix());
        try {
            Files.write(lib, content);
            loadExtracted(lib);
        } finally {
            Files.deleteIfExists(lib);
        }
    }

    /**
     * Windows: a loaded DLL can't be deleted, so rather than leaving a new file behind on every run,
     * it's extracted once to a file named after its content hash in the temporary directory (per user
     * on Windows) or the {@value #TMPDIR_PROPERTY} one, and that file is re-used as long as its content
     * matches.
     */
    private static void loadFromWindowsCache(byte[] content) throws IOException {
        String hash = sha256(content);
        Path tmpDir = extractionDir();
        Path lib = tmpDir.resolve("libsodiumjni-" + hash + ".dll");

        if (!hasContent(lib, content)) {
            Path tmp = Files.createTempFile(tmpDir, "libsodiumjni-", ".dll.tmp");
            try {
                Files.write(tmp, content);
                tryMove(tmp, lib);
            } finally {
                Files.deleteIfExists(tmp);
            }
            if (!hasContent(lib, content))
                throw new IOException("Could not extract the native library to " + lib);
        }

        loadExtracted(lib);
    }

    /**
     * Moves source to dest, replacing dest. Failures are ignored: they're expected when dest is
     * in use by another process that loaded it (it can't be replaced then), in which case dest
     * should already have the right content, which the caller checks.
     */
    private static void tryMove(Path source, Path dest) {
        try {
            Files.move(source, dest, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(source, dest, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // see above
            }
        } catch (IOException ignored) {
            // see above
        }
    }

    private static boolean hasContent(Path file, byte[] content) throws IOException {
        return Files.isRegularFile(file) && Arrays.equals(Files.readAllBytes(file), content);
    }

    private static String sha256(byte[] content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest)
                hex.append(Character.forDigit((b >> 4) & 0xf, 16)).append(Character.forDigit(b & 0xf, 16));
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // can't happen, SHA-256 is required to be supported by all JVMs
            throw new IllegalStateException(e);
        }
    }

    public static void assumeInitialized() {
        initialized = true;
    }

}
