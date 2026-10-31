package br.com.finalcraft.evernifecore.conformance.minecraft.suite;

import br.com.finalcraft.evernifecore.action.EventSource;
import br.com.finalcraft.evernifecore.action.EventSourceUsage;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.logic.Condition;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Conditions;
import br.com.finalcraft.evernifecore.logic.ExecutionContext;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryOrigin;
import br.com.finalcraft.evernifecore.logic.delivery.DeliveryPolicy;
import br.com.finalcraft.evernifecore.minecraft.api.MinecraftFPlayer;
import br.com.finalcraft.evernifecore.minecraft.inventory.GenericInventory;
import br.com.finalcraft.ecnmsutils.NMSUtils;
import br.com.finalcraft.evernifecore.minecraft.screen.BukkitScreenScheduler;
import br.com.finalcraft.evernifecore.minecraft.screen.BukkitScreenSurface;
import br.com.finalcraft.evernifecore.minecraft.screen.ChestKind;
import br.com.finalcraft.evernifecore.minecraft.screen.ChestScreenRenderer;
import br.com.finalcraft.evernifecore.minecraft.version.MCDetailedVersion;
import br.com.finalcraft.evernifecore.minecraft.version.MCVersion;
import br.com.finalcraft.evernifecore.ranking.RankedList;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.ClickPolicy;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.ConfirmRequest;
import br.com.finalcraft.evernifecore.screen.ConfirmScreen;
import br.com.finalcraft.evernifecore.screen.DefaultIcons;
import br.com.finalcraft.evernifecore.screen.DeliveriesScreen;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.IconData;
import br.com.finalcraft.evernifecore.screen.Layout;
import br.com.finalcraft.evernifecore.screen.MutableState;
import br.com.finalcraft.evernifecore.screen.Pager;
import br.com.finalcraft.evernifecore.screen.Paint;
import br.com.finalcraft.evernifecore.screen.RankingRequest;
import br.com.finalcraft.evernifecore.screen.RankingScreen;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenLayout;
import br.com.finalcraft.evernifecore.screen.ScreenNavigation;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.Slots;
import br.com.finalcraft.evernifecore.screen.prompt.PromptKind;
import br.com.finalcraft.evernifecore.screen.prompt.PromptRegistry;
import br.com.finalcraft.evernifecore.screen.prompt.PromptResult;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import br.com.finalcraft.evernifecore.time.Window;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;

/**
 * The screen framework against a real chest window: every sample screen opens for the player, is registered as the
 * screen they look at, carries the title it was asked for, does the one thing it exists to show (a second screen
 * pushed and popped, a chat question that times out and gives the screen back, a page turned, a delivery claimed, a
 * choice the player may not pick drawn disabled, a source held only while the screen is open) and closes exactly
 * once, as {@link CloseReason#PLAYER_CLOSED} - the reason a close of the window is reported as on every build.
 *
 * <p>{@code /ecc screen <screen> <player>} opens the same screens and leaves them open, so the window can be read from
 * outside the server - by the server's own inventory and by the client.</p>
 */
public final class ScreenSuite implements ConformanceSuite {

    private static final long TIMEOUT_MILLIS = 15_000L;
    private static final long UNTIL_MILLIS = 5_000L;
    /** Window titles longer than this are cut up to 1.8 by the chest renderer; the server throws on them otherwise. */
    private static final int LEGACY_TITLE_LIMIT = 32;
    private static final MCDetailedVersion LAST_LEGACY_TITLE = MCDetailedVersion.v1_8_R3;
    private static final String LONG_TITLE = "ecc title-long 40 characters of title!!";
    /** Registered with a FALSE default, so not even an operator - the bot is one - holds it. */
    private static final String NOBODY_NODE = "ecoreconformance.screen.nobody";
    private static final ObjectMapper JSON = new ObjectMapper();

    /** Always passes; it only declares the source after which it would be asked again. */
    private static final Condition AFTER_A_PICKUP = new Condition() {
        @Override
        public ConditionResult test(ExecutionContext ctx) {
            return ConditionResult.pass();
        }

        @Override
        public Set<EventSource> getNeeds() {
            return Collections.singleton(EventSource.ITEM_PICKUP);
        }
    };

    private static final Map<String, Sample> SCREENS = new LinkedHashMap<>();

