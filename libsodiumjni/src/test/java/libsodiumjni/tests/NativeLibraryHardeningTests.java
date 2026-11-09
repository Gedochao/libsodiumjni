package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;

import libsodiumjni.internal.LoadLibrary;
import org.junit.Assume;
import org.junit.Test;

/**
 * Checks that the native library is built with stack protection (-fstack-protector-strong), which
 * makes it import the stack canary failure handler. Many toolchains enable it by default, but the
 * build shouldn't rely on that.
 *
 * <p>Linux only: on macOS, libsodium is linked statically in the library, and its own objects
 * import that handler, whatever the flags used for the libsodiumjni code. (On Linux, libsodium is a
 * separate shared library.)
 */
public class NativeLibraryHardeningTests {

    private static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static boolean nmAvailable() {
        try {
            Process proc = new ProcessBuilder("nm", "--version").redirectErrorStream(true).start();
            proc.getInputStream().readAllBytes();
            proc.waitFor();
            return true;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    public void nativeLibraryHasStackProtection() throws Exception {
        Assume.assumeTrue("Linux only", isLinux());
        Assume.assumeTrue("needs nm", nmAvailable());

        String resourcePath = LoadLibrary.resourcePath();
        URL url = NativeLibraryHardeningTests.class.getClassLoader().getResource(resourcePath);
        Path lib = Files.createTempFile("libsodiumjni-hardening", ".so");
        try {
            try (InputStream is = url.openStream()) {
                Files.copy(is, lib, StandardCopyOption.REPLACE_EXISTING);
            }
            // undefined (imported) symbols
            Process proc = new ProcessBuilder("nm", "-D", "--undefined-only", lib.toString())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("nm failed:\n" + output, 0, proc.waitFor());
            assertTrue(
                    resourcePath + " doesn't import __stack_chk_fail, is it built with stack protection?\n" + output,
                    output.contains("__stack_chk_fail"));
        } finally {
            Files.deleteIfExists(lib);
        }
    }
}
