package br.com.finalcraft.evernifecore.testing;

import java.nio.file.Path;
import java.util.List;

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

    /**
     * A fresh four-level tree with everything the traversal has to get right - captures, a node
     * executable, a two-token capture, a flag declared on a node and a variadic tail. Register it
     * instead of writing another fixture; see {@link ReferenceCommandTree}.
     */
    public static ReferenceCommandTree referenceTree() {
        return new ReferenceCommandTree();
    }

    /**
     * Every shape the framework refuses at registration, one minimal class each, with the fragments
     * its error message has to carry. See {@link CommandShapeErrors}.
     */
    public static List<CommandShapeErrors.Case> shapeErrors() {
        return CommandShapeErrors.cases();
    }
}
