package br.com.finalcraft.evernifecore.finalcommandsystemtests;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseCall;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ParseResult;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.testing.FinalCmdTestHarness;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import jakarta.annotation.Nonnull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A parser learns which object it runs under - the instance mounted at the node - on every path that calls it. */
class ParseCallExecutorSystemTest {

    @TempDirNobodyCleans
    Path tempDir;

    private FinalCmdTestHarness harness;

    @AfterEach
    void teardown() {
        if (harness != null) harness.close();
    }

    static final List<Object> SEEN = new ArrayList<>();

    public static class RecordingParser extends ArgParser<String> {
        public RecordingParser(ArgInfo argInfo) {
            super(argInfo);
        }

        @Override
        public @Nonnull ParseResult<String> parse(@Nonnull ParseCall call) {
            SEEN.add(call.getExecutor());
            return ParseResult.of(call.getArgumento().toString());
        }

        @Override
        public @Nonnull ParseResult<String> fromSender(@Nonnull ParseCall call) {
            SEEN.add(call.getExecutor());
            return ParseResult.of(call.getSender().getName());
        }

        @Override
        public @Nonnull List<String> tabComplete(TabContext tabContext) {
            SEEN.add(tabContext.getExecutor());
            return Collections.singletonList("thing");
        }
    }

    public static class Mounted {
        @FinalCMD.SubCMD(subcmd = "pick")
        public void pick(FCommandSender sender, @Arg(value = "<thing>", parser = RecordingParser.class) String thing) {
        }

        @FinalCMD.SubCMD(subcmd = "self")
        public void self(FCommandSender sender, @Arg(value = "[thing]", parser = RecordingParser.class, fromSender = true) String thing) {
        }
    }

    @FinalCMD(aliases = "execcmd")
    public static class Root_Cmd {
        @FinalCMD.Node(subcmd = "node")
        final Mounted node = new Mounted();
    }

    @Test
    void parserSeesTheMountedNodeInstance() {
        harness = new FinalCmdTestHarness("Exec", tempDir);
        Root_Cmd root = new Root_Cmd();
        FinalCMDPluginCommand command = harness.register(root);
        TestCommandSender sender = new TestCommandSender("console");
        SEEN.clear();

        harness.dispatch(command, sender, "node pick x");
        assertEquals(1, SEEN.size(), "parse ran once");
        assertSame(root.node, SEEN.get(0), "parse");

        harness.dispatch(command, sender, "node self");
        assertEquals(2, SEEN.size(), "fromSender ran once");
        assertSame(root.node, SEEN.get(1), "fromSender");

        assertEquals(Collections.singletonList("thing"), harness.tab(command, sender, "node", "pick", ""));
        assertSame(root.node, SEEN.get(SEEN.size() - 1), "tabComplete");
    }
}
