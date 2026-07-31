package br.com.finalcraft.evernifecore.fancytext;

import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.providers.extractors.IECPluginExtractor;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.testing.TestCommandSender;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.SendCustom;
import br.com.finalcraft.evernifecore.locale.scanner.FCLocaleScanner;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.CyclicBarrier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A LocaleMessage lives in a static field, so the command context it renders with cannot live on
 * the message. These pin that two executions - even simultaneous ones - each see their own label.
 */
@ECoreTest
public class MessageScopeContractTest {


    @TempDir
    Path tempDir;

    private String registeredPluginName;

    @AfterEach
    void teardown() {
        if (registeredPluginName != null) {
            ECPluginManager.removePluginData(registeredPluginName);
            registeredPluginName = null;
        }
    }

    public static class ScopedLocales {
        @FCLocale(text = "use /${label} help")
        public static LocaleMessage USAGE;
    }

    @Test
    void twoConcurrentSendsOfTheSameMessageEachSeeTheirOwnLabel() throws Exception {
        FCLocaleScanner.scanForLocale(pluginData("MessageScopePlugin"), true, ScopedLocales.class);

        TestCommandSender alphaSender = new TestCommandSender("ALPHA");
        TestCommandSender betaSender = new TestCommandSender("BETA");

        // both threads sit inside their own scope at the same moment, so a shared mutable context
        // would necessarily leak from one into the other
        CyclicBarrier bothInsideTheirScope = new CyclicBarrier(2);
        Thread alpha = sendingThread("alpha", alphaSender, bothInsideTheirScope);
        Thread beta = sendingThread("beta", betaSender, bothInsideTheirScope);

        alpha.start();
        beta.start();
        alpha.join();
        beta.join();

        assertEquals("use /alpha help", alphaSender.getMessages().get(0));
        assertEquals("use /beta help", betaSender.getMessages().get(0));
    }

    // The scope belongs to the thread that ran the command; the message is often delivered by
    // another one. What travels with the message is the context it was BUILT with.
    @Test
    void aMessageBuiltInsideACommandStillAnswersForItsLabelWhenDeliveredFromAnotherThread() throws Exception {
        FCLocaleScanner.scanForLocale(pluginData("AsyncDeliveryPlugin"), true, ScopedLocales.class);

        TestCommandSender sender = new TestCommandSender("CONSOLE");

        SendCustom builtInsideTheCommand;
        try (MessageScope scope = MessageScope.open(CommandPath.ofRoot("mycmd"))) {
            builtInsideTheCommand = ScopedLocales.USAGE.custom();
        }

        Thread deliverer = new Thread(() -> builtInsideTheCommand.send(sender), "async-delivery");
        deliverer.start();
        deliverer.join();

        assertEquals("use /mycmd help", sender.getMessages().get(0),
                "a task delivering a message built inside a command must not lose its label");
    }

    // The caller who says which context to render with wins over the one the thread happens to be in.
    @Test
    void anExplicitRenderContextBeatsTheScopeOfTheSendingThread() {
        FCLocaleScanner.scanForLocale(pluginData("ExplicitContextPlugin"), true, ScopedLocales.class);

        TestCommandSender sender = new TestCommandSender("CONSOLE");
        RenderContext forced = RenderContext.of(sender, CommandMessageContext.of("forced", "sub"));

        try (MessageScope scope = MessageScope.open(CommandPath.ofRoot("scoped"))) {
            ScopedLocales.USAGE.send(forced, sender);
            FancyText.of("plain /${label} ${subcmd}").send(forced, sender);
        }

        assertEquals("use /forced help", sender.getMessages().get(0));
        assertEquals("plain /forced sub", sender.getMessages().get(1));
    }

    // ${label} answers for itself in ANY message, not only in one built from a locale file.
    @Test
    void theCommandLabelResolvesInAPlainMessageInsideAScopeAndStaysRawOutsideOne() {
        TestCommandSender sender = new TestCommandSender("CONSOLE");

        try (MessageScope scope = MessageScope.open(CommandPath.ofSingleSegment("mycmd", "sub"))) {
            assertEquals("use /mycmd sub", FancyText.of("use /${label} ${subcmd}").toLegacyString(RenderContext.of(sender)));
        }

        assertEquals("use /${label} ${subcmd}", FancyText.of("use /${label} ${subcmd}").toLegacyString(RenderContext.of(sender)),
                "outside a command scope there is no label to answer with, so the token stays as written");
    }

    // A message that declares its own label wins over the framework-wide one.
    @Test
    void aMessageThatDeclaresLabelItselfShadowsTheFrameworkWideOne() {
        TestCommandSender sender = new TestCommandSender("CONSOLE");

        try (MessageScope scope = MessageScope.open(CommandPath.ofRoot("mycmd"))) {
            assertEquals("use /mine", FancyText.of("use /${label}")
                    .addPlaceholder("label", "mine")
                    .toLegacyString(RenderContext.of(sender)));
        }
    }

    @Test
    void aScopeStopsBeingVisibleOnceItIsClosed() {
        assertSame(CommandMessageContext.EMPTY, MessageScope.currentOrEmpty());

        try (MessageScope scope = MessageScope.open(CommandPath.ofSingleSegment("mycmd", "sub"))) {
            assertEquals("mycmd", MessageScope.currentOrEmpty().getLabel());
            assertEquals("sub", MessageScope.currentOrEmpty().getSubCommandName());
        }

        assertSame(CommandMessageContext.EMPTY, MessageScope.currentOrEmpty(),
                "the scope must not survive the invocation it was opened for");
    }

    @Test
    void aNestedScopeDoesNotCloseWhatTheOuterOneStillNeeds() {
        try (MessageScope outer = MessageScope.open(CommandPath.ofRoot("outer"))) {
            try (MessageScope inner = MessageScope.open(CommandPath.ofRoot("inner"))) {
                assertEquals("outer", MessageScope.currentOrEmpty().getLabel(),
                        "the outer scope owns the context until it closes");
            }
            assertEquals("outer", MessageScope.currentOrEmpty().getLabel());
        }
        assertNull(MessageScope.currentOrEmpty().getLabel());
    }

    private Thread sendingThread(String label, TestCommandSender sender, CyclicBarrier barrier) {
        return new Thread(() -> {
            try (MessageScope scope = MessageScope.open(CommandPath.ofRoot(label))) {
                barrier.await();
                ScopedLocales.USAGE.send(sender);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private ECPluginData pluginData(String pluginName) {
        EverNifeCore.getProviders().getBaseProvider().register(IECPluginExtractor.class,
                Plugins.fake(pluginName, tempDir.resolve(pluginName).toFile()));
        registeredPluginName = pluginName;
        return ECPluginManager.getOrCreateECorePluginData(new Object());
    }


}