    static {
        screen("basic", 27, "", probe -> {
            Screen<Layout> screen = named(Screen.of(3), "basic", probe);
            screen.icon(11, Icon.of("minecraft:diamond").displayName("§bDiamond").lore("§7first line", "§esecond line"));
            screen.icon(13, Icon.of(DefaultIcons.wool(DefaultIcons.Color.RED)).displayName("§cRed wool").lore("§7coloured by data"));
            screen.icon(15, Icon.of("minecraft:paper").displayName("§aPaper"));
            return screen;
        });
        screen("hopper", ChestKind.HOPPER.getDefaultSize(), "", probe -> emerald(named(ChestKind.HOPPER.newScreen(), "hopper", probe), 2));
        screen("dispenser", ChestKind.DISPENSER.getDefaultSize(), "", probe -> emerald(named(ChestKind.DISPENSER.newScreen(), "dispenser", probe), 4));
        screen("workbench", ChestKind.WORKBENCH.getDefaultSize(), "", probe -> emerald(named(ChestKind.WORKBENCH.newScreen(), "workbench", probe), 5));
        screen("nav", 27, "", probe -> {
            Screen<Layout> screen = named(Screen.of(3), "nav", probe);
            screen.icon(13, Icon.of("minecraft:chest")).onClick(click -> click.open(navSecond()));
            return screen;
        });
        screen("storage", 27, "", probe -> {
            Screen<Layout> screen = named(Screen.of(3), "storage", probe);
            screen.icon(Slots.row(1), Icon.of("minecraft:stone"));
            screen.storage(Slots.box(2, 1, 3, 9)).backedBy(new GenericInventory()).policy(ClickPolicy.EDIT_ALL);
            return screen;
        });
        screen("prompt", 27, "", probe -> {
            Screen<Layout> screen = named(Screen.of(3), "prompt", probe);
            screen.icon(13, Icon.of("minecraft:paper")).onClick(click -> click.ask(question()));
            return screen;
        });
        screen("prompt-anvil", 27, "", probe -> {
            Screen<Layout> screen = named(Screen.of(3), "prompt-anvil", probe);
            screen.icon(13, Icon.of("minecraft:paper")).onClick(click -> click.ask(anvilQuestion(Duration.ofSeconds(60)))
                    .thenAccept(amount -> Bukkit.getLogger().info("[ECoreConformance] prompt-anvil answered " + amount)));
            return screen;
        });
        screen("pager", 27, "", probe -> {
            Screen<Layout> screen = named(Screen.of(3), "pager", probe);
            List<Integer> entries = new ArrayList<>();
            for (int i = 1; i <= 30; i++) entries.add(i);
            probe.pager = new Pager();
            screen.list(entries)
                    .pager(probe.pager)
                    .into(Slots.box(1, 1, 2, 9))
                    .pagedBy(Slots.of(18), Slots.of(26))
                    .render((entry, icon) -> icon.from(Icon.of("minecraft:paper").amount(entry)));
            return screen;
        });
        screen("title-long", 27, LONG_TITLE, probe -> {
            Screen<Layout> screen = Screen.of(3).title(LONG_TITLE).onClose(close -> probe.closes.add(close.getReason()));
            screen.icon(13, Icon.of("minecraft:name_tag"));
            return screen;
        });
        screen("layout", 27, SeededLayout.TITLE, probe -> Screen.of(SeededLayout.class)
                .onClose(close -> probe.closes.add(close.getReason())));
        screen("confirm", 27, "§8ecc confirm?", probe -> ConfirmScreen.of(ConfirmRequest.of("§8ecc confirm?"))
                .onClose(close -> probe.closes.add(close.getReason())));
        screen("deliveries", 36, "", probe -> named(DeliveriesScreen.of(), "deliveries", probe));
        screen("ranking", 36, "", probe -> named(RankingScreen.of(RankingRequest.of(threeRanked())), "ranking", probe));
        screen("input", 27, "", probe -> {
            Screen<InputLayout> screen = named(Screen.of(InputLayout.class), "input", probe);
            screen.input(l -> l.FIELD).onInput(PromptSpec.of("§e[ECoreConformance] screen/input - no answer needed, it times out")
                    .prefer(PromptKind.CHAT).timeout(Duration.ofSeconds(2)), (view, typed) -> probe.answers.add(typed));
            return screen;
        });
        screen("progress", 27, "", probe -> {
            Screen<ProgressLayout> screen = named(Screen.of(ProgressLayout.class), "progress", probe);
            screen.progress(l -> l.BAR, new MutableState<>(0.5d));
            return screen;
        });
        screen("choice", 27, "", probe -> {
            Screen<ChoiceLayout> screen = named(Screen.of(ChoiceLayout.class), "choice", probe);
            screen.choice(l -> l.ANYONE, FancyText.of("§aanyone may pick this"), Gate.open(), Collections.emptyList());
            screen.choice(l -> l.NOBODY, FancyText.of("§cnobody may pick this"), Gate.builder().require(nobodyHasIt()).build(),
                    Collections.emptyList());
            return screen;
        });
        screen("reactive", 27, "", probe -> {
            Screen<Layout> screen = named(Screen.of(3), "reactive", probe);
            screen.icon(13, Icon.of("minecraft:hopper")).dependsOn(AFTER_A_PICKUP);
            return screen;
        });
    }

