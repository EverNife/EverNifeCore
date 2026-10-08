package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.minecraft.gui.ConfirmGui;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.component.Pager;
import br.com.finalcraft.evernifecore.minecraft.gui.icons.EnumStainedGlassPane;
import br.com.finalcraft.evernifecore.minecraft.gui.icons.EnumWool;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.GuiLayout;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.IconData;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.model.ClickPolicy;
import br.com.finalcraft.evernifecore.minecraft.gui.model.GuiType;
import br.com.finalcraft.evernifecore.minecraft.gui.model.Slots;
import br.com.finalcraft.evernifecore.minecraft.gui.view.BukkitGuiScheduler;
import br.com.finalcraft.evernifecore.minecraft.gui.view.CloseReason;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiNavigation;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiViews;
import br.com.finalcraft.evernifecore.minecraft.gui.view.prompt.ChatPrompt;
import br.com.finalcraft.evernifecore.minecraft.gui.view.prompt.ChatPromptChannel;
import br.com.finalcraft.evernifecore.minecraft.gui.view.prompt.PromptResult;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import br.com.finalcraft.evernifecore.minecraft.itemstack.FCItemFactory;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * The gui framework against a real window: every sample screen opens for the player, is registered as the screen
 * they look at, carries the title it was asked for, does the one thing it exists to show (a second screen pushed and
 * popped, a chat question that times out and gives the screen back, a page turned) and closes exactly once, as
 * {@link CloseReason#PLAYER_CLOSED} - the reason a close of the window is reported as on every build.
 *
 * <p>{@code /ecc gui <screen> <player>} opens the same screens and leaves them open, so the window can be read from
 * outside the server - by the server's own inventory and by the client.</p>
 */
public final class GuiSuite implements ConformanceSuite {

    private static final long TIMEOUT_MILLIS = 15_000L;
    /** Window titles longer than this are cut up to 1.8 ({@code GuiViews}); the server throws on them otherwise. */
    private static final int LEGACY_TITLE_LIMIT = 32;
    private static final MCDetailedVersion LAST_LEGACY_TITLE = MCDetailedVersion.v1_8_R3;
    private static final String LONG_TITLE = "ecc title-long 40 characters of title!!";

    private static final Map<String, Screen> SCREENS = new LinkedHashMap<>();

    static {
        screen("basic", 27, "", probe -> named(Gui.of(3), "basic", probe)
                .icon(11, Icon.of(FCItemFactory.from(Material.DIAMOND).displayName("§bDiamond").lore("§7first line", "§esecond line").build()))
                .icon(13, EnumWool.RED.asFactory().displayName("§cRed wool").lore("§7coloured by data").asIcon())
                .icon(15, Icon.of(FCItemFactory.from(Material.PAPER).displayName("§aPaper").build())));
        screen("hopper", GuiType.HOPPER.getDefaultSize(), "", probe -> named(Gui.of(GuiType.HOPPER), "hopper", probe)
                .icon(2, Icon.of(new ItemStack(Material.EMERALD))));
        screen("dispenser", GuiType.DISPENSER.getDefaultSize(), "", probe -> named(Gui.of(GuiType.DISPENSER), "dispenser", probe)
                .icon(4, Icon.of(new ItemStack(Material.EMERALD))));
        screen("workbench", GuiType.WORKBENCH.getDefaultSize(), "", probe -> named(Gui.of(GuiType.WORKBENCH), "workbench", probe)
                .icon(5, Icon.of(new ItemStack(Material.EMERALD))));
        screen("nav", 27, "", probe -> named(Gui.of(3), "nav", probe)
                .icon(13, Icon.of(new ItemStack(Material.CHEST)).onClick(click -> click.open(navSecond()))));
        screen("storage", 27, "", probe -> {
            Gui<LayoutBase> gui = named(Gui.of(3), "storage", probe)
                    .icon(Slots.row(1), Icon.of(new ItemStack(Material.STONE)));
            GenericInventory backing = new GenericInventory();
            gui.storage(Slots.box(2, 1, 3, 9)).backedBy(backing).policy(ClickPolicy.EDIT_ALL);
            return gui;
        });
        screen("prompt", 27, "", probe -> named(Gui.of(3), "prompt", probe)
                .icon(13, Icon.of(new ItemStack(Material.PAPER)).onClick(click -> click.askOnChat(question()))));
        screen("pager", 27, "", probe -> {
            Gui<LayoutBase> gui = named(Gui.of(3), "pager", probe);
            List<Integer> entries = new ArrayList<>();
            for (int i = 1; i <= 30; i++) entries.add(i);
            probe.pager = new Pager();
            gui.list(entries)
                    .pager(probe.pager)
                    .into(Slots.box(1, 1, 2, 9))
                    .pagedBy(Slots.of(18), Slots.of(26))
                    .render((entry, icon) -> icon.from(new ItemStack(Material.PAPER, entry)));
            return gui;
        });
        screen("title-long", 27, LONG_TITLE, probe -> Gui.of(3).title(LONG_TITLE)
                .onClose(close -> probe.closes.add(close.getReason()))
                .icon(13, Icon.of(new ItemStack(Material.NAME_TAG))));
        screen("layout", 27, SeededLayout.TITLE, probe -> Gui.of(SeededLayout.class)
                .onClose(close -> probe.closes.add(close.getReason())));
        screen("confirm", 27, "§8ecc confirm?", probe -> ConfirmGui.of("§8ecc confirm?")
                .onClose(close -> probe.closes.add(close.getReason())));
    }

    @Override
    public String getId() {
        return "gui";
    }

    /** The screens {@code /ecc gui} can open, in the order the suite checks them. */
    public static List<String> getScreenIds() {
        return new ArrayList<>(SCREENS.keySet());
    }

    /** Opens {@code screen} for {@code player} and leaves it open; {@code null} when no screen has that id. */
    public static CompletableFuture<GuiView> open(String screen, Player player) {
        Screen found = SCREENS.get(screen);
        return found == null ? null : found.build.apply(new Probe()).open(player);
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        List<ConformanceCase> cases = new ArrayList<>();
        for (Screen screen : SCREENS.values()) {
            cases.add(PlayerCases.later(screen.id, ctx, player -> PlayerCases.within(check(screen, ctx, player), TIMEOUT_MILLIS)));
        }
        return cases;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The check every screen goes through
    // -----------------------------------------------------------------------------------------------------------------

    private static CompletableFuture<Verdict> check(Screen screen, ConformanceContext ctx, Player player) {
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        List<String> problems = new ArrayList<>();
        Probe probe = new Probe();
        List<CloseReason> closes = probe.closes;

        //a screen left open by an earlier /ecc gui would be displaced by this one and skew the count
        GuiView stale = GuiViews.getOpenView(player);
        if (stale != null) stale.close();

        after(1, answer, () -> {
            int before = GuiViews.getOpenCount();
            screen.build.apply(probe).open(player).whenComplete((view, refused) -> after(0, answer, () -> {
                if (refused != null) {
                    answer.complete(Verdict.fail("open refused: " + Verdict.describe(refused)));
                    return;
                }
                expect(problems, GuiViews.getOpenView(player) == view, "getOpenView is not the opened view");
                expect(problems, GuiViews.getOpenCount() == before + 1, "open count " + before + " -> " + GuiViews.getOpenCount());
                expect(problems, view.getCurrentTitle().equals(screen.title), "title '" + view.getCurrentTitle() + "'");
                expect(problems, view.getGeometry().getSize() == screen.size, "window size " + view.getGeometry().getSize());
                CompletableFuture<String> exercised = exercise(screen.id, ctx, player, view, probe, problems);
                exercised.whenComplete((detail, threw) -> after(1, answer, () -> {
                    if (threw != null) problems.add(Verdict.describe(threw));
                    GuiView last = GuiViews.getOpenView(player);
                    if (last != null) last.close();
                    after(1, answer, () -> {
                        expect(problems, GuiViews.getOpenView(player) == null, "still registered after close");
                        expect(problems, GuiViews.getOpenCount() == before, "open count after close " + GuiViews.getOpenCount());
                        expect(problems, closes.equals(Collections.singletonList(CloseReason.PLAYER_CLOSED)), "onClose saw " + closes);
                        String summary = "title='" + screen.title + "' sentTitle='" + sentTitle(screen.title) + "' size=" + screen.size
                                + (detail == null || detail.isEmpty() ? "" : " " + detail) + " closes=" + closes;
                        answer.complete(problems.isEmpty() ? Verdict.pass(summary) : Verdict.fail(String.join("; ", problems) + " | " + summary));
                    });
                }));
            }));
        });
        return answer;
    }

    /** What each screen exists to show, beyond opening and closing. Answers the detail of the case. */
    private static CompletableFuture<String> exercise(String id, ConformanceContext ctx, Player player, GuiView view, Probe probe,
                                                      List<String> problems) {
        switch (id) {
            case "nav": {
                CompletableFuture<String> done = new CompletableFuture<>();
                CompletableFuture<Object> answered = GuiNavigation.open(view, navSecond());
                after(2, done, () -> {
                    GuiView top = GuiViews.getOpenView(player);
                    boolean pushed = top != null && top != view && NAV_SECOND_TITLE.equals(top.getCurrentTitle());
                    expect(problems, pushed, "second screen not on top: " + (top == null ? null : top.getCurrentTitle()));
                    expect(problems, view.isSuspended(), "first screen was not set aside");
                    if (!pushed) {
                        done.complete("");
                        return;
                    }
                    GuiNavigation.back(top, "popped");
                    after(2, done, () -> {
                        expect(problems, "popped".equals(answered.getNow(null)), "the opener got " + answered);
                        expect(problems, GuiViews.getOpenView(player) == view && !view.isSuspended(), "first screen did not come back");
                        done.complete("pushed and popped");
                    });
                });
                return done;
            }
            case "prompt": {
                CompletableFuture<String> done = new CompletableFuture<>();
                CompletableFuture<PromptResult<String>> result = ChatPromptChannel.get().ask(view,
                        ChatPrompt.of("§e[ECoreConformance] gui/prompt - no answer needed, it times out").timeout(Duration.ofSeconds(2)));
                after(2, done, () -> expect(problems, view.isSuspended(), "the screen was not set aside while asking"));
                result.whenComplete((asked, threw) -> after(2, done, () -> {
                    expect(problems, asked != null && asked.getKind() == PromptResult.Kind.TIMEOUT, "prompt ended " + asked);
                    expect(problems, GuiViews.getOpenView(player) == view && !view.isSuspended(), "the screen did not come back");
                    done.complete("prompt=" + (asked == null ? null : asked.getKind()));
                }));
                return done;
            }
            case "storage":
                expect(problems, view.hasStorage() && view.getStorages().size() == 1, "storages " + view.getStorages().size());
                return CompletableFuture.completedFuture("storages=" + view.getStorages().size());
            case "pager": {
                Pager pager = probe.pager;
                expect(problems, pager.getTotalEntries() == 30 && pager.getPageSize() == 18 && pager.getTotalPages() == 2,
                        "pager " + pager);
                pager.next();
                expect(problems, pager.getPage() == 2, "next() left the page at " + pager.getPage());
                return CompletableFuture.completedFuture("pager=" + pager);
            }
            case "layout": {
                File seeded = new File(ctx.getDataFolder(), "guis/" + SeededLayout.class.getSimpleName() + ".yml");
                expect(problems, seeded.isFile(), "the yml was not seeded at " + seeded);
                expect(problems, view.getIconAt(13) != null, "the layout icon is not on slot 13");
                return CompletableFuture.completedFuture("seeded=" + seeded.isFile());
            }
            case "confirm":
                expect(problems, view.getIconAt(11) != null && view.getIconAt(15) != null, "confirm/deny buttons missing");
                return CompletableFuture.completedFuture("");
            default:
                return CompletableFuture.completedFuture("");
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Building blocks
    // -----------------------------------------------------------------------------------------------------------------

    private static final String NAV_SECOND_TITLE = "§8ecc nav second";

    private static Gui<LayoutBase> navSecond() {
        return Gui.of(3).title(NAV_SECOND_TITLE).icon(13, Icon.of(new ItemStack(Material.DIAMOND)));
    }

    private static ChatPrompt<String> question() {
        return ChatPrompt.of("§e[ECoreConformance] type anything, or let it time out").timeout(Duration.ofSeconds(5));
    }

    private static Gui<LayoutBase> named(Gui<LayoutBase> gui, String name, Probe probe) {
        return gui.title("§8ecc " + name).onClose(close -> probe.closes.add(close.getReason()));
    }

    private static void screen(String id, int size, String title, ScreenBuilder build) {
        SCREENS.put(id, new Screen(id, size, title.isEmpty() ? "§8ecc " + id : title, build));
    }

    /** The title the window packet carries: what the framework asked for, cut where the protocol cannot hold it. */
    private static String sentTitle(String title) {
        return title.length() > LEGACY_TITLE_LIMIT && MCVersion.isLowerEquals(LAST_LEGACY_TITLE)
                ? title.substring(0, LEGACY_TITLE_LIMIT) : title;
    }

    private static void expect(List<String> problems, boolean holds, String otherwise) {
        if (!holds) problems.add(otherwise);
    }

    interface Step {
        void run() throws Exception;
    }

    /** Runs {@code step} on the main thread {@code ticks} from now; a throw fails {@code guard} instead of vanishing. */
    private static void after(long ticks, CompletableFuture<?> guard, Step step) {
        Runnable guarded = () -> {
            if (guard.isDone()) return;
            try {
                step.run();
            } catch (Throwable thrown) {
                guard.completeExceptionally(thrown);
            }
        };
        if (ticks <= 0) {
            GuiViews.onMainThread(guarded);
        } else {
            BukkitGuiScheduler.INSTANCE.later(ticks, guarded);
        }
    }

    interface ScreenBuilder {
        Gui<?> apply(Probe probe);
    }

    /** What a check reads back from the screen it built: every close it saw, and the pager when it has one. */
    private static final class Probe {
        private final List<CloseReason> closes = Collections.synchronizedList(new ArrayList<>());
        private Pager pager;
    }

    private static final class Screen {
        private final String id;
        private final int size;
        private final String title;
        private final ScreenBuilder build;

        private Screen(String id, int size, String title, ScreenBuilder build) {
            this.id = id;
            this.size = size;
            this.title = title;
            this.build = build;
        }
    }

    /** A level-2 screen: its file is seeded under {@code guis/} the first time it is read. */
    @GuiLayout(title = SeededLayout.TITLE, rows = 3)
    public static class SeededLayout extends LayoutBase {

        static final String TITLE = "§8ecc layout";

        @IconData(slot = {13})
        public Icon CENTER = Icon.of(new ItemStack(Material.EMERALD));

        @IconData(slot = {0, 8, 18, 26}, background = true)
        public Icon CORNER = EnumStainedGlassPane.GRAY.asFactory().asIcon();
    }
}
