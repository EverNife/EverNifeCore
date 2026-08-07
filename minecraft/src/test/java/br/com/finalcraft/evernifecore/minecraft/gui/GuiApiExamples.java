package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.cfg.ConfigSetting;
import br.com.finalcraft.evernifecore.minecraft.gui.component.ListComponent;
import br.com.finalcraft.evernifecore.minecraft.gui.component.Pager;
import br.com.finalcraft.evernifecore.minecraft.gui.icons.DefaultIcons;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.GuiLayout;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IconData;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Layouts;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickKind;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiType;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Region;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.state.MutableState;
import br.com.finalcraft.evernifecore.minecraft.gui.state.State;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Arrays;
import java.util.Collections;
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

    /** A list poured into a region, with the page size derived from the region and read by the title. */
    static void memberList(Player player, List<String> members) {
        Pager pager = new Pager();

        Gui.of(6)
                .title(() -> "§9Membros - página " + pager.getPage() + "/" + pager.getTotalPages())
                .list(members)
                .pager(pager)
                .into(Slots.box(2, 2, 5, 8))
                .pagedBy(Slots.at(6, 3), Slots.at(6, 7))
                .render((member, icon) -> icon
                        .from(FCItemFactory.from(Material.PAPER))
                        .displayName("§a" + member)
                        .onClick(ctx -> ctx.getViewer().sendMessage(member)))
                .open(player);
    }

    /** The source too big to materialise: one page at a time, plus the count nothing else can answer. */
    static void hugeCatalogue(Player player) {
        Gui.of(6)
                .list((page, size) -> search(page, size))
                .total(GuiApiExamples::count)
                .into(Slots.box(1, 1, 5, 9))
                .pagedBy(Slots.at(6, 3), Slots.at(6, 7))
                .render((entry, icon) -> icon.from(FCItemFactory.from(Material.PAPER)).displayName(entry))
                .open(player);
    }

    // ---- the admin decides how many buttons exist: the list itself comes from the yml ----

    /** One entry of the config-driven list: a plain pojo with a codec registered in {@code ConfigFactory}. */
    public static class UpgradeOption {

        private int level;
        private double price;
        private String permission;

        public UpgradeOption() {

        }

        UpgradeOption(int level, double price, String permission) {
            this.level = level;
            this.price = price;
            this.permission = permission;
        }

        public int getLevel() {
            return level;
        }

        public double getPrice() {
            return price;
        }

        public String getPermission() {
            return permission;
        }

    }

    public enum UpgradeState {DEFAULT, OWNED, LOCKED}

    @GuiLayout(title = "§9Melhorias", rows = 3)
    public static class UpgradesLayout extends LayoutBase {

        @ConfigSetting(key = "Settings.options", comment = @FCLocale(lang = LocaleType.EN_US,
                text = "Every entry becomes a button. Add or erase them at will."))
        public List<UpgradeOption> options = Arrays.asList(
                new UpgradeOption(1, 1000D, "upgrade.n1"),
                new UpgradeOption(2, 5000D, "upgrade.n2"),
                new UpgradeOption(3, 25000D, "upgrade.n3"));

        /** The button TEMPLATE: its slots are the region the entries are poured into. */
        @IconData(slot = {10, 11, 12, 13, 14, 15, 16})
        public Icon OPTION = FCItemFactory.from(Material.IRON_INGOT)
                .displayName("§aNível %option_level%")
                .lore("§7Custo: §6$%option_price%")
                .asIcon()
                .addState(UpgradeState.OWNED, FCItemFactory.from(Material.DIAMOND)
                        .displayName("§a§lNível %option_level%"))
                .addState(UpgradeState.LOCKED, FCItemFactory.from(Material.BARRIER)
                        .displayName("§7Nível %option_level%"));

        @IconData(slot = {18})
        public Icon PREVIOUS = DefaultIcons.previousPage();

        @IconData(slot = {26})
        public Icon NEXT = DefaultIcons.nextPage();

    }

    private static final RegexReplacer<UpgradeOption> OPTION = new RegexReplacer<UpgradeOption>()
            .addParser("option_level", UpgradeOption::getLevel)
            .addParser("option_price", UpgradeOption::getPrice);

    /** The whole menu. It has no idea how many buttons there are - that is the yml's business. */
    static void configDrivenList(Player player) {
        UpgradesLayout layout = Layouts.of(UpgradesLayout.class);

        ListComponent<UpgradeOption, UpgradesLayout> options = Gui.of(layout).list(() -> layout.options);
        options.into(l -> l.OPTION)
                .pagedBy(l -> l.PREVIOUS, l -> l.NEXT)
                .render((option, icon) -> icon
                        .addScope(OPTION, option)
                        .permission(option.getPermission())
                        .states(UpgradeState.class, () -> stateOf(option))
                        .onClick(ctx -> ctx.getViewer().sendMessage("§a" + option.getLevel())))
                .open(player);
    }

    // ---- a tab, a sort order and a filter that are still there the next time the menu opens ----

    public enum Tab {DEFAULT, SELLING}

    public enum Sorting {DEFAULT, PRICE_ASC, PRICE_DESC}

    @GuiLayout(title = "§9Leilão", rows = 6)
    public static class AuctionLayout extends LayoutBase {

        /** One icon, two states - not two icons fighting over slot 47. */
        @IconData(slot = {47})
        public Icon TAB = FCItemFactory.from(Material.CHEST)
                .displayName("§eVendo: §aComprando")
                .asIcon()
                .addState(Tab.SELLING, FCItemFactory.from(Material.ENDER_CHEST)
                        .displayName("§eVendo: §6Vendendo"));

        /** PRICE_ASC is written {@code priceAsc} in the yml. */
        @IconData(slot = {51})
        public Icon SORT = FCItemFactory.from(Material.HOPPER)
                .displayName("§eOrdem: §fMais recente")
                .asIcon()
                .addState(Sorting.PRICE_ASC, FCItemFactory.from(Material.HOPPER)
                        .displayName("§eOrdem: §fMenor preço"))
                .addState(Sorting.PRICE_DESC, FCItemFactory.from(Material.HOPPER)
                        .displayName("§eOrdem: §fMaior preço"));

        @IconData(slot = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25})
        public Icon LOT = FCItemFactory.from(Material.PAPER).asIcon();

        @IconData(slot = {45})
        public Icon PREVIOUS = DefaultIcons.previousPage();

        @IconData(slot = {53})
        public Icon NEXT = DefaultIcons.nextPage();

    }

    static void auction(Player player, AuctionPreferences preferences) {
        MutableState<Tab> tab = State.bound(preferences::getTab, preferences::setTab);
        MutableState<Sorting> sorting = State.bound(preferences::getSorting, preferences::setSorting);

        Gui<AuctionLayout> gui = Gui.of(AuctionLayout.class);

        gui.icon(l -> l.TAB)
                .states(Tab.class, tab::get)
                .onClick(ctx -> tab.set(tab.get() == Tab.DEFAULT ? Tab.SELLING : Tab.DEFAULT));

        gui.icon(l -> l.SORT)
                .cycle(Sorting.class, sorting)
                .onCycle(chosen -> player.sendMessage("§7" + chosen));

        gui.list(() -> lots(tab.get(), sorting.get()))
                .dependsOn(tab, sorting)
                .into(l -> l.LOT)
                .pagedBy(l -> l.PREVIOUS, l -> l.NEXT)
                .render((lot, icon) -> icon.displayName("§a" + lot))
                .open(player);
    }

    // ---- stand-ins for whatever the plugin actually has ----

    private static UpgradeState stateOf(UpgradeOption option) {
        return UpgradeState.DEFAULT;
    }

    private static List<String> search(int page, int pageSize) {
        return Collections.emptyList();
    }

    private static int count() {
        return 0;
    }

    private static List<String> lots(Tab tab, Sorting sorting) {
        return Collections.emptyList();
    }

    private interface AuctionPreferences {
        Tab getTab();

        void setTab(Tab tab);

        Sorting getSorting();

        void setSorting(Sorting sorting);
    }

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
