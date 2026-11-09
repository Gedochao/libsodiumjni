package libsodiumjni.tests;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs a main class from the test class path in a fresh JVM, for tests that need a clean process
 * state (e.g. libsodium / libsodiumjni not initialized yet) or a modified environment.
 */
final class ChildJvm {

    static final class Result {
        final int exitCode;
        final String output;

        Result(int exitCode, String output) {
            this.exitCode = exitCode;
            this.output = output;
        }
    }

    private ChildJvm() {}

    static Result run(Class<?> mainClass, List<String> args, Map<String, String> env, Path workDir)
            throws IOException, InterruptedException {
        Path javaBin = Path.of(System.getProperty("java.home"), "bin", "java");
        List<String> command = new ArrayList<>();
        command.add(javaBin.toString());
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add(mainClass.getName());
        command.addAll(args);

        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        if (workDir != null) builder.directory(workDir.toFile());
        builder.environment().putAll(env);
        Process proc = builder.start();
        String output = new String(proc.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new Result(proc.waitFor(), output);
    }
}