    @Override
    public String getId() {
        return "screen";
    }

    /** The screens {@code /ecc screen} can open, in the order the suite checks them. */
    public static List<String> getScreenIds() {
        return new ArrayList<>(SCREENS.keySet());
    }

    /** Opens {@code screen} for {@code player} and leaves it open; {@code null} when no screen has that id. */
    public static CompletableFuture<ScreenView> open(String screen, Player player) {
        Sample found = SCREENS.get(screen);
        return found == null ? null : found.build.apply(new Probe()).open(viewer(player));
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        List<ConformanceCase> cases = new ArrayList<>();
        for (Sample screen : SCREENS.values()) {
            cases.add(PlayerCases.later(screen.id, ctx, player -> PlayerCases.within(check(screen, ctx, player), TIMEOUT_MILLIS)));
        }
        return cases;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  The check every screen goes through
    // -----------------------------------------------------------------------------------------------------------------

    private static CompletableFuture<Verdict> check(Sample screen, ConformanceContext ctx, Player player) {
        CompletableFuture<Verdict> answer = new CompletableFuture<>();
        List<String> problems = new ArrayList<>();
        Probe probe = new Probe();
        List<CloseReason> closes = probe.closes;
        boolean pickupHeldBefore = EventSourceUsage.get().isNeeded(EventSource.ITEM_PICKUP);

        //a screen left open by an earlier /ecc screen would be displaced by this one and skew the count
        ScreenView stale = Screens.getOpenView(player.getUniqueId());
        if (stale != null) stale.close();

        prepare(screen.id, player, probe).whenComplete((prepared, unprepared) -> after(1, answer, () -> {
            if (unprepared != null) {
                answer.complete(Verdict.fail("could not prepare the screen: " + Verdict.describe(unprepared)));
                return;
            }
            int before = Screens.getOpenCount();
            screen.build.apply(probe).open(viewer(player)).whenComplete((view, refused) -> after(0, answer, () -> {
                if (refused != null) {
                    answer.complete(Verdict.fail("open refused: " + Verdict.describe(refused)));
                    return;
                }
                expect(problems, Screens.getOpenView(player.getUniqueId()) == view, "getOpenView is not the opened view");
                expect(problems, Screens.getOpenCount() == before + 1, "open count " + before + " -> " + Screens.getOpenCount());
                expect(problems, view.getCurrentTitle().equals(screen.title), "title '" + view.getCurrentTitle() + "'");
                expect(problems, view.getGrid().getSize() == screen.size, "window size " + view.getGrid().getSize());
                CompletableFuture<String> exercised = exercise(screen.id, ctx, player, view, probe, problems);
                exercised.whenComplete((detail, threw) -> after(1, answer, () -> {
                    if (threw != null) problems.add(Verdict.describe(threw));
                    //the item-pickup listener this case switched on must leave now, not 3 minutes later under a suite that wants it off
                    Duration configuredIdle = EventSourceUsage.get().getIdleTime();
                    if (screen.id.equals("reactive")) EventSourceUsage.get().setIdleTime(Duration.ZERO);
                    ScreenView last = Screens.getOpenView(player.getUniqueId());
                    if (last != null) last.close();
                    after(1, answer, () -> {
                        EventSourceUsage.get().setIdleTime(configuredIdle);
                        expect(problems, Screens.getOpenView(player.getUniqueId()) == null, "still registered after close");
                        expect(problems, Screens.getOpenCount() == before, "open count after close " + Screens.getOpenCount());
                        expect(problems, closes.equals(Collections.singletonList(CloseReason.PLAYER_CLOSED)), "onClose saw " + closes);
                        if (screen.id.equals("reactive")) {
                            expect(problems, !EventSourceUsage.get().isNeeded(EventSource.ITEM_PICKUP), "item-pickup still needed after"
                                    + " the screen closed" + (pickupHeldBefore ? " - something else held it before the screen opened" : ""));
                        }
                        String summary = "title='" + screen.title + "' sentTitle='" + sentTitle(screen.title) + "' size=" + screen.size
                                + (detail == null || detail.isEmpty() ? "" : " " + detail) + " closes=" + closes;
                        answer.complete(problems.isEmpty() ? Verdict.pass(summary) : Verdict.fail(String.join("; ", problems) + " | " + summary));
                    });
                }));
            }));
        }));
        return answer;
    }

    /** What has to exist on the server before a screen opens; only the check prepares, {@code /ecc screen} does not. */
    private static CompletableFuture<?> prepare(String id, Player player, Probe probe) {
        if (!id.equals("deliveries")) {
            return CompletableFuture.completedFuture(null);
        }
        ObjectNode diamond = JSON.createObjectNode().put("type", "give-item").put("item", "minecraft:diamond").put("amount", 1);
        return Deliveries.get().enqueue(player.getUniqueId(), DeliveryOrigin.of("ECoreConformance", "screen", "screen/deliveries"),
                DeliveryPolicy.KEEP_SELF_CONTAINED, null, Collections.singletonList(diamond)).thenAccept(seeded -> probe.delivery = seeded.getId());
    }

    /** What each screen exists to show, beyond opening and closing. Answers the detail of the case. */
    private static CompletableFuture<String> exercise(String id, ConformanceContext ctx, Player player, ScreenView view, Probe probe,
                                                      List<String> problems) {
        switch (id) {
            case "nav": {
                CompletableFuture<String> done = new CompletableFuture<>();
                CompletableFuture<Object> answered = ScreenNavigation.open(view, navSecond());
                after(2, done, () -> {
                    ScreenView top = Screens.getOpenView(player.getUniqueId());
                    boolean pushed = top != null && top != view && NAV_SECOND_TITLE.equals(top.getCurrentTitle());
                    expect(problems, pushed, "second screen not on top: " + (top == null ? null : top.getCurrentTitle()));
                    expect(problems, view.isSuspended(), "first screen was not set aside");
                    if (!pushed) {
                        done.complete("");
                        return;
                    }
                    ScreenNavigation.back(top, "popped");
                    after(2, done, () -> {
                        expect(problems, "popped".equals(answered.getNow(null)), "the opener got " + answered);
                        expect(problems, Screens.getOpenView(player.getUniqueId()) == view && !view.isSuspended(), "first screen did not come back");
                        done.complete("pushed and popped");
                    });
                });
                return done;
            }
            case "prompt": {
                CompletableFuture<String> done = new CompletableFuture<>();
                CompletableFuture<PromptResult<String>> result = PromptRegistry.get().ask(view,
                        PromptSpec.of("§e[ECoreConformance] screen/prompt - no answer needed, it times out")
                                .prefer(PromptKind.CHAT).timeout(Duration.ofSeconds(2)));
                after(2, done, () -> expect(problems, view.isSuspended(), "the screen was not set aside while asking"));
                result.whenComplete((asked, threw) -> after(2, done, () -> {
                    expect(problems, asked != null && asked.getKind() == PromptResult.Kind.TIMEOUT, "prompt ended " + asked);
                    expect(problems, Screens.getOpenView(player.getUniqueId()) == view && !view.isSuspended(), "the screen did not come back");
                    done.complete("prompt=" + (asked == null ? null : asked.getKind()));
                }));
                return done;
            }
            case "prompt-anvil": {
                //the anvil where the library links on this server; chat where there is no NMS bridge at all
                boolean bridged = NMSUtils.isSupported();
                PromptKind expected = bridged ? PromptKind.ANVIL : PromptKind.CHAT;
                CompletableFuture<String> done = new CompletableFuture<>();
                int level = player.getLevel();
                ItemStack[] contents = copyOf(player.getInventory().getContents());
                CompletableFuture<PromptResult<Integer>> result = PromptRegistry.get().ask(view,
                        anvilQuestion(bridged ? Duration.ofSeconds(10) : Duration.ofSeconds(2)));
                after(2, done, () -> {
                    PromptKind kind = PromptRegistry.get().getPending(view);
                    String window = openWindowType(player);
                    String seen = "anvil=" + window + " kind=" + kind;
                    expect(problems, view.isSuspended(), "the screen was not set aside while asking");
                    expect(problems, kind == expected, "asked through " + kind + ", expected " + expected);
                    if (bridged) {
                        expect(problems, InventoryType.ANVIL.name().equals(window), "the open window is " + window);
                        player.closeInventory(); //the server closes the anvil: the question ends, the screen does not
                    }
                    until(done, problems, "the prompt did not end: " + seen, result::isDone, () -> after(2, done, () -> {
                        PromptResult<Integer> asked = result.getNow(null);
                        PromptResult.Kind ending = asked == null ? null : asked.getKind();
                        expect(problems, ending == (bridged ? PromptResult.Kind.CANCELLED : PromptResult.Kind.TIMEOUT),
                                "prompt ended " + asked);
                        expect(problems, Screens.getOpenView(player.getUniqueId()) == view && !view.isSuspended(),
                                "the screen did not come back");
                        expect(problems, player.getLevel() == level, "level " + level + " -> " + player.getLevel());
                        expect(problems, Arrays.equals(contents, player.getInventory().getContents()),
                                "the inventory changed: " + Arrays.toString(player.getInventory().getContents()));
                        done.complete(seen + " level=" + player.getLevel() + " prompt=" + ending);
                    }), System.currentTimeMillis() + 12_000L);
                });
                return done;
            }
            case "storage": {
                int storages = view.getScreen().getStorages().size();
                expect(problems, view.hasStorage() && storages == 1, "storages " + storages);
                return CompletableFuture.completedFuture("storages=" + storages);
            }
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
            case "deliveries":
                return claimTheSeededDelivery(view, probe, problems);
            case "ranking": {
                CompletableFuture<String> done = new CompletableFuture<>();
                until(done, problems, "the three entries never came from the list", () -> isEntry(view, 2, "ranking#2"), () -> {
                    expect(problems, !isEntry(view, 3, "ranking#3"), "a fourth entry on slot 3");
                    String top = drawnName(view, 0);
                    expect(problems, top != null && top.contains("#1") && top.contains("alpha"), "slot 0 drew '" + top + "'");
                    done.complete("entries=3 top='" + top + "'");
                });
                return done;
            }
            case "input": {
                CompletableFuture<String> done = new CompletableFuture<>();
                view.click("FIELD", ClickGesture.PRIMARY); //answers whether an item may move, not whether the click routed
                after(2, done, () -> {
                    expect(problems, view.isSuspended(), "the screen was not set aside while asking in chat");
                    until(done, problems, "the screen did not come back after the question timed out",
                            () -> Screens.getOpenView(player.getUniqueId()) == view && !view.isSuspended(), () -> {
                                expect(problems, probe.answers.isEmpty(), "a timed out question answered " + probe.answers);
                                done.complete("asked in chat, timed out, screen back");
                            });
                });
                return done;
            }
            case "progress": {
                Paint bar = view.getPaintAt(13);
                expect(problems, bar != null && bar.getProgress() == 0.5d, "the bar painted " + bar);
                String drawn = drawnName(view, 13);
                expect(problems, drawn != null && drawn.contains("50%"), "slot 13 drew '" + drawn + "'");
                return CompletableFuture.completedFuture("drawn='" + drawn + "'");
            }
            case "choice": {
                Paint anyone = view.getPaintAt(11);
                Paint nobody = view.getPaintAt(15);
                expect(problems, anyone != null && anyone.isEnabled(), "the open choice is " + anyone);
                expect(problems, nobody != null && !nobody.isEnabled() && String.valueOf(nobody.getReason()).contains(NOBODY_NODE),
                        "the gated choice is " + nobody);
                return CompletableFuture.completedFuture("disabled-because='" + (nobody == null ? null : nobody.getReason()) + "'");
            }
            case "reactive": {
                boolean needed = EventSourceUsage.get().isNeeded(EventSource.ITEM_PICKUP);
                Set<EventSource> held = view.getHeldSources();
                expect(problems, needed && held.contains(EventSource.ITEM_PICKUP), "while open: needed=" + needed + " held=" + held);
                return CompletableFuture.completedFuture("held=" + held);
            }
            default:
                return CompletableFuture.completedFuture("");
        }
    }

    /** The seeded delivery shows up in the list, a click on its entry claims it, and the list drops it. */
    private static CompletableFuture<String> claimTheSeededDelivery(ScreenView view, Probe probe, List<String> problems) {
        CompletableFuture<String> done = new CompletableFuture<>();
        DeliveriesScreen screen = (DeliveriesScreen) view.getScreen();
        until(done, problems, "the seeded delivery " + probe.delivery + " never reached the list", () -> indexOf(screen, probe.delivery) >= 0, () -> {
            int index = indexOf(screen, probe.delivery);
            view.click("deliveries#" + index, ClickGesture.PRIMARY);
            until(done, problems, "the delivery was still listed after the claim", () -> indexOf(screen, probe.delivery) < 0,
                    () -> done.complete("claimed entry #" + index));
        });
        return done;
    }

    private static int indexOf(DeliveriesScreen screen, UUID delivery) {
        List<Delivery> pending = screen.getPending();
        for (int i = 0; i < pending.size(); i++) {
            if (pending.get(i).getId().equals(delivery)) return i;
        }
        return -1;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Building blocks
    // -----------------------------------------------------------------------------------------------------------------

    private static final String NAV_SECOND_TITLE = "§8ecc nav second";

    private static Screen<Layout> navSecond() {
        Screen<Layout> screen = Screen.of(3).title(NAV_SECOND_TITLE);
        screen.icon(13, Icon.of("minecraft:diamond"));
        return screen;
    }

    /** How many, on the anvil: a refusal teaches the way out, and an answer is logged for whoever typed it by hand. */
    private static PromptSpec<Integer> anvilQuestion(Duration timeout) {
        return PromptSpec.of("§e[ECoreConformance] screen/prompt-anvil - how many?").prefer(PromptKind.ANVIL).timeout(timeout)
                .parse(text -> {
                    if (!text.matches("[0-9]+")) {
                        throw new IllegalArgumentException("Not a number: '" + text + "'. Type digits only, like 250.");
                    }
                    return Integer.valueOf(text);
                });
    }

    /**
     * The type of the window {@code player} has open, by reflection: {@code InventoryView} is a class before 1.21 and an
     * interface after it, so a compiled {@code getType()} call links on one side only.
     */
    private static String openWindowType(Player player) {
        Object view = player.getOpenInventory();
        try {
            return String.valueOf(InventoryView.class.getMethod("getType").invoke(view));
        } catch (ReflectiveOperationException unreadable) {
            return "unreadable: " + unreadable;
        }
    }

    private static ItemStack[] copyOf(ItemStack[] contents) {
        ItemStack[] copy = new ItemStack[contents.length];
        for (int i = 0; i < contents.length; i++) {
            copy[i] = contents[i] == null ? null : contents[i].clone();
        }
        return copy;
    }

    private static PromptSpec<String> question() {
        return PromptSpec.of("§e[ECoreConformance] type anything, or let it time out").prefer(PromptKind.CHAT)
                .timeout(Duration.ofSeconds(5));
    }

    private static RankedList<String> threeRanked() {
        Map<String, Integer> score = new HashMap<>();
        score.put("alpha", 30);
        score.put("beta", 20);
        score.put("gamma", 10);
        return RankedList.anonymous(String.class)
                .source(() -> Arrays.asList("gamma", "alpha", "beta"))
                .unlimitedEntries()
                .orderBy(score::get)
                .descending()
                .build();
    }

    /** The stock {@code permission} condition, on a node no player holds. */
    private static Condition nobodyHasIt() {
        if (Bukkit.getPluginManager().getPermission(NOBODY_NODE) == null) {
            Bukkit.getPluginManager().addPermission(new Permission(NOBODY_NODE, PermissionDefault.FALSE));
        }
        ObjectNode node = JSON.createObjectNode().put("type", "permission").put("node", NOBODY_NODE);
        return Conditions.parse(ECPluginManager.getProvidingPlugin(ScreenSuite.class), node, Window.getServerZone());
    }

    private static <S extends Screen<?>> S named(S screen, String name, Probe probe) {
        screen.title("§8ecc " + name);
        screen.onClose(close -> probe.closes.add(close.getReason()));
        return screen;
    }

    private static Screen<Layout> emerald(Screen<Layout> screen, int slot) {
        screen.icon(slot, Icon.of("minecraft:emerald"));
        return screen;
    }

    private static FPlayer viewer(Player player) {
        return MinecraftFPlayer.of(player);
    }

    /** The display name of what the window's inventory holds at {@code slot} - what the chest really drew. */
    private static String drawnName(ScreenView view, int slot) {
        BukkitScreenSurface surface = ChestScreenRenderer.surfaceOf(view);
        ItemStack stack = surface == null ? null : surface.getInventory().getItem(slot);
        return stack == null || !stack.hasItemMeta() ? null : stack.getItemMeta().getDisplayName();
    }

    private static boolean isEntry(ScreenView view, int slot, String elementId) {
        Paint paint = view.getPaintAt(slot);
        return paint != null && elementId.equals(paint.getElementId());
    }

    private static void screen(String id, int size, String title, ScreenBuilder build) {
        SCREENS.put(id, new Sample(id, size, title.isEmpty() ? "§8ecc " + id : title, build));
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
            BukkitScreenScheduler.INSTANCE.run(guarded);
        } else {
            BukkitScreenScheduler.INSTANCE.later(ticks, guarded);
        }
    }

    /** Asks {@code holds} every 2 ticks for up to 5 s, then runs {@code then}; giving up records {@code otherwise}. */
    private static void until(CompletableFuture<String> done, List<String> problems, String otherwise, BooleanSupplier holds, Step then) {
        //a deadline on the clock, not a tick count: a lagging server catches up by running ticks back to back, and the
        //prompt timeouts these waits outlast are measured on the clock
        until(done, problems, otherwise, holds, then, System.currentTimeMillis() + UNTIL_MILLIS);
    }

    private static void until(CompletableFuture<String> done, List<String> problems, String otherwise, BooleanSupplier holds, Step then,
                              long deadline) {
        after(2, done, () -> {
            if (holds.getAsBoolean()) {
                then.run();
            } else if (System.currentTimeMillis() >= deadline) {
                problems.add(otherwise);
                done.complete("");
            } else {
                until(done, problems, otherwise, holds, then, deadline);
            }
        });
    }

    interface ScreenBuilder {
        Screen<?> apply(Probe probe);
    }

    /** What a check reads back from the screen it built: every close, the pager, typed answers, the seeded delivery. */
    private static final class Probe {
        private final List<CloseReason> closes = Collections.synchronizedList(new ArrayList<>());
        private final List<String> answers = Collections.synchronizedList(new ArrayList<>());
        private Pager pager;
        private volatile UUID delivery;
    }

    private static final class Sample {
        private final String id;
        private final int size;
        private final String title;
        private final ScreenBuilder build;

        private Sample(String id, int size, String title, ScreenBuilder build) {
            this.id = id;
            this.size = size;
            this.title = title;
            this.build = build;
        }
    }

    /** A layout-built screen: its file is seeded under {@code guis/} the first time it is read. */
    @ScreenLayout(title = SeededLayout.TITLE, rows = 3)
    public static class SeededLayout extends Layout {

        static final String TITLE = "§8ecc layout";

        @IconData(slot = {13})
        public Icon CENTER = Icon.of("minecraft:emerald");

        @IconData(slot = {0, 8, 18, 26}, background = true)
        public Icon CORNER = Icon.of(DefaultIcons.pane(DefaultIcons.Color.GRAY));
    }

    @ScreenLayout(title = "§8ecc input", rows = 3)
    public static class InputLayout extends Layout {

        @IconData(slot = {13})
        public Icon FIELD = Icon.of("minecraft:name_tag").displayName("§eClick and type in chat");
    }

    @ScreenLayout(title = "§8ecc progress", rows = 3)
    public static class ProgressLayout extends Layout {

        @IconData(slot = {13})
        public Icon BAR = Icon.of("minecraft:experience_bottle").displayName("§a{progress}%");
    }

    @ScreenLayout(title = "§8ecc choice", rows = 3)
    public static class ChoiceLayout extends Layout {

        @IconData(slot = {11})
        public Icon ANYONE = Icon.of("minecraft:emerald").displayName("§aanyone may pick this");

        @IconData(slot = {15})
        public Icon NOBODY = Icon.of("minecraft:redstone").displayName("§cnobody may pick this");
    }
}
