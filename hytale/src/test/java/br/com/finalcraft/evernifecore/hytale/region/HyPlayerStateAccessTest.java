package br.com.finalcraft.evernifecore.hytale.region;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.region.flag.EffectLevel;
import br.com.finalcraft.evernifecore.region.playerstate.ECGameMode;
import br.com.finalcraft.evernifecore.region.playerstate.IPlayerStateAccess.Capability;
import br.com.finalcraft.evernifecore.region.playerstate.PlayerTime;
import br.com.finalcraft.evernifecore.testing.Senders;
import com.hypixel.hytale.protocol.FlyMode;
import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.protocol.ToClientPacket;
import com.hypixel.hytale.protocol.packets.world.ClearEditorTimeOverride;
import com.hypixel.hytale.protocol.packets.world.UpdateEditorTimeOverride;
import com.hypixel.hytale.protocol.packets.world.UpdateTime;
import jakarta.annotation.Nullable;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** How each capability Hytale declares maps onto the server, with the server's own calls recorded. */
class HyPlayerStateAccessTest {

    /** The server, in memory: what one player has, and every packet sent. */
    static final class RecordingOps implements HyPlayerStateAccess.Ops {
        final List<ToClientPacket> packets = new ArrayList<>();
        final List<String> commands = new ArrayList<>();
        final Map<String, Float> effects = new LinkedHashMap<>();
        FlyMode fly = FlyMode.Disabled;
        boolean flying;
        GameMode mode = GameMode.Adventure;
        float health = 100;

        @Override
        public Instant worldTime(FPlayer player) {
            return Instant.parse("2026-10-18T12:00:00Z");
        }

        @Override
        public void send(UUID player, ToClientPacket packet) {
            packets.add(packet);
        }

        @Override
        public FlyMode flyMode(FPlayer player) {
            return fly;
        }

        @Override
        public void setFlyMode(FPlayer player, FlyMode mode) {
            fly = mode;
        }

        @Override
        public boolean isFlying(FPlayer player) {
            return flying;
        }

        @Override
        public void setFlying(FPlayer player, boolean flying) {
            this.flying = flying;
        }

        @Override
        public GameMode gameMode(FPlayer player) {
            return mode;
        }

        @Override
        public void setGameMode(FPlayer player, GameMode mode) {
            this.mode = mode;
        }

        @Override
        public @Nullable String effectId(String lowercased) {
            return lowercased.equals("potion_speed") ? "Potion_Speed" : null;
        }

        @Override
        public Map<String, Float> effects(FPlayer player) {
            return effects;
        }

        @Override
        public void addEffect(FPlayer player, String effectId, float seconds) {
            effects.put(effectId, seconds);
        }

        @Override
        public void removeEffect(FPlayer player, String effectId) {
            effects.remove(effectId);
        }

        @Override
        public float health(FPlayer player) {
            return health;
        }

        @Override
        public float maxHealth(FPlayer player) {
            return 100;
        }

        @Override
        public void setHealth(FPlayer player, float health) {
            this.health = health;
        }

        final List<Float> damages = new ArrayList<>();

        @Override
        public void damage(FPlayer player, float amount) {
            damages.add(amount);
        }

        @Override
        public boolean command(@Nullable FPlayer asPlayer, String command) {
            commands.add((asPlayer == null ? "console" : asPlayer.getName()) + ": " + command);
            return true;
        }
    }

    private final RecordingOps ops = new RecordingOps();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final HyPlayerStateAccess access = new HyPlayerStateAccess(ops, () -> scheduler);
    private final FPlayer player = Senders.player("Kweebec");

    @Test
    void weatherAndFoodAreTheCapabilitiesHytaleLacks() {
        assertEquals(Arrays.asList(Capability.WEATHER, Capability.FOOD), access.getUnsupported());
        assertThrows(UnsupportedOperationException.class, () -> access.setFood(player, 5));
        assertNull(access.toWeatherId("clear"));
    }

    @Test
    void aFixedClockIsAPausedOverrideAndResetClearsIt() {
        access.setTime(player, new PlayerTime(13000, false), Duration.ZERO);
        assertEquals(new PlayerTime(13000, false), access.getTime(player));
        UpdateEditorTimeOverride override = assertInstanceOf(UpdateEditorTimeOverride.class, ops.packets.get(0));
        assertTrue(override.paused);

        access.setTime(player, null, Duration.ZERO);
        assertNull(access.getTime(player));
        assertInstanceOf(ClearEditorTimeOverride.class, ops.packets.get(1));
        assertInstanceOf(UpdateTime.class, ops.packets.get(2));
    }

    @Test
    void flightIsTheFlyModeAndTakingItLandsThePlayer() {
        access.setAllowFlight(player, true);
        assertEquals(FlyMode.Allowed, ops.fly);
        access.setFlying(player, true);
        assertTrue(access.isFlying(player));
        access.setAllowFlight(player, false);
        assertEquals(FlyMode.Disabled, ops.fly);
        assertFalse(ops.flying);
    }

    @Test
    void onlyAdventureAndCreativeExist() {
        assertTrue(access.supportsGameMode(ECGameMode.CREATIVE));
        assertFalse(access.supportsGameMode(ECGameMode.SPECTATOR));
        assertFalse(access.supportsGameMode(ECGameMode.SURVIVAL));
        access.setGameMode(player, ECGameMode.CREATIVE);
        assertEquals(GameMode.Creative, ops.mode);
        assertEquals(ECGameMode.CREATIVE, access.getGameMode(player));
        assertThrows(IllegalArgumentException.class, () -> access.setGameMode(player, ECGameMode.SPECTATOR));
    }

    @Test
    void anEffectIsFoundByItsIdInAnyCase() {
        assertTrue(access.isEffect("Potion_Speed"));
        access.addEffect(player, EffectLevel.of("potion_speed", 3), Duration.ofSeconds(10));
        assertEquals(10f, ops.effects.get("Potion_Speed"));
        assertEquals(Duration.ofSeconds(10), access.getEffect(player, "potion_speed").getRemaining());
        access.removeEffect(player, "potion_speed");
        assertTrue(ops.effects.isEmpty());
        assertThrows(IllegalArgumentException.class, () -> access.addEffect(player, EffectLevel.of("nausea", 1), Duration.ofSeconds(1)));
    }

    @Test
    void healthIsTheStatClampedAndDamageGoesThroughTheDamageSystemsUnlessItIgnoresArmor() {
        access.setHealth(player, 500);
        assertEquals(100.0, access.getHealth(player));
        access.damage(player, 30, true);
        assertEquals(70.0, access.getHealth(player), "ignoring armor lowers the stat straight");
        access.damage(player, 10, false);
        assertEquals(70.0, access.getHealth(player), "the game's way never touches the stat itself");
        assertEquals(Collections.singletonList(10f), ops.damages, "it goes to the server's damage systems");
    }

    @Test
    void aCommandRunsAsTheConsoleOrAsThePlayer() {
        access.dispatchCommand(null, "say hi");
        access.dispatchCommand(player, "spawn");
        assertEquals(Arrays.asList("console: say hi", "Kweebec: spawn"), ops.commands);
    }
}
