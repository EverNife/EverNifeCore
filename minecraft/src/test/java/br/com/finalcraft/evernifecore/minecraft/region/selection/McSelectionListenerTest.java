package br.com.finalcraft.evernifecore.minecraft.region.selection;

import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.minecraft.region.RegionServer;
import br.com.finalcraft.evernifecore.minecraft.region.listener.RegionListeners;
import br.com.finalcraft.evernifecore.minecraft.region.listener.RegionInteractListener;
import br.com.finalcraft.evernifecore.region.flag.Decision;
import br.com.finalcraft.evernifecore.region.flag.EventSource;
import br.com.finalcraft.evernifecore.region.flag.FlagKey;
import br.com.finalcraft.evernifecore.region.flag.Tristate;
import br.com.finalcraft.evernifecore.region.flag.TristateFlag;
import br.com.finalcraft.evernifecore.region.flag.context.ActionContext;
import br.com.finalcraft.evernifecore.region.selection.SelectionSession;
import br.com.finalcraft.evernifecore.region.selection.SelectionSpec;
import br.com.finalcraft.evernifecore.region.selection.SelectionTool;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import br.com.finalcraft.evernifecore.region.shape.Shape;
import br.com.finalcraft.evernifecore.region.usage.EventSourceUsage;
import br.com.finalcraft.evernifecore.testing.Senders;
import br.com.finalcraft.evernifecore.testing.TempDirNobodyCleans;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static br.com.finalcraft.evernifecore.minecraft.testkit.BukkitEventWorld.isRegistered;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Clicks with the selection tool on a Bukkit server, each fired through the plugin manager. */
class McSelectionListenerTest {

    @TempDirNobodyCleans
    Path tempDir;

    private RegionServer server;
    private final Set<ItemStack> marked = Collections.newSetFromMap(new IdentityHashMap<>());
    private final McSelectionItems items = new McSelectionItems(new McSelectionItems.ItemMark() {
        @Override
        public ItemStack mark(ItemStack item) {
            marked.add(item);
            return item;
        }

        @Override
        public boolean isMarked(ItemStack item) {
            return marked.contains(item);
        }
    });
    private final List<Shape> confirmed = new ArrayList<>();
    private Player steve;
    private ItemStack tool;

    /** Denies every interaction in its region, and counts how often it was asked. */
    private static final class DenyingProbe extends TristateFlag {
        final AtomicInteger asked = new AtomicInteger();

        DenyingProbe(ECPluginData owner) {
            super(FlagKey.of(owner, "deny-interact-probe"), Collections.singleton(EventSource.INTERACT), Tristate.FALSE, Tristate.TRUE);
        }

        @Override
        public Decision decide(ActionContext context, Tristate value) {
            asked.incrementAndGet();
            return value == Tristate.TRUE ? Decision.DENY : Decision.PASS;
        }
    }

    @BeforeEach
    void open() {
        server = RegionServer.open(tempDir);
        FCLocaleManager.loadLocale(server.regions.getOwner(), SelectionTool.class, SelectionSession.class);
        steve = server.player("Steve", 0.5, 64, 0.5);
        tool = new ItemStack(Material.GOLDEN_HOE);
        marked.add(tool);
    }

    @AfterEach
    void close() {
        SelectionTool.cancelAll();
        server.close();
    }

    /**
     * Steve's selection, opened through a test sender with his id: the session talks to whoever opened it, and a Bukkit
     * player's chat needs a live server. The listener still hands the clicks in as the Bukkit player.
     */
    private SelectionSession start() {
        return SelectionTool.start(Senders.player("Steve", steve.getUniqueId()).at(RegionServer.WORLD, 0.5, 64, 0.5),
                SelectionSpec.cuboid(), confirmed::add);
    }

    private PlayerInteractEvent click(Action action, ItemStack held, int x, int y, int z) {
        return new PlayerInteractEvent(steve, action, held, server.world.getBlockAt(x, y, z), BlockFace.UP);
    }

    @Test
    void aLeftClickWithTheToolAddsThePointAndCancelsTheEvent() {
        server.listen(new McSelectionListener(items));
        SelectionSession session = start();

        PlayerInteractEvent left = click(Action.LEFT_CLICK_BLOCK, tool, 10, 70, 10);
        server.server.getPluginManager().callEvent(left);

        assertTrue(left.isCancelled(), "the tool never breaks the block it marks");
        assertEquals(Collections.singletonList(BlockPos.of(10, 70, 10)), session.getPoints());
    }

