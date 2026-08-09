package br.com.finalcraft.evernifecore.fancytext.hover;

import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatform;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformChatAdapter;
import br.com.finalcraft.evernifecore.text.ITextMetrics;
import br.com.finalcraft.evernifecore.api.common.providers.platform.IPlatformVecAdapter;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.listeners.base.ECListener;
import br.com.finalcraft.evernifecore.logger.ILogAdapter;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Proves the hover registry is actually consulted at render time, not just stored: a runtime hover
 * type registered the way a plugin integrator would (outside the {@code fancytext} package) gets
 * rendered for real, and an unsupported type degrades - or, absent a degrade, is silently omitted -
 * instead of ever throwing.
 */
@ECoreTest
public class FancyHoverRegistryContractTest {


    private IPlatform installedBeforeTest;

    @AfterEach
    void restorePlatform() {
        if (installedBeforeTest != null) {
            EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class, installedBeforeTest);
            installedBeforeTest = null;
        }
    }

    /** Installs a platform that reports every id in {@code unsupportedTypeIds} as unsupported, everything else as supported. */
    private void withUnsupportedHoverTypes(Set<String> unsupportedTypeIds) {
        installedBeforeTest = EverNifeCore.getPlatform();
        EverNifeCore.getProviders().getBaseProvider().register(IPlatform.class,
                new HoverSupportOverridePlatform(installedBeforeTest, unsupportedTypeIds));
    }

    // --- a synthetic hover kind standing in for a plugin integrator's own custom tooltip ----------

    private static final class CoordinatesHover implements FancyHover {
        private final int x, y, z;

        CoordinatesHover(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public String typeId() {
            return "test-coordinates";
        }

        String asLabel() {
            return x + ", " + y + ", " + z;
        }

        @Override
        public String serialize() {
            return x + "," + y + "," + z;
        }

        @Override
        public FancyHover deserialize(String payload) {
            String[] parts = payload.split(",");
            return new CoordinatesHover(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
    }

    // --- a second pair of synthetic types, used only to prove the degrade-hop cap ------------------

    private static final class HopAHover implements FancyHover {
        @Override
        public String typeId() {
            return "test-hop-a";
        }

        @Override
        public String serialize() {
            return "";
        }

        @Override
        public FancyHover deserialize(String payload) {
            return new HopAHover();
        }
    }

    private static final class HopBHover implements FancyHover {
        @Override
        public String typeId() {
            return "test-hop-b";
        }

        @Override
        public String serialize() {
            return "";
        }

        @Override
        public FancyHover deserialize(String payload) {
            return new HopBHover();
        }
    }

    static {
        FancyHoverRegistry.register(FancyHoverType.of("test-coordinates",
                (CoordinatesHover coords) -> HoverEvent.showText(Component.text(coords.asLabel()))));
        // A degrades into B and B degrades right back into A - a real mutual cycle, so a resolver
        // that chased more than one hop would recurse forever instead of ever returning.
        FancyHoverRegistry.register(FancyHoverType.<HopAHover>of("test-hop-a",
                        a -> HoverEvent.showText(Component.text("a")))
                .withDegrade(a -> new HopBHover()));
        FancyHoverRegistry.register(FancyHoverType.<HopBHover>of("test-hop-b",
                        b -> HoverEvent.showText(Component.text("b")))
                .withDegrade(b -> new HopAHover()));
    }

    @Test
    void customHoverTypeRegisteredAtRuntimeIsActuallyRendered() {
        CoordinatesHover coordinates = new CoordinatesHover(10, 64, -30);
        Component component = new FancySegment("waypoint").setHover(coordinates).toComponent();

        HoverEvent<?> hoverEvent = component.hoverEvent();
        assertNotNull(hoverEvent, "a runtime-registered hover type must actually be rendered");
        assertEquals(HoverEvent.Action.SHOW_TEXT, hoverEvent.action());
        assertEquals(Component.text(coordinates.asLabel()), hoverEvent.value(),
                "the rendered hover must be exactly what the custom type declares, not a fallback");
    }

    @Test
    void unsupportedHoverTypeWithNoDegradeOmitsTheHoverInsteadOfThrowing() {
        withUnsupportedHoverTypes(Collections.singleton("test-coordinates"));

        FancySegment segment = new FancySegment("waypoint").setHover(new CoordinatesHover(1, 2, 3));

        Component component = assertDoesNotThrow(() -> {
            return segment.toComponent();
        }, "an unsupported hover type must never throw during render");
        assertNull(component.hoverEvent(),
                "with no degrade declared, an unsupported type must omit the hover entirely, never leak its raw payload");
    }

    @Test
    void unsupportedBuiltinItemTypeDegradesToItsDeclaredTextFallback() {
        withUnsupportedHoverTypes(Collections.singleton(ItemHover.TYPE_ID));

        Component component = new FancySegment("head").setHoverItem("minecraft:diamond").toComponent();

        HoverEvent<?> hoverEvent = component.hoverEvent();
        assertNotNull(hoverEvent, "the item type declares a degrade, so the hover must survive as its fallback");
        assertEquals(HoverEvent.Action.SHOW_TEXT, hoverEvent.action(), "the declared degrade is a plain-text hover");
    }

    @Test
    void degradeChainStopsAfterOneHopInsteadOfRecursing() {
        // Both this hover's own type and the type it would degrade into are reported unsupported:
        // the render must stop after the first hop and omit, never chase A -> B -> A forever.
        withUnsupportedHoverTypes(new HashSet<>(Arrays.asList("test-hop-a", "test-hop-b")));

        Component component = assertDoesNotThrow(() -> {
            return new FancySegment("waypoint").setHover(new HopAHover()).toComponent();
        }, "a cycle of unsupported degrades must never throw or hang");
        assertNull(component.hoverEvent(),
                "capped at one hop: the degraded type is also unsupported, so the hover is omitted");
    }

    /** Delegates everything to the currently-installed platform, except {@code supportsHover}. */
    private static final class HoverSupportOverridePlatform implements IPlatform {
        private final IPlatform delegate;
        private final Set<String> unsupportedTypeIds;

        HoverSupportOverridePlatform(IPlatform delegate, Set<String> unsupportedTypeIds) {
            this.delegate = delegate;
            this.unsupportedTypeIds = unsupportedTypeIds;
        }

        @Override
        public IPlatformChatAdapter getChatAdapter() {
            return new IPlatformChatAdapter() {
                @Override
                public ITextMetrics getTextMetrics() {
                    return ITextMetrics.UNMEASURED;
                }

                @Override
                public List<FCommandSender> getBroadcastAudience() {
                    return Collections.emptyList();
                }

                @Override
                public boolean supportsHover(String typeId) {
                    return !unsupportedTypeIds.contains(typeId);
                }
            };
        }

        @Override public String getPlatformProviderId() { return delegate.getPlatformProviderId(); }
        @Override public List<FPlayer> getOnlinePlayers() { return delegate.getOnlinePlayers(); }
        @Override public FPlayer getPlayer(String playerName) { return delegate.getPlayer(playerName); }
        @Override public FPlayer getPlayer(UUID playerUuid) { return delegate.getPlayer(playerUuid); }
        @Override public boolean isPluginLoaded(String pluginName) { return delegate.isPluginLoaded(pluginName); }
        @Override public boolean makeConsoleExecuteCommand(String command) { return delegate.makeConsoleExecuteCommand(command); }
        @Override public boolean makePlayerExecuteCommand(FCommandSender sender, String command) { return delegate.makePlayerExecuteCommand(sender, command); }
        @Override public boolean registerCommand(FinalCMDPluginCommand finalCMDPluginCommand) { return delegate.registerCommand(finalCMDPluginCommand); }
        @Override public void unregisterCommand(String commandName, ECPluginData notifyPlugin) { delegate.unregisterCommand(commandName, notifyPlugin); }
        @Override public void registerECListener(ECPluginData ecPluginData, ECListener listener) { delegate.registerECListener(ecPluginData, listener); }
        @Override public void unregisterECListener(ECListener listener) { delegate.unregisterECListener(listener); }
        @Override public boolean isPAPIPresent() { return delegate.isPAPIPresent(); }
        @Override public String parse(@Nullable FPlayer player, @Nonnull String text) { return delegate.parse(player, text); }
        @Override public <P extends IPlayerData> RegexReplacer<P> createPlaceholderIntegration(@Nonnull ECPluginData plugin, @Nonnull String pluginBaseID, @Nonnull Class<P> playerDataType) { return delegate.createPlaceholderIntegration(plugin, pluginBaseID, playerDataType); }
        @Override public ILogAdapter createLogAdapterFor(ECPluginData ecPluginData) { return delegate.createLogAdapterFor(ecPluginData); }
        @Override public void sendActionBarMessage(FPlayer player, FancyText fancyText) { delegate.sendActionBarMessage(player, fancyText); }
        @Override public boolean serverSupportsActionBar() { return delegate.serverSupportsActionBar(); }
        @Override public IPlatformVecAdapter getVecAdapter() { return delegate.getVecAdapter(); }
        @Override public CompletableFuture<Void> runOnMainThread(Runnable task) { return delegate.runOnMainThread(task); }
        @Override public <T> CompletableFuture<T> runOnMainThread(Supplier<T> task) { return delegate.runOnMainThread(task); }
        @Override public CompletableFuture<Void> runOnMainThreadNextTick(Runnable task) { return delegate.runOnMainThreadNextTick(task); }
        @Override public <T> CompletableFuture<T> runOnMainThreadNextTick(Supplier<T> task) { return delegate.runOnMainThreadNextTick(task); }
        @Override public void registerConfigTypes() { delegate.registerConfigTypes(); }
        @Override public void registerArgParsers() { delegate.registerArgParsers(); }
        @Override public void shutdown(String reason) { delegate.shutdown(reason); }
    }
}
