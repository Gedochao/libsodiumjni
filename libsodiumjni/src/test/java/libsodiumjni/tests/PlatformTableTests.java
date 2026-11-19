package libsodiumjni.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import libsodiumjni.internal.LoadLibrary;
import org.junit.Assume;
import org.junit.Test;

/**
 * The supported platforms (classifier, directory of the native library under META-INF/native, file
 * extension) are listed in several places, which must agree:
 * <ul>
 *   <li>{@link LoadLibrary#resourcePath(String, String)}, where the library is loaded from
 *   <li>the build ({@code nativeResourceDirs} in project/utils), where it's put in the JAR (passed
 *       by the build in the libsodiumjni.test.nativeResourceDirs property)
 *   <li>.github/scripts/check-release-natives.sh, which checks releases have all of them
 *   <li>the README's table of supported platforms
 * </ul>
 */
public class PlatformTableTests {

    /** A row of the platforms table of check-release-natives.sh */
    private static final class ScriptPlatform {
        final String classifier;
        final String dir;
        final String ext;

        ScriptPlatform(String classifier, String dir, String ext) {
            this.classifier = classifier;
            this.dir = dir;
            this.ext = ext;
        }
    }

    private static Path workspace() {
        String workspace = System.getProperty("libsodiumjni.test.workspace", "");
        Assume.assumeFalse("no workspace passed by the build (not run by Mill)", workspace.isEmpty());
        return Paths.get(workspace);
    }

    /** classifier -> directory, from the build */
    private static Map<String, String> buildPlatforms() {
        String value = System.getProperty("libsodiumjni.test.nativeResourceDirs", "");
        Assume.assumeFalse("no platforms passed by the build (not run by Mill)", value.isEmpty());
        Map<String, String> platforms = new LinkedHashMap<>();
        for (String entry : value.split(",")) {
            int idx = entry.indexOf('=');
            platforms.put(entry.substring(0, idx), entry.substring(idx + 1));
        }
        return platforms;
    }

    /** Rows of the heredoc of the platforms() function of check-release-natives.sh */
    private static List<ScriptPlatform> scriptPlatforms() throws IOException {
        Path script = workspace().resolve(".github/scripts/check-release-natives.sh");
        List<String> lines = Files.readAllLines(script, StandardCharsets.UTF_8);
        int start = lines.indexOf("platforms() {");
        assertTrue("No platforms() function in " + script, start >= 0);
        List<ScriptPlatform> platforms = new ArrayList<>();
        // after "cat <<'EOF'", up to "EOF"
        for (int i = start + 2; i < lines.size() && !lines.get(i).equals("EOF"); i++) {
            String[] fields = lines.get(i).split(";", -1);
            assertEquals("Unexpected platforms line in " + script + ": " + lines.get(i), 5, fields.length);
            platforms.add(new ScriptPlatform(fields[0], fields[1], fields[2]));
        }
        assertFalse("No platforms found in " + script + ", is it parsed correctly?", platforms.isEmpty());
        return platforms;
    }

    /** Classifiers of the README's table of supported platforms */
    private static List<String> readmeClassifiers() throws IOException {
        String readme = Files.readString(workspace().resolve("README.md"), StandardCharsets.UTF_8);
        // e.g. "| Linux   | x86_64  | `x86_64-pc-linux`      |"
        Matcher m = Pattern.compile("(?m)^\\|[^|\\n]+\\|[^|\\n]+\\|\\s*`([^`]+)`\\s*\\|\\s*$").matcher(readme);
        List<String> classifiers = new ArrayList<>();
        while (m.find())
            classifiers.add(m.group(1));
        assertFalse("No platforms found in README.md, is its table parsed correctly?", classifiers.isEmpty());
        return classifiers;
    }

    /** os.name / os.arch values of a JVM running on the platform of a classifier */
    private static String[] jvmOsAndArch(String classifier) {
        String arch = classifier.substring(0, classifier.indexOf('-'));
        String os = classifier.substring(classifier.indexOf('-') + 1);
        switch (os) {
            // JVMs report x86_64 as amd64 on Linux and Windows, x86_64 on macOS
            case "pc-linux":
                return new String[] {"Linux", arch.equals("x86_64") ? "amd64" : arch};
            case "apple-darwin":
                return new String[] {"Mac OS X", arch};
            case "pc-win32":
                return new String[] {"Windows 11", arch.equals("x86_64") ? "amd64" : arch};
            default:
                throw new AssertionError("Unrecognized OS in classifier " + classifier);
        }
    }

    @Test
    public void allTablesHaveTheSamePlatforms() throws IOException {
        TreeSet<String> build = new TreeSet<>(buildPlatforms().keySet());
        TreeSet<String> script = new TreeSet<>();
        for (ScriptPlatform p : scriptPlatforms())
            script.add(p.classifier);
        TreeSet<String> readme = new TreeSet<>(readmeClassifiers());
        assertEquals("Platforms of check-release-natives.sh vs the build", build, script);
        assertEquals("Platforms of the README vs the build", build, readme);
    }

    @Test
    public void releaseCheckLooksForTheLibrariesWhereTheyAre() throws IOException {
        Map<String, String> build = buildPlatforms();
        for (ScriptPlatform p : scriptPlatforms()) {
            // where the build puts it, and the script looks for it
            assertEquals("Directory of " + p.classifier + " in check-release-natives.sh vs the build", build.get(p.classifier), p.dir);
            // where LoadLibrary loads it from
            String[] osAndArch = jvmOsAndArch(p.classifier);
            assertEquals(
                    "LoadLibrary.resourcePath for " + p.classifier + " vs check-release-natives.sh",
                    "META-INF/native/" + p.dir + "/libsodiumjni." + p.ext,
                    LoadLibrary.resourcePath(osAndArch[0], osAndArch[1]));
        }
    }
}
