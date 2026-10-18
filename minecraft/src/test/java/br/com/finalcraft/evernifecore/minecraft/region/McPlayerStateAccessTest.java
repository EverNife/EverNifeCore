package br.com.finalcraft.evernifecore.minecraft.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.flag.EffectLevel;
import br.com.finalcraft.evernifecore.region.playerstate.ECGameMode;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess.Capability;
import br.com.finalcraft.evernifecore.region.playerstate.PlayerTime;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.WeatherType;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Each player-state capability through a Bukkit player. */
class McPlayerStateAccessTest {

    @TempDir
    Path tempDir;

    private BukkitEventWorld server;
    private Player bukkit;
    private FPlayer player;
    private final List<String> dispatched = new ArrayList<>();
    private McPlayerStateAccess access;

    @BeforeEach
    void setUp() {
        server = BukkitEventWorld.install(tempDir);
        bukkit = server.player("Steve", new Location(server.world("world", 0, 256), 0, 64, 0));
        player = FCBukkitUtil.adapt(bukkit);
        access = new McPlayerStateAccess((sender, command) -> dispatched.add((sender == null ? "console" : sender.getName()) + ": " + command));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    void bukkitHasEveryCapability() {
        for (Capability capability : Capability.values()) {
            assertTrue(access.supports(capability), capability.name());
        }
        assertTrue(access.getUnsupported().isEmpty());
    }

    @Test
    void theClockIsTheWorldsUntilSetAndAgainAfterReset() {
        assertNull(access.getTime(player));
        access.setTime(player, new PlayerTime(13000, false), Duration.ZERO);
        assertEquals(new PlayerTime(13000, false), access.getTime(player));
        assertEquals(13000L, bukkit.getPlayerTimeOffset());
        access.setTime(player, null, Duration.ZERO);
        assertNull(access.getTime(player));
    }

    @Test
    void clearAndRainMapToBukkitWeather() {
        assertNull(access.getWeather(player));
        assertEquals(IPlayerStateAccess.RAIN, access.toWeatherId("DOWNFALL"));
        assertNull(access.toWeatherId("thunder"));
        access.setWeather(player, IPlayerStateAccess.RAIN);
        assertEquals(WeatherType.DOWNFALL, bukkit.getPlayerWeather());
        assertEquals(IPlayerStateAccess.RAIN, access.getWeather(player));
        access.setWeather(player, IPlayerStateAccess.CLEAR);
        assertEquals(IPlayerStateAccess.CLEAR, access.getWeather(player));
        access.setWeather(player, null);
        assertNull(access.getWeather(player));
    }

    @Test
    void takingFlightAwayLandsTheFlyingPlayer() {
        access.setAllowFlight(player, true);
        access.setFlying(player, true);
        assertTrue(access.isFlying(player));
        access.setAllowFlight(player, false);
        assertFalse(access.getAllowFlight(player));
        assertFalse(access.isFlying(player));
    }

    @Test
    void gameModesAreTheServersOwn() {
        for (ECGameMode mode : ECGameMode.values()) {
            assertTrue(access.supportsGameMode(mode), mode + " exists on 1.21");
        }
        access.setGameMode(player, ECGameMode.CREATIVE);
        assertEquals(GameMode.CREATIVE, bukkit.getGameMode());
        assertEquals(ECGameMode.CREATIVE, access.getGameMode(player));
    }

    @Test
    void anEffectIsGivenReadByItsOwnIdAndRemoved() {
        assertTrue(access.isEffect("speed"));
        access.addEffect(player, EffectLevel.of("minecraft:speed", 2), Duration.ofSeconds(10));
        assertEquals(1, bukkit.getActivePotionEffects().iterator().next().getAmplifier());
        assertEquals(PotionEffectType.SPEED, bukkit.getActivePotionEffects().iterator().next().getType());
        assertEquals(EffectLevel.of("minecraft:speed", 2), access.getEffect(player, "minecraft:speed").getEffect());
        assertEquals(Duration.ofSeconds(10), access.getEffect(player, "minecraft:speed").getRemaining());
        assertNull(access.getEffect(player, "night_vision"));
        access.removeEffect(player, "speed");
        assertTrue(bukkit.getActivePotionEffects().isEmpty());
    }

    @Test
    void healthIsClampedAndDamageLowersIt() {
        access.setHealth(player, 50);
        assertEquals(20.0, access.getHealth(player));
        access.damage(player, 4);
        assertEquals(16.0, access.getHealth(player));
        assertEquals(20.0, access.getMaxHealth(player));
    }

    @Test
    void foodIsClampedToTwenty() {
        access.setFood(player, 7);
        assertEquals(7, access.getFood(player));
        access.setFood(player, 99);
        assertEquals(20, access.getFood(player));
        assertEquals(20, access.getMaxFood(player));
    }

    @Test
    void aCommandRunsAsTheConsoleOrAsThePlayer() {
        access.dispatchCommand(null, "say hi");
        access.dispatchCommand(player, "spawn");
        assertEquals(Arrays.asList("console: say hi", "Steve: spawn"), dispatched);
    }
}
