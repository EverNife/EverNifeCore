package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FlagArg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.MethodData;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.FinalCmdTestHarness;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the TEMPORARY fail-fast guard on {@code @FlagArg}: the declarative flag pipeline is not
 * wired into {@link CMDMethodInterpreter#invoke} yet, so a method that declares a {@link FlagArg}
 * parameter must refuse to register instead of registering and crashing on every single invoke with
 * a wrong-number-of-arguments {@link IllegalArgumentException}. A later iteration removes this guard
 * once the declarative pipeline lands.
 */
class FlagArgFailFastSystemTest {

    //NEVER: ECPluginData's locale bootstrap fires an async saveAsync() (EveryConfig, virtual-thread
    //executor) for every hardcoded language file; JUnit's default cleanup can race that in-flight
    //write and fail to delete the directory (observed on Windows). Leftovers land under the OS temp
    //folder, not the repo.
    @TempDir(cleanup = CleanupMode.NEVER)
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    private FinalCmdTestHarness newHarness() {
        harness = new FinalCmdTestHarness("FlagArgFailFast", tempDir);
        return harness;
    }

    public static class FlagArgNotWiredYet {
        @FinalCMD(aliases = "flagargfailfastcmd")
        public void run(FCommandSender sender, @FlagArg(name = "--force") Boolean force) {}
    }

    // ------------------------------------------------------------------
    // Observable end-to-end behavior: FinalCMDManager.registerCommand wraps its whole body in a
    // catch(Throwable), so the only thing callers of the public API ever see is registerCommand()
    // returning false - same shape as the RegistrationSystemTest A6/A10 scenarios.
    // ------------------------------------------------------------------

    @Test
    void flagArgParameterFailsRegistrationInsteadOfCrashingOnInvoke() {
        boolean registered = newHarness().registerExpectingFailure(new FlagArgNotWiredYet());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // Exact exception type: constructing MethodData/CMDMethodInterpreter directly (both are public)
    // bypasses FinalCMDManager's catch(Throwable) and proves the actual failure is an
    // IllegalStateException carrying a message that points at @FlagArg, not merely "some exception".
    // ------------------------------------------------------------------

    @Test
    void constructingTheInterpreterDirectlyProvesTheExactExceptionType() throws NoSuchMethodException {
        FinalCmdTestHarness harness = newHarness();
        Method method = FlagArgNotWiredYet.class.getMethod("run", FCommandSender.class, Boolean.class);
        FinalCMDData finalCMDData = new FinalCMDData(method.getAnnotation(FinalCMD.class));
        MethodData<FinalCMDData> methodData = new MethodData<>(finalCMDData, method);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new CMDMethodInterpreter(harness.ecPluginData, methodData, new FlagArgNotWiredYet()));

        assertTrue(exception.getMessage().contains("@FlagArg"));
    }
}
