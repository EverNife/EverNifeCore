package br.com.finalcraft.evernifecore.group;

import br.com.finalcraft.evernifecore.action.Actor;
import br.com.finalcraft.evernifecore.action.DamageContext;
import br.com.finalcraft.evernifecore.action.ECEntityType;
import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.XpContext;
import br.com.finalcraft.evernifecore.action.event.XpGainActionEvent;
import br.com.finalcraft.evernifecore.economy.BalanceChangeEvent;
import br.com.finalcraft.evernifecore.economy.Currencies;
import br.com.finalcraft.evernifecore.economy.CurrencyOwner;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.flag.Decision;
import br.com.finalcraft.evernifecore.flag.FlagRegistry;
import br.com.finalcraft.evernifecore.flag.ProtectionFlagGate;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.group.event.GroupMemberPreJoinEvent;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.GateResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.testing.Actors;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Economies;
import br.com.finalcraft.evernifecore.testing.GroupWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.Plugins;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import br.com.finalcraft.evernifecore.testing.TestCurrency;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestGlow;
import br.com.finalcraft.evernifecore.testing.junit.ECoreTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The five group flags the core applies: friendly fire, the member cap, the shared gains and the glow. */
@ECoreTest
class GroupFlagsTest {

    private static final String WORLD = "world";

    @TempDirNobodyCleans
    Path tempDir;

    private ECoreTestWorld world;
    private ECPluginData core;
    private GroupWorld parties;
    private GroupKind party;
    private TestCurrency xp;
    private TestFPlayerSender alice;
    private TestFPlayerSender bob;
    private TestFPlayerSender carol;
    private TestFPlayerSender dave;

    @BeforeEach
    void open() {
        world = Platforms.lenient().withGlow().install().withEconomy(Economies.inMemory().build());
        core = Plugins.fakeCore(tempDir.resolve("core").toFile());
        CoreFlags.registerAll(core);
        xp = Economies.currency("evernifecore:exp-points", CurrencyOwner.Kind.PLAYER);
        world.withCurrency(core, xp);
        parties = GroupWorld.open("FakeParty", tempDir.resolve("party"));
        party = parties.kind("party", GroupKind.Cardinality.MANY);
        alice = parties.player("Alice").at(WORLD, 5, 5, 5);
        bob = parties.player("Bob").at(WORLD, 8, 5, 5);
        carol = parties.player("Carol").at(WORLD, 6, 5, 5);
        dave = parties.player("Dave").at(WORLD, 200, 5, 5);
    }

    @AfterEach
    void close() {
        parties.close();
        FlagRegistry.get().unregisterAll(core);
        Plugins.forget(Plugins.CORE);
        world.close();
    }

    private Group alphaWith(TestFPlayerSender... members) {
        Group alpha = parties.group("alpha", party);
        for (TestFPlayerSender member : members) {
            alpha.add(member.getUniqueId());
        }
        return alpha;
    }

    private Decision hit(Actor attacker, TestFPlayerSender victim) {
        DamageContext hit = new DamageContext(EventSource.DAMAGE, victim.getLocation().getWorldLocPos(), attacker, null, Actors.player(victim));
        return ProtectionFlagGate.decide(parties.regions().getLookup(), hit);
    }

    private Region arena() {
        return parties.regions().cuboid("arena", WORLD, 0, 0, 0, 20, 20, 20);
    }

    // ---- deny-friendly-fire ----

    @Test
    void friendlyFireOnAPlaceDeniesAHitBetweenMembersOfOneGroup() {
        alphaWith(alice, bob);
        arena().setFlag(CoreFlags.DENY_FRIENDLY_FIRE, Tristate.TRUE);

        assertEquals(Decision.DENY, hit(Actors.player(bob), alice));
        assertEquals(Decision.PASS, hit(Actors.player(bob), carol), "Carol shares no group with Bob");
    }

    @Test
    void friendlyFireOnAGroupFollowsItsMembersOutOfEveryRegion() {
        alphaWith(alice, bob).setFlag(CoreFlags.DENY_FRIENDLY_FIRE, Tristate.TRUE);

        assertEquals(Decision.DENY, hit(Actors.player(bob), alice));
    }

