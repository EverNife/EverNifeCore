package br.com.finalcraft.evernifecore.region.session;

import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.flag.AmountEvery;
import br.com.finalcraft.evernifecore.region.flag.EffectLevel;
import br.com.finalcraft.evernifecore.region.flag.FlagParseException;
import br.com.finalcraft.evernifecore.region.flag.FlagTypes;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.flag.core.DamageOverTimeSettings;
import br.com.finalcraft.evernifecore.region.flag.core.FlightFlag;
import br.com.finalcraft.evernifecore.region.flag.core.TimeLock;
import br.com.finalcraft.evernifecore.region.playerstate.ActiveEffect;
import br.com.finalcraft.evernifecore.region.playerstate.ECGameMode;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.playerstate.PlayerTime;
import br.com.finalcraft.evernifecore.testing.TestFPlayerSender;
import br.com.finalcraft.evernifecore.testing.TestPlatform;
import br.com.finalcraft.evernifecore.testing.TestPlayerState;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The session flags that change the player: applied on entering, and on leaving the player gets back what they had before -
 * even when it equals what the region gave. Region {@code zone} spans X 0..10; X 30 is outside.
 */
class PlayerStateFlagsTest extends TrackerFixture {

    private TestPlayerState.State enter(TestPlatform platform, Region zone) {
        TestPlayerState.State state = platform.getTestPlayerState().of(player);
        tracker.onJoin(player, at(30.5));
        tracker.onMove(player, at(30.5), at(5.5), MoveCause.MOVE, null);
        return state;
    }

    private void leave() {
        tracker.onMove(player, at(5.5), at(30.5), MoveCause.MOVE, null);
    }

    @Test
    void allowFlyFalseLandsCreativeAndGivesTheFlightBack(TestPlatform platform) {
        Region zone = region("zone", 0, 10);
        zone.setFlag(registered(CoreFlags.ALLOW_FLY), Tristate.FALSE);
        TestPlayerState.State state = platform.getTestPlayerState().of(player);
        state.gameMode = ECGameMode.CREATIVE;
        state.allowFlight = true;
        state.flying = true;

        enter(platform, zone);
        assertFalse(state.allowFlight, "creative loses flight too");
        assertFalse(state.flying);
        leave();
        assertTrue(state.allowFlight, "the flight they had comes back");
        assertTrue(state.flying);
    }

    @Test
    void allowFlyFalseKeepsASpectatorFlying(TestPlatform platform) {
        Region zone = region("zone", 0, 10);
        zone.setFlag(registered(CoreFlags.ALLOW_FLY), Tristate.FALSE);
        TestPlayerState.State state = platform.getTestPlayerState().of(player);
        state.gameMode = ECGameMode.SPECTATOR;
        state.allowFlight = true;
        state.flying = true;

        enter(platform, zone);
        assertTrue(state.allowFlight);
        assertTrue(state.flying, "a spectator keeps flying");
    }

    @Test
    void allowFlyTrueLetsFlyAndUndefinedHoldsNothing(TestPlatform platform) {
        Region zone = region("zone", 0, 10);
        FlightFlag allowFly = registered(CoreFlags.ALLOW_FLY);
        zone.setFlag(allowFly, Tristate.TRUE);
        TestPlayerState.State state = enter(platform, zone);
        assertTrue(state.allowFlight);
        leave();
        assertFalse(state.allowFlight, "survival had no flight before");

        zone.setFlag(allowFly, Tristate.UNDEFINED);
        state.allowFlight = true;
        tracker.onMove(player, at(30.5), at(5.5), MoveCause.MOVE, null);
        state.allowFlight = false; //changed by someone else inside
        leave();
        assertFalse(state.allowFlight, "undefined held nothing, so nothing is written back");
    }

    @Test
    void forceFlyPutsTheFlyingAndWinsOverAllowFlyFalse(TestPlatform platform) {
        Region zone = region("zone", 0, 10);
        zone.setFlag(registered(CoreFlags.ALLOW_FLY), Tristate.FALSE);
        zone.setFlag(registered(CoreFlags.FORCE_FLY), Tristate.TRUE);

        TestPlayerState.State state = enter(platform, zone);
        assertTrue(state.allowFlight, "force-fly implies being allowed to fly");
        assertTrue(state.flying, "and puts the player flying, over allow-fly = FALSE");
        leave();
        assertFalse(state.allowFlight);
        assertFalse(state.flying);
    }

    @Test
    void forceFlyLeavingAloneLetsAllowFlyFalseStand(TestPlatform platform) {
        Region outer = region("outer", 0, 20);
        Region inner = region("inner", 0, 10);
        outer.setFlag(registered(CoreFlags.ALLOW_FLY), Tristate.FALSE);
        inner.setFlag(registered(CoreFlags.FORCE_FLY), Tristate.TRUE);
        TestPlayerState.State state = platform.getTestPlayerState().of(player);
        state.allowFlight = true;

        tracker.onJoin(player, at(5.5));
        assertTrue(state.flying);
        tracker.onMove(player, at(5.5), at(15.5), MoveCause.MOVE, null);
        assertFalse(state.allowFlight, "out of force-fly, allow-fly = FALSE of the outer region applies again");
        tracker.onMove(player, at(15.5), at(30.5), MoveCause.MOVE, null);
        assertTrue(state.allowFlight, "out of both, the flight from before");
    }

