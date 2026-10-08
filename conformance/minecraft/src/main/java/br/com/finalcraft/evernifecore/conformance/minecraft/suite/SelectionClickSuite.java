package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.minecraft.region.selection.McSelectionItems;
import br.com.finalcraft.evernifecore.minecraft.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.region.selection.ItemSpec;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A click of a real client on a block, holding the selection wand, has to arrive as a {@link PlayerInteractEvent}
 * whose item the core still recognises as the selection tool - the path the region selection depends on, and the
 * one that once lost the wand's mark on 1.7.10. {@code /ecc click <player>} hands the wand over and starts
 * recording; the client then clicks a block, and this suite reads what was recorded.
 */
public final class SelectionClickSuite implements ConformanceSuite {

    /** Who was armed, and every block click they made since, as {@code ACTION tool=<bool> block=<type>}. */
    private static final Map<UUID, List<String>> RECORDED = new ConcurrentHashMap<>();

    @Override
    public String getId() {
        return "selection-click";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Collections.singletonList(PlayerCases.of("click-is-selection-tool", ctx, player -> {
            List<String> clicks = RECORDED.get(player.getUniqueId());
            if (clicks == null) {
                return Verdict.skipped("not armed - /ecc click " + player.getName() + " first, then click a block");
            }
            boolean recognised = clicks.stream().anyMatch(click -> click.contains(" tool=true "));
            return Verdict.of(recognised, clicks.isEmpty() ? "armed, but no block click arrived" : "clicks=" + clicks);
        }));
    }

    /** Gives {@code player} the wand and starts recording their block clicks afresh. */
    public static void arm(Player player) {
        new McSelectionItems().give(FCBukkitUtil.adapt(player), ItemSpec.DEFAULT_TOOL);
        RECORDED.put(player.getUniqueId(), Collections.synchronizedList(new ArrayList<>()));
    }

    /** Registered by the plugin once; records only the armed players. */
    public static final class Recorder implements Listener {

        @EventHandler(priority = EventPriority.MONITOR)
        public void onClick(PlayerInteractEvent event) {
            List<String> clicks = RECORDED.get(event.getPlayer().getUniqueId());
            Block block = event.getClickedBlock();
            if (clicks == null || block == null) {
                return;
            }
            clicks.add(event.getAction() + " tool=" + new McSelectionItems().isSelectionTool(event.getItem()) + " block=" + block.getType());
        }
    }
}