    @Test
    void friendlyFireWithPvpAllowedLeavesPvpOnlyBetweenDifferentGroups() {
        alphaWith(alice, bob);
        parties.group("beta", party).add(carol.getUniqueId());
        Region arena = arena();
        arena.setFlag(CoreFlags.DENY_PVP, Tristate.FALSE);
        arena.setFlag(CoreFlags.DENY_FRIENDLY_FIRE, Tristate.TRUE);

        assertEquals(Decision.DENY, hit(Actors.player(bob), alice), "one group");
        assertEquals(Decision.PASS, hit(Actors.player(bob), carol), "two groups fight");
    }

    @Test
    void denyPvpTrueAlreadyCoversTheHitsFriendlyFireLetsThrough() {
        alphaWith(alice, bob);
        Region arena = arena();
        arena.setFlag(CoreFlags.DENY_PVP, Tristate.TRUE);
        arena.setFlag(CoreFlags.DENY_FRIENDLY_FIRE, Tristate.FALSE);

        assertEquals(Decision.DENY, hit(Actors.player(bob), alice));
        assertEquals(Decision.DENY, hit(Actors.player(bob), carol));
    }

    // ---- the victim's flags speak for the victim ----

    @Test
    void aVictimInAnInvincibleGroupHitByAMobIsProtected() {
        alphaWith(alice).setFlag(CoreFlags.INVINCIBLE, Tristate.TRUE);

        assertEquals(Decision.DENY, hit(Actors.entity("minecraft:zombie", ECEntityType.MONSTER), alice));
    }

    @Test
    void theAttackersInvincibleGroupDoesNotProtectAVictimOutsideIt() {
        alphaWith(bob).setFlag(CoreFlags.INVINCIBLE, Tristate.TRUE);

        assertEquals(Decision.PASS, hit(Actors.player(bob), carol));
        arena();
        assertEquals(Decision.PASS, hit(Actors.player(bob), carol), "inside a region too");
    }

    @Test
    void theVictimsGroupMultipliesTheDamageItTakesAndTheAttackersDoesNot() {
        alphaWith(alice).setFlag(CoreFlags.DAMAGE_MULTIPLIER, 3.0);
        parties.group("beta", party).add(bob.getUniqueId());
        parties.getManager().get("beta").setFlag(CoreFlags.DAMAGE_MULTIPLIER, 5.0);
        DamageContext bobHitsAlice = new DamageContext(EventSource.DAMAGE, alice.getLocation().getWorldLocPos(), Actors.player(bob), null,
                Actors.player(alice));

        assertEquals(Decision.PASS, ProtectionFlagGate.decide(parties.regions().getLookup(), bobHitsAlice));
        assertEquals(3.0, bobHitsAlice.getDamageMultiplier());
    }

    @Test
    void theHandlerOfAVictimFlagSeesTheVictimNotTheAttacker() {
        Region arena = arena();
        arena.setFlag(CoreFlags.INVINCIBLE, Tristate.TRUE);
        List<FPlayer> seen = new ArrayList<>();
        arena.setFlagHandler(parties.getOwner(), CoreFlags.INVINCIBLE, outcome -> seen.add(outcome.getPlayer()));

        assertEquals(Decision.DENY, hit(Actors.player(bob), alice));
        assertEquals(Decision.DENY, hit(Actors.entity("minecraft:zombie", ECEntityType.MONSTER), alice));

        assertEquals(2, seen.size(), seen.toString());
        assertEquals(alice.getUniqueId(), seen.get(0).getUniqueId(), "the victim, not Bob who hit her");
        assertEquals(alice.getUniqueId(), seen.get(1).getUniqueId(), "the victim, though no player is behind a zombie");
    }

    @Test
    void aVictimInAGroupThatDeniesMobDamageIsSparedByAZombie() {
        alphaWith(alice).setFlag(CoreFlags.DENY_DAMAGE_FROM_MOBS, Tristate.TRUE);

        assertEquals(Decision.DENY, hit(Actors.entity("minecraft:zombie", ECEntityType.MONSTER), alice));
        assertEquals(Decision.PASS, hit(Actors.entity("minecraft:zombie", ECEntityType.MONSTER), carol));
    }

    // ---- max-members ----

    @Test
    void aFullGroupRefusesTheAddOfTheOwnerAndTheJoinOfThePlayer() {
        Group alpha = alphaWith(alice, bob);
        alpha.setFlag(CoreFlags.MAX_MEMBERS, 2);

        assertFalse(alpha.add(carol.getUniqueId()));
        GateResult join = alpha.join(carol);

        assertFalse(join.isPassed());
        assertEquals("The party 'alpha' is full (2/2, max-members): raise the flag or remove a member.", join.explain().toPlainText());
        assertFalse(alpha.isMember(carol.getUniqueId()));
        assertTrue(parties.recorded(GroupMemberPreJoinEvent.class).stream()
                .noneMatch(event -> event.getPlayer().equals(carol.getUniqueId())), "a full group is refused before the pre event");
    }

