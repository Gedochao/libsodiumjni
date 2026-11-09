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
 */
public final class LoadLibrary {

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
        String osArch = osArch0.toLowerCase(Locale.ROOT);
        boolean isArmArch = osArch.contains("aarch64");

        String osName = osName0.toLowerCase(Locale.ROOT);
        if (osName.contains("linux")) {
            switch (osArch) {
                case "amd64":
                case "x86_64":
                    return "META-INF/native/linux64/libsodiumjni.so";
                case "aarch64":
                    return "META-INF/native/linux-aarch64/libsodiumjni.so";
                default:
                    throw new RuntimeException("Unsupported architecture on Linux: " + osArch0);
            }
        }
        else if (osName.contains("mac") && isArmArch) return "META-INF/native/arm-darwin/libsodiumjni.dylib";
        else if (osName.contains("mac")) return "META-INF/native/darwin/libsodiumjni.dylib";
        else if (osName.contains("windows")) return "META-INF/native/windows64/libsodiumjni.dll";
        else
          throw new RuntimeException("Unrecognized or unsupported OS: " + osName);
    }

    public static String libSuffix() {
        String osName = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (osName.contains("linux")) return ".so";
        else if (osName.contains("mac")) return ".dylib";
        else if (osName.contains("windows")) return ".dll";
        else
          throw new RuntimeException("Unrecognized or unsupported OS: " + osName);
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
            throw new RuntimeException("Resource " + resourcePath + " not found");
        try (InputStream is = resUrl.openStream()) {
            return is.readAllBytes();
        }
    }

    /**
     * Linux / macOS: a loaded library can be deleted right away (it stays mapped), so it's extracted
     * to a randomly named temporary file (not a predictable one, as the temporary directory is
     * usually shared between users), which is deleted once loaded.
     */
    private static void loadAndDelete(byte[] content) throws IOException {
        Path lib = Files.createTempFile("libsodiumjni-", libSuffix());
        try {
            Files.write(lib, content);
            System.load(lib.toString());
        } finally {
            Files.deleteIfExists(lib);
        }
    }

    /**
     * Windows: a loaded DLL can't be deleted, so rather than leaving a new file behind on every run,
     * it's extracted once to a file named after its content hash in the temporary directory (per user
     * on Windows), and that file is re-used as long as its content matches.
     */
    private static void loadFromWindowsCache(byte[] content) throws IOException {
        String hash = sha256(content);
        Path tmpDir = Paths.get(System.getProperty("java.io.tmpdir"));
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

        System.load(lib.toString());
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
            throw new RuntimeException(e);
        }
    }

    public static void assumeInitialized() {
        initialized = true;
    }

}
