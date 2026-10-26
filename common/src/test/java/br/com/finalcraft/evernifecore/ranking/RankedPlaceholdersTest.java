package br.com.finalcraft.evernifecore.ranking;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.integration.placeholders.PAPIIntegration;
import br.com.finalcraft.evernifecore.region.placeholder.RegionPlaceholders;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * The {@code top_[list]_...} placeholders over FinalJobs' {@code miner} list - alice 30, bob 50, carol 10, ordered
 * descending, two kept - asked on FinalJobs' own prefix (bare names) and on the core's (full ids).
 */
@ECoreTest
class RankedPlaceholdersTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData jobs;
    private RegexReplacer<IPlayerData> jobsReplacer;
    private final TestFPlayerSender alice = new TestFPlayerSender("alice");
    private final TestFPlayerSender bob = new TestFPlayerSender("bob");
    private final TestFPlayerSender carol = new TestFPlayerSender("carol");

    @BeforeEach
    void open() {
        world = Platforms.lenient().papiPresent(true).recordingPlaceholderIntegrations().install();
        jobs = Plugins.fakePluginData("FinalJobs", tempDir.resolve("jobs").toFile());

        RankedList.of(jobs, "miner", FPlayer.class)
                .source(() -> Arrays.<FPlayer>asList(alice, bob, carol))
                .maxEntries(2)
                .orderBy(this::levelOf).descending()
                .addValue("kills", player -> player.getName().length() * 100)
                .build();

        jobsReplacer = PAPIIntegration.createPlaceholderIntegration(jobs, "finaljobs", IPlayerData.class);
        RankedPlaceholders.register(jobsReplacer, jobs);
    }

    @AfterEach
    void close() {
        RankedLists.clear();
        Plugins.forget("FinalJobs");
        Plugins.forget(Plugins.CORE);
        world.close();
    }

    private int levelOf(FPlayer player) {
        return player == alice ? 30 : player == bob ? 50 : 10;
    }

    private static String ask(RegexReplacer<IPlayerData> replacer, String placeholder, @Nullable TestFPlayerSender viewer) {
        PlayerData data = null;
        if (viewer != null) {
            data = new PlayerData(viewer.getUniqueId(), viewer.getName());
            data.setPlayer(viewer);
        }
        String asked = "%" + placeholder + "%";
        String answer = replacer.apply(asked, data);
        return asked.equals(answer) ? "<unanswered>" : answer;
    }

    private String jobs(String placeholder) {
        return ask(jobsReplacer, placeholder, alice);
    }

    @Test
    void theNameOfTheNthEntryIsThePlayersName() {
        assertEquals("bob", jobs("top_[miner]_1_name"));
        assertEquals("alice", jobs("top_[miner]_2_name"));
    }

    @Test
    void theValueOfTheNthEntryIsWhatTheListIsOrderedBy() {
        assertEquals("50", jobs("top_[miner]_1_value"));
    }

    @Test
    void aDeclaredValueIsAnsweredByItsKey() {
        assertEquals("300", jobs("top_[miner]_1_[kills]"));
    }

    @Test
    void sizeIsWhatTheListKeptAndTotalIsWhatTheSourceReturned() {
        assertEquals("2", jobs("top_[miner]_size"));
        assertEquals("3", jobs("top_[miner]_total"));
    }

    @Test
    void positionIsTheAskersPlaceAndEmptyWhenTheyAreNotOnTheList() {
        assertEquals("2", ask(jobsReplacer, "top_[miner]_position", alice));
        assertEquals("", ask(jobsReplacer, "top_[miner]_position", carol), "carol was cut by the ceiling");
        assertEquals("", ask(jobsReplacer, "top_[miner]_position", null));
    }

    @Test
    void whatDoesNotExistAnswersEmpty() {
        assertEquals("", jobs("top_[nope]_1_name"), "no such list");
        assertEquals("", jobs("top_[miner]_3_name"), "no third entry: the ceiling kept two");
        assertEquals("", jobs("top_[miner]_0_name"));
        assertEquals("", jobs("top_[miner]_1_[deaths]"), "no such key");
        assertEquals("", jobs("top_[miner]_whatever"));
    }

    @Test
    void anAsyncListWithNoReadYetAnswersEmptyAndAskingStartsTheRead() {
        AtomicInteger reads = new AtomicInteger();
        CompletableFuture<List<FPlayer>> pending = new CompletableFuture<>();
        RankedList.of(jobs, "slow", FPlayer.class)
                .sourceAsync(() -> {
                    reads.incrementAndGet();
                    return pending;
                })
                .unlimitedEntries()
                .build();

        assertEquals("", jobs("top_[slow]_1_name"), "the placeholder never waits for the backend");
        assertEquals(1, reads.get(), "asking is what starts the read");

        pending.complete(Arrays.<FPlayer>asList(carol));
        assertEquals("carol", jobs("top_[slow]_1_name"));
        assertEquals(1, reads.get());
    }

    @Test
    void theCorePrefixNamesAListByItsFullId() {
        ECPluginData core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        RegexReplacer<IPlayerData> coreReplacer = RegionPlaceholders.registerCore(core);
        assertNotNull(coreReplacer, "PlaceholderAPI is present, so the core serves its prefix");
        RankedPlaceholders.registerCore(coreReplacer);

        assertEquals("bob", ask(coreReplacer, "top_[finaljobs:miner]_1_name", alice));
        assertEquals("", ask(coreReplacer, "top_[miner]_1_name", alice), "the core prefix wants the owner too");
    }
}