    @Test
    void anInviteIntoAFullGroupIsRefusedOnAcceptAndRoomComesBackWithALeave() {
        Group alpha = alphaWith(alice);
        alpha.setFlag(CoreFlags.MAX_MEMBERS, 1);
        alpha.invite(bob.getUniqueId(), Duration.ofMinutes(5), null);

        assertFalse(alpha.accept(bob).isPassed());
        alpha.remove(alice.getUniqueId(), LeaveCause.LEFT);
        assertTrue(alpha.join(bob).isPassed());
    }

    @Test
    void theKindsDefaultCapsAGroupThatSetsNothing() {
        GroupKind duo = GroupKind.of(parties.getOwner(), "duo").flagDefault(CoreFlags.MAX_MEMBERS, 2).build();
        parties.getManager().registerKind(duo);
        Group pair = parties.group("pair", duo);
        pair.add(alice.getUniqueId());
        pair.add(bob.getUniqueId());

        assertFalse(pair.add(carol.getUniqueId()));
    }

    @Test
    void theKindsDefaultReachesTheResolutionOfAGroupThatSetsNothing() {
        GroupKind team = GroupKind.of(parties.getOwner(), "team").flagDefault(CoreFlags.DENY_FRIENDLY_FIRE, Tristate.TRUE).build();
        parties.getManager().registerKind(team);
        Group red = parties.group("red", team);
        red.add(alice.getUniqueId());
        red.add(bob.getUniqueId());

        assertEquals(Decision.DENY, hit(Actors.player(bob), alice));
        red.setFlag(CoreFlags.DENY_FRIENDLY_FIRE, Tristate.FALSE);
        assertEquals(Decision.PASS, hit(Actors.player(bob), alice), "the group's own value wins over the kind's default");
    }

    // ---- share-xp / share-money ----

    @Test
    void shareXpCopiesTheFractionToTheOtherMembersInRangeOnly() {
        alphaWith(alice, bob, dave).setFlag(CoreFlags.SHARE_XP, 0.5);

        ECEventBus.global().post(new XpGainActionEvent.Post(new XpContext(alice.getLocation().getWorldLocPos(), Actors.player(alice), null, 10)));

        assertEquals(0, new BigDecimal("5").compareTo(xp.getBalance(CurrencyOwner.player(bob.getUniqueId()))), "a copy of half");
        assertEquals(0, BigDecimal.ZERO.compareTo(xp.getBalance(CurrencyOwner.player(alice.getUniqueId()))), "the gainer keeps what they got");
        assertEquals(0, BigDecimal.ZERO.compareTo(xp.getBalance(CurrencyOwner.player(dave.getUniqueId()))), "Dave is 200 blocks away");
    }

    @Test
    void shareMoneyGivesEachMemberInRangeOneCopyAndNeverSharesItsOwnGiveAgain() {
        alphaWith(alice, bob, carol, dave).setFlag(CoreFlags.SHARE_MONEY, 0.5);
        List<BalanceChangeEvent> shared = new ArrayList<>();
        Caller shareCaller = GroupShareListener.callerOf(CoreFlags.SHARE_MONEY);
        ECEventSubscription<BalanceChangeEvent> recorder = ECEventBus.global().subscribe(BalanceChangeEvent.class, event -> {
            if (event.getCaller().equals(shareCaller)) {
                shared.add(event);
            }
        });
        try {
            Currencies.money().give(CurrencyOwner.player(alice.getUniqueId()), new BigDecimal("100"), Caller.of(core, "test"));
        } finally {
            recorder.unsubscribe();
        }

        assertEquals("evernifecore:group/share-money", shareCaller.toString());
        assertEquals(2, shared.size(), "one give per member in range (Bob, Carol), once - no echo of the shares");
        assertEquals(0, new BigDecimal("100").compareTo(Currencies.money().getBalance(CurrencyOwner.player(alice.getUniqueId()))));
        assertEquals(0, new BigDecimal("50").compareTo(Currencies.money().getBalance(CurrencyOwner.player(bob.getUniqueId()))));
        assertEquals(0, BigDecimal.ZERO.compareTo(Currencies.money().getBalance(CurrencyOwner.player(dave.getUniqueId()))));
    }

