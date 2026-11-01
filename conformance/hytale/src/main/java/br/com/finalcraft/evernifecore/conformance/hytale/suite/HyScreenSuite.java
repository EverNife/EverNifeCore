package br.com.finalcraft.evernifecore.conformance.hytale.suite;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.conformance.ConformanceCase;
import br.com.finalcraft.evernifecore.conformance.ConformanceContext;
import br.com.finalcraft.evernifecore.conformance.ConformanceSuite;
import br.com.finalcraft.evernifecore.conformance.Verdict;
import br.com.finalcraft.evernifecore.conformance.hytale.HyWorld;
import br.com.finalcraft.evernifecore.hytale.screen.HyPageRenderer;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.MutableState;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.Slots;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEvent;
import com.hypixel.hytale.protocol.packets.interface_.CustomPageEventType;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.entity.entities.player.pages.CustomUIPage;
import com.hypixel.hytale.server.core.entity.entities.player.pages.PageManager;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Screens on a Hytale server, drawn by the core's page renderer: the page reaches the player's {@code PageManager}, a
 * click and a dismissal come back through the page manager the way a client's {@code CustomPageEvent} does, a state
 * change goes out as an update, a close and a replacement take the page away, a screen with an editable area is
 * refused, and a question is asked on a page. No client draws anything here: the synthetic player swallows the packets.
 *
 * <p>The page manager drops a data event while it waits for the client to acknowledge a page, and the synthetic player
 * acknowledges nothing; each case clears those waits first. An acknowledgement nobody waited for throws, which is how
 * a case sees that an update went out.</p>
 */
public final class HyScreenSuite implements ConformanceSuite {

    /** Screens {@code /ecc screen} opens for somebody looking with a real client. */
    public static final List<String> SCREEN_IDS = Collections.singletonList("sample");

    private static final long WAIT_MILLIS = 5_000L;

    @Override
    public String getId() {
        return "screen";
    }

    @Override
    public List<ConformanceCase> getCases(ConformanceContext ctx) {
        return Arrays.asList(
                withScreen("page-opens-for-player", ctx, this::pageOpens),
                withScreen("click-reaches-click-rule", ctx, this::clickReachesRule),
                withScreen("patch-after-state-change", ctx, this::patchAfterStateChange),
                withScreen("dismiss-closes-as-player", ctx, this::dismissClosesAsPlayer),
                withScreen("server-close-clears-page", ctx, this::serverCloseClearsPage),
                withScreen("replace-closes-previous", ctx, this::replaceClosesPrevious),
                withScreen("storage-slots-unsupported", ctx, this::storageSlotsUnsupported),
                withScreen("prompt-asks-on-a-page", ctx, this::promptAsksOnAPage));
    }

