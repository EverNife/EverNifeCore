package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickKind;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiType;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Region;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.state.MutableState;
import br.com.finalcraft.evernifecore.minecraft.gui.state.State;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The documented gui API, written out exactly as the manual shows it, so that the compiler is what
 * verifies the documentation instead of a reader.
 *
 * <p>Nothing here runs: building an {@code ItemStack} needs a live server, and none of these methods
 * is a test. What they prove is that the chains in the manual still say what they say - a rename or a
 * signature change breaks the build here, on the example, rather than silently in a doc nobody
 * recompiles.</p>
 */
@SuppressWarnings("unused")
final class GuiApiExamples {

    private GuiApiExamples() {

    }

    /** The smallest menu there is. */
    static void helloWorld(Player player) {
        Gui.of(3)
                .title("§9Menu")
                .icon(13, FCItemFactory.from(Material.DIAMOND)
                        .displayName("§bClique em mim")
                        .onClick(ctx -> ctx.getViewer().sendMessage("§aOi!")))
                .open(player);
    }

    /** Row and column addressing, 1-based, next to the raw 0-based slot. */
    static void slotVocabulary(Player player) {
        Gui.of(6)
                .icon(Slots.at(2, 5), FCItemFactory.from(Material.DIAMOND).asIcon())
                .icon(Slots.border(), FCItemFactory.from(Material.PAPER).asIcon().background())
                .icon(Slots.box(2, 2, 5, 8), FCItemFactory.from(Material.STONE).asIcon())
                .icon(Slots.of(0, 8), FCItemFactory.from(Material.STONE).asIcon())
                .open(player);
    }

    /** An icon that redraws itself once a second while somebody is looking at it. */
    static Icon selfUpdatingIcon() {
        return FCItemFactory.from(Material.CLOCK)
                .displayName("§eLeilão")
                .lore("§7Termina em: §c" + remaining())
                .every(20)
                .render(icon -> icon.lore("§7Termina em: §c" + remaining()));
    }

    /** State: what makes the screen change without reopening it. */
    static void cookieClicker(Player player) {
        Gui.of(3)
                .title("§9Cookie Clicker")
                .component(c -> {
                    MutableState<Integer> clicks = c.remember(0);

                    c.render(slots -> slots.icon(13, FCItemFactory.from(Material.COOKIE)
                            .displayName("§6Clicado " + clicks.get() + " vezes")
                            .onClick(ctx -> clicks.update(value -> value + 1))));
                })
                .open(player);
    }

    /** One state, two components: both re-render, each writing only its own slots. */
    static void sharedState(Player player) {
        MutableState<String> filter = State.of("");

        Gui.of(6)
                .component(c -> {
                    //typed on purpose: it pins remember(State) as the overload a shared state picks
                    State<String> shared = c.remember(filter);
                    c.render(slots -> slots.icon(4, filterIcon(shared.get())));
                })
                .component(c -> {
                    c.remember(filter);
                    c.render(slots -> slots.icon(Slots.box(2, 1, 5, 9), resultIcon(filter.get())));
                })
                .open(player);
    }

    /** Watching a domain object instead of mirroring it into a state. */
    static void watchingAnObject(Player player, Arena arena) {
        Gui.of(6)
                .component(c -> {
                    State<ArenaSnapshot> snapshot = c.watch(arena::snapshot);
                    c.render(slots -> slots.icon(22, arenaIcon(snapshot.get())));
                })
                .component(c -> {
                    //the object is mutated in place, so equals cannot report it: compare a key that moves
                    State<ArenaSnapshot> live = c.watch(arena::current, ArenaSnapshot::getVersion);
                    c.every(20);
                    c.render(slots -> slots.icon(31, arenaIcon(live.get())));
                })
                .open(player);
    }

    /** The title is state too, and the screen it names survives the reopen it costs. */
    static void titleAsState(Player player, MutableState<Integer> page) {
        Gui.of(6)
                .title(() -> "§9Página " + page.get())
                .component(c -> {
                    c.remember(page);
                    c.render(slots -> slots.icon(Slots.at(6, 5), FCItemFactory.from(Material.ARROW)
                            .onClick(ctx -> {
                                page.update(value -> value + 1);
                                ctx.refresh();
                            })));
                })
                .open(player);
    }

    /** Everything is cancelled until a region says otherwise. */
    static void clickPolicyAndDebounce(Player player) {
        Gui.of(3)
                .debounce(200)
                .addRegion(new Region("storage", Slots.box(2, 2, 2, 8), Region.LAYER_CONTENT,
                        ClickPolicy.builder()
                                .allowTake()
                                .denyPlace()
                                .denyDrag()
                                .allow(ClickKind.HOTBAR)
                                .allowIfPresent("SWAP_OFFHAND")
                                .build()))
                .onClose(ctx -> giveBack(ctx.getViewer(), ctx.getContents(Slots.box(2, 2, 2, 8))))
                .open(player);
    }

    /** The smaller windows, all vanilla container types and none of them NMS. */
    static void otherWindowTypes(Player player) {
        Gui.of(GuiType.HOPPER)
                .title("§9Quantidade")
                .icon(0, amountButton(1))
                .icon(1, amountButton(8))
                .icon(2, amountButton(16))
                .icon(3, amountButton(32))
                .icon(4, amountButton(64))
                .open(player);
    }

    // ---- stand-ins for whatever the plugin actually has ----

    private static String remaining() {
        return "";
    }

    private static Icon filterIcon(String filter) {
        return FCItemFactory.from(Material.PAPER).asIcon();
    }

    private static Icon resultIcon(String filter) {
        return FCItemFactory.from(Material.PAPER).asIcon();
    }

    private static Icon arenaIcon(ArenaSnapshot snapshot) {
        return FCItemFactory.from(Material.PAPER).asIcon();
    }

    private static Icon amountButton(int amount) {
        return FCItemFactory.from(Material.PAPER).asIcon();
    }

    private static void giveBack(Player player, List<ItemStack> contents) {

    }

    private interface Arena {
        ArenaSnapshot snapshot();

        ArenaSnapshot current();
    }

    private interface ArenaSnapshot {
        long getVersion();
    }

}
