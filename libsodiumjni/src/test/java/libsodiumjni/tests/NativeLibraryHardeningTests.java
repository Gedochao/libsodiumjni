package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import libsodiumjni.internal.LoadLibrary;
import org.junit.Assume;
import org.junit.Test;

/**
 * Checks the hardening of the native libraries: on Linux, full RELRO and a non-executable stack, on
 * Windows, ASLR, DEP and Control Flow Guard (see the link options in project/settings). Read from
 * the bundled library itself, rather than with readelf / dumpbin (not available everywhere).
 *
 * <p>Also checks that the libsodiumjni native code is compiled with stack protection
 * (-fstack-protector-strong), which makes it import the stack canary failure handler. Many
 * toolchains enable it by default, but the build shouldn't rely on that.
 *
 * <p>This checks the object files of the libsodiumjni code (passed by the build in the
 * libsodiumjni.test.objectFiles property), rather than the native library: libsodium is linked
 * statically in the latter on Linux and macOS, and its own objects import that handler, whatever
 * the flags used for the libsodiumjni code.
 */
public class NativeLibraryHardeningTests {

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
    public void nativeCodeHasStackProtection() throws Exception {
        String objectFiles = System.getProperty("libsodiumjni.test.objectFiles", "");
        Assume.assumeFalse("no object files passed by the build (Windows, or not run by Mill)", objectFiles.isEmpty());
        Assume.assumeTrue("needs nm", nmAvailable());

        for (String objectFile : objectFiles.split(File.pathSeparator)) {
            // undefined (imported) symbols, e.g. "___stack_chk_fail" on macOS, "__stack_chk_fail" on Linux
            Process proc = new ProcessBuilder("nm", "-u", objectFile).redirectErrorStream(true).start();
            String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("nm failed:\n" + output, 0, proc.waitFor());
            assertFalse("No undefined symbols found in " + objectFile + ", is nm output parsed correctly?", output.isBlank());
            assertTrue(
                    objectFile + " doesn't import __stack_chk_fail, is it compiled with stack protection?\n" + output,
                    output.contains("__stack_chk_fail"));
        }
    }

    private static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    }

    private static byte[] bundledLibrary() throws IOException {
        String resourcePath = LoadLibrary.resourcePath();
        URL url = NativeLibraryHardeningTests.class.getClassLoader().getResource(resourcePath);
        assertTrue("Resource " + resourcePath + " not found", url != null);
        try (InputStream is = url.openStream()) {
            return is.readAllBytes();
        }
    }

    /** Hardening problems of a Linux shared library (none if it's hardened) */
    static List<String> linuxHardeningProblems(byte[] library) {
        ElfFile.Hardening hardening = ElfFile.hardening(library);
        List<String> problems = new ArrayList<>();
        if (!hardening.relro)
            problems.add("no RELRO (GNU_RELRO program header, link with -z relro)");
        if (!hardening.bindNow)
            problems.add("only partial RELRO, symbols aren't bound at load time (link with -z now)");
        if (!hardening.hasStackHeader || hardening.executableStack)
            problems.add("executable stack (link with -z noexecstack)");
        return problems;
    }

    /** Hardening problems of a Windows DLL (none if it's hardened) */
    static List<String> windowsHardeningProblems(byte[] library) {
        int characteristics = PeFile.dllCharacteristics(library);
        List<String> problems = new ArrayList<>();
        if ((characteristics & PeFile.DYNAMIC_BASE) == 0)
            problems.add("no ASLR (DYNAMIC_BASE, link with /DYNAMICBASE)");
        if ((characteristics & PeFile.HIGH_ENTROPY_VA) == 0)
            problems.add("no high entropy ASLR (HIGH_ENTROPY_VA, link with /HIGHENTROPYVA)");
        if ((characteristics & PeFile.NX_COMPAT) == 0)
            problems.add("no DEP (NX_COMPAT, link with /NXCOMPAT)");
        if ((characteristics & PeFile.GUARD_CF) == 0)
            problems.add("no Control Flow Guard (GUARD_CF, compile and link with /guard:cf)");
        return problems;
    }

    @Test
    public void linuxLibraryIsHardened() throws Exception {
        Assume.assumeTrue("Linux-only check", isLinux());
        List<String> problems = linuxHardeningProblems(bundledLibrary());
        assertTrue("libsodiumjni.so isn't hardened: " + problems, problems.isEmpty());
    }

    @Test
    public void windowsDllIsHardened() throws Exception {
        Assume.assumeTrue("Windows-only check", isWindows());
        List<String> problems = windowsHardeningProblems(bundledLibrary());
        assertTrue("libsodiumjni.dll isn't hardened: " + problems, problems.isEmpty());
    }
}