    /** Opens the screen {@code id} of {@link #SCREEN_IDS} for {@code player} and leaves it open. */
    public static CompletableFuture<ScreenView> open(String id, FPlayer player) {
        MutableState<Integer> clicks = new MutableState<>(1);
        Screen<?> screen = Screen.of(3).title("EverNifeCore - " + id);
        screen.icon(10, Icon.of(ItemSpec.of("hytale:Rock_Stone", 1).withText("Stone", Arrays.asList("A plain block", "drawn by the core"))));
        screen.icon(13, Icon.of(ItemSpec.of("hytale:Ingredient_Bar_Iron", 1).withText("Click me", Collections.singletonList("the amount counts the clicks"))))
                .id("counter").dependsOn(clicks).render(icon -> icon.amount(clicks.get()))
                .onClick(context -> clicks.set(clicks.get() + 1));
        screen.icon(16, Icon.of(ItemSpec.of("minecraft:diamond", 1).withText("A Minecraft item", Collections.singletonList("no Hytale asset: a neutral block"))));
        return Screens.open(player, screen);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Cases
    // -----------------------------------------------------------------------------------------------------------------

    private CompletableFuture<Verdict> pageOpens(Visit visit) {
        return visit.open(Screen.of(1)).thenCompose(view -> visit.onWorld(pages -> {
            CustomUIPage page = pages.getCustomPage();
            return Verdict.of(view.isSurface(page) && view.getRenderer().getId().equals(HyPageRenderer.ID),
                    "renderer=" + view.getRenderer().getId() + " page=" + (page == null ? null : page.getClass().getSimpleName()));
        }));
    }

    private CompletableFuture<Verdict> clickReachesRule(Visit visit) {
        CompletableFuture<String> clicked = new CompletableFuture<>();
        Screen<?> screen = Screen.of(1);
        screen.icon(4, Icon.of(ItemSpec.of("hytale:Rock_Stone", 1))).id("probe")
                .onClick(context -> clicked.complete(context.getElementId() + "/" + context.getGesture()));
        return visit.open(screen)
                .thenCompose(view -> visit.onWorld(pages -> visit.send(pages, CustomPageEventType.Data, "{\"Slot\":\"4\",\"Kind\":\"Activating\"}")))
                .thenCompose(sent -> clicked.orTimeout(WAIT_MILLIS, TimeUnit.MILLISECONDS))
                .thenApply(heard -> Verdict.of("probe/PRIMARY".equals(heard), "heard " + heard));
    }

    private CompletableFuture<Verdict> patchAfterStateChange(Visit visit) {
        MutableState<Integer> stock = new MutableState<>(1);
        Screen<?> screen = Screen.of(1);
        screen.icon(2, Icon.of(ItemSpec.of("hytale:Rock_Stone", 1))).id("stock").dependsOn(stock).render(icon -> icon.amount(stock.get()));
        return visit.open(screen)
                .thenCompose(view -> visit.onWorld(pages -> {
                    pages.clearLegacyCustomPageAcknowledgements();
                    return visit.acknowledged(pages);
                }))
                .thenCompose(before -> {
                    if (before) {
                        return CompletableFuture.completedFuture(Verdict.fail("an update was waiting before the state changed"));
                    }
                    stock.set(9);
                    return visit.until(pages -> visit.acknowledged(pages))
                            .thenApply(sent -> Verdict.of(sent, sent ? "the state change went out as a page update"
                                    : "no page update within " + WAIT_MILLIS + " ms of the state change"));
                });
    }

    private CompletableFuture<Verdict> dismissClosesAsPlayer(Visit visit) {
        CompletableFuture<CloseReason> closed = new CompletableFuture<>();
        return visit.open(Screen.of(1).onClose(context -> closed.complete(context.getReason())))
                .thenCompose(view -> visit.onWorld(pages -> visit.send(pages, CustomPageEventType.Dismiss, "")))
                .thenCompose(sent -> closed.orTimeout(WAIT_MILLIS, TimeUnit.MILLISECONDS))
                .thenApply(reason -> Verdict.of(reason == CloseReason.PLAYER_CLOSED, "closed as " + reason));
    }

    private CompletableFuture<Verdict> serverCloseClearsPage(Visit visit) {
        CompletableFuture<CloseReason> closed = new CompletableFuture<>();
        return visit.open(Screen.of(1).onClose(context -> closed.complete(context.getReason())))
                .thenCompose(view -> {
                    view.getScheduler().run(view::close);
                    return closed.orTimeout(WAIT_MILLIS, TimeUnit.MILLISECONDS);
                })
                .thenCompose(reason -> visit.onWorld(pages -> Verdict.of(reason == CloseReason.REQUESTED && pages.getCustomPage() == null,
                        "closed as " + reason + ", page left " + pages.getCustomPage())));
    }

    private CompletableFuture<Verdict> replaceClosesPrevious(Visit visit) {
        CompletableFuture<CloseReason> firstClosed = new CompletableFuture<>();
        return visit.open(Screen.of(1).onClose(context -> firstClosed.complete(context.getReason())))
                .thenCompose(first -> visit.open(Screen.of(2)))
                .thenCompose(second -> firstClosed.orTimeout(WAIT_MILLIS, TimeUnit.MILLISECONDS)
                        .thenCompose(reason -> visit.onWorld(pages -> Verdict.of(reason == CloseReason.REQUESTED && second.isSurface(pages.getCustomPage()),
                                "first closed as " + reason + ", second on screen " + second.isSurface(pages.getCustomPage())))));
    }

    private CompletableFuture<Verdict> storageSlotsUnsupported(Visit visit) {
        Screen<?> editable = Screen.of(1);
        editable.storage(Slots.of(0));
        return Screens.open(visit.player, editable).handle((view, refused) -> {
            if (view != null) {
                view.getScheduler().run(view::close);
                return Verdict.fail("a screen with an editable area opened on the page renderer");
            }
            String message = String.valueOf((refused instanceof CompletionException ? refused.getCause() : refused).getMessage());
            return Verdict.of(message.contains("STORAGE_SLOTS") && message.contains(HyPageRenderer.ID), message);
        });
    }

    private CompletableFuture<Verdict> promptAsksOnAPage(Visit visit) {
        return visit.open(Screen.of(1)).thenCompose(view -> {
            CompletableFuture<Integer> answer = view.ask(PromptSpec.of("How many?").parse(Integer::parseInt));
            return visit.until(pages -> pages.getCustomPage() != null && !view.isSurface(pages.getCustomPage()))
                    .thenCompose(asked -> visit.onWorld(pages -> visit.send(pages, CustomPageEventType.Data,
                            "{\"Kind\":\"Submit\",\"@Value\":\"42\"}")))
                    .thenCompose(sent -> answer.orTimeout(WAIT_MILLIS, TimeUnit.MILLISECONDS))
                    .thenCompose(value -> visit.until(pages -> view.isSurface(pages.getCustomPage()))
                            .thenApply(back -> Verdict.of(value == 42 && back, "answer " + value + ", screen back " + back)));
        });
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Plumbing
    // -----------------------------------------------------------------------------------------------------------------

    private interface Body {
        CompletableFuture<Verdict> run(Visit visit);
    }

    /** A case with the player; every screen it opened is closed afterwards, whatever the verdict. */
    private static ConformanceCase withScreen(String id, ConformanceContext ctx, Body body) {
        return HyRegionsSuite.withPlayer(id, ctx, player -> {
            Visit visit = new Visit(player);
            CompletableFuture<Verdict> verdict;
            try {
                verdict = body.run(visit);
            } catch (Throwable broken) {
                verdict = CompletableFuture.completedFuture(Verdict.fail(Verdict.describe(broken)));
            }
            return verdict.whenComplete((done, failed) -> visit.closeAll());
        });
    }

    private static final class Visit {

        private final FPlayer player;
        private final PlayerRef ref;
        private final World world;
        private final List<ScreenView> opened = new CopyOnWriteArrayList<>();

        private Visit(FPlayer player) {
            this.player = player;
            this.ref = HyWorld.player(player);
            this.world = HyWorld.worldOf(ref);
        }

        private CompletableFuture<ScreenView> open(Screen<?> screen) {
            return Screens.open(player, screen).thenApply(view -> {
                opened.add(view);
                return view;
            });
        }

        private <T> CompletableFuture<T> onWorld(Function<PageManager, T> task) {
            return HyWorld.on(world, () -> task.apply(pagesOf(ref)));
        }

        /** Delivers what a client would send, after clearing the acknowledgements the synthetic player never sends. */
        private boolean send(PageManager pages, CustomPageEventType type, String data) {
            pages.clearLegacyCustomPageAcknowledgements();
            Ref<EntityStore> entity = ref.getReference();
            pages.handleLegacyCustomPageEvent(entity, entity.getStore(), new CustomPageEvent(type, data));
            return true;
        }

        /** Whether the page manager was waiting for an acknowledgement - an update went out since the last clear. */
        private boolean acknowledged(PageManager pages) {
            Ref<EntityStore> entity = ref.getReference();
            try {
                pages.handleLegacyCustomPageEvent(entity, entity.getStore(), new CustomPageEvent(CustomPageEventType.Acknowledge, ""));
                return true;
            } catch (IllegalArgumentException nothingWaited) {
                return false;
            }
        }

        /** Polls {@code check} on the world thread until it holds or {@link #WAIT_MILLIS} pass; a clock deadline, not ticks. */
        private CompletableFuture<Boolean> until(Function<PageManager, Boolean> check) {
            long deadline = System.currentTimeMillis() + WAIT_MILLIS;
            return poll(check, deadline);
        }

        private CompletableFuture<Boolean> poll(Function<PageManager, Boolean> check, long deadline) {
            return onWorld(check).thenCompose(held -> {
                if (held || System.currentTimeMillis() >= deadline) {
                    return CompletableFuture.completedFuture(held);
                }
                return HyWorld.ticks(world, 3).thenCompose(waited -> poll(check, deadline));
            });
        }

        private void closeAll() {
            for (ScreenView view : opened) {
                view.getScheduler().run(view::close);
            }
        }

    }

    private static PageManager pagesOf(PlayerRef ref) {
        Ref<EntityStore> entity = ref.getReference();
        Player player = entity.getStore().getComponent(entity, Player.getComponentType());
        if (player == null) {
            throw new IllegalStateException(ref.getUsername() + " has no Player component in the world");
        }
        return player.getPageManager();
    }

}