    @Test
    void aRightClickRemovesTheLastPoint() {
        server.listen(new McSelectionListener(items));
        SelectionSession session = start();

        server.server.getPluginManager().callEvent(click(Action.LEFT_CLICK_BLOCK, tool, 10, 70, 10));
        server.server.getPluginManager().callEvent(click(Action.LEFT_CLICK_BLOCK, tool, 0, 60, 5));
        server.server.getPluginManager().callEvent(click(Action.RIGHT_CLICK_BLOCK, tool, 3, 3, 3));

        assertEquals(Collections.singletonList(BlockPos.of(10, 70, 10)), session.getPoints());
    }

    @Test
    void aSneakingRightClickConfirms() {
        server.listen(new McSelectionListener(items));
        SelectionSession session = start();
        server.server.getPluginManager().callEvent(click(Action.LEFT_CLICK_BLOCK, tool, 10, 70, 10));
        server.server.getPluginManager().callEvent(click(Action.LEFT_CLICK_BLOCK, tool, 0, 60, 5));

        steve.setSneaking(true);
        server.server.getPluginManager().callEvent(click(Action.RIGHT_CLICK_BLOCK, tool, 3, 3, 3));

        assertEquals(1, confirmed.size());
        assertTrue(confirmed.get(0) instanceof CuboidShape);
        assertFalse(session.isActive(), "a confirmed selection is over");
    }

    @Test
    void aClickWithoutTheToolIsNotASelectionClick() {
        server.listen(new McSelectionListener(items));
        SelectionSession session = start();

        PlayerInteractEvent plain = click(Action.LEFT_CLICK_BLOCK, new ItemStack(Material.GOLDEN_HOE), 10, 70, 10);
        server.server.getPluginManager().callEvent(plain);

        assertTrue(session.getPoints().isEmpty());
        assertFalse(plain.isCancelled(), "an unmarked hoe is just a hoe");
    }

    @Test
    void aClickWithTheToolButNoSessionPassesThrough() {
        server.listen(new McSelectionListener(items));
        start();
        SelectionTool.cancelAll();

        PlayerInteractEvent left = click(Action.LEFT_CLICK_BLOCK, tool, 10, 70, 10);
        server.server.getPluginManager().callEvent(left);

        assertFalse(left.isCancelled(), "no selection is open, so the click was not one");
    }

    @Test
    void anAirClickIsIgnored() {
        server.listen(new McSelectionListener(items));
        SelectionSession session = start();

        server.server.getPluginManager().callEvent(new PlayerInteractEvent(steve, Action.LEFT_CLICK_AIR, tool, null, BlockFace.SELF));

        assertTrue(session.getPoints().isEmpty());
    }

    @Test
    void aSelectionClickInsideDenyInteractStillSelects() {
        DenyingProbe denyInteract = server.flag(new DenyingProbe(server.regions.getOwner()));
        server.box("claim", 0, 0, 0, 20, 100, 20, denyInteract);
        server.listen(new RegionInteractListener());
        server.listen(new McSelectionListener(items));
        SelectionSession session = start();

        PlayerInteractEvent left = click(Action.LEFT_CLICK_BLOCK, tool, 10, 70, 10);
        server.server.getPluginManager().callEvent(left);

        assertEquals(Collections.singletonList(BlockPos.of(10, 70, 10)), session.getPoints(), "the point is taken inside the region");
        assertTrue(left.isCancelled());
        assertEquals(0, denyInteract.asked.get(), "cancelled by the selection first, so the region flag never saw the click");

        server.server.getPluginManager().callEvent(click(Action.LEFT_CLICK_BLOCK, new ItemStack(Material.GOLDEN_HOE), 10, 70, 10));
        assertEquals(1, denyInteract.asked.get(), "a plain click in the same place is the flag's");
    }

    @Test
    void theListenerSitsOnTheServerOnlyWhileASelectionIsOpen() {
        Duration idleTime = EventSourceUsage.get().getIdleTime();
        RegionListeners bridge = RegionListeners.start(server.server.getPluginData());
        try {
            assertFalse(isRegistered(PlayerInteractEvent.getHandlerList(), McSelectionListener.class), "no selection is open");

            start();
            assertTrue(isRegistered(PlayerInteractEvent.getHandlerList(), McSelectionListener.class));

            EventSourceUsage.get().setIdleTime(Duration.ZERO);
            SelectionTool.cancelAll();
            server.regions.runDue();
            assertFalse(isRegistered(PlayerInteractEvent.getHandlerList(), McSelectionListener.class), "the last selection closed");
        } finally {
            bridge.stop();
            EventSourceUsage.get().setIdleTime(idleTime);
        }
    }
}
