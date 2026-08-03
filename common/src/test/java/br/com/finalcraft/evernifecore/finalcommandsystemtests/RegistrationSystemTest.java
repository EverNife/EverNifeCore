package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
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
 * Pins the registration flow of {@code FinalCMDManager.registerCommand}: how a command
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
    // @FinalCMD on the TYPE + @SubCMD methods -> N subcommands, sorted by label
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "typesubcmds")
    public static class TypeAnnotatedWithSubCommands {
        @FinalCMD.SubCMD(subcmd = "zulu")
        public void zulu(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "alpha")
        public void alpha(FCommandSender sender) {}

        @FinalCMD.SubCMD(subcmd = "mike")
        public void mike(FCommandSender sender) {}
    }

    @Test
    void typeAnnotatedWithSubCommandsRegistersSortedByLabel() {
        FinalCMDPluginCommand command = newHarness().register(new TypeAnnotatedWithSubCommands());

        assertNotNull(command);
        assertEquals(3, command.getRoot().getChildren().size());
        assertEquals(
                List.of("alpha", "mike", "zulu"),
                command.getRoot().getChildren().stream().map(i -> i.getPrimaryLabel()).toList()
        );
    }

    // ------------------------------------------------------------------
    // @FinalCMD on a single method (no subcommands) -> main interpreter only
    // ------------------------------------------------------------------

    public static class SingleMainMethod {
        @FinalCMD(aliases = "singlemain")
        public void run(FCommandSender sender) {}
    }

    @Test
    void singleMainMethodHasNoSubCommands() {
        FinalCMDPluginCommand command = newHarness().register(new SingleMainMethod());

        assertNotNull(command);
        assertNotNull(command.getMainInterpreter());
        assertTrue(command.getRoot().getChildren().isEmpty());
    }

    // ------------------------------------------------------------------
    // several @FinalCMD methods on the same class -> N independent commands;
    // a @SubCMD found alongside them is ignored (logged severe, not registered as a subcommand)
    // ------------------------------------------------------------------

    public static class SeveralIndependentFinalCMDs {
        @FinalCMD(aliases = "independentone")
        public void one(FCommandSender sender) {}

        @FinalCMD(aliases = "independenttwo")
        public void two(FCommandSender sender) {}

        //Ignored with a severe log: SubCMD is meaningless once the class has more than one @FinalCMD
        @FinalCMD.SubCMD(subcmd = "orphan")
        public void orphan(FCommandSender sender) {}
    }

    @Test
    void severalFinalCMDMethodsRegisterAsIndependentCommandsAndIgnoreTheStraySubCMD() {
        List<FinalCMDPluginCommand> commands = newHarness().registerAll(new SeveralIndependentFinalCMDs());

        assertEquals(2, commands.size());
        assertTrue(commands.stream().anyMatch(c -> c.getPrimaryLabel().equals("independentone")));
        assertTrue(commands.stream().anyMatch(c -> c.getPrimaryLabel().equals("independenttwo")));
        //Neither independent command picked up the stray @SubCMD as one of its own
        assertTrue(commands.stream().allMatch(c -> c.getRoot().getChildren().isEmpty()));
    }

    // ------------------------------------------------------------------
    // class with no @FinalCMD annotation at all -> registerCommand returns false
    // ------------------------------------------------------------------

    public static class NoAnnotationAtAll {
        public void doStuff(FCommandSender sender) {}
    }

    @Test
    void classWithoutAnyFinalCMDAnnotationFailsRegistration() {
        boolean registered = newHarness().registerExpectingFailure(new NoAnnotationAtAll());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // @FinalCMD.Ignore excludes the method from the scan
    // ------------------------------------------------------------------

    public static class IgnoredMethod {
        @FinalCMD(aliases = "ignorekept")
        public void run(FCommandSender sender) {}

        @FinalCMD.Ignore
        @FinalCMD(aliases = "ignoreskipped")
        public void ignoredMethod(FCommandSender sender) {}
    }

    @Test
    void ignoreAnnotationExcludesTheMethodFromTheScan() {
        //Only "ignorekept" should have been produced - "ignoreskipped" was skipped entirely
        List<FinalCMDPluginCommand> commands = newHarness().registerAll(new IgnoredMethod());

        assertEquals(1, commands.size());
        assertEquals("ignorekept", commands.get(0).getPrimaryLabel());
    }

    // ------------------------------------------------------------------
    // a method with no contextual arg at all -> registration fails
    // (IllegalStateException("no contextual args") is thrown by CMDMethodInterpreter's constructor,
    // but FinalCMDManager.registerCommand wraps its whole body in a catch(Throwable), so the
    // observable result at this API is simply registerCommand() returning false - it never throws)
    // ------------------------------------------------------------------

    public static class NoContextualArgAtAll {
        @FinalCMD(aliases = "nocontextualarg")
        public void run(@Arg("<x>") String x) {}
    }

    @Test
    void methodWithNoContextualArgFailsRegistrationInsteadOfThrowing() {
        boolean registered = newHarness().registerExpectingFailure(new NoContextualArgAtAll());

        assertFalse(registered);
    }

    // ------------------------------------------------------------------
    // a parameter with an unrelated annotation (e.g. @Deprecated) falls back to contextual
    // ------------------------------------------------------------------

    public static class UnrelatedAnnotationFallsBackToContextual {
        static FCommandSender lastSeenSender;

        @FinalCMD(aliases = "unrelatedannotation")
        @SuppressWarnings("deprecation")
        public void run(@Deprecated FCommandSender sender) {
            lastSeenSender = sender;
        }
    }

    @Test
    void parameterWithUnrelatedAnnotationIsTreatedAsContextual() {
        FinalCMDPluginCommand command = newHarness().register(new UnrelatedAnnotationFallsBackToContextual());
        assertNotNull(command, "registration should have succeeded: @Deprecated is not a recognized command annotation, so it falls back to contextual");

        TestCommandSender sender = new TestCommandSender("console");
        harness.dispatch(command, sender, "");

        assertEquals(sender, UnrelatedAnnotationFallsBackToContextual.lastSeenSender);
    }

    // ------------------------------------------------------------------
    // a subclass method that doesn't redeclare annotations falls back to the parent's @Arg
    // ------------------------------------------------------------------

    public static abstract class InheritedArgs_Base {
        static String lastReceived;

        @FinalCMD(aliases = "inheritedargs")
        public void run(FCommandSender sender, @Arg("<value>") String value) {
            lastReceived = value;
        }
    }

    public static class InheritedArgs_Subclass extends InheritedArgs_Base {
        @Override
        public void run(FCommandSender sender, String value) { //no re-declared annotations
            super.run(sender, value);
        }
    }

    @Test
    void subclassMethodWithoutAnnotationsFallsBackToTheParentsArgs() {
        FinalCMDPluginCommand command = newHarness().register(new InheritedArgs_Subclass());
        assertNotNull(command, "MethodArgScanner should have climbed to the parent to find @Arg");

        TestCommandSender sender = new TestCommandSender("console");
        harness.dispatch(command, sender, "hello");

        assertEquals("hello", InheritedArgs_Subclass.lastReceived);
    }

    // ------------------------------------------------------------------
    // re-registering the same label unregisters the old one first (captured on the platform),
    // no duplicate ends up registered
    // ------------------------------------------------------------------

    public static class SameLabel_First {
        @FinalCMD(aliases = "samelabel")
        public void run(FCommandSender sender) {}
    }

    public static class SameLabel_Second {
        @FinalCMD(aliases = "samelabel")
        public void run(FCommandSender sender) {}
    }

    @Test
    void reregisterSameLabelUnregistersFirst() {
        newHarness();
        FinalCMDPluginCommand first = harness.register(new SameLabel_First());
        FinalCMDPluginCommand second = harness.register(new SameLabel_Second());

        assertNotNull(first);
        assertNotNull(second);
        assertTrue(harness.platform.getUnregisteredLabels().contains("samelabel"));
        //The label now resolves to the SECOND registration, not a duplicate of the first
        assertEquals(second, harness.platform.getCaptured("samelabel"));
    }

    // ------------------------------------------------------------------
    // @Arg on a type with no registered ArgParser -> registration fails
    // ------------------------------------------------------------------

    private static final class UnregisteredType {
    }

    public static class UnregisteredParserType {
        @FinalCMD(aliases = "unregisteredparser")
        public void run(FCommandSender sender, @Arg("<x>") UnregisteredType value) {}
    }

    @Test
    void argWithNoRegisteredParserFailsRegistrationInsteadOfThrowing() {
        boolean registered = newHarness().registerExpectingFailure(new UnregisteredParserType());

        assertFalse(registered);
    }
}
