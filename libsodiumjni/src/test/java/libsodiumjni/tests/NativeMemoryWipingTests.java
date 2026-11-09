package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.Assume;
import org.junit.Test;

/**
 * Checks that the native code wipes the plaintext it copies, before freeing it.
 *
 * <p>Runs {@link FreeCheckerChild} in a child JVM with free_checker.c preloaded, which reports freed
 * native memory blocks still containing the secret. The child also leaves a control marker in freed
 * memory, to make sure the checker works with this JVM / OS (if it doesn't, this test fails).
 *
 * <p>Only heap memory can be checked this way. Secrets copied on the stack (key buffers) are wiped
 * too, but aren't covered by this test.
 */
public class NativeMemoryWipingTests {

    private static boolean isMac() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("mac");
    }

    private static boolean isLinux() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("linux");
    }

    private static boolean ccAvailable() {
        try {
            Process proc = new ProcessBuilder("cc", "--version").redirectErrorStream(true).start();
            proc.getInputStream().readAllBytes();
            return proc.waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    private static String run(List<String> command, Path workDir, Map<String, String> env)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(command).directory(workDir.toFile()).redirectErrorStream(true);
        builder.environment().putAll(env);
        Process proc = builder.start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = proc.waitFor();
        assertEquals(String.join(" ", command) + " failed:\n" + output, 0, exitCode);
        return output;
    }

    private static void deleteRecursively(Path dir) throws IOException {
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                Files.deleteIfExists(p);
        }
    }

    @Test
    public void plaintextIsWipedFromNativeMemoryBeforeBeingFreed() throws Exception {
        Assume.assumeTrue("macOS / Linux only", isMac() || isLinux());
        Assume.assumeTrue("needs a C compiler (cc)", ccAvailable());

        Path tmp = Files.createTempDirectory("libsodiumjni-free-checker");
        try {
            Path source = tmp.resolve("free_checker.c");
            try (InputStream is = NativeMemoryWipingTests.class.getClassLoader().getResourceAsStream("free_checker.c")) {
                Files.write(source, is.readAllBytes());
            }
            Path checker = tmp.resolve(isMac() ? "free_checker.dylib" : "free_checker.so");
            List<String> compile = new ArrayList<>(List.of("cc", "-shared", "-fPIC", "-O0", "-o", checker.toString(), source.toString()));
            if (isLinux()) compile.add("-ldl");
            run(compile, tmp, Map.of());

            SecureRandom random = new SecureRandom();
            byte[] secret = new byte[64];
            byte[] control = new byte[64];
            random.nextBytes(secret);
            random.nextBytes(control);
            String secretHex = HexFormat.of().formatHex(secret);
            String controlHex = HexFormat.of().formatHex(control);

            Path out = tmp.resolve("free_checker.out");
            ChildJvm.Result child = ChildJvm.run(
                    FreeCheckerChild.class,
                    List.of(secretHex, controlHex),
                    Map.of(
                            isMac() ? "DYLD_INSERT_LIBRARIES" : "LD_PRELOAD", checker.toString(),
                            "FREE_CHECKER_SECRET", secretHex,
                            "FREE_CHECKER_CONTROL", controlHex,
                            "FREE_CHECKER_OUT", out.toString()),
                    tmp);
            assertTrue(
                    "Child JVM didn't complete (exit code " + child.exitCode + "):\n" + child.output,
                    child.exitCode == 0 && child.output.contains("DONE"));

            List<String> report = Files.exists(out) ? Files.readAllLines(out) : List.of();
            assertTrue("free_checker wasn't loaded in the child JVM", report.contains("ACTIVE"));
            assertTrue(
                    "free_checker didn't detect the control marker in freed memory, so it can't be trusted here",
                    report.contains("LEAK control"));
            long secretLeaks = report.stream().filter("LEAK secret"::equals).count();
            assertFalse(
                    "The secret was found in " + secretLeaks + " freed native memory block(s)",
                    secretLeaks > 0);
        } finally {
            deleteRecursively(tmp);
        }
    }
}
