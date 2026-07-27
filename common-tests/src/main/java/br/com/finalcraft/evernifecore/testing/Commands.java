package br.com.finalcraft.evernifecore.testing;

import java.nio.file.Path;

/**
 * Entry point for command tests: a harness that registers, dispatches and tab-completes through
 * the real FinalCMD framework, against a fake plugin of its own.
 */
public final class Commands {

    private Commands() {
    }

    /**
     * @param namePrefix  prefix of the fake plugin's name; a unique suffix is appended, so two
     *                    harnesses never share command labels or locale state
     * @param dataFolder  where the fake plugin writes its configs - a {@code @TempDir} normally
     */
    public static FinalCmdTestHarness harness(String namePrefix, Path dataFolder) {
        return new FinalCmdTestHarness(namePrefix, dataFolder);
    }
}
