package br.com.finalcraft.evernifecore.ranking;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestClock;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The data half of a ranked list: its id, where a read runs, how many reads run, what a read is kept for, and what
 * every entry answers - all off the chat page.
 */
@ECoreTest
class RankedListTest {

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData jobs;
    private ECPluginData other;

    @BeforeEach
    void open() {
        world = Platforms.lenient().install();
        TestClock.acquire();
        jobs = Plugins.fakePluginData("FinalJobs", tempDir.resolve("jobs").toFile());
        other = Plugins.fakePluginData("Other", tempDir.resolve("other").toFile());
    }

    @AfterEach
    void close() {
        RankedLists.clear();
        Plugins.forget("FinalJobs");
        Plugins.forget("Other");
        TestClock.release();
        world.close();
    }

    private static RankedList.StepOrder<Integer> counting(AtomicInteger reads, Integer... entries) {
        return RankedList.anonymous(Integer.class)
                .source(() -> {
                    reads.incrementAndGet();
                    return Arrays.asList(entries);
                })
                .unlimitedEntries();
    }

    // ------------------------------------------------------------------
    //  identity
    // ------------------------------------------------------------------

    @Test
    void theIdIsTheOwnerAndTheNameLowerCasedAndFindIgnoresCase() {
        RankedList<Integer> list = RankedList.of(jobs, "Miner-Level", Integer.class)
                .source(Collections::emptyList).unlimitedEntries().build();

        assertEquals("finaljobs:miner-level", list.getId());
        assertSame(list, RankedLists.find("FinalJobs:MINER-LEVEL"));
    }

