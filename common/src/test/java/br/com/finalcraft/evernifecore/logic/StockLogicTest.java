package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.flag.FlagKey;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.player.state.ECGameMode;
import br.com.finalcraft.evernifecore.player.state.HeldPlayerState;
import br.com.finalcraft.evernifecore.player.state.IPlayerStateAccess.Capability;
import br.com.finalcraft.evernifecore.player.state.StateHolder;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.TestPlayerState;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static br.com.finalcraft.evernifecore.logic.LogicFixtures.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stock variables, conditions and effects, and holding a slot of the player's state from logic. */
class StockLogicTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private TestFPlayerSender alex;
    private TestPlayerState state;

    @BeforeEach
    void setUp() {
        world = Platforms.strict().ignoringPlatformRegistrations().platformProviderId("test").mainThreadInline().loggingToStdout().playerState().install();
        shop = LogicWorld.open("FakeShop", tempDir);
        alex = shop.player("Alex");
        state = ((TestPlatform) EverNifeCore.getPlatform()).getTestPlayerState();
    }

    @AfterEach
    void tearDown() {
        HeldPlayerState.forget(alex.getUniqueId(), null);
        shop.close();
        world.close();
    }

    private ExecutionContext ctx() {
        return shop.context(alex).build();
    }

    private boolean passes(String json) {
        return Conditions.test(Conditions.parse(shop.getOwner(), node(json), ZoneOffset.UTC), ctx()).isPassed();
    }

    private void apply(String json, ExecutionContext ctx) {
        Effects.apply(ctx, Effects.parse(shop.getOwner(), node(json), ZoneOffset.UTC));
    }

    @Test
    void theStockTypesAreRegisteredUnderTheCore() {
        List<String> variables = new ArrayList<>();
        for (TypeDescriptor<?> descriptor : Variables.registry().getAll()) {
            variables.add(descriptor.getKey());
        }
        assertTrue(variables.containsAll(Arrays.asList("evernifecore:exp-level", "evernifecore:exp-points", "evernifecore:health",
                "evernifecore:food", "evernifecore:gamemode", "evernifecore:permission", "evernifecore:placeholder", "evernifecore:playtime",
                "evernifecore:stat")), variables.toString());
        for (String effect : Arrays.asList("message", "command", "set", "add", "teleport", "gamemode", "fly", "potion-effect", "player-time", "player-weather")) {
            assertTrue(Effects.registry().find(effect).isPresent(), effect);
        }
        assertTrue(Conditions.registry().find("compare").isPresent());
        assertTrue(Conditions.registry().find("permission").isPresent());
    }

    @Test
    void compareReadsNumbersTextsAndBooleans() {
        state.of(alex).level = 12;
        alex.grant("shop.vip");

        assertTrue(passes("{'type': 'compare', 'variable': 'exp-level', 'op': '>=', 'amount': 10}"));
        assertFalse(passes("{'type': 'compare', 'variable': 'exp-level', 'op': '<', 'amount': '12'}"));
        assertTrue(passes("{'type': 'compare', 'variable': 'gamemode', 'op': '=', 'amount': 'survival'}"));
        assertTrue(passes("{'type': 'compare', 'variable': 'gamemode', 'op': 'contains', 'amount': 'surv'}"));
        assertTrue(passes("{'type': 'permission', 'node': 'shop.vip'}"));
        assertFalse(passes("{'type': 'permission', 'node': 'shop.admin'}"));
        assertTrue(passes("{'type': 'compare', 'variable': 'health', 'op': '=', 'amount': '20'}"));
    }

    @Test
    void aFailedCompareSaysTheValueAndWhatItMustBe() {
        state.of(alex).level = 3;

        ConditionResult result = Conditions.test(Conditions.parse(shop.getOwner(),
                node("{'type': 'compare', 'variable': 'exp-level', 'op': '>=', 'amount': 10}"), ZoneOffset.UTC), ctx());

        assertEquals("exp-level is 3, and it must be >= 10.", result.getWhy().toPlainText());
    }

    @Test
    void resolveReadsAStockVariable() {
        assertEquals("You have 20 health and {nada}", ctx().resolve("You have {health} health and {nada}"));
    }

    @Test
    void aTypeThatNeedsACapabilityThePlatformLacksFailsTheParseNamingIt() {
        state.without(Capability.EXPERIENCE, Capability.STATISTICS);

        TypeRegistryException compare = assertThrows(TypeRegistryException.class, () -> Conditions.parse(shop.getOwner(),
                node("{'type': 'compare', 'variable': 'exp-level', 'op': '>=', 'amount': 1}"), ZoneOffset.UTC));
        TypeRegistryException stat = assertThrows(TypeRegistryException.class, () -> Variables.registry().parse(shop.getOwner(),
                node("{'type': 'stat', 'name': 'JUMP'}")));

        assertTrue(compare.getMessage().contains("EXPERIENCE"), compare.getMessage());
        assertTrue(stat.getMessage().contains("STATISTICS"), stat.getMessage());
    }

    @Test
    void statReadsTheNamedStatistic() {
        state.of(alex).statistics.put("JUMP", new BigDecimal("42"));

        Variable<?> jumps = Variables.registry().parse(shop.getOwner(), node("{'type': 'stat', 'name': 'JUMP'}"));

        assertEquals(new BigDecimal("42"), jumps.read(ctx()));
    }

    @Test
    void setAndAddWriteAWritableVariableAndAddOnATextIsAuditedAsFailed() {
        ExecutionContext ctx = ctx();

        apply("{'type': 'add', 'variable': 'food', 'amount': -5}", ctx);
        apply("{'type': 'set', 'variable': 'exp-level', 'amount': 7}", ctx);
        apply("{'type': 'add', 'variable': 'gamemode', 'amount': 1}", ctx);

        assertEquals(15, state.of(alex).food);
        assertEquals(7, state.of(alex).level);
        assertEquals(1, ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED).size());
    }

    @Test
    void withShareSplitsAnAddAndLeavesASetWhole() {
        Effect add = Effects.parse(shop.getOwner(), node("{'type': 'add', 'variable': 'exp-points', 'amount': 100}"), ZoneOffset.UTC);

        Effects.apply(ctx(), add.withShare(new BigDecimal("0.25")));

        assertEquals(25, state.of(alex).exp);
    }

    @Test
    void commandRunsAsTheConsoleUnlessAsPlayer() {
        apply("{'type': 'command', 'command': '/give {player} diamond 1'}", ctx());
        apply("{'type': 'command', 'command': 'spawn', 'as': 'player'}", ctx());

        assertEquals(Arrays.asList("console: give Alex diamond 1", "Alex: spawn"), state.getCommands());
    }

    @Test
    void messageSendsTheResolvedTextAndTeleportMovesThePlayer() {
        apply("{'type': 'message', 'text': '&aWelcome, {player}!'}", ctx());
        apply("{'type': 'teleport', 'world': 'world', 'x': 100, 'y': 65, 'z': -20}", ctx());

        alex.assertAnyMessageContains("Welcome, Alex!");
        assertEquals(1, alex.getTeleports().size());
        assertEquals(100, alex.getTeleports().get(0).getWorldLocPos().getX(), 0.0);
    }

    @Test
    void gamemodeHeldByLogicAndAFlagOnTheSameSlotFirstSavesLastGivesBack() {
        StateHolder stage = StateHolder.of("finalquests:tutorial/stage:mine");
        FlagKey flag = FlagKey.of("evernifecore", "game-mode");
        HoldableEffect adventure = (HoldableEffect) Effects.parse(shop.getOwner(), node("{'type': 'gamemode', 'value': 'adventure'}"), ZoneOffset.UTC);
        HeldPlayerState held = HeldPlayerState.of(alex.getUniqueId());

        HoldableEffect.HeldEffect byLogic = adventure.hold(ctx(), stage);
        held.hold(HeldPlayerState.GAME_MODE, flag, ECGameMode.CREATIVE, player -> state.of(player).gameMode = ECGameMode.CREATIVE, false, alex);
        state.of(alex).gameMode = ECGameMode.CREATIVE;

        assertEquals(Arrays.asList(stage.getHolderId(), flag.getHolderId()), new ArrayList<>(held.getHolders(HeldPlayerState.GAME_MODE).keySet()));
        assertFalse(byLogic.release(), "the flag still holds the slot");
        assertEquals(ECGameMode.CREATIVE, state.of(alex).gameMode);
        assertTrue(held.release(HeldPlayerState.GAME_MODE, flag, alex), "the last holder gives it back");
        assertEquals(ECGameMode.SURVIVAL, state.of(alex).gameMode, "what the player had before the first holder");
    }

    @Test
    void releasingAHeldEffectWhileAFlagStillHoldsTheSlotPutsTheFlagsValueBack() {
        FlagKey flag = FlagKey.of("evernifecore", "game-mode");
        HeldPlayerState held = HeldPlayerState.of(alex.getUniqueId());
        held.hold(HeldPlayerState.GAME_MODE, flag, ECGameMode.CREATIVE, player -> state.of(player).gameMode = ECGameMode.CREATIVE, false, alex);
        state.of(alex).gameMode = ECGameMode.CREATIVE;
        HoldableEffect adventure = (HoldableEffect) Effects.parse(shop.getOwner(), node("{'type': 'gamemode', 'value': 'adventure'}"), ZoneOffset.UTC);

        HoldableEffect.HeldEffect byLogic = adventure.hold(ctx(), StateHolder.of("finalquests:tutorial/stage:mine"));
        assertEquals(ECGameMode.ADVENTURE, state.of(alex).gameMode);

        assertFalse(byLogic.release(), "the flag still holds the slot");
        assertEquals(ECGameMode.CREATIVE, state.of(alex).gameMode, "the flag's mode is back, not the effect's");
        assertTrue(held.release(HeldPlayerState.GAME_MODE, flag, alex));
        assertEquals(ECGameMode.SURVIVAL, state.of(alex).gameMode, "what the player had before the first holder");
    }

    @Test
    void scopeHeldFromADefinitionHoldsForTheContextsHolderAndForgetOnQuitGivesItBack() {
        StateHolder stage = StateHolder.of("finalquests:tutorial/stage:mine");
        ExecutionContext ctx = shop.context(alex).attribute(HoldableEffect.HOLDER_ATTRIBUTE, stage).build();

        apply("{'type': 'gamemode', 'value': 'adventure', 'scope': 'held'}", ctx);

        assertEquals(ECGameMode.ADVENTURE, state.of(alex).gameMode);
        assertTrue(HeldPlayerState.of(alex.getUniqueId()).getHolders(HeldPlayerState.GAME_MODE).containsKey(stage.getHolderId()));
        assertEquals(1, ctx.getAudit().getEntries(AuditKind.HELD).size());

        HeldPlayerState.forget(alex.getUniqueId(), alex);

        assertEquals(ECGameMode.SURVIVAL, state.of(alex).gameMode, "quit gives back what logic still held");
        assertTrue(HeldPlayerState.of(alex.getUniqueId()).getHolders(HeldPlayerState.GAME_MODE).isEmpty(), "a fresh state after forget");
    }

    @Test
    void scopeHeldWithoutAHolderInTheContextIsHeldByTheCaller() {
        apply("{'type': 'fly', 'scope': 'held'}", ctx());

        assertTrue(state.of(alex).allowFlight);
        assertTrue(HeldPlayerState.of(alex.getUniqueId()).getHolders(HeldPlayerState.FLIGHT).containsKey("fakeshop:test"));
    }

    @Test
    void theHoldableEffectsChangeTheirSlotForGoodWithoutScopeHeld() {
        apply("{'type': 'potion-effect', 'effect': 'speed', 'level': 2}", ctx());
        apply("{'type': 'player-time', 'value': 6000}", ctx());
        apply("{'type': 'player-weather', 'value': 'rain'}", ctx());

        assertEquals(2, state.of(alex).effects.get("speed").getEffect().getLevel());
        assertEquals(6000, state.of(alex).time.getTicks());
        assertEquals("rain", state.of(alex).weather);
        assertTrue(HeldPlayerState.of(alex.getUniqueId()).getHolders(HeldPlayerState.TIME).isEmpty(), "nothing held");
    }
}