    @Test
    void gameModeAppliesAndGivesBackTheModeFromBeforeEvenWhenItWasTheSame(TestPlatform platform) {
        Region zone = region("zone", 0, 10);
        zone.setFlag(registered(CoreFlags.GAME_MODE), ECGameMode.ADVENTURE);
        TestPlayerState.State state = enter(platform, zone);
        assertEquals(ECGameMode.ADVENTURE, state.gameMode);
        leave();
        assertEquals(ECGameMode.SURVIVAL, state.gameMode);

        state.gameMode = ECGameMode.ADVENTURE;
        tracker.onMove(player, at(30.5), at(5.5), MoveCause.MOVE, null);
        state.gameMode = ECGameMode.CREATIVE; //changed inside by someone else
        leave();
        assertEquals(ECGameMode.ADVENTURE, state.gameMode, "the mode from before, though it equals the region's");
    }

    @Test
    void aGameModeTheServerLacksDoesNothing(TestPlatform platform) {
        platform.getTestPlayerState().withoutGameModes(ECGameMode.SPECTATOR);
        Region zone = region("zone", 0, 10);
        zone.setFlag(registered(CoreFlags.GAME_MODE), ECGameMode.SPECTATOR);
        assertEquals(ECGameMode.SURVIVAL, enter(platform, zone).gameMode);
    }

    @Test
    void weatherLockShowsClearOrRainAndGivesTheWeatherBack(TestPlatform platform) {
        Region zone = region("zone", 0, 10);
        zone.setFlag(registered(CoreFlags.WEATHER_LOCK), IPlayerStateAccess.CLEAR);
        TestPlayerState.State state = enter(platform, zone);
        assertEquals(IPlayerStateAccess.CLEAR, state.weather);
        leave();
        assertNull(state.weather, "the world's weather again");

        state.weather = IPlayerStateAccess.CLEAR;
        zone.setFlag(CoreFlags.WEATHER_LOCK, IPlayerStateAccess.RAIN);
        tracker.onMove(player, at(30.5), at(5.5), MoveCause.MOVE, null);
        assertEquals(IPlayerStateAccess.RAIN, state.weather);
        leave();
        assertEquals(IPlayerStateAccess.CLEAR, state.weather);
    }

    @Test
    void aWeatherIdOfThePlatformIsAValueAndAnUnknownOneStaysRaw() throws FlagParseException {
        registered(CoreFlags.WEATHER_LOCK);
        assertEquals("thunder", FlagTypes.WEATHER.parse("THUNDER"));
        assertThrows(FlagParseException.class, () -> FlagTypes.WEATHER.parse("sandstorm"));

        Region zone = region("zone", 0, 10);
        ObjectNode raw = JsonNodeFactory.instance.objectNode();
        raw.put("value", "sandstorm");
        zone.getFlagStore().putRaw("evernifecore:weather-lock", raw);
        assertNull(zone.getFlag(CoreFlags.WEATHER_LOCK));
        assertEquals(raw, zone.getUnknownFlags().get("evernifecore:weather-lock"), "kept raw, like any value that does not read");
    }

    @Test
    void timeLockLocksTheClockAndGivesBackTheOneFromBefore(TestPlatform platform) {
        Region zone = region("zone", 0, 10);
        zone.setFlag(registered(CoreFlags.TIME_LOCK), new TimeLock(TimeLock.TimeOfDay.NIGHT, false));
        TestPlayerState.State state = enter(platform, zone);
        assertEquals(new PlayerTime(13000, false), state.time);
        assertEquals(Duration.ofSeconds(2), state.lastTransition, "the default smooth transition");
        leave();
        assertNull(state.time, "the world's clock again");

        state.time = new PlayerTime(13000, false);
        tracker.onMove(player, at(30.5), at(5.5), MoveCause.MOVE, null);
        state.time = new PlayerTime(1, true);
        leave();
        assertEquals(new PlayerTime(13000, false), state.time, "the clock from before, though it equals the region's");
    }

    @Test
    void potionEffectsTakeOnlyWhatTheyGave(TestPlatform platform) {
        Region zone = region("zone", 0, 10);
        zone.setFlag(registered(CoreFlags.POTION_EFFECTS), Arrays.asList(EffectLevel.of("speed", 2), EffectLevel.of("night_vision", 1)));
        TestPlayerState.State state = platform.getTestPlayerState().of(player);
        ActiveEffect ownSpeed = new ActiveEffect(EffectLevel.of("speed", 1), Duration.ofSeconds(100));
        state.effects.put("speed", ownSpeed);
        state.effects.put("haste", new ActiveEffect(EffectLevel.of("haste", 1), Duration.ofSeconds(50)));

        enter(platform, zone);
        assertEquals(EffectLevel.of("speed", 2), state.effects.get("speed").getEffect());
        assertEquals(EffectLevel.of("night_vision", 1), state.effects.get("night_vision").getEffect());
        leave();
        assertEquals(ownSpeed, state.effects.get("speed"), "the speed the player had comes back as it was");
        assertNull(state.effects.get("night_vision"), "what the region gave is taken");
        assertEquals(EffectLevel.of("haste", 1), state.effects.get("haste").getEffect(), "an effect from elsewhere stays");
    }

