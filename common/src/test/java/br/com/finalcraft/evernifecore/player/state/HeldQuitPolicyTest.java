package br.com.finalcraft.evernifecore.player.state;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerFullyLoggedInEvent;
import br.com.finalcraft.evernifecore.api.events.player.ECPlayerQuitEvent;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.region.session.RegionMovementTracker;
import br.com.finalcraft.evernifecore.testing.ECEventConformance;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.PlayerDataWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.Storages;
import br.com.finalcraft.evernifecore.testing.TestClock;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlayerState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What each holder of a slot does when the player quits: give back, keep for a window, or let the plugin decide on the spot. */
class HeldQuitPolicyTest {

    private static final String OWN = "own: sword, 12 bread";
    private static final String KIT = "kit: bow, 64 arrows";
    private static final String OTHER_KIT = "kit: only an axe";
    private static final Duration WINDOW = Duration.ofSeconds(10);

    @TempDir
    Path tempDir;

    private final StateHolder duel = StateHolder.of("theduel:battle");
    private final StateHolder lobby = StateHolder.of("thelobby:waiting");
    private final List<HeldStateQuitEvent.Expired> expired = new CopyOnWriteArrayList<>();
    private final List<ECEventSubscription<?>> subscriptions = new ArrayList<>();

    private ECoreTestWorld platform;
    private PlayerDataWorld world;
    private TestPlayerState access;
    private TestFPlayerSender player;
    private TestPlayerState.State state;

    @BeforeEach
    void setUp() {
        platform = Platforms.lenient().playerState().install();
        TestClock.acquire();
        Plugins.fakeCore(tempDir.resolve("core").toFile());
        world = PlayerDataWorld.with(Storages.memory()).boot(tempDir);
        player = new TestFPlayerSender("duelist");
        access = platform.platform().getTestPlayerState();
        state = access.of(player);
        state.inventory = OWN;
        subscriptions.add(ECEventBus.global().subscribe(HeldStateQuitEvent.Expired.class, expired::add));
        login();
    }

    @AfterEach
    void tearDown() {
        subscriptions.forEach(ECEventSubscription::unsubscribe);
        world.close();
        Plugins.forget(Plugins.CORE);
        platform.close();
        TestClock.release();
    }

