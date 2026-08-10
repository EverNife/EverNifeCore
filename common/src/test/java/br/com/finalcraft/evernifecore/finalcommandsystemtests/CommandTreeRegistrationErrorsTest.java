package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.CommandShapeErrors;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.annotation.ElementType;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shape-error battery of the test engine, run against the framework that ships in this repository.
 * Every message has the same three jobs: name the class, name the member (or node path) and say the
 * call that fixes it - a registration error that only says "invalid node" is a bug report waiting to
 * happen.
 * <p>
 * The fixtures live in {@link CommandShapeErrors} rather than here so a plugin building trees of its
 * own can run the same battery without copying a single class.
 */
class CommandTreeRegistrationErrorsTest {

    //NEVER: see RegistrationSystemTest - the locale bootstrap's async saveAsync() can race JUnit's
    //default @TempDir cleanup on Windows.
    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("TreeErrors", tempDir);
        return harness;
    }

    static List<CommandShapeErrors.Case> shapeErrors() {
        return CommandShapeErrors.cases();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("shapeErrors")
    void theShapeIsRefusedWithAMessageThatTeaches(CommandShapeErrors.Case shapeError) {
        String message = newHarness().registerExpectingError(shapeError.executor()).getMessage();

        for (String fragment : shapeError.teaches()) {
            assertTrue(message.contains(fragment), "the message should mention [" + fragment + "]: " + message);
        }
    }

    // ------------------------------------------------------------------
    // Two refusals have no fixture because they are unrepresentable: the compiler and the annotation
    // declarations already close them, and only widening those would bring the runtime case back
    // ------------------------------------------------------------------

    @Test
    void subCmdAndNodeCannotLandOnTheSameMember() {
        Set<ElementType> subCmdTargets = new HashSet<>(Arrays.asList(FinalCMD.SubCMD.class.getAnnotation(Target.class).value()));
        Set<ElementType> nodeTargets = new HashSet<>(Arrays.asList(FinalCMD.Node.class.getAnnotation(Target.class).value()));

        subCmdTargets.retainAll(nodeTargets);
        assertTrue(subCmdTargets.isEmpty(), "@SubCMD and @Node must stay on disjoint element kinds, they were: " + subCmdTargets);
    }

    @Test
    void aNodeHasNoUsageToDeclare() {
        for (Method member : FinalCMD.Node.class.getDeclaredMethods()) {
            assertFalse(member.getName().equals("usage"), "@FinalCMD.Node must not gain a usage() member");
        }
    }

    // ------------------------------------------------------------------
    // What a refused shape costs at boot: the command, and nothing else
    // ------------------------------------------------------------------

    @Test
    void aRefusedShapeLosesTheCommandInsteadOfBreakingTheRegistration() {
        FinalCmdTestHarness harness = newHarness();

        List<FinalCMDPluginCommand> registered = harness.registerAll(new CommandShapeErrors.NodeWithoutChildren());

        assertTrue(registered.isEmpty(), "a malformed command must not register");
        assertNull(harness.platform.getCaptured("nochildcmd"), "the platform must never receive a malformed command");
    }
}
