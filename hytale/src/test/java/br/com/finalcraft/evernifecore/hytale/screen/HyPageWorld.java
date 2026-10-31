package br.com.finalcraft.evernifecore.hytale.screen;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;

import java.nio.file.Path;
import java.util.function.UnaryOperator;

/**
 * Screens drawn by the page renderer over a {@link RecordingPageTransport}: the recording renderer of the screen world
 * taken out, the page renderer and its page prompt channel registered by a fake core on the world's own tick clock, and
 * the world's {@code answerPrompt} pressing OK on the open question page.
 */
final class HyPageWorld implements AutoCloseable {

    final ScreenWorld screens;
    final RecordingPageTransport transport = new RecordingPageTransport();
    final HyPageRenderer renderer;
    /** The renderer is keyed {@code evernifecore:page}, and a type is registered by the plugin its key names. */
    private final ECPluginData core;

    private HyPageWorld(ScreenWorld screens, Path dataFolder) {
        this.screens = screens;
        this.renderer = new HyPageRenderer(transport, screens.getTicks());
        this.core = Plugins.fakeCore(dataFolder.resolve("core").toFile());
        Screens.unregisterRenderers(screens.getOwner());
        Screens.registerRenderer(core, renderer);
        screens.prompts().clear();
        PromptRegistry.get().register(core, renderer.getPromptChannel());
        screens.answeringPromptsWith(transport::answerAny);
    }

    static HyPageWorld open(Path dataFolder) {
        return new HyPageWorld(ScreenWorld.open(dataFolder), dataFolder);
    }

    static HyPageWorld open(Path dataFolder, UnaryOperator<Platforms> platform) {
        return new HyPageWorld(ScreenWorld.open(dataFolder, platform), dataFolder);
    }

    @Override
    public void close() {
        try {
            Screens.closeAll();
            Screens.registry().unregister(core, HyPageRenderer.ID);
            PromptRegistry.get().unregisterAll(core);
            Plugins.forget(Plugins.CORE);
        } finally {
            screens.close();
        }
    }

}
