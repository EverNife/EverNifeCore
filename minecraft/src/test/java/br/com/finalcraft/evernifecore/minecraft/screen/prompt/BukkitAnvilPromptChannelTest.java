package br.com.finalcraft.evernifecore.minecraft.screen.prompt;

import br.com.finalcraft.ecnmsutils.anvil.AnvilHandle;
import br.com.finalcraft.ecnmsutils.anvil.AnvilRequest;
import br.com.finalcraft.ecnmsutils.anvil.AnvilVerdict;
import br.com.finalcraft.ecnmsutils.anvil.NmsAnvil;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.BukkitScreenWorld;
import br.com.finalcraft.evernifecore.minecraft.screen.testkit.PlayerDouble;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.prompt.PromptKind;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.screen.prompt.PromptResult;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The anvil channel over a double of ECNmsUtils' anvil: what it hands the library, and how each thing the anvil reports
 * ends the question. The library's own side - the window, repair cost, the slots - is the library's tests' and the
 * server matrix's.
 */
class BukkitAnvilPromptChannelTest {

    @TempDirNobodyCleans
    Path tempDir;

    private BukkitScreenWorld world;
    private PlayerDouble player;
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final FakeAnvil anvil = new FakeAnvil();

    @BeforeEach
    void setup() {
        world = BukkitScreenWorld.install(tempDir);
        player = world.newPlayer("Steve");
        PromptRegistry.get().register(world.getPluginData(), new BukkitAnvilPromptChannel(() -> anvil, available::get));
    }

    @AfterEach
    void teardown() {
        world.close();
    }

    private ScreenView openScreen() {
        return world.open(Screen.of(3).title("Auction"), player);
    }

    private <T> CompletableFuture<PromptResult<T>> ask(ScreenView view, PromptSpec<T> spec) {
        CompletableFuture<PromptResult<T>> asked = PromptRegistry.get().ask(view, spec.prefer(PromptKind.ANVIL));
        world.advanceTicks(1);
        return asked;
    }

    private static PromptSpec<Integer> howMany() {
        return PromptSpec.of("How many?").parse(text -> {
            if (!text.matches("\\d+")) {
                throw new IllegalArgumentException("Not a number: '" + text + "'. Type digits only, like 250.");
            }
            return Integer.parseInt(text);
        });
    }

    private void closeScreen(ScreenView view) {
        view.close();
        world.advanceTicks(1);
    }

    @Test
    void itServesThePlayerOnlyWhileTheLibrarySaysTheAnvilWorks() {
        assertEquals(PromptKind.ANVIL, PromptRegistry.get().resolve(player.asFPlayer(), PromptKind.ANVIL));

        available.set(false);

        assertEquals(PromptKind.CHAT, PromptRegistry.get().resolve(player.asFPlayer(), PromptKind.ANVIL),
                "no anvil on this server: the question falls back to chat");
        ScreenView view = openScreen();
        ask(view, PromptSpec.of("Name?"));
        assertEquals(PromptKind.CHAT, PromptRegistry.get().getPending(view));
        assertTrue(anvil.requests.isEmpty(), "and the library was never asked to open one");
        Screens.viewerGone(player.getUniqueId(), CloseReason.DISCONNECTED);
    }

    @Test
    void itHandsTheLibraryTheQuestionTheInitialTextAndTheIcon() {
        ScreenView view = openScreen();
        ask(view, PromptSpec.of("§eHow many?").initialText("10").icon(ItemSpec.of("minecraft:name_tag", 1)));

        assertTrue(view.isSuspended());
        assertEquals(1, anvil.requests.size());
        AnvilRequest request = anvil.requests.get(0);
        assertSame(player.asPlayer(), anvil.players.get(0));
        assertEquals("§eHow many?", request.getTitle());
        assertEquals("10", request.getInitialText());
        assertEquals(Material.NAME_TAG, request.getIcon().getType());
        request.getOnClose().run();
        world.advanceTicks(1);
        closeScreen(view);
    }

