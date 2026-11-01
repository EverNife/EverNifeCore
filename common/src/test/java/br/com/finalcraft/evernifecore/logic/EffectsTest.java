package br.com.finalcraft.evernifecore.logic;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.logic.LogicFixtures.RecordingEffect;
import br.com.finalcraft.evernifecore.logic.audit.AuditEntry;
import br.com.finalcraft.evernifecore.logic.audit.AuditKind;
import br.com.finalcraft.evernifecore.logic.audit.EffectApplyEvent;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.LogicWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import br.com.finalcraft.evernifecore.types.TypeRegistryException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static br.com.finalcraft.evernifecore.logic.LogicFixtures.node;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Applying effects on the target's thread, the modifiers, the deferred sink and the apply event. */
class EffectsTest {

    @TempDir
    Path tempDir;

    private ECoreTestWorld world;
    private LogicWorld shop;
    private final RecordingEffect reward = new RecordingEffect("reward");

    private void open(Platforms platform) {
        world = platform.install();
        shop = LogicWorld.open("FakeShop", tempDir);
        Effects.registry().register(shop.getOwner(), TypeDescriptor.<Effect>of("fakeshop:reward")
                .describe("Records who got the reward.")
                .factory(params -> reward)
                .build());
    }

    private void open() {
        open(Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().loggingToStdout().playerState());
    }

    @AfterEach
    void tearDown() {
        DeferredEffects.register(null);
        if (shop != null) {
            shop.close();
        }
        if (world != null) {
            world.close();
        }
    }

    private Effect parse(String json) {
        return Effects.parse(shop.getOwner(), node(json), ZoneOffset.UTC);
    }

    @Test
    void applyOnTheWorldThreadRunsInPlaceAndOffItGoesThroughRunOnWorldThread() {
        open(Platforms.strict().ignoringPlatformRegistrations().mainThreadInline().offWorldThread().loggingToStdout().playerState());
        TestFPlayerSender alex = shop.player("Alex");

        Effects.apply(shop.context(alex).build(), reward);

        assertEquals(Collections.singletonList("world"), ((TestPlatform) EverNifeCore.getPlatform()).getWorldThreadRuns());
        assertEquals(Collections.singletonList("Alex"), reward.applied);
    }

    @Test
    void applyAlreadyOnTheWorldThreadRunsWithoutQueueing() {
        open();
        TestFPlayerSender alex = shop.player("Alex");

        Effects.apply(shop.context(alex).build(), reward);

        assertTrue(((TestPlatform) EverNifeCore.getPlatform()).getWorldThreadRuns().isEmpty());
        assertEquals(Collections.singletonList("Alex"), reward.applied);
    }

    @Test
    void effectApplyEventSetEffectReplacesWhatRuns() {
        open();
        RecordingEffect replacement = new RecordingEffect("replacement");
        ECEventSubscription<EffectApplyEvent> swap = ECEventBus.global().subscribe(EffectApplyEvent.class, event -> event.setEffect(replacement));
        try {
            Effects.apply(shop.context(shop.player("Alex")).build(), reward);
        } finally {
            swap.unsubscribe();
        }

        assertTrue(reward.applied.isEmpty());
        assertEquals(Collections.singletonList("Alex"), replacement.applied);
    }

    @Test
    void aCancelledEffectApplyEventDoesNotApply() {
        open();
        ECEventSubscription<EffectApplyEvent> veto = ECEventBus.global().subscribe(EffectApplyEvent.class, event -> event.setCancelled(true));
        try {
            Effects.apply(shop.context(shop.player("Alex")).build(), reward);
        } finally {
            veto.unsubscribe();
        }

        assertTrue(reward.applied.isEmpty());
    }

    @Test
    void aDelayedEffectWhosePlayerLeftGoesToTheDefaultSinkAndIsAuditedDeferredDropped() {
        open();
        TestFPlayerSender alex = shop.player("Alex");
        ExecutionContext ctx = shop.context(alex).build();

        Effects.apply(ctx, parse("{'type': 'reward', 'delay': '2s'}"));
        assertTrue(reward.applied.isEmpty(), "nothing before the delay");
        shop.quit(alex);
        shop.advance(Duration.ofSeconds(2));
        shop.runDue();

        assertTrue(reward.applied.isEmpty(), "an offline player never gets it");
        List<AuditEntry> dropped = ctx.getAudit().getEntries(AuditKind.DEFERRED_DROPPED);
        assertEquals(1, dropped.size());
        assertTrue(dropped.get(0).getText().contains(alex.getUniqueId().toString()), dropped.get(0).getText());
    }

    @Test
    void aDelayedEffectWhosePlayerStayedAppliesWhenDue() {
        open();
        ExecutionContext ctx = shop.context(shop.player("Alex")).build();

        Effects.apply(ctx, parse("{'type': 'reward', 'delay': '2s'}"));
        shop.advance(Duration.ofSeconds(1));
        shop.runDue();
        assertTrue(reward.applied.isEmpty());
        shop.advance(Duration.ofSeconds(1));
        shop.runDue();

        assertEquals(Collections.singletonList("Alex"), reward.applied);
    }

    @Test
    void repeatRunsTheGivenTimesAtTheGivenInterval() {
        open();
        ExecutionContext ctx = shop.context(shop.player("Alex")).build();

        Effects.apply(ctx, parse("{'type': 'reward', 'repeat': 3, 'every': '10s'}"));
        assertEquals(1, reward.applied.size(), "the first run is now");
        shop.advance(Duration.ofSeconds(10));
        shop.runDue();
        shop.advance(Duration.ofSeconds(10));
        shop.runDue();

        assertEquals(3, reward.applied.size());
    }

