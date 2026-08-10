package br.com.finalcraft.evernifecore.minecraft.gui;

import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.minecraft.gui.cfg.ConfigSetting;
import br.com.finalcraft.evernifecore.minecraft.gui.component.CycleBinder;
import br.com.finalcraft.evernifecore.minecraft.gui.component.ListComponent;
import br.com.finalcraft.evernifecore.minecraft.gui.component.Pager;
import br.com.finalcraft.evernifecore.minecraft.gui.icons.DefaultIcons;
import br.com.finalcraft.evernifecore.minecraft.gui.icons.EnumStainedGlassPane;
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
import br.com.finalcraft.evernifecore.minecraft.gui.view.ClickContext;
import br.com.finalcraft.evernifecore.minecraft.gui.view.prompt.ChatPrompt;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.minecraft.inventory.stored.StoredInventory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;

/**
 * The documented gui API, written out exactly as the manual shows it, so that the compiler is what
 * verifies the documentation instead of a reader.
 *
 * <p>Nothing here runs: building an {@code ItemStack} needs a live server, and none of these methods
 * is a test. What they prove is that the chains in the manual still say what they say - a rename or a
 * signature change breaks the build here, on the example, rather than silently in a doc nobody
 * recompiles.</p>
 *
 * <p>It covers the manual's <em>examples</em>, not every member its reference tables list. A method
 * named only in a table can still be renamed without this file noticing, so a member that matters
 * enough to document is a member worth an example - here, or in a test that exercises it.</p>
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
                .addComponent(c -> {
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
                .addComponent(c -> {
                    //typed on purpose: it pins remember(State) as the overload a shared state picks
                    State<String> shared = c.remember(filter);
                    c.render(slots -> slots.icon(4, filterIcon(shared.get())));
                })
                .addComponent(c -> {
                    c.remember(filter);
                    c.render(slots -> slots.icon(Slots.box(2, 1, 5, 9), resultIcon(filter.get())));
                })
                .open(player);
    }

    /** Redrawing on demand: what a component does when the thing it draws changed behind its back. */
    static void redrawnOnDemand(Player player, Queue<String> feed) {
        Gui.of(3)
                .addComponent(c -> {
                    c.render(slots -> slots.icon(13, FCItemFactory.from(Material.PAPER)
                            .displayName(feed.isEmpty() ? "§7Nada novo" : "§e" + feed.peek())
                            //invalidate() waits for the next tick to redraw; renderNow() does it here,
                            //which is what an answer the player is watching for wants
                            .onClick(ctx -> {
                                feed.poll();
                                c.renderNow();
                            })));
                })
                .open(player);
    }

    /** Watching a domain object instead of mirroring it into a state. */
    static void watchingAnObject(Player player, Arena arena) {
        Gui.of(6)
                .addComponent(c -> {
                    State<ArenaSnapshot> snapshot = c.watch(arena::snapshot);
                    c.render(slots -> slots.icon(22, arenaIcon(snapshot.get())));
                })
                .addComponent(c -> {
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
                .addComponent(c -> {
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
                .addRegion(new Region("storage", Slots.box(2, 2, 2, 8),
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

    /** Page buttons of the plugin's own, drawn only while there is somewhere to go. */
    static void memberListWithItsOwnArrows(Player player, List<String> members) {
        Pager pager = new Pager();

        Gui<LayoutBase> gui = Gui.of(6)
                .title(() -> "§9Membros - " + pager.getTotalEntries() + " no total");
        gui.list(members)
                .pager(pager)
                .into(Slots.box(2, 2, 5, 8))
                .render((member, icon) -> icon
                        .from(FCItemFactory.from(Material.PAPER))
                        .displayName("§a" + member));
        //the buttons remember the pager, so turning a page redraws them along with the list
        gui.addComponent(component -> {
            component.remember(pager);
            component.render(slots -> {
                if (pager.hasPrevious()) {
                    slots.icon(Slots.at(6, 2), DefaultIcons.previousPage()
                            .displayName("§7Primeira página")
                            .onClick(ctx -> pager.first()));
                    slots.icon(Slots.at(6, 3), DefaultIcons.previousPage()
                            .onClick(ctx -> pager.previous()));
                }
                if (pager.hasNext()) {
                    slots.icon(Slots.at(6, 7), DefaultIcons.nextPage()
                            .onClick(ctx -> pager.next()));
                }
            });
        });
        gui.open(player);
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

        gui.list(() -> lots(tab.get(), sorting.get()))
                .dependsOn(tab, sorting)
                .into(l -> l.LOT)
                .pagedBy(l -> l.PREVIOUS, l -> l.NEXT)
                .render((lot, icon) -> icon.displayName("§a" + lot));

        CycleBinder<Sorting> sort = gui.icon(l -> l.SORT)
                .cycle(Sorting.class, sorting)
                .onCycle(chosen -> player.sendMessage("§7" + chosen));
        //the cycle hands its icon back, so the icon keeps being configured after the walk is declared
        sort.getBinder().every(20);
        sort.open(player);
    }

    // ---- a screen about one player, opened by another, with navigation and a chat prompt ----

    /** Whatever the plugin's own player data looks like. The screen only needs it to be one. */
    public interface ShopPlayerData extends IPlayerData {

        double getBalance();

    }

    @GuiLayout(title = "§9Loja de §f%playerdata_name% §7- %category_name%", rows = 6, integrateToPAPI = true)
    public static class ShopLayout extends LayoutBase {

        @IconData(slot = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25})
        public Icon PRODUCT = FCItemFactory.from(Material.PAPER)
                .displayName("§a%product_name%")
                .lore("§7Preço: §6$%product_price%", "§7Vendedor: §f%playerdata_name%")
                .asIcon();

        @IconData(slot = {45})
        public Icon PREVIOUS = DefaultIcons.previousPage();

        @IconData(slot = {53})
        public Icon NEXT = DefaultIcons.nextPage();

        @IconData(slot = {49})
        public Icon BACK = DefaultIcons.back();

    }

    @GuiLayout(title = "§9%product_name%", rows = 3)
    public static class ProductLayout extends LayoutBase {

        @IconData(slot = {11})
        public Icon TYPE_AMOUNT = FCItemFactory.from(Material.PAPER).displayName("§eDigitar quantidade").asIcon();

        @IconData(slot = {15})
        public Icon BUY = FCItemFactory.from(Material.EMERALD).displayName("§aComprar").asIcon();

        @IconData(slot = {22})
        public Icon BACK = DefaultIcons.back();

    }

    private static final RegexReplacer<Category> CATEGORY = new RegexReplacer<Category>()
            .addParser("category_name", Category::getName);

    private static final RegexReplacer<Product> PRODUCT = new RegexReplacer<Product>()
            .addParser("product_name", Product::getName)
            .addParser("product_price", Product::getPrice);

    /** The viewer is whoever has it open; the player data is whoever it is about. Not the same person. */
    public static class ShopGui extends LayoutGui<ShopPlayerData, ShopLayout> {

        public ShopGui(ShopPlayerData subject, Category category) {
            super(Layouts.of(ShopLayout.class), subject);
            addReplacer(CATEGORY, category);

            icon(l -> l.BACK).onClick(ClickContext::back);

            list(category::getProducts)
                    .into(l -> l.PRODUCT)
                    .pagedBy(l -> l.PREVIOUS, l -> l.NEXT)
                    .render((product, icon) -> icon
                            .addScope(PRODUCT, product)
                            .onClick(ctx -> ctx.open(new ProductGui(getPlayerData(), product))));
        }

    }

    public static class ProductGui extends LayoutGui<ShopPlayerData, ProductLayout> {

        private final Product product;
        private final MutableState<Integer> amount = State.of(1);

        public ProductGui(ShopPlayerData subject, Product product) {
            super(Layouts.of(ProductLayout.class), subject);
            this.product = product;
            addReplacer(PRODUCT.compound(product));

            icon(l -> l.BACK).onClick(ClickContext::back);

            icon(l -> l.TYPE_AMOUNT).onClick(ctx -> ctx.askOnChat(
                    ChatPrompt.of("§eDigite a quantidade (1-" + product.getStock() + "), ou 'cancelar':")
                            .parse(input -> {
                                int typed = Integer.parseInt(input);
                                if (typed < 1 || typed > product.getStock()) {
                                    throw new IllegalArgumentException("§cEntre 1 e " + product.getStock());
                                }
                                return typed;
                            })
                            .timeout(Duration.ofSeconds(45))
                            .onTimeout(view -> view.getViewer().sendMessage("§cTempo esgotado."))
                            .onQuit(() -> note("desistiu no meio"))
                            .cancelWord("cancelar"))
                    .thenAccept(amount::set));

            icon(l -> l.BUY).onClick(this::buy);
        }

        private void buy(ClickContext ctx) {
            ctx.open(ConfirmGui.of("§eComprar §f" + amount.get() + "x " + product.getName() + "§e?")
                            .display(product.getDisplay())
                            .dangerous())
                    .thenAccept(yes -> {
                        if (!yes) {
                            return;
                        }
                        ctx.getViewer().sendMessage("§aCompra realizada.");
                        ctx.back();          //back to the shop, on the page and category it was left on
                    });
        }

    }

    /** Opening it for somebody else is the whole reason viewer and player data are two things. */
    static void openForStaff(Player staff, ShopPlayerData subject, Category category) {
        new ShopGui(subject, category).open(staff);
    }

    /** The short form of the prompt, when the defaults are enough. */
    static void askTheShortWay(ClickContext ctx) {
        ctx.askOnChat("§eQual o motivo?", reason -> reason, reason -> note(reason));
    }

    // ---- a screen of one's own that answers with a value ----

    public enum Answer {KEEP, DROP}

    public static class KeepOrDropGui extends ResultGui<Answer, LayoutBase> {

        public KeepOrDropGui() {
            super(GuiType.CHEST, 3, null);
            title("§9Guardar ou descartar?");
            icon(11, FCItemFactory.from(Material.CHEST)
                    .displayName("§aGuardar")
                    .onClick(ctx -> ctx.back(Answer.KEEP)));
            icon(15, FCItemFactory.from(Material.BUCKET)
                    .displayName("§cDescartar")
                    .onClick(ctx -> ctx.back(Answer.DROP)));
        }

    }

    /** The answer arrives typed, because the screen said what it answers with. */
    static void askAndAct(ClickContext ctx) {
        ctx.open(new KeepOrDropGui()).thenAccept(answer -> {
            if (answer == Answer.DROP) {
                note("descartado");
            }
        });
    }

    /** Swapping the current screen out: back() still goes to whatever was under this one. */
    static void swapInPlace(ClickContext ctx) {
        ctx.replace(new KeepOrDropGui());
    }

    // ---- a screen that is about a player AND answers a value ----

    public enum Punishment {BAN, MUTE}

    /** The two things at once. answer(ctx, ...) is back(value) with the compiler checking the value. */
    public static class PunishGui extends LayoutResultGui<Punishment, ShopPlayerData, ProductLayout> {

        public PunishGui(ShopPlayerData accused) {
            super(Layouts.of(ProductLayout.class), accused);

            icon(l -> l.BUY).onClick(ctx -> confirm(ctx, Punishment.BAN));
            icon(l -> l.BACK).onClick(ClickContext::back);
        }

        private void confirm(ClickContext ctx, Punishment kind) {
            ctx.open(ConfirmGui.of("§e" + kind + " em " + getPlayerData().getName() + "§e?").dangerous())
                    .thenAccept(yes -> {
                        if (yes) {
                            answer(ctx, kind);
                        }
                    });
        }

    }

    /** Opened from a profile screen, and the choice comes back typed. */
    static void punishFromProfile(ClickContext ctx, ShopPlayerData accused) {
        ctx.open(new PunishGui(accused)).thenAccept(kind -> note("aplicou " + kind));
    }

    /** The same screen, another subject: no new Gui, so the page and the filter stay where they are. */
    public static class ReportQueueGui extends LayoutGui<ShopPlayerData, ProductLayout> {

        private final Queue<ShopPlayerData> queue;

        public ReportQueueGui(ShopPlayerData first, Queue<ShopPlayerData> queue) {
            super(Layouts.of(ProductLayout.class), first);
            this.queue = queue;

            icon(l -> l.BACK).onClick(ctx -> {
                setPlayerData(queue.poll());
                ctx.refresh();
            });
        }

    }

    // ---- the one screen where the player really takes items out and puts items in ----

    /** Whatever the plugin keeps a backpack in: a plain section with a {@code GenericInventory} in it. */
    public static class BackpackSection extends PDSection {

        private GenericInventory contents = new GenericInventory();
        private int size = 27;

        public BackpackSection() {                            //Jackson

        }

        public GenericInventory getContents() {
            return contents;
        }

        public int getSize() {
            return size;
        }

        public void setSize(int size) {
            this.size = size;
            markDirty();
        }

    }

    @GuiLayout(title = "§9Mochila de §f%playerdata_name%", rows = 6)
    public static class BackpackLayout extends LayoutBase {

        /** The editable area. The admin resizes the backpack by moving these slots. */
        @IconData(slot = {10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34})
        public Icon AREA = Icon.empty();                      //no item: it is an area, not a button

        @IconData(slot = {49})
        public Icon CLOSE = DefaultIcons.back();

        @IconData(slot = {45})
        public Icon HELP = FCItemFactory.from(Material.PAPER)
                .displayName("§eMochila")
                .lore("§7Arraste itens para dentro e para fora.",
                        "§7Tudo é salvo automaticamente.")
                .asIcon();

        @IconData(slot = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35,
                36, 37, 38, 39, 40, 41, 42, 43, 44, 46, 47, 48, 50, 51, 52, 53}, background = true)
        public Icon BACKGROUND = EnumStainedGlassPane.BLUE.asIcon();

    }

    /** No listener, no {@code InventoryHolder}, no close handler saving by hand. */
    public static class BackpackGui extends LayoutGui<IPlayerData, BackpackLayout> {

        public BackpackGui(IPlayerData data, BackpackSection backpack) {
            super(Layouts.of(BackpackLayout.class), data);

            icon(l -> l.CLOSE).onClick(ClickContext::close);

            storage(l -> l.AREA)
                    .backedBy(backpack.getContents())
                    .policy(ClickPolicy.builder()
                            .allowTake()
                            .allowPlace()
                            .allowSwap()
                            .allowDrag()
                            .build())
                    .denyPlace(item -> isBlacklisted(item))
                    .onChange(ctx -> backpack.markDirty());
        }

    }

    /** Opening it: the section is read first, and the screen is built on the answer. */
    static void openBackpack(Player player) {
        PlayerController.getPlayerData(player.getUniqueId())
                .thenCompose(data -> data.getPDSection(BackpackSection.class)
                        .thenApply(backpack -> new BackpackGui(data, backpack)))
                .thenAccept(gui -> gui.open(player));
    }

    // ---- the same area over the store that answers back ----

    /** A {@link StoredInventory} is a field like any other: the registered codec writes the envelope. */
    public static class VaultSection extends PDSection {

        private StoredInventory contents = new StoredInventory(27);

        public VaultSection() {                               //Jackson

        }

        public StoredInventory getContents() {
            return contents;
        }

    }

    @GuiLayout(title = "§9Cofre", rows = 6)
    public static class VaultLayout extends LayoutBase {

        @IconData(slot = {9, 10, 11, 12, 13, 14, 15, 16, 17,
                18, 19, 20, 21, 22, 23, 24, 25, 26,
                27, 28, 29, 30, 31, 32, 33, 34, 35})
        public Icon AREA = Icon.empty();

        @IconData(slot = {49})
        public Icon CLOSE = DefaultIcons.back();

    }

    /**
     * What the store adds over a plain one: a slot that holds one of anything, a change refused before
     * it happens, and one told afterwards - which is where saving belongs, because it only ever runs for
     * a change that really happened.
     */
    public static class VaultGui extends LayoutGui<IPlayerData, VaultLayout> {

        public VaultGui(IPlayerData data, VaultSection vault) {
            super(Layouts.of(VaultLayout.class), data);

            StoredInventory contents = vault.getContents();
            contents.setMaxStackSize(0, 1);                   //the display slot: one of anything
            contents.onPreUpdate(event -> event.setCancelled(isBlacklisted(event.getNewItem())));
            contents.onPostUpdate(event -> vault.markDirty());

            icon(l -> l.CLOSE).onClick(ClickContext::close);
            storage(l -> l.AREA).backedBy(contents).policy(ClickPolicy.EDIT_ALL);
        }

    }

    @GuiLayout(title = "§9Editando kit", rows = 6)
    public static class KitLayout extends LayoutBase {

        @IconData(slot = {10, 11, 12, 13, 14, 15, 16})
        public Icon AREA = Icon.empty();

        @IconData(slot = {48})
        public Icon SAVE = FCItemFactory.from(Material.EMERALD).displayName("§aSalvar").asIcon();

        @IconData(slot = {50})
        public Icon CANCEL = FCItemFactory.from(Material.BARRIER).displayName("§cCancelar").asIcon();

    }

    /** What the admin builds becomes config, so closing without saving has to discard instead of save. */
    static void editKit(Player admin, Kit kit) {
        Gui<KitLayout> editor = Gui.of(KitLayout.class);

        editor.storage(l -> l.AREA)
                .backedBy(kit.getContents())
                .policy(ClickPolicy.EDIT_ALL)
                .onChange(ctx -> note("mexeu no kit"));       //nothing is written per click

        editor.icon(l -> l.SAVE).onClick(ctx -> {
            kit.save();
            ctx.getViewer().sendMessage("§aKit §f" + kit.getName() + " §asalvo.");
            ctx.close();
        });

        editor.icon(l -> l.CANCEL).onClick(ctx -> {
            kit.reload();
            ctx.close();
        });

        //an editor that cannot tell "emptied on purpose" from "closed without saving" erases the kit
        //whenever the admin walks away from an empty screen
        editor.onClose(ctx -> {
            if (!ctx.wasClosedBy(l -> l.SAVE)) {
                kit.reload();
            }
        });

        editor.open(admin);
    }

    public enum DepositState {DEFAULT, EMPTY}

    @GuiLayout(title = "§9Depósito", rows = 6)
    public static class DepositLayout extends LayoutBase {

        @IconData(slot = {10, 11, 12, 13})
        public Icon INPUT = Icon.empty();

        @IconData(slot = {28, 29, 30, 31})
        public Icon STORED = Icon.empty();

        @IconData(slot = {49})
        public Icon PROCESS = FCItemFactory.from(Material.FURNACE)
                .displayName("§aProcessar")
                .asIcon()
                .addState(DepositState.EMPTY, FCItemFactory.from(Material.BARRIER)
                        .displayName("§7Nada para processar"));

    }

    /** Three areas, three policies, one screen - and not a single {@code if} in a handler. */
    public static class DepositGui extends LayoutGui<ShopPlayerData, DepositLayout> {

        public DepositGui(ShopPlayerData data, Deposit deposit) {
            super(Layouts.of(DepositLayout.class), data);

            storage(l -> l.INPUT)                             //takes items in, never hands them back
                    .backedBy(deposit.getInput())
                    .policy(ClickPolicy.builder().allowPlace().denyTake().allowDrag().build())
                    .onChange(ctx -> deposit.markDirty());

            storage(l -> l.STORED)                            //read only
                    .backedBy(deposit.getStored())
                    .policy(ClickPolicy.DENY_ALL);

            icon(l -> l.PROCESS)
                    .states(DepositState.class,
                            () -> deposit.isEmpty() ? DepositState.EMPTY : DepositState.DEFAULT)
                    .onClick(ctx -> {
                        deposit.process();
                        ctx.refresh();
                    });
        }

    }

    // ---- stand-ins for whatever the plugin actually has ----

    private static boolean isBlacklisted(ItemStack item) {
        return false;
    }

    private interface Kit {

        String getName();

        GenericInventory getContents();

        void save();

        void reload();

    }

    private interface Deposit {

        GenericInventory getInput();

        GenericInventory getStored();

        boolean isEmpty();

        void process();

        void markDirty();

    }

    private static void note(String message) {

    }

    private interface Category {

        String getName();

        List<Product> getProducts();

    }

    private interface Product {

        String getName();

        double getPrice();

        int getStock();

        ItemStack getDisplay();

    }


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
