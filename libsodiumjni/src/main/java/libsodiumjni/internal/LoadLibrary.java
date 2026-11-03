package libsodiumjni.internal;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class LoadLibrary {

    private static boolean initialized = false;

    public static void initialize(String libraryPath) {
        if (!initialized) {
            synchronized (LoadLibrary.class) {
                System.load(libraryPath);
                initialized = true;
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


    public static void initializeFromResources() throws IOException {
        initializeFromResources(resourcePath());
    }

    public static void initializeFromResources(String resourcePath) throws IOException {
        if (!initialized) {
            synchronized (LoadLibrary.class) {
                URL resUrl = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
                if (resUrl == null)
                    throw new RuntimeException("Resource " + resourcePath + " not found");
                byte[] content = resUrl.openStream().readAllBytes();
                Path lib = Files.createTempFile("libsodium", libSuffix());
                Files.write(lib, content);
                System.load(lib.toString());
                initialized = true;
            }
        }
    }

    public static void assumeInitialized() {
        initialized = true;
    }

}
