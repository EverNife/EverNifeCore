package br.com.finalcraft.evernifecore.screen;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ranking.RankedList;
import br.com.finalcraft.evernifecore.testing.RecordingSurface;
import br.com.finalcraft.evernifecore.testing.ScreenWorld;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The core's ranking screen lists a RankedList in order, marks the viewer's row and measures the way to the next position. */
class RankingScreenTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ScreenWorld world;
    private TestFPlayerSender alex;
    private TestFPlayerSender bob;
    private TestFPlayerSender carl;
    private TestFPlayerSender dora;
    private final Map<String, Integer> score = new HashMap<>();
    private RankedList<FPlayer> kills;

    @BeforeEach
    void setup() {
        world = ScreenWorld.open(tempDir);
        alex = world.viewer("Alex");
        bob = world.viewer("Bob");
        carl = world.viewer("Carl");
        dora = world.viewer("Dora");
        score.put("Bob", 100);
        score.put("Alex", 50);
        score.put("Carl", 10);
        kills = RankedList.anonymous(FPlayer.class)
                .source(() -> Arrays.<FPlayer>asList(alex, bob, carl))
                .unlimitedEntries()
                .orderBy(player -> score.get(player.getName()))
                .descending()
                .build();
        kills.snapshot().join(); //cached: the screen reads it in place
        Layouts.clear();
    }

    @AfterEach
    void teardown() {
        Layouts.clear();
        world.close();
    }

    private ScreenView open(FPlayer viewer) {
        ScreenView view = Screens.open(viewer, ScreenKey.RANKING, ScreenRequest.of(null).payload(RankingRequest.of(kills).title("Kills"))).join();
        world.advanceTicks(2);
        return view;
    }

    private RecordingSurface surface(ScreenView view) {
        return world.getRenderer().surfaceOf(view);
    }

    private void close(ScreenView view) {
        view.close();
        world.advanceTicks(2);
    }

    @Test
    void theEntriesComeInOrderAndOnlyTheViewersRowIsMarked() {
        ScreenView view = open(alex);

        assertEquals("Kills", view.getCurrentTitle());
        assertEquals("§e#1 §fBob", surface(view).getSlot(0).getItem().getDisplayName());
        assertEquals("§a» §e#2 §fAlex", surface(view).getSlot(1).getItem().getDisplayName());
        assertEquals("§e#3 §fCarl", surface(view).getSlot(2).getItem().getDisplayName());
        assertTrue(surface(view).getSlot(1).getItem().getLore().contains("§750"), surface(view).getSlot(1).getItem().getLore().toString());
        close(view);
    }

    @Test
    void theBarIsTheViewersValueAgainstThePositionAbove() {
        ScreenView view = open(alex);

        assertEquals(0.5d, ((RankingScreen) view.getScreen()).getRatio(), 1e-9);
        assertEquals("§a50% §7of the way to §e#1", surface(view).getSlot(31).getItem().getDisplayName());
        close(view);
    }

    @Test
    void theTopIsAFullBarAndSomeoneUnrankedAnEmptyOne() {
        ScreenView top = open(bob);
        assertEquals(1d, ((RankingScreen) top.getScreen()).getRatio(), 1e-9);
        close(top);

        ScreenView outside = open(dora);
        assertEquals(0d, ((RankingScreen) outside.getScreen()).getRatio(), 1e-9);
        for (int slot = 0; slot < 3; slot++) {
            assertFalse(surface(outside).getSlot(slot).getItem().getDisplayName().startsWith("§a» "), "nobody's row is hers");
        }
        close(outside);
    }

}
