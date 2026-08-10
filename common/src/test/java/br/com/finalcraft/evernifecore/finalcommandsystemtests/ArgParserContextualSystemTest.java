package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserContextual;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ContextualParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.Logs;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.testing.TestLocaleMessage;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A parameter resolved from the surroundings instead of from a token answers through the same engine a
 * positional one does: the parser says what happened, and the framework decides whether the command
 * still runs and who gets told.
 */
class ArgParserContextualSystemTest {

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
        harness = new FinalCmdTestHarness("Contextual", tempDir);
        return harness;
    }

    /** A parameter no token could ever spell - it exists only to be read off the invocation. */
    public static class Widget {
        final String owner;

        Widget(String owner) {
            this.owner = owner;
        }
    }

    public static class ResolvingParser extends ArgParserContextual<Widget> {
        public ResolvingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Widget> parse(@Nonnull ContextualParseCall call) {
            return ParseResult.of(new Widget(call.getSender().getName()));
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    public static class RefusingParser extends ArgParserContextual<Widget> {
        static final String NO_WIDGET = "you are not carrying a widget";

        public RefusingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Widget> parse(@Nonnull ContextualParseCall call) {
            return denied(new TestLocaleMessage(NO_WIDGET));
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    /**
     * A parser whose refusal text is a real {@code @FCLocale} field instead of a hand-built message.
     * The field is null until somebody scans this class, and naming it on the parameter is the only
     * thing that ever brings the parser into a command - it never passes through {@code ArgParserManager}.
     */
    public static class LocalizedRefusingParser extends ArgParserContextual<Widget> {
        static final String NO_WIDGET_TEXT = "the vault refused to hand you a widget";

        @FCLocale(text = NO_WIDGET_TEXT)
        public static LocaleMessage NO_WIDGET;

        public LocalizedRefusingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Widget> parse(@Nonnull ContextualParseCall call) {
            return denied(NO_WIDGET);
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    /**
     * A parser whose refusal carries a message that is not there - the shape a real one takes when its
     * {@code @FCLocale} field was never scanned, and the reason nobody can be told anything.
     */
    public static class UndeliverableRefusingParser extends ArgParserContextual<Widget> {
        public UndeliverableRefusingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Widget> parse(@Nonnull ContextualParseCall call) {
            return denied((LocaleMessage) null);
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    public static class EmptyHandedParser extends ArgParserContextual<Widget> {
        public EmptyHandedParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Widget> parse(@Nonnull ContextualParseCall call) {
            return ParseResult.empty();
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    public static class ExplodingParser extends ArgParserContextual<Widget> {
        static final String BOOM = "the widget registry is down";

        public ExplodingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Widget> parse(@Nonnull ContextualParseCall call) {
            throw new IllegalStateException(BOOM);
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    /**
     * Nothing to give, and the honest answer is the shape of the command itself - the one message a
     * parser could not have written.
     */
    public static class NeedsTheUsageLineParser extends ArgParserContextual<Widget> {
        public NeedsTheUsageLineParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Widget> parse(@Nonnull ContextualParseCall call) {
            return missing();
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    /** Refuses and says nothing at all - the command stops, the sender is left with silence. */
    public static class SilentlyRefusingParser extends ArgParserContextual<Widget> {
        public SilentlyRefusingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Widget> parse(@Nonnull ContextualParseCall call) {
            return denied();
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    /** Refuses from a helper three frames down, which cannot change the return type of the chain. */
    public static class AbortingFromAHelperParser extends ArgParserContextual<Widget> {
        static final String VAULT_CLOSED = "the vault is closed today";

        public AbortingFromAHelperParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<Widget> parse(@Nonnull ContextualParseCall call) {
            return openTheVault();
        }

        private static ParseResult<Widget> openTheVault() {
            throw new ArgParseException(ParseResult.denied(new TestLocaleMessage(VAULT_CLOSED)));
        }

        @Override
        public boolean requiresToBeAPlayer() {
            return false;
        }
    }

    @FinalCMD(aliases = "contextualcmd")
    public static class Contextual_Cmd {
        static boolean invoked = false;
        static Widget received;

        static void reset() {
            invoked = false;
            received = null;
        }

        @FinalCMD.SubCMD(subcmd = "resolving")
        public void resolving(FCommandSender sender, @Arg.Contextual(value = "widget", parser = ResolvingParser.class) Widget widget) {
            invoked = true;
            received = widget;
        }

        @FinalCMD.SubCMD(subcmd = "refusing")
        public void refusing(FCommandSender sender, @Arg.Contextual(value = "widget", parser = RefusingParser.class) Widget widget) {
            invoked = true;
            received = widget;
        }

        @FinalCMD.SubCMD(subcmd = "localizedrefusing")
        public void localizedRefusing(FCommandSender sender, @Arg.Contextual(value = "widget", parser = LocalizedRefusingParser.class) Widget widget) {
            invoked = true;
            received = widget;
        }

        @FinalCMD.SubCMD(subcmd = "undeliverable")
        public void undeliverable(FCommandSender sender, @Arg.Contextual(value = "widget", parser = UndeliverableRefusingParser.class) Widget widget) {
            invoked = true;
            received = widget;
        }

        @FinalCMD.SubCMD(subcmd = "emptyhanded")
        public void emptyHanded(FCommandSender sender, @Arg.Contextual(value = "widget", parser = EmptyHandedParser.class) Widget widget) {
            invoked = true;
            received = widget;
        }

        @FinalCMD.SubCMD(subcmd = "boom")
        public void boom(FCommandSender sender, @Arg.Contextual(value = "widget", parser = ExplodingParser.class) Widget widget) {
            invoked = true;
            received = widget;
        }

        @FinalCMD.SubCMD(subcmd = "needshelp")
        public void needsHelp(FCommandSender sender, @Arg.Contextual(value = "widget", parser = NeedsTheUsageLineParser.class) Widget widget) {
            invoked = true;
            received = widget;
        }

        @FinalCMD.SubCMD(subcmd = "silent")
        public void silent(FCommandSender sender, @Arg.Contextual(value = "widget", parser = SilentlyRefusingParser.class) Widget widget) {
            invoked = true;
            received = widget;
        }

        @FinalCMD.SubCMD(subcmd = "vault")
        public void vault(FCommandSender sender, @Arg.Contextual(value = "widget", parser = AbortingFromAHelperParser.class) Widget widget) {
            invoked = true;
            received = widget;
        }
    }

    private FinalCMDPluginCommand fresh() {
        Contextual_Cmd.reset();
        return newHarness().register(new Contextual_Cmd());
    }

    @Test
    void aResolvedContextualParameterReachesTheMethod() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "resolving");

        assertTrue(Contextual_Cmd.invoked);
        assertEquals("console", Contextual_Cmd.received.owner);
        assertTrue(sender.getMessages().isEmpty(), "nothing failed, so there is nothing to say");
    }

    @Test
    void aContextualRefusalStopsTheCommandAndTellsTheSenderWhy() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "refusing");

        assertFalse(Contextual_Cmd.invoked, "a refused contextual parameter aborts the invocation");
        sender.assertAnyMessageContains(RefusingParser.NO_WIDGET);
    }

    /**
     * A contextual parser is reachable without ever being registered anywhere - naming it on the
     * parameter is enough - so the registration that names it is the only chance its own messages have
     * to exist at all. Without that, the refusal carries a null message and nobody is told anything.
     */
    @Test
    void aContextualParserNamedOnlyByTheAnnotationStillSpeaksItsOwnFCLocale() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "localizedrefusing");

        assertFalse(Contextual_Cmd.invoked, "a refused contextual parameter aborts the invocation");
        sender.assertAnyMessageContains(LocalizedRefusingParser.NO_WIDGET_TEXT);
    }

    /**
     * Telling the sender why is the last thing that happens and the only thing left to lose: a refusal
     * nobody can put into words still refuses, and the dispatch above it never sees the difference.
     */
    @Test
    void aRefusalNobodyCanPutIntoWordsStillAbortsInsteadOfEscapingTheDispatch() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        List<String> logged = Logs.capture(() -> harness.dispatch(command, sender, "undeliverable"));

        assertFalse(Contextual_Cmd.invoked, "a refused contextual parameter aborts the invocation");
        assertTrue(sender.getMessages().isEmpty(), "there was never a message to send");
        assertTrue(logged.stream().anyMatch(line -> line.contains("failed while reporting")),
                "the broken delivery reaches the log instead of the dispatch: " + logged);
    }

    @Test
    void aContextualParserWithNothingToGiveLeavesTheParameterNullAndTheCommandRuns() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "emptyhanded");

        assertTrue(Contextual_Cmd.invoked, "no value is not the same as a failure");
        assertNull(Contextual_Cmd.received);
        assertTrue(sender.getMessages().isEmpty());
    }

    @Test
    void aRuntimeExceptionInsideAContextualParserIsCaughtAndTheSenderIsToldSomethingGeneric() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "boom");

        assertFalse(Contextual_Cmd.invoked);
        assertFalse(sender.getMessages().isEmpty(), "the exception no longer escapes without a word");
        assertFalse(sender.anyMessageContains(ExplodingParser.BOOM),
                "the sender is told the parameter failed, never the internals: " + sender.getMessages());
    }

    /**
     * The one refusal whose message is the command itself. What separates it from every other one is
     * exactly what the sender reads: the usage line, not the generic "that value is not valid here" a
     * failed conversion earns.
     */
    @Test
    void aContextualParserWithNothingToGiveSendsTheUsageLineInsteadOfAParserMessage() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "needshelp");

        assertFalse(Contextual_Cmd.invoked, "the invocation stopped");
        sender.assertAnyMessageContains("contextualcmd needshelp");
        assertFalse(sender.anyMessageContains("is not valid here"),
                "the usage line replaces the generic argument error, it does not accompany it: " + sender.getMessages());
    }

    @Test
    void aRefusalCarryingNoMessageAtAllStopsTheCommandWithoutSayingAnything() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        List<String> logged = Logs.capture(() -> harness.dispatch(command, sender, "silent"));

        assertFalse(Contextual_Cmd.invoked, "a refusal is a refusal even with nothing to say");
        sender.assertNoMessageSent();
        assertTrue(logged.stream().noneMatch(line -> line.contains("failed while reporting")),
                "nothing was lost - there was nothing to deliver: " + logged);
    }

    @Test
    void anAbortThrownFromAHelperRefusesExactlyLikeAReturnedOne() {
        FinalCMDPluginCommand command = fresh();
        TestCommandSender sender = new TestCommandSender("console");

        harness.dispatch(command, sender, "vault");

        assertFalse(Contextual_Cmd.invoked);
        sender.assertAnyMessageContains(AbortingFromAHelperParser.VAULT_CLOSED);
    }

    // ------------------------------------------------------------------
    // The usual case: a parameter that declares nothing at all
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "plainsendercmd")
    public static class PlainSender_Cmd {
        static FCommandSender received;

        @FinalCMD.SubCMD(subcmd = "who")
        public void who(FCommandSender sender) {
            received = sender;
        }
    }

    @Test
    void aParameterWithNoAnnotationAtAllIsStillResolvedFromTheInvocation() {
        FinalCMDPluginCommand command = newHarness().register(new PlainSender_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        PlainSender_Cmd.received = null;

        harness.dispatch(command, sender, "who");

        assertSame(sender, PlainSender_Cmd.received, "the very sender that dispatched, with nothing declared to ask for it");
    }

    // ------------------------------------------------------------------
    // The same answer inside a @FinalCMD.Capture, which stops the walk halfway
    // ------------------------------------------------------------------

    @FinalCMD(aliases = "capturemisscmd")
    public static class CaptureMiss_Cmd {
        static boolean leafInvoked = false;

        @FinalCMD.Node(subcmd = "user")
        public static class UserNode {
            @FinalCMD.Capture
            public String capture(@Arg("<user>") String user,
                                  @Arg.Contextual(value = "widget", parser = NeedsTheUsageLineParser.class) Widget widget) {
                return user;
            }

            @FinalCMD.SubCMD(subcmd = "leaf")
            public void leaf(FCommandSender sender, @Arg.NodeCaptured String user) {
                leafInvoked = true;
            }
        }
    }

    /**
     * A capture is reached mid-path, so the usage line the sender gets is the NODE's - the same one a
     * required token of that capture would have earned. Chosen, not discovered: nothing about being
     * halfway down a path makes a different answer more honest.
     */
    @Test
    void theSameAnswerInsideACaptureSendsTheNodesOwnUsageLineAndNeverReachesTheLeaf() {
        FinalCMDPluginCommand command = newHarness().register(new CaptureMiss_Cmd());
        TestCommandSender sender = new TestCommandSender("console");
        CaptureMiss_Cmd.leafInvoked = false;

        harness.dispatch(command, sender, "user Steve leaf");

        assertFalse(CaptureMiss_Cmd.leafInvoked, "the walk stopped at the capture");
        sender.assertAnyMessageContains("user <user>");
    }
}