    @Test
    void aNameOutsideTheCharsetIsRefusedWithTheWayOut() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> RankedList.of(jobs, "top:miner", Integer.class));

        assertTrue(refused.getMessage().contains("a-z, 0-9") && refused.getMessage().contains("RankedList.anonymous"),
                "the refusal has to say what a name may hold and what a list with no stable name uses: " + refused.getMessage());
    }

    @Test
    void anAnonymousListHasNoIdAndIsNeverRegistered() {
        RankedList<Integer> list = RankedList.anonymous(Integer.class).source(Collections::emptyList).unlimitedEntries().build();

        assertNull(list.getId());
        assertNull(list.getOwner());
        assertTrue(RankedLists.ownedBy(jobs).isEmpty());
    }

    @Test
    void buildingTheSameIdAgainReplacesTheRegistration() {
        RankedList<Integer> first = RankedList.of(jobs, "top", Integer.class).source(Collections::emptyList).unlimitedEntries().build();
        RankedList<Integer> second = RankedList.of(jobs, "top", Integer.class).source(Collections::emptyList).unlimitedEntries().build();

        assertSame(second, RankedLists.find("finaljobs:top"), "a reload builds the list again, and old links must reach the new one");
        assertEquals(1, RankedLists.ownedBy(jobs).size());
        assertFalse(RankedLists.ownedBy(jobs).contains(first));
    }

    @Test
    void closeOwnedByDropsOnlyThatOwnersLists() {
        RankedList.of(jobs, "top", Integer.class).source(Collections::emptyList).unlimitedEntries().build();
        RankedList<Integer> kept = RankedList.of(other, "top", Integer.class).source(Collections::emptyList).unlimitedEntries().build();

        RankedLists.closeOwnedBy(jobs);

        assertNull(RankedLists.find("finaljobs:top"));
        assertSame(kept, RankedLists.find("other:top"));
    }

    // ------------------------------------------------------------------
    //  where and how often the source is read
    // ------------------------------------------------------------------

    @Test
    void aPlainSourceIsReadOnTheCallingThread() {
        AtomicReference<Thread> readOn = new AtomicReference<>();
        RankedList<Integer> list = RankedList.anonymous(Integer.class)
                .source(() -> {
                    readOn.set(Thread.currentThread());
                    return Collections.singletonList(1);
                })
                .unlimitedEntries().build();

        CompletableFuture<RankedSnapshot<Integer>> read = list.snapshot();

        assertTrue(read.isDone(), "a plain source answers before snapshot() returns");
        assertSame(Thread.currentThread(), readOn.get());
    }

    @Test
    void anAsyncSourceNeverBlocksTheCallerAndServesNothingBeforeItsFirstRead() {
        CompletableFuture<List<Integer>> pending = new CompletableFuture<>();
        RankedList<Integer> list = RankedList.anonymous(Integer.class).sourceAsync(() -> pending).unlimitedEntries().build();

        CompletableFuture<RankedSnapshot<Integer>> read = list.snapshot();

        assertFalse(read.isDone(), "the caller must not wait for the backend");
        assertNull(list.snapshotIfPresent(), "nothing was ever read, so nothing can be served");

        pending.complete(Arrays.asList(3, 1, 2));

        assertEquals(3, read.join().getShownCount());
        assertSame(read.join(), list.snapshotIfPresent());
    }

    @Test
    void oneAsyncReadInFlightIsSharedByEveryCaller() {
        AtomicInteger reads = new AtomicInteger();
        CompletableFuture<List<Integer>> pending = new CompletableFuture<>();
        RankedList<Integer> list = RankedList.anonymous(Integer.class)
                .sourceAsync(() -> {
                    reads.incrementAndGet();
                    return pending;
                })
                .unlimitedEntries().build();

        CompletableFuture<RankedSnapshot<Integer>> first = list.snapshot();
        CompletableFuture<RankedSnapshot<Integer>> second = list.snapshot();
        CompletableFuture<RankedSnapshot<Integer>> refreshed = list.refresh();

        assertSame(first, second, "a caller arriving during a read shares it");
        assertSame(first, refreshed, "even refresh() shares the read already running");
        assertEquals(1, reads.get());
    }

    @Test
    void aFailedAsyncReadKeepsThePreviousSnapshotAndIsRetriedOncePerTtl() {
        AtomicInteger reads = new AtomicInteger();
        List<CompletableFuture<List<Integer>>> handed = new ArrayList<>();
        RankedList<Integer> list = RankedList.anonymous(Integer.class)
                .sourceAsync(() -> {
                    reads.incrementAndGet();
                    CompletableFuture<List<Integer>> future = new CompletableFuture<>();
                    handed.add(future);
                    return future;
                })
                .unlimitedEntries()
                .cache(CachePolicy.ttl(Duration.ofSeconds(10)))
                .build();

        list.snapshot();
        handed.get(0).complete(Arrays.asList(1, 2));
        RankedSnapshot<Integer> good = list.snapshotIfPresent();

        TestClock.advance(Duration.ofSeconds(11));
        CompletableFuture<RankedSnapshot<Integer>> failing = list.snapshot();
        handed.get(1).completeExceptionally(new IllegalStateException("backend down"));

        assertTrue(failing.isCompletedExceptionally(), "the read that failed says so to whoever waited on it");
        assertSame(good, list.snapshotIfPresent(), "a failed read must not take the previous one down");

        assertSame(good, list.snapshot().join(), "inside the ttl of the failure, the previous read is served");
        assertEquals(2, reads.get(), "a source that is down is not hammered once per reader");

        TestClock.advance(Duration.ofSeconds(11));
        list.snapshot();
        assertEquals(3, reads.get(), "once the ttl passed, the source is tried again");
    }

    // ------------------------------------------------------------------
    //  the policies
    // ------------------------------------------------------------------

    @Test
    void ttlReusesTheReadUntilItExpires() {
        AtomicInteger reads = new AtomicInteger();
        RankedList<Integer> list = counting(reads, 1, 2).cache(CachePolicy.ttl(Duration.ofSeconds(5))).build();

        list.snapshot();
        TestClock.advance(Duration.ofSeconds(4));
        list.snapshot();
        assertEquals(1, reads.get());

        TestClock.advance(Duration.ofSeconds(2));
        list.snapshot();
        assertEquals(2, reads.get());
    }

    @Test
    void noneReadsOnEveryCallAndKeepsNothing() {
        AtomicInteger reads = new AtomicInteger();
        RankedList<Integer> list = counting(reads, 1, 2).cache(CachePolicy.none()).build();

        list.snapshot();
        list.snapshot();

        assertEquals(2, reads.get());
        assertNull(list.snapshotIfPresent(), "a read nobody may serve again is not held");
    }

    @Test
    void manualKeepsTheReadUntilInvalidate() {
        AtomicInteger reads = new AtomicInteger();
        RankedList<Integer> list = counting(reads, 1, 2).cache(CachePolicy.manual()).build();

        list.snapshot();
        TestClock.advance(Duration.ofDays(1));
        list.snapshot();
        assertEquals(1, reads.get());

        list.invalidate();
        list.snapshot();
        assertEquals(2, reads.get());
    }

    @Test
    void invalidateDuringAnAsyncReadKeepsTheLateReadOutOfTheCache() {
        List<CompletableFuture<List<Integer>>> handed = new ArrayList<>();
        RankedList<Integer> list = RankedList.anonymous(Integer.class)
                .sourceAsync(() -> {
                    CompletableFuture<List<Integer>> future = new CompletableFuture<>();
                    handed.add(future);
                    return future;
                })
                .unlimitedEntries()
                .cache(CachePolicy.manual())
                .build();

        CompletableFuture<RankedSnapshot<Integer>> stale = list.snapshot();
        list.invalidate();
        handed.get(0).complete(Collections.singletonList(1));

        assertEquals(1, stale.join().getShownCount(), "whoever waited on the stale read still gets an answer");
        assertNull(list.snapshotIfPresent(), "but under manual() a read invalidated mid-flight would be served forever");
        list.snapshot();
        assertEquals(2, handed.size(), "the next snapshot() reads again");
    }

    @Test
    void refreshReadsEvenWhileTheCachedReadIsValid() {
        AtomicInteger reads = new AtomicInteger();
        RankedList<Integer> list = counting(reads, 1).cache(CachePolicy.manual()).build();

        list.snapshot();
        list.refresh();

        assertEquals(2, reads.get());
    }

    // ------------------------------------------------------------------
    //  what one read holds
    // ------------------------------------------------------------------

    @Test
    void theCeilingTruncatesTheReadAndCountsWhatItCut() {
        RankedSnapshot<Integer> snapshot = RankedList.anonymous(Integer.class)
                .source(() -> Arrays.asList(1, 2, 3, 4, 5))
                .maxEntries(3)
                .build().snapshot().join();

        assertTrue(snapshot.isTruncated());
        assertEquals(3, snapshot.getShownCount());
        assertEquals(5, snapshot.getTotalCount());
        assertEquals(3, snapshot.getEntries().get(2).getPosition());
    }

    @Test
    void descendingComparesNumbersAcrossBoxesAndTextIgnoringCase() {
        RankedSnapshot<Object> numbers = RankedList.anonymous(Object.class)
                .source(() -> Arrays.<Object>asList(2, 10L, 3.5d))
                .unlimitedEntries()
                .orderBy(entry -> entry).descending()
                .build().snapshot().join();
        List<Object> order = new ArrayList<>();
        numbers.getEntries().forEach(entry -> order.add(entry.getObject()));
        assertEquals(Arrays.<Object>asList(10L, 3.5d, 2), order, "an Integer and a Long are numbers, not text: \"10\" < \"2\"");

        RankedSnapshot<String> text = RankedList.anonymous(String.class)
                .source(() -> Arrays.asList("alice", "Zoe", "bob"))
                .unlimitedEntries()
                .orderBy(entry -> entry).descending()
                .build().snapshot().join();
        assertEquals("Zoe", text.getEntries().get(0).getObject());
        assertEquals("alice", text.getEntries().get(2).getObject());
    }

    @Test
    void addValueIsComputedOncePerEntryWhoeverAsks() {
        AtomicInteger calls = new AtomicInteger();
        RankedList<String> list = RankedList.anonymous(String.class)
                .source(() -> Collections.singletonList("alpha"))
                .unlimitedEntries()
                .addValue("Shout", entry -> {
                    calls.incrementAndGet();
                    return entry.toUpperCase();
                })
                .build();
        RankedEntry<String> entry = list.snapshot().join().getEntries().get(0);

        assertEquals("ALPHA", list.valueOf(entry, "shout"));
        assertEquals("ALPHA", list.valueOf(entry, "SHOUT"));
        assertEquals(1, calls.get(), "a hundred readers of one entry cost one call");
        assertEquals(1, list.valueOf(entry, RankedList.NUMBER_KEY));
        assertEquals("alpha", list.valueOf(entry, RankedList.VALUE_KEY), "with no order, the value is the entry itself");
        assertNull(list.valueOf(entry, "never-declared"));
    }

    @Test
    void positionOfFindsThePlayerOnTheSnapshotAndNobodyElse() {
        TestFPlayerSender alice = new TestFPlayerSender("alice");
        TestFPlayerSender bob = new TestFPlayerSender("bob");
        TestFPlayerSender carol = new TestFPlayerSender("carol");
        RankedSnapshot<FPlayer> snapshot = RankedList.anonymous(FPlayer.class)
                .source(() -> Arrays.<FPlayer>asList(alice, bob))
                .unlimitedEntries()
                .orderBy(FPlayer::getName).descending()
                .build().snapshot().join();

        assertEquals(Integer.valueOf(1), snapshot.positionOf(bob.getUniqueId()));
        assertEquals(Integer.valueOf(2), snapshot.positionOf(alice.getUniqueId()));
        assertNull(snapshot.positionOf(carol.getUniqueId()));
        assertNull(snapshot.positionOf(null));
    }
}