    @Test
    void timedFlagsActOncePerIntervalStopAtFullAndSkipWhoIsOutside(TestPlatform platform) {
        Region spa = region("spa", 0, 10);
        Region swamp = region("swamp", 40, 50);
        spa.setFlag(registered(CoreFlags.HEAL), AmountEvery.of(2, Duration.ofSeconds(5)));
        spa.setFlag(registered(CoreFlags.FEED), AmountEvery.of(3, Duration.ofSeconds(5)));
        swamp.setFlag(registered(CoreFlags.DAMAGE_OVER_TIME), AmountEvery.of(1, Duration.ofSeconds(2)));
        swamp.setFlag(registered(CoreFlags.HUNGER_OVER_TIME), AmountEvery.of(2, Duration.ofSeconds(2)));
        TestFPlayerSender sick = new TestFPlayerSender("sick");
        TestFPlayerSender outside = new TestFPlayerSender("outside");
        TestPlayerState states = platform.getTestPlayerState();
        states.of(player).health = 15;
        states.of(player).food = 15;
        states.of(outside).health = 10;
        tracker.onJoin(player, at(5.5));
        tracker.onJoin(sick, at(45.5));
        tracker.onJoin(outside, at(25.5));
        RegionTicker ticker = new RegionTicker(lookup);
        ticker.begin();
        try {
            for (int second = 1; second <= 4; second++) {
                clock.advance(Duration.ofSeconds(1));
                agenda.runQueued();
            }
            assertEquals(15.0, states.of(player).health, "nothing before the first interval");
            assertEquals(18.0, states.of(sick).health, "1 every 2s, twice");
            assertEquals(16, states.of(sick).food, "2 every 2s, twice");
            clock.advance(Duration.ofSeconds(1));
            agenda.runQueued();
            assertEquals(17.0, states.of(player).health);
            assertEquals(18, states.of(player).food);
            for (int second = 6; second <= 15; second++) {
                clock.advance(Duration.ofSeconds(1));
                agenda.runQueued();
            }
            assertEquals(20.0, states.of(player).health, "healing stops at full");
            assertEquals(20, states.of(player).food, "feeding stops at full");
            assertEquals(10.0, states.of(outside).health, "who stands outside is never touched");
            assertEquals(20, states.of(outside).food);
        } finally {
            ticker.end();
        }
    }

    @Test
    void damageOverTimeCountsArmorUnlessTheSettingsIgnoreIt(TestPlatform platform) {
        Region swamp = region("swamp", 0, 10);
        swamp.setFlag(registered(CoreFlags.DAMAGE_OVER_TIME), AmountEvery.of(4, Duration.ofSeconds(1)));
        TestPlayerState.State state = platform.getTestPlayerState().of(player);
        state.armor = 0.5;
        tracker.onJoin(player, at(5.5));
        RegionTicker ticker = new RegionTicker(lookup);
        ticker.begin();
        try {
            clock.advance(Duration.ofSeconds(1));
            agenda.runQueued();
            assertEquals(18.0, state.health, "armor took half of the 4");
            swamp.<DamageOverTimeSettings>editFlagSettings(CoreFlags.DAMAGE_OVER_TIME, settings -> settings.setIgnoreArmor(true));
            clock.advance(Duration.ofSeconds(1));
            agenda.runQueued();
            assertEquals(14.0, state.health, "ignore-armor deals the whole 4");
        } finally {
            ticker.end();
        }
    }

    @Test
    void theBypassNodeOrTheSettingsBypassKeepTheFlight(TestPlatform platform) {
        Region zone = region("zone", 0, 10);
        zone.setFlag(registered(CoreFlags.ALLOW_FLY), Tristate.FALSE);
        TestPlayerState.State state = platform.getTestPlayerState().of(player);
        state.allowFlight = true;
        player.grant("evernifecore.region.bypass.evernifecore.allow-fly");
        enter(platform, zone);
        assertTrue(state.allowFlight, "the standard bypass node lifts allow-fly");

        TestFPlayerSender vip = new TestFPlayerSender("vip").grant("server.vip.fly");
        zone.editFlagSettings(CoreFlags.ALLOW_FLY, settings -> settings.setBypassPermission("server.vip.fly"));
        platform.getTestPlayerState().of(vip).allowFlight = true;
        tracker.onJoin(vip, at(5.5));
        assertTrue(platform.getTestPlayerState().of(vip).allowFlight, "so does the bypass-permission of the settings");

        TestFPlayerSender plain = new TestFPlayerSender("plain");
        platform.getTestPlayerState().of(plain).allowFlight = true;
        tracker.onJoin(plain, at(5.5));
        assertFalse(platform.getTestPlayerState().of(plain).allowFlight, "who has neither is grounded");
    }
}
