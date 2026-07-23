package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.finalcommandsystemtests.harness.TestCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the registration flow of {@code FinalCMDManager.registerCommand} (matrix A): how a command
 * class is scanned into a {@link FinalCMDPluginCommand}, and what happens when the scan can't
 * produce one.
 */
class RegistrationSystemTest {

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
        harness = new FinalCmdTestHarness("Registration", tempDir);
        return harness;
    }

    // ------------------------------------------------------------------
    // A1 - @FinalCMD on the TYPE + @SubCMD methods -> N subcommands, sorted by label
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "a1cmd")
    public static class A1_TypeAnnotatedWithSubCommands {
        @FinalCMD.SubCMD(subcmd = "zulu")
        public void zulu(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "alpha")
        public void alpha(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "mike")
        public void mike(FCommandSender sender) {}
    }

    @Test
    void a1_typeAnnotatedWithSubCommandsRegistersSortedByLabel() {
        FinalCMDPluginCommand command = newHarness().register(new A1_TypeAnnotatedWithSubCommands());

        assertNotNull(command);
        assertEquals(3, command.getSubCommands().size());
        assertEquals(
                List.of("alpha", "mike", "zulu"),
                command.getSubCommands().stream().map(i -> i.getLabels()[0]).toList()
        );
    }

    // ------------------------------------------------------------------
    // A2 - @FinalCMD on a single method (no subcommands) -> main interpreter only
    // ------------------------------------------------------------------

    public static class A2_SingleMainMethod {
        @FinalCMD(aliases = "a2cmd")
        public void run(FCommandSender sender) {}
    }

    @Test
    void a2_singleMainMethodHasNoSubCommands() {
        FinalCMDPluginCommand command = newHarness().register(new A2_SingleMainMethod());

        assertNotNull(command);
        assertNotNull(command.getMainInterpreter());
        assertTrue(command.getSubCommands().isEmpty());
    }

    // ------------------------------------------------------------------
    // A3 - several @FinalCMD methods on the same class -> N independent commands;
    // a @SubCMD found alongside them is ignored (logged severe, not registered as a subcommand)
    // ------------------------------------------------------------------

    public static class A3_SeveralIndependentFinalCMDs {
        @FinalCMD(aliases = "a3cmdone")
        public void one(FCommandSender sender) {}

        @FinalCMD(aliases = "a3cmdtwo")
        public void two(FCommandSender sender) {}

        //Ignored with a severe log: SubCMD is meaningless once the class has more than one @FinalCMD
        @FinalCMD.SubCMD(subcmd = "orphan")
        public void orphan(FCommandSender sender) {}
    }

    @Test
    void a3_severalFinalCMDMethodsRegisterAsIndependentCommandsAndIgnoreTheStraySubCMD() {
        List<FinalCMDPluginCommand> commands = newHarness().registerAll(new A3_SeveralIndependentFinalCMDs());

        assertEquals(2, commands.size());
        assertTrue(commands.stream().anyMatch(c -> c.getPrimaryLabel().equals("a3cmdone")));
        assertTrue(commands.stream().anyMatch(c -> c.getPrimaryLabel().equals("a3cmdtwo")));
        //Neither independent command picked up the stray @SubCMD as one of its own
        assertTrue(commands.stream().allMatch(c -> c.getSubCommands().isEmpty()));
    }

    // ------------------------------------------------------------------
    // A4 - class with no @FinalCMD annotation at all -> registerCommand returns false
    // ------------------------------------------------------------------

    public static class A4_NoAnnotationAtAll {
        public void doStuff(FCommandSender sender) {}
    }

    @Test
    void a4_classWithoutAnyFinalCMDAnnotationFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new A4_NoAnnotationAtAll());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // A5 - @FinalCMD.Ignore excludes the method from the scan
    // ------------------------------------------------------------------

    public static class A5_IgnoredMethod {
        @FinalCMD(aliases = "a5cmd")
        public void run(FCommandSender sender) {}

        @FinalCMD.Ignore
        @FinalCMD(aliases = "a5ignored")
        public void ignoredMethod(FCommandSender sender) {}
    }

    @Test
    void a5_ignoreAnnotationExcludesTheMethodFromTheScan() {
        //Only "a5cmd" should have been produced - "a5ignored" was skipped entirely
        List<FinalCMDPluginCommand> commands = newHarness().registerAll(new A5_IgnoredMethod());

        assertEquals(1, commands.size());
        assertEquals("a5cmd", commands.get(0).getPrimaryLabel());
    }

    // ------------------------------------------------------------------
    // A6 - a method with no contextual arg at all -> registration fails
    // (IllegalStateException("no contextual args") is thrown by CMDMethodInterpreter's constructor,
    // but FinalCMDManager.registerCommand wraps its whole body in a catch(Throwable), so the
    // observable result at this API is simply registerCommand() returning false - it never throws)
    // ------------------------------------------------------------------

    public static class A6_NoContextualArgAtAll {
        @FinalCMD(aliases = "a6cmd")
        public void run(@Arg(name = "<x>") String x) {}
    }

    @Test
    void a6_methodWithNoContextualArgFailsRegistrationInsteadOfThrowing() {
        boolean registered = newHarness().registerExpectingFailure(new A6_NoContextualArgAtAll());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // A7 - a parameter with an unrelated annotation (e.g. @Deprecated) falls back to contextual
    // ------------------------------------------------------------------

    public static class A7_UnrelatedAnnotationFallsBackToContextual {
        static FCommandSender lastSeenSender;

        @FinalCMD(aliases = "a7cmd")
        @SuppressWarnings("deprecation")
        public void run(@Deprecated FCommandSender sender) {
            lastSeenSender = sender;
        }
    }

    @Test
    void a7_parameterWithUnrelatedAnnotationIsTreatedAsContextual() {
        FinalCMDPluginCommand command = newHarness().register(new A7_UnrelatedAnnotationFallsBackToContextual());
        assertNotNull(command, "registration should have succeeded: @Deprecated is not a recognized command annotation, so it falls back to contextual");

        TestCommandSender sender = new TestCommandSender("console");
        harness.dispatch(command, sender, "");

        assertEquals(sender, A7_UnrelatedAnnotationFallsBackToContextual.lastSeenSender);
    }

    // ------------------------------------------------------------------
    // A8 - a subclass method that doesn't redeclare annotations falls back to the parent's @Arg
    // ------------------------------------------------------------------

    public static abstract class A8_Base {
        static String lastReceived;

        @FinalCMD(aliases = "a8cmd")
        public void run(FCommandSender sender, @Arg(name = "<value>") String value) {
            lastReceived = value;
        }
    }

    public static class A8_Subclass extends A8_Base {
        @Override
        public void run(FCommandSender sender, String value) { //no re-declared annotations
            super.run(sender, value);
        }
    }

    @Test
    void a8_subclassMethodWithoutAnnotationsFallsBackToTheParentsArgs() {
        FinalCMDPluginCommand command = newHarness().register(new A8_Subclass());
        assertNotNull(command, "MethodArgScanner should have climbed to the parent to find @Arg");

        TestCommandSender sender = new TestCommandSender("console");
        harness.dispatch(command, sender, "hello");

        assertEquals("hello", A8_Subclass.lastReceived);
    }

    // ------------------------------------------------------------------
    // A9 - re-registering the same label unregisters the old one first (captured on the platform),
    // no duplicate ends up registered
    // ------------------------------------------------------------------

    public static class A9_First {
        @FinalCMD(aliases = "a9cmd")
        public void run(FCommandSender sender) {}
    }

    public static class A9_Second {
        @FinalCMD(aliases = "a9cmd")
        public void run(FCommandSender sender) {}
    }

    @Test
    void a9_reregisterSameLabelUnregistersFirst() {
        newHarness();
        FinalCMDPluginCommand first = harness.register(new A9_First());
        FinalCMDPluginCommand second = harness.register(new A9_Second());

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(harness.platform.getUnregisteredLabels().contains("a9cmd"));
        //The label now resolves to the SECOND registration, not a duplicate of the first
        assertEquals(second, harness.platform.getCaptured("a9cmd"));
    }

    // ------------------------------------------------------------------
    // A10 - @Arg on a type with no registered ArgParser -> registration fails
    // ------------------------------------------------------------------

    private static final class UnregisteredType {
    }

    public static class A10_UnregisteredParserType {
        @FinalCMD(aliases = "a10cmd")
        public void run(FCommandSender sender, @Arg(name = "<x>") UnregisteredType value) {}
    }

    @Test
    void a10_argWithNoRegisteredParserFailsRegistrationInsteadOfThrowing() {
        boolean registered = newHarness().registerExpectingFailure(new A10_UnregisteredParserType());

        assertFalse(registered);
    }
}