    private void login() {
        player.online(true);
        PlayerData data = PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
        data.setPlayer(player);
        ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(data, false));
    }

    private void quit() {
        PlayerData data = PlayerController.getLoaded(player.getUniqueId());
        ECEventBus.global().post(new ECPlayerQuitEvent(data));
        PlayerController.handlePlayerQuit(player.getUniqueId());
        player.online(false);
    }

    private void hold(StateHolder holder, String kit, HoldOptions options) {
        IPlayerStateAccess access = EverNifeCore.getPlatform().getPlayerState();
        HeldPlayerState.of(player.getUniqueId()).hold(HeldPlayerState.INVENTORY, holder, kit, p -> access.setInventory(p, kit), options, player);
        access.setInventory(player, kit);
    }

    private HoldOptions keeps(Duration window) {
        return HoldOptions.persistent().onQuit(QuitPolicy.keepFor(window));
    }

    private List<String> holders() {
        return new ArrayList<>(HeldPlayerState.of(player.getUniqueId()).getHolders(HeldPlayerState.INVENTORY).keySet());
    }

    private String kept() {
        return PlayerController.getPDSection(player.getUniqueId(), PlayerStateBackup.class).join().get(HeldPlayerState.INVENTORY);
    }

    private void pass(Duration time) {
        TestClock.advance(time);
        TestClock.runDue();
    }

    private void whenDelegated(Consumer<HeldStateQuitEvent.Delegated> answer) {
        subscriptions.add(ECEventBus.global().subscribe(HeldStateQuitEvent.Delegated.class, answer::accept));
    }

    @Test
    void theEventsTakeNoReservedName() {
        assertEquals(Collections.emptyList(), ECEventConformance.checkAll(HeldStateQuitEvent.class, HeldStateQuitEvent.Delegated.class, HeldStateQuitEvent.Expired.class));
    }

    // ---- keepFor ----

    @Test
    void keepForWritesNothingOnTheQuitAndTheBackupKeepsTheBefore() {
        hold(duel, KIT, keeps(WINDOW));

        quit();

        assertEquals(KIT, state.inventory, "the kit stays on the player, to be saved with them");
        assertEquals(OWN, kept());
        assertTrue(expired.isEmpty());
    }

    @Test
    void reconnectingInsideTheWindowKeepsTheKitAndTheHolderActive() {
        hold(duel, KIT, keeps(WINDOW));
        quit();
        pass(Duration.ofSeconds(5));

        login();

        assertEquals(KIT, state.inventory);
        assertEquals(Collections.singletonList(duel.getHolderId()), holders());
        pass(WINDOW);
        assertTrue(expired.isEmpty(), "the window ended with the reconnection");
        assertEquals(Collections.singletonList(duel.getHolderId()), holders());

        assertTrue(HeldPlayerState.of(player.getUniqueId()).release(HeldPlayerState.INVENTORY, duel, player));
        assertEquals(OWN, state.inventory, "and the holder still gives the own back when it ends");
        assertNull(kept());
    }

    @Test
    void seedingAPlayerAlreadyOnlineKeepsAHoldWithNoWindowAndTheQuitStillGivesBack() {
        hold(duel, KIT, HoldOptions.memory());

        RegionMovementTracker.get().seedOnline(player, WorldLocPos.of(0, 64, 0, "world"));

        assertEquals(KIT, state.inventory);
        assertEquals(Collections.singletonList(duel.getHolderId()), holders());
        quit();
        assertEquals(OWN, state.inventory, "still held when the quit came");
    }

    @Test
    void aRealJoinStillDropsWhatMemoryKeptOfAPastLife() {
        hold(duel, KIT, HoldOptions.memory());

        RegionMovementTracker.get().onJoin(player, WorldLocPos.of(0, 64, 0, "world"));

        assertEquals(Collections.emptyList(), holders());
    }

    @Test
    void reconnectingThroughTheMovementTrackerAlsoKeepsTheHold() {
        WorldLocPos at = WorldLocPos.of(0, 64, 0, "world");
        hold(duel, KIT, keeps(WINDOW));
        quit();

        player.online(true);
        PlayerData data = PlayerController.handleLogin(player.getUniqueId(), player.getName()).join();
        data.setPlayer(player);
        RegionMovementTracker.get().onJoin(player, at);
        assertEquals(KIT, state.inventory, "the join of the region system must not take the kit for a past life's");
        assertEquals(Collections.singletonList(duel.getHolderId()), holders());

        ECEventBus.global().post(new ECPlayerFullyLoggedInEvent(data, false));
        assertEquals(KIT, state.inventory);
        pass(WINDOW);
        assertTrue(expired.isEmpty());
    }

    @Test
    void theLoginBeforeTheMovementTrackerKeepsTheHoldToo() {
        hold(duel, KIT, keeps(WINDOW));
        quit();

        login();
        RegionMovementTracker.get().onJoin(player, WorldLocPos.of(0, 64, 0, "world"));

        assertEquals(KIT, state.inventory);
        assertEquals(Collections.singletonList(duel.getHolderId()), holders());
    }

    @Test
    void theWindowEndingOffTheServerPostsOnceAndTheNextJoinGivesTheOwnBack() {
        hold(duel, KIT, keeps(WINDOW));
        quit();

        pass(WINDOW);
        assertEquals(1, expired.size());
        HeldStateQuitEvent.Expired event = expired.get(0);
        assertTrue(event.isHeldBy(duel));
        assertEquals(player.getUniqueId(), event.getPlayerId());
        assertEquals(HeldPlayerState.INVENTORY, event.getSlot());
        assertEquals(OWN, event.getBefore());
        assertEquals(KIT, state.inventory, "nobody to write to");
        pass(WINDOW);
        assertEquals(1, expired.size(), "once");

        login();
        assertEquals(OWN, state.inventory);
        assertNull(kept());
        assertEquals(1, expired.size());
    }

    @Test
    void aSecondCallOfTheSameQuitDoesNothing() {
        hold(duel, KIT, keeps(WINDOW));
        quit();

        HeldPlayerState.forget(player.getUniqueId(), player);

        assertEquals(KIT, state.inventory);
        assertEquals(Collections.singletonList(duel.getHolderId()), holders());
    }

    @Test
    void aSecondQuitOpensAWindowOfItsOwnAndTheFirstTimerIsDead() {
        hold(duel, KIT, keeps(WINDOW));
        quit();
        pass(Duration.ofSeconds(8));
        login();
        pass(Duration.ofSeconds(1));
        quit();

        pass(Duration.ofSeconds(2));
        assertTrue(expired.isEmpty(), "the timer of the first quit would have fired by now");
        pass(Duration.ofSeconds(8));
        assertEquals(1, expired.size());
    }

    // ---- delegate ----

    @Test
    void theDelegatedEventCarriesTheSnapshotAndAWritablePlayer() {
        List<HeldStateQuitEvent.Delegated> asked = new ArrayList<>();
        whenDelegated(event -> {
            asked.add(event);
            access.setInventory(event.getPlayer(), event.getCurrent() + ", marked");
            event.keepFor(WINDOW);
        });
        hold(duel, KIT, HoldOptions.persistent().onQuit(QuitPolicy.delegate()));

        quit();

        assertEquals(1, asked.size());
        HeldStateQuitEvent.Delegated event = asked.get(0);
        assertEquals(HeldPlayerState.INVENTORY, event.getSlot());
        assertEquals(OWN, event.getBefore());
        assertEquals(KIT, event.getCurrent());
        assertSame(player, event.getPlayer());
        assertTrue(event.isHeldBy(duel));
        assertEquals(KIT + ", marked", state.inventory, "written while the player was still there");
    }

    @Test
    void answeringKeepForOpensTheWindow() {
        whenDelegated(event -> event.keepFor(WINDOW));
        hold(duel, KIT, HoldOptions.persistent().onQuit(QuitPolicy.delegate()));

        quit();
        assertEquals(KIT, state.inventory);
        assertEquals(OWN, kept());

        pass(WINDOW);
        assertEquals(1, expired.size());
        login();
        assertEquals(OWN, state.inventory);
    }

    @Test
    void aHoldInMemoryAnsweredWithKeepForLandsInThePlayersData() {
        whenDelegated(event -> event.keepFor(WINDOW));
        hold(duel, KIT, HoldOptions.memory().onQuit(QuitPolicy.delegate()));
        assertNull(kept());

        quit();

        assertEquals(KIT, state.inventory);
        assertEquals(OWN, kept(), "a crash during the window must still give the own back");
    }

    @Test
    void answeringGiveBackGivesBackOnTheQuit() {
        whenDelegated(HeldStateQuitEvent.Delegated::giveBack);
        hold(duel, KIT, HoldOptions.persistent().onQuit(QuitPolicy.delegate()));

        quit();

        assertEquals(OWN, state.inventory);
        assertNull(kept());
        pass(WINDOW);
        assertTrue(expired.isEmpty());
    }

    @Test
    void noAnswerGivesBack() {
        hold(duel, KIT, HoldOptions.persistent().onQuit(QuitPolicy.delegate()));

        quit();

        assertEquals(OWN, state.inventory);
    }

    @Test
    void aSubscriberThatThrowsGivesBack() {
        whenDelegated(event -> {
            throw new IllegalStateException("a broken plugin");
        });
        hold(duel, KIT, HoldOptions.persistent().onQuit(QuitPolicy.delegate()));

        quit();

        assertEquals(OWN, state.inventory, "a subscriber that throws cannot lose the own inventory");
    }

    // ---- releaseAway ----

    @Test
    void releasedAwayTheNextJoinGivesBackAndNoExpiryComes() {
        hold(duel, KIT, keeps(WINDOW));
        quit();

        assertTrue(HeldPlayerState.releaseAway(player.getUniqueId(), HeldPlayerState.INVENTORY, duel));
        assertEquals(KIT, state.inventory, "nobody to write to");
        pass(WINDOW);
        assertTrue(expired.isEmpty());

        login();
        assertEquals(OWN, state.inventory);
        assertNull(kept());
    }

    @Test
    void releasingAwayWhatItDoesNotHoldTouchesNothing() {
        hold(duel, KIT, keeps(WINDOW));
        quit();

        assertFalse(HeldPlayerState.releaseAway(player.getUniqueId(), HeldPlayerState.INVENTORY, lobby));

        assertEquals(Collections.singletonList(duel.getHolderId()), holders());
        assertEquals(OWN, kept());
        pass(WINDOW);
        assertEquals(1, expired.size(), "the holder's window still runs");
    }

    @Test
    void anOnlinePlayerIsNotAway() {
        hold(duel, KIT, keeps(WINDOW));

        assertFalse(HeldPlayerState.releaseAway(player.getUniqueId(), HeldPlayerState.INVENTORY, duel));
        assertEquals(KIT, state.inventory);
        assertEquals(Collections.singletonList(duel.getHolderId()), holders());
    }

    @Test
    void aPlainReleaseDuringTheWindowWritesNothingOnTheOldPlayer() {
        hold(duel, KIT, keeps(WINDOW));
        quit();

        assertTrue(HeldPlayerState.of(player.getUniqueId()).release(HeldPlayerState.INVENTORY, duel, player));

        assertEquals(KIT, state.inventory, "the object of a player who left: a write there would be lost, and the backup with it");
        assertEquals(OWN, kept());
        login();
        assertEquals(OWN, state.inventory);
    }

    // ---- two holders ----

    @Test
    void aHolderThatGivesBackLeavesTheSlotWithTheOneThatKeeps() {
        hold(duel, KIT, keeps(WINDOW));
        hold(lobby, OTHER_KIT, HoldOptions.persistent());

        quit();

        assertEquals(Collections.singletonList(duel.getHolderId()), holders());
        assertEquals(KIT, state.inventory, "the latest let go, as a release does: the one still there puts its value back");
        assertEquals(OWN, kept());
    }

    @Test
    void aDelegateAnsweredWithKeepForKeepsTheSlotBesideAGiveBack() {
        whenDelegated(event -> event.keepFor(WINDOW));
        hold(lobby, OTHER_KIT, HoldOptions.persistent());
        hold(duel, KIT, HoldOptions.persistent().onQuit(QuitPolicy.delegate()));

        quit();

        assertEquals(Collections.singletonList(duel.getHolderId()), holders());
        assertEquals(KIT, state.inventory);
    }

    @Test
    void theFirstWindowToEndLetsGoAsAReleaseAndTheLastGivesBack() {
        hold(duel, KIT, keeps(WINDOW));
        hold(lobby, OTHER_KIT, keeps(Duration.ofSeconds(30)));

        quit();
        assertEquals(Arrays.asList(duel.getHolderId(), lobby.getHolderId()), holders());
        assertEquals(OTHER_KIT, state.inventory);

        pass(WINDOW);
        assertEquals(1, expired.size());
        assertTrue(expired.get(0).isHeldBy(duel));
        assertEquals(Collections.singletonList(lobby.getHolderId()), holders());
        assertEquals(OWN, kept());

        pass(Duration.ofSeconds(20));
        assertEquals(2, expired.size());
        assertTrue(expired.get(1).isHeldBy(lobby));

        login();
        assertEquals(OWN, state.inventory);
        assertNull(kept());
    }

    @Test
    void comingBackAfterTheLatestWindowEndedPutsTheHolderStillThereBack() {
        hold(duel, KIT, keeps(Duration.ofSeconds(30)));
        hold(lobby, OTHER_KIT, keeps(WINDOW));

        quit();
        pass(WINDOW);
        assertEquals(OTHER_KIT, state.inventory, "the kit of the holder that let go is what was saved with the player");

        login();

        assertEquals(KIT, state.inventory);
        assertEquals(Collections.singletonList(duel.getHolderId()), holders());
    }

    // ---- no PlayerData layer ----

    @Test
    void withoutAPlayerDataLayerAWindowCannotExistAndTheQuitGivesBack() {
        world.close();
        hold(duel, KIT, keeps(WINDOW));

        HeldPlayerState.forget(player.getUniqueId(), player);

        assertEquals(OWN, state.inventory);
        world = PlayerDataWorld.with(Storages.memory()).boot(tempDir);
    }
}
