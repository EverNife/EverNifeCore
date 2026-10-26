package br.com.finalcraft.evernifecore.minecraft.waypoint;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.math.game.vector.locpos.WorldLocPos;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.testing.ECoreTestWorld;
import br.com.finalcraft.evernifecore.testing.Platforms;
import br.com.finalcraft.evernifecore.testing.minecraft.Doubles;
import br.com.finalcraft.evernifecore.testing.minecraft.ItemWorld;
import br.com.finalcraft.evernifecore.waypoint.Waypoint;
import br.com.finalcraft.evernifecore.waypoint.WaypointId;
import br.com.finalcraft.evernifecore.waypoint.WaypointTarget;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hologram channel reaches DecentHolograms by reflection alone: proven against a stand-in DHAPI with the real signatures. */
class McHologramWaypointChannelTest {

    private static final UUID STEVE = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    private static final WorldLocPos CAMP = WorldLocPos.of(10.5, 64, 0.5, "world");

    private ECoreTestWorld world;

    @BeforeEach
    void reset() {
        DHAPI.reset();
    }

    @AfterEach
    void close() {
        world.close();
        DHAPI.reset();
    }

    private static FPlayer steve() {
        AtomicReference<Player> self = new AtomicReference<>();
        Player player = Doubles.of(Player.class)
                .returning("getName", "Steve")
                .returning("getUniqueId", STEVE)
                .returning("isOnline", true)
                .returning("getWorld", ItemWorld.world("world"))
                .on("getPlayer", args -> self.get())
                .build();
        self.set(player);
        return FCBukkitUtil.adapt(player);
    }

    private static Waypoint camp() {
        return Waypoint.of(WaypointId.of("finalquests", "lumber-camp"), WaypointTarget.at(CAMP)).label("§aLumber camp").build();
    }

    @Test
    void decentHologramsGetsAHologramOnlyThePlayerSees() {
        world = Platforms.lenient().pluginsLoaded("DecentHolograms").install();
        McHologramWaypointChannel channel = new McHologramWaypointChannel();
        FPlayer steve = steve();
        assertTrue(channel.isSupported());

        channel.show(steve, camp(), CAMP);

        String name = McHologramWaypointChannel.nameOf(steve, camp());
        assertEquals(Collections.singletonList("createHologram:" + name), DHAPI.CALLS);
        Hologram hologram = DHAPI.HOLOGRAMS.get(name);
        assertEquals(Collections.singletonList("§aLumber camp"), hologram.lines);
        assertEquals(new Location(ItemWorld.world("world"), 10.5, 64 + McHologramWaypointChannel.LIFT, 0.5), hologram.location);
        assertFalse(hologram.defaultVisibleState, "hidden from everyone else");
        assertEquals(Collections.singleton(STEVE), hologram.showPlayers);
        assertEquals(1, hologram.showAllCalls, "the visibility was applied");

        channel.refresh(steve, camp(), CAMP);
        assertEquals(1, DHAPI.CALLS.size(), "a target that did not move moves nothing");
        channel.refresh(steve, camp(), WorldLocPos.of(20.5, 64, 0.5, "world"));
        channel.hide(steve, camp());

        assertEquals(Arrays.asList("createHologram:" + name, "moveHologram:" + name, "removeHologram:" + name), DHAPI.CALLS);
        assertTrue(DHAPI.HOLOGRAMS.isEmpty());
    }

    @Test
    void aHologramPluginThatIsDisabledOrAbsentIsUnsupported() {
        world = Platforms.lenient().pluginsLoaded().pluginsDisabled("DecentHolograms", "FancyHolograms").install();
        McHologramWaypointChannel channel = new McHologramWaypointChannel();

        assertFalse(channel.isSupported(), "installed but disabled");
        channel.show(steve(), camp(), CAMP);
        assertTrue(DHAPI.CALLS.isEmpty());
    }

    @Test
    void theNameIsUniquePerPlayerAndWaypointAndTakesOnlyWhatThePluginsAccept() {
        world = Platforms.lenient().install();

        String name = McHologramWaypointChannel.nameOf(steve(), Waypoint.of(WaypointId.of("final.quests", "camp"), WaypointTarget.at(CAMP)).build());

        assertEquals("enc-wp-" + STEVE + "-final_quests-camp", name);
    }
}