    @Test
    void aGroupThatStopsSharingTakesTheListenerOffTheBus() {
        Group alpha = alphaWith(alice, bob);
        alpha.setFlag(CoreFlags.SHARE_MONEY, 0.5);
        alpha.removeFlag(CoreFlags.SHARE_MONEY);

        Currencies.money().give(CurrencyOwner.player(alice.getUniqueId()), new BigDecimal("100"), Caller.of(core, "test"));

        assertEquals(0, BigDecimal.ZERO.compareTo(Currencies.money().getBalance(CurrencyOwner.player(bob.getUniqueId()))));
    }

    // ---- glow-members ----

    @Test
    void glowMembersGlowsTheOnlineMembersThroughTheProviderAndStopsWithTheirLeaveAndTheValue() {
        TestGlow glow = world.platform().getTestGlow();
        Group alpha = alphaWith(alice, bob);

        alpha.setFlag(CoreFlags.GLOW_MEMBERS, Tristate.TRUE);
        assertTrue(glow.isGlowing(alice.getUniqueId()));
        assertTrue(glow.isGlowing(bob.getUniqueId()));

        alpha.remove(bob.getUniqueId(), LeaveCause.LEFT);
        assertFalse(glow.isGlowing(bob.getUniqueId()));
        alpha.add(carol.getUniqueId());
        assertTrue(glow.isGlowing(carol.getUniqueId()));

        alpha.removeFlag(CoreFlags.GLOW_MEMBERS);
        assertFalse(glow.isGlowing(alice.getUniqueId()));
        assertFalse(glow.isGlowing(carol.getUniqueId()));
        assertEquals(6, glow.getCalls().size(), glow.getCalls().toString());
    }

    @Test
    void aMemberWhoLogsInWhileTheGroupGlowsStartsGlowing() {
        TestGlow glow = world.platform().getTestGlow();
        Group alpha = alphaWith(alice, bob);
        parties.quit(bob);
        alpha.setFlag(CoreFlags.GLOW_MEMBERS, Tristate.TRUE);
        assertFalse(glow.isGlowing(bob.getUniqueId()));

        parties.login(bob);

        assertTrue(glow.isGlowing(bob.getUniqueId()));
        assertTrue(glow.isGlowing(alice.getUniqueId()));
    }

    @Test
    void aMemberWhoQuitsStopsGlowing() {
        TestGlow glow = world.platform().getTestGlow();
        alphaWith(alice, bob).setFlag(CoreFlags.GLOW_MEMBERS, Tristate.TRUE);
        assertTrue(glow.isGlowing(bob.getUniqueId()));

        parties.quit(bob);

        assertFalse(glow.isGlowing(bob.getUniqueId()), "unglowed while still online, so no Glowing tag rides the logout");
        assertTrue(glow.isGlowing(alice.getUniqueId()));
        parties.login(bob);
        assertTrue(glow.isGlowing(bob.getUniqueId()), "glows again on the way back in");
    }

    @Test
    void aDissolvedGroupStopsGlowing() {
        TestGlow glow = world.platform().getTestGlow();
        Group alpha = alphaWith(alice);
        alpha.setFlag(CoreFlags.GLOW_MEMBERS, Tristate.TRUE);

        alpha.dissolve(DissolveCause.OWNER);

        assertFalse(glow.isGlowing(alice.getUniqueId()));
    }

    @Test
    void theGroupFlagsMeanNothingOnARegionAndSaySo() {
        Region arena = arena();

        assertFalse(CoreFlags.MAX_MEMBERS.accepts(arena));
        assertFalse(CoreFlags.SHARE_XP.accepts(arena.getGroup("vip")), "a region's list of players shares nothing");
        assertTrue(CoreFlags.MAX_MEMBERS.accepts(arena.getGroup("vip")), "a region's group may cap its list");
        assertTrue(CoreFlags.GLOW_MEMBERS.accepts(parties.group("alpha", party)));
        assertTrue(CoreFlags.all().contains(CoreFlags.DENY_FRIENDLY_FIRE));
    }

    @Test
    void addRenewsAMemberOfAFullGroup() {
        Group alpha = alphaWith(alice);
        alpha.setFlag(CoreFlags.MAX_MEMBERS, 1);

        assertTrue(alpha.add(alice.getUniqueId(), Duration.ofMinutes(1)), "a member already in renews, the cap does not count them twice");
    }
}
