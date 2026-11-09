package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Checks LoadLibrary.initializeFromResources() in fresh JVMs (see LoadLibraryChild), each with its
 * own temporary directory, to check what is left there.
 */
public class LoadLibraryInitializationTests {

    private static boolean isWindows() {
        return System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("windows");
    }

    /**
     * Files the extracted library may leave behind in the temporary directory: none where a loaded
     * library can be deleted, a single cached one on Windows, where it can't
     */
    private static int maxLeftOverFiles() {
        return isWindows() ? 1 : 0;
    }

    private static void runChild(String scenario, Path tmpDir) throws Exception {
        ChildJvm.Result result = ChildJvm.run(
                List.of("-Djava.io.tmpdir=" + tmpDir),
                LoadLibraryChild.class,
                List.of(scenario),
                Map.of(),
                null);
        assertTrue(
                "Scenario '" + scenario + "' failed (exit code " + result.exitCode + "):\n" + result.output,
                result.exitCode == 0 && result.output.contains("OK"));
    }

    private static List<String> files(Path dir) throws IOException {
        try (Stream<Path> paths = Files.list(dir)) {
            return paths.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    private static void withTmpDir(TmpDirTest test) throws Exception {
        Path tmpDir = Files.createTempDirectory("libsodiumjni-load-tests");
        try {
            test.run(tmpDir);
        } finally {
            try (Stream<Path> paths = Files.walk(tmpDir)) {
                for (Path p : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator)
                    Files.deleteIfExists(p);
            }
        }
    }

    private interface TmpDirTest {
        void run(Path tmpDir) throws Exception;
    }

    @Test
    public void loadsWithNullContextClassLoader() throws Exception {
        withTmpDir(tmpDir -> runChild("null-context-class-loader", tmpDir));
    }

    @Test
    public void missingResourceThrowsIllegalStateException() throws Exception {
        withTmpDir(tmpDir -> runChild("missing-resource", tmpDir));
    }

    @Test
    public void extractedLibrariesDontPileUp() throws Exception {
        withTmpDir(tmpDir -> {
            runChild("load", tmpDir);
            runChild("load", tmpDir);
            List<String> files = files(tmpDir);
            assertTrue(
                    "Expected at most " + maxLeftOverFiles() + " file(s) left in the temporary directory after two runs, got " + files,
                    files.size() <= maxLeftOverFiles());
        });
    }

    @Test
    public void concurrentLoadsExtractTheLibraryOnce() throws Exception {
        withTmpDir(tmpDir -> {
            runChild("concurrent", tmpDir);
            List<String> files = files(tmpDir);
            assertTrue(
                    "Expected at most " + maxLeftOverFiles() + " file(s) left in the temporary directory, got " + files,
                    files.size() <= maxLeftOverFiles());
        });
    }

    @Test
    public void windowsCacheIsReused() throws Exception {
        org.junit.Assume.assumeTrue("Windows only", isWindows());
        withTmpDir(tmpDir -> {
            runChild("load", tmpDir);
            List<String> first = files(tmpDir);
            runChild("load", tmpDir);
            assertEquals(first, files(tmpDir));
            assertEquals(1, first.size());
        });
    }
}