    @Test
    void withoutAnIconTheAnvilShowsPaper() {
        ScreenView view = openScreen();
        ask(view, PromptSpec.of("Name?"));

        assertEquals(Material.PAPER, anvil.requests.get(0).getIcon().getType());
        anvil.requests.get(0).getOnClose().run();
        world.advanceTicks(1);
        closeScreen(view);
    }

    @Test
    void aRefusedAnswerKeepsTheAnvilOpenWithTheParsersText() {
        ScreenView view = openScreen();
        CompletableFuture<PromptResult<Integer>> asked = ask(view, howMany());

        AnvilVerdict verdict = anvil.requests.get(0).getOnInput().apply("abc");
        world.advanceTicks(1);

        assertFalse(verdict.isAccepted());
        assertEquals("Not a number: 'abc'. Type digits only, like 250.", verdict.getMessage(), "the result's name");
        assertFalse(asked.isDone());
        assertTrue(anvil.handles.get(0).open, "the anvil was not closed");
        assertTrue(view.isSuspended());
        anvil.requests.get(0).getOnClose().run();
        world.advanceTicks(1);
        closeScreen(view);
    }

    @Test
    void anAcceptedAnswerEndsItWithTheValueAndTheScreenBack() {
        ScreenView view = openScreen();
        CompletableFuture<PromptResult<Integer>> asked = ask(view, howMany());

        AnvilVerdict verdict = anvil.requests.get(0).getOnInput().apply(" 250 ");
        world.advanceTicks(1);

        assertTrue(verdict.isAccepted());
        assertEquals(250, asked.getNow(null).getValue());
        assertFalse(view.isSuspended());
        assertSame(view, Screens.getOpenView(player.getUniqueId()));
        closeScreen(view);
    }

    @Test
    void closingTheAnvilEndsItCancelledAndTheScreenComesBack() {
        ScreenView view = openScreen();
        CompletableFuture<PromptResult<Integer>> asked = ask(view, howMany());

        anvil.requests.get(0).getOnClose().run();
        world.advanceTicks(1);

        assertEquals(PromptResult.Kind.CANCELLED, asked.getNow(null).getKind());
        assertSame(view, Screens.getOpenView(player.getUniqueId()));
        closeScreen(view);
    }

    @Test
    void anEndingTheRegistryDecidesClosesTheAnvilAndItsOnCloseIsNotASecondEnding() {
        ScreenView view = openScreen();
        CompletableFuture<PromptResult<Integer>> asked = ask(view, howMany().timeout(Duration.ofMillis(50)));

        world.advanceTicks(1); //the timeout: the registry dismisses, the handle closes, the library runs onClose
        world.advanceTicks(2);

        assertFalse(anvil.handles.get(0).open, "dismiss closed the anvil");
        assertEquals(1, anvil.handles.get(0).onCloseRuns, "and the library ran onClose once, as it does");
        assertEquals(PromptResult.Kind.TIMEOUT, asked.getNow(null).getKind(), "still a timeout, not a cancel");
        assertSame(view, Screens.getOpenView(player.getUniqueId()), "given back once");
        closeScreen(view);
    }

    /** ECNmsUtils' anvil as far as the channel can see it: the requests it got and the handles it gave. */
    private static final class FakeAnvil implements NmsAnvil {

        private final List<AnvilRequest> requests = new ArrayList<>();
        private final List<Player> players = new ArrayList<>();
        private final List<FakeHandle> handles = new ArrayList<>();

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public AnvilHandle open(Player player, AnvilRequest request) {
            requests.add(request);
            players.add(player);
            FakeHandle handle = new FakeHandle(request);
            handles.add(handle);
            return handle;
        }

    }

    private static final class FakeHandle implements AnvilHandle {

        private final AnvilRequest request;
        private boolean open = true;
        private int onCloseRuns;

        private FakeHandle(AnvilRequest request) {
            this.request = request;
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public void close() {
            if (!open) {
                return;
            }
            open = false;
            onCloseRuns++;
            request.getOnClose().run();
        }

    }

}