    @Test
    void chanceZeroNeverAppliesAndChanceOneAlwaysDoes() {
        open();
        ExecutionContext ctx = shop.context(shop.player("Alex")).build();
        Effect never = parse("{'type': 'reward', 'chance': 0}");
        Effect always = parse("{'type': 'reward', 'chance': 1}");

        for (int i = 0; i < 50; i++) {
            Effects.apply(ctx, never);
        }
        assertTrue(reward.applied.isEmpty());
        for (int i = 0; i < 50; i++) {
            Effects.apply(ctx, always);
        }
        assertEquals(50, reward.applied.size());
    }

    @Test
    void anIfThatFailsSkipsTheEffect() {
        open();
        ExecutionContext ctx = shop.context(shop.player("Alex")).build();

        Effects.apply(ctx, parse("{'type': 'reward', 'if': {'type': 'all', 'conditions': [{'type': 'none', 'conditions': [{'type': 'all', 'conditions': []}]}]}}"));

        assertTrue(reward.applied.isEmpty(), "none of an always-passing all fails");
    }

    @Test
    void targetWorldPlayersAppliesToEveryOnlinePlayerOfTheWorld() {
        open();
        TestFPlayerSender alex = shop.player("Alex");
        shop.player("Bia");
        shop.player("Caio").at("nether", 0, 64, 0);

        Effects.apply(shop.context(alex).build(), parse("{'type': 'reward', 'target': 'world-players'}"));

        assertEquals(Arrays.asList("Alex", "Bia"), reward.applied);
    }

    @Test
    void targetOnlinePlayersAppliesToEveryOnlinePlayerWhateverTheOwner() {
        open();
        TestFPlayerSender alex = shop.player("Alex");
        shop.player("Bia");
        shop.player("Caio").at("nether", 0, 64, 0);
        ExecutionContext ofARegion = shop.context(alex).owner(ProgressOwner.of(ProgressOwner.REGION, "fakeshop/plaza")).build();

        Effects.apply(ofARegion, parse("{'type': 'reward', 'target': 'online-players'}"));

        assertEquals(Arrays.asList("Alex", "Bia", "Caio"), reward.applied);
    }

    @Test
    void targetGroupWithoutAProviderFailsTeachingHowToRegisterOne() {
        open();
        ExecutionContext ctx = shop.context(shop.player("Alex")).owner(ProgressOwner.of("group", "finalparty/abc")).build();

        Effects.apply(ctx, parse("{'type': 'reward', 'target': 'group'}"));

        assertTrue(reward.applied.isEmpty());
        List<AuditEntry> failed = ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED);
        assertEquals(1, failed.size());
        assertTrue(failed.get(0).getText().contains("ProgressOwners.register"), failed.get(0).getText());
    }

    @Test
    void aFailingEffectInAListIsAuditedAndTheOthersStillRun() {
        open();
        RecordingEffect broken = new RecordingEffect("broken");
        broken.failsWhen = ctx -> true;
        RecordingEffect after = new RecordingEffect("after");
        ExecutionContext ctx = shop.context(shop.player("Alex")).build();

        Effects.apply(ctx, Effects.of(Arrays.<Effect>asList(reward, broken, after)));

        assertEquals(1, reward.applied.size());
        assertEquals(1, after.applied.size());
        assertEquals(1, ctx.getAudit().getEntries(AuditKind.EFFECT_FAILED).size());
        assertEquals(2, ctx.getAudit().getEntries(AuditKind.EFFECT_APPLIED).size());
    }

    @Test
    void asConsoleOnAnEffectThatTakesNoSenderIsAParseError() {
        open();

        TypeRegistryException refused = assertThrows(TypeRegistryException.class, () -> parse("{'type': 'reward', 'as': 'console'}"));

        assertTrue(refused.getMessage().contains("as: console") && refused.getMessage().contains("command"), refused.getMessage());
    }

    @Test
    void heldOnAnEffectThatCannotBeHeldFailsTheParseNamingEffectsOnEnter() {
        open();

        TypeRegistryException refused = assertThrows(TypeRegistryException.class, () -> parse("{'type': 'reward', 'scope': 'held'}"));

        assertTrue(refused.getMessage().contains("effects-on-enter") && refused.getMessage().contains("scope: permanent"), refused.getMessage());
    }

    @Test
    void toIsRefusedOnAnEffectThatHandsNothingOver() {
        open();

        TypeRegistryException refused = assertThrows(TypeRegistryException.class, () -> parse("{'type': 'reward', 'to': 'mailbox'}"));

        assertTrue(refused.getMessage().contains("hands nothing over") && refused.getMessage().contains("give-item"), refused.getMessage());
    }

    @Test
    void aModifierWithAnUnreadableValueNamesTheKeyAndWhatItExpects() {
        open();

        TypeRegistryException chance = assertThrows(TypeRegistryException.class, () -> parse("{'type': 'reward', 'chance': 2}"));
        TypeRegistryException repeat = assertThrows(TypeRegistryException.class, () -> parse("{'type': 'reward', 'repeat': 3}"));

        assertTrue(chance.getMessage().contains("chance = 2") && chance.getMessage().contains("from 0"), chance.getMessage());
        assertTrue(repeat.getMessage().contains("every"), repeat.getMessage());
    }
}
