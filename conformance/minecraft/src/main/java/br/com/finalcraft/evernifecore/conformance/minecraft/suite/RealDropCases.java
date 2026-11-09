package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.flag.Tristate;
import br.com.finalcraft.evernifecore.action.event.DropActionEvent;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.eventbus.ECEventBus;
import br.com.finalcraft.evernifecore.eventbus.ECEventSubscription;
import br.com.finalcraft.evernifecore.flag.core.CoreFlags;
import br.com.finalcraft.evernifecore.region.CreationResult;
import br.com.finalcraft.evernifecore.region.Region;
import br.com.finalcraft.evernifecore.region.RegionManager;
import br.com.finalcraft.evernifecore.region.shape.CuboidShape;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Drops thrown by the real client (Q and Ctrl+Q): {@code HumanEntity#dropItem(boolean)} only exists from 1.16, and the server's own
 * path after a cancelled drop is what differs per era. {@code /ecc drop arm <scenario> <player>} puts the bot in a region with
 * {@link #DROP_STACK} cobblestone in its first slot; the driver presses the keys, {@code sample} reads the server after each press and
 * {@code finish} judges and cleans up. The cases of the {@code regions} suite only read those verdicts, so a run the bot never threw in
 * fails instead of skipping. What is read is the whole inventory, because a cancelled drop of air can come back as something else.
 */
public final class RealDropCases {

    /** How much cobblestone the bot is handed. */
    private static final int DROP_STACK = 5;
    /** The ground is read this far around the bot; what it throws lands well inside it. */
    private static final int REACH = 16;
    private static final List<String> BYPASS_NODES = Arrays.asList("evernifecore.region.bypass.*",
            "evernifecore.region.bypass.evernifecore.destroy-dropped-items", "evernifecore.region.bypass.evernifecore.deny-item-drop");

    /** The scenario of one case; all of them stand inside {@code destroy-dropped-items}. */
    private enum Scenario {
        /** Q and Ctrl+Q: the hand empties and nothing lands. */
        DESTROY("destroy-dropped-items-never-spawns"),
        /** A plugin cancelling at {@code LOWEST} comes before the flag: the item stays with the bot. */
        LOWEST_CANCEL("destroy-dropped-items-lowest-cancel-keeps-the-item"),
        /** A plugin uncancelling at {@code NORMAL}, after the flag emptied the entity: only a duplicate fails, the rest is measured. */
        NORMAL_UNCANCEL("destroy-dropped-items-normal-uncancel-duplicates-nothing"),
        /** {@code deny-item-drop} on the same region wins: the drop never gets to be destroyed. */
        DENY_WINS("destroy-dropped-items-deny-item-drop-wins");

        private final String caseId;

        Scenario(String caseId) {
            this.caseId = caseId;
        }
    }

    private static final Map<Scenario, Verdict> VERDICTS = new ConcurrentHashMap<>();
    private static volatile RegionManager regions;
    private static volatile Run current;

    private RealDropCases() {
    }

    /** @param manager the plugin's own manager, where the scenarios' regions live */
    static void use(RegionManager manager) {
        regions = manager;
    }

    static List<ConformanceCase> cases(ConformanceContext ctx) {
        List<ConformanceCase> cases = new ArrayList<>();
        for (Scenario scenario : Scenario.values()) {
            cases.add(PlayerCases.of(scenario.caseId, ctx, player -> {
                Verdict verdict = VERDICTS.get(scenario);
                return verdict != null ? verdict : Verdict.fail("the bot never threw: /ecc drop arm|sample|finish "
                        + scenario.name().toLowerCase() + " " + player.getName() + " around its Q and Ctrl+Q, as the matrix driver does");
            }));
        }
        return cases;
    }

    /** One step of {@code /ecc drop}; answers what the sender is told. */
    public static String step(String step, String scenarioName, Player player) {
        Scenario scenario;
        try {
            scenario = Scenario.valueOf(scenarioName.toUpperCase().replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            return "unknown drop scenario '" + scenarioName + "' - known: " + Arrays.toString(Scenario.values());
        }
        switch (step) {
            case "arm":
                current = new Run(scenario, player);
                return current.arm();
            case "sample":
                return current == null ? "nothing armed" : current.sample();
            case "finish":
                return current == null ? "nothing armed" : current.finish();
            default:
                return "unknown drop step '" + step + "' - known: arm, sample, finish";
        }
    }

    private static Plugin plugin() {
        return Bukkit.getPluginManager().getPlugin("ECoreConformance");
    }

    /** One armed scenario: the region, the listeners beside the core's, and the reads taken so far. */
    private static final class Run {

        private final Scenario scenario;
        private final Player player;
        private final List<Listener> listeners = new ArrayList<>();
        private final List<String> samples = new ArrayList<>();
        private final List<Sample> reads = new ArrayList<>();
        private int[] posts = new int[1];
        private final Map<String, Integer> spawned = new TreeMap<>();
        private Region region;
        private ECEventSubscription<DropActionEvent.Post> heard;
        private PermissionAttachment noBypass;
        private ItemStack[] kept;
        private int selected;
        private String refused;

        private Run(Scenario scenario, Player player) {
            this.scenario = scenario;
            this.player = player;
        }

        private String arm() {
            Location at = player.getLocation();
            int x = at.getBlockX(), y = at.getBlockY(), z = at.getBlockZ();
            CreationResult created = regions.create("ecc-drop-" + scenario.name().toLowerCase().replace('_', '-'), player.getWorld().getName(),
                    CuboidShape.of(BlockPos.of(x - 8, y - 8, z - 8), BlockPos.of(x + 8, y + 8, z + 8)));
            region = created.getRegion();
            if (region == null) {
                refused = "the region was refused: " + created.getRefusal();
                return refused;
            }
            region.setFlag(CoreFlags.DESTROY_DROPPED_ITEMS, Tristate.TRUE);
            if (scenario == Scenario.DENY_WINS) {
                region.setFlag(CoreFlags.DENY_ITEM_DROP, Tristate.TRUE);
            }
            listeners.add(new Listener() {
                @EventHandler(priority = EventPriority.MONITOR)
                public void onSpawn(ItemSpawnEvent event) {
                    //the whole world spawns items too: only what can be this throw counts, and an uncancelled drop of air can spawn as air
                    Material type = event.getEntity().getItemStack().getType();
                    if ((type == Material.COBBLESTONE || type == Material.AIR) && event.getLocation().getWorld().equals(player.getWorld())
                            && event.getLocation().distance(player.getLocation()) <= REACH) {
                        spawned.merge(type.name(), 1, Integer::sum);
                    }
                }
            });
            if (scenario == Scenario.LOWEST_CANCEL) {
                listeners.add(new Listener() {
                    @EventHandler(priority = EventPriority.LOWEST)
                    public void onDrop(PlayerDropItemEvent event) {
                        event.setCancelled(true);
                    }
                });
            }
            if (scenario == Scenario.NORMAL_UNCANCEL) {
                listeners.add(new Listener() {
                    @EventHandler(priority = EventPriority.NORMAL)
                    public void onDrop(PlayerDropItemEvent event) {
                        event.setCancelled(false);
                    }
                });
            }
            //a tick later: the core's listeners come up through the main thread once the region needs them; the bot's hand is empty until then
            Bukkit.getScheduler().runTaskLater(plugin(), () -> {
                noBypass = player.addAttachment(plugin());
                BYPASS_NODES.forEach(node -> noBypass.setPermission(node, false));
                kept = player.getInventory().getContents();
                selected = player.getInventory().getHeldItemSlot();
                listeners.forEach(listener -> Bukkit.getPluginManager().registerEvents(listener, plugin()));
                heard = ECEventBus.global().subscribe(DropActionEvent.Post.class, post -> posts[0]++);
                player.getNearbyEntities(REACH, REACH, REACH).stream().filter(entity -> entity instanceof Item).forEach(Entity::remove);
                player.getInventory().clear();
                player.getInventory().setHeldItemSlot(0);
                player.getInventory().setItem(0, new ItemStack(Material.COBBLESTONE, DROP_STACK));
                player.updateInventory();
            }, 2L);
            return "armed " + scenario;
        }

        private String sample() {
            Sample read = new Sample(inventory(), ground(), new TreeMap<>(spawned), posts[0]);
            reads.add(read);
            samples.add(read.toString());
            return "sample " + read;
        }

        private String finish() {
            Verdict verdict;
            try {
                verdict = refused != null ? Verdict.fail(refused) : judge();
            } catch (Throwable thrown) {
                verdict = Verdict.fail(Verdict.describe(thrown));
            }
            VERDICTS.put(scenario, verdict);
            listeners.forEach(HandlerList::unregisterAll);
            if (heard != null) heard.unsubscribe();
            if (region != null) regions.remove(region);
            if (kept != null) {
                player.getNearbyEntities(REACH, REACH, REACH).stream().filter(entity -> entity instanceof Item).forEach(Entity::remove);
                player.getInventory().setContents(kept);
                player.getInventory().setHeldItemSlot(selected);
                player.removeAttachment(noBypass);
                player.updateInventory();
            }
            current = null;
            return "finished " + scenario + ": " + verdict;
        }

        private Verdict judge() {
            String detail = "samples=" + samples;
            if (reads.isEmpty()) {
                return Verdict.fail("no throw was sampled");
            }
            Sample last = reads.get(reads.size() - 1);
            switch (scenario) {
                case DESTROY:
                    return Verdict.of(reads.size() == 2 && reads.get(0).holds(DROP_STACK - 1) && reads.get(0).ground.isEmpty()
                            && last.inventory.isEmpty() && last.ground.isEmpty() && last.spawned.isEmpty() && last.posts == 2, detail);
                case LOWEST_CANCEL:
                case DENY_WINS:
                    return Verdict.of(last.holds(DROP_STACK) && last.ground.isEmpty() && last.spawned.isEmpty() && last.posts == 0, detail);
                default:
                    //whatever the era does with an uncancelled drop of air, nothing comes out of it that was not there before
                    return Verdict.of(last.units() <= DROP_STACK && last.onlyCobblestone(), detail);
            }
        }

        private Map<String, Integer> inventory() {
            Map<String, Integer> found = new TreeMap<>();
            for (ItemStack stack : player.getInventory().getContents()) {
                add(found, stack);
            }
            return found;
        }

        private Map<String, Integer> ground() {
            Map<String, Integer> found = new TreeMap<>();
            for (Entity entity : player.getNearbyEntities(REACH, REACH, REACH)) {
                if (entity instanceof Item) {
                    add(found, ((Item) entity).getItemStack());
                }
            }
            return found;
        }

        private static void add(Map<String, Integer> found, ItemStack stack) {
            if (stack != null && stack.getType() != Material.AIR) {
                found.merge(stack.getType().name(), stack.getAmount(), Integer::sum);
            }
        }
    }

    /** What the server shows right after one throw. */
    private static final class Sample {

        private final Map<String, Integer> inventory;
        private final Map<String, Integer> ground;
        private final Map<String, Integer> spawned;
        private final int posts;

        private Sample(Map<String, Integer> inventory, Map<String, Integer> ground, Map<String, Integer> spawned, int posts) {
            this.inventory = inventory;
            this.ground = ground;
            this.spawned = spawned;
            this.posts = posts;
        }

        private boolean holds(int cobblestone) {
            return inventory.size() == 1 && Integer.valueOf(cobblestone).equals(inventory.get(Material.COBBLESTONE.name()));
        }

        private int units() {
            return inventory.values().stream().mapToInt(Integer::intValue).sum() + ground.values().stream().mapToInt(Integer::intValue).sum();
        }

        private boolean onlyCobblestone() {
            return inventory.keySet().stream().allMatch(Material.COBBLESTONE.name()::equals) && ground.keySet().stream().allMatch(Material.COBBLESTONE.name()::equals);
        }

        @Override
        public String toString() {
            return "{inventory=" + inventory + " ground=" + ground + " spawned=" + spawned + " posts=" + posts + "}";
        }
    }
}
