package libsodiumjni.tests;

import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.Test;

/**
 * Checks Sodium.init() in fresh JVMs (see SodiumInitChild): in this JVM, other tests may have
 * initialized Sodium already, which would make these tests pass vacuously.
 */
public class SodiumInitTests {

    private static void assertChildSucceeds(String scenario) throws Exception {
        ChildJvm.Result result = ChildJvm.run(SodiumInitChild.class, List.of(scenario), Map.of(), null);
        assertTrue(
                "Scenario '" + scenario + "' failed (exit code " + result.exitCode + "):\n" + result.output,
                result.exitCode == 0 && result.output.contains("OK"));
    }

    @Test
    public void initSucceedsWhenLibsodiumIsAlreadyInitialized() throws Exception {
        assertChildSucceeds("already-initialized");
    }

    @Test
    public void concurrentInitCallsSucceed() throws Exception {
        assertChildSucceeds("concurrent");
    }
}
