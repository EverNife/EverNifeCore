package br.com.finalcraft.evernifecore.testing;

import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.item.ItemSpec;
import br.com.finalcraft.evernifecore.logic.ConditionResult;
import br.com.finalcraft.evernifecore.logic.Gate;
import br.com.finalcraft.evernifecore.screen.ClickGesture;
import br.com.finalcraft.evernifecore.screen.CloseReason;
import br.com.finalcraft.evernifecore.screen.ElementKind;
import br.com.finalcraft.evernifecore.screen.ElementPatch;
import br.com.finalcraft.evernifecore.screen.GridSurface;
import br.com.finalcraft.evernifecore.screen.Icon;
import br.com.finalcraft.evernifecore.screen.MutableState;
import br.com.finalcraft.evernifecore.screen.RenderedIcon;
import br.com.finalcraft.evernifecore.screen.RendererCapability;
import br.com.finalcraft.evernifecore.screen.Screen;
import br.com.finalcraft.evernifecore.screen.ScreenCloseEvent;
import br.com.finalcraft.evernifecore.screen.ScreenNavigation;
import br.com.finalcraft.evernifecore.screen.ScreenOpenEvent;
import br.com.finalcraft.evernifecore.screen.ScreenRenderer;
import br.com.finalcraft.evernifecore.screen.ScreenScheduler;
import br.com.finalcraft.evernifecore.screen.ScreenSurface;
import br.com.finalcraft.evernifecore.screen.ScreenView;
import br.com.finalcraft.evernifecore.screen.Screens;
import br.com.finalcraft.evernifecore.screen.Slots;
import br.com.finalcraft.evernifecore.screen.prompt.PromptSpec;
import br.com.finalcraft.evernifecore.types.TypeDescriptor;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * The suite every {@link ScreenRenderer} passes: one named scenario per promise the view makes and the renderer has to
 * keep, each answering a failure line or nothing. {@code check(renderer, world)} returns the failures - an empty list
 * is a conforming renderer. A scenario the renderer cannot take part in (no TITLE_CHANGE, already has STORAGE_SLOTS)
 * is skipped, not failed. The prompt scenarios ask through whatever channel {@code world.answerPrompt} answers.
 *
 * <p>The scenarios: {@code open-registers-view}, {@code open-posts-event}, {@code click-routes-by-id-and-gesture},
 * {@code patch-batches-per-tick}, {@code patch-touches-only-dirty-elements}, {@code close-runs-onclose-once},
 * {@code close-posts-event-with-reason}, {@code prompt-sets-aside-and-restores}, {@code nav-push-pop-hands-value},
 * {@code capability-refusal-teaches}, {@code title-change-when-declared}, and one per element kind:
 * {@code label-draws-its-text}, {@code choice-disabled-carries-reason}, {@code progress-reports-ratio},
 * {@code input-asks-through-a-prompt}, {@code list-pages-with-arrows}.</p>
 *
 * <p>The renderer must be registered in {@code world} already. For the run it is swapped for a probe under the same id
 * that wraps each window it opens, which is how the suite counts {@code apply} calls on any renderer; the original is
 * put back afterwards.</p>
 */
public final class RendererConformance {

    private static final AtomicInteger VIEWERS = new AtomicInteger();

    private RendererConformance() {

    }

    /** {@link #check(ScreenRenderer, ScreenWorld, Supplier)} with a fresh {@code world.viewer(...)} per scenario. */
    @Nonnull
    public static List<String> check(@Nonnull ScreenRenderer renderer, @Nonnull ScreenWorld world) {
        return check(renderer, world, () -> world.viewer("Conformance" + VIEWERS.incrementAndGet()));
    }

    /**
     * Runs the suite with {@code viewers} answering the player each scenario opens its screen for - a renderer whose
     * {@code supports} needs a handshake per player gives viewers that already made it. Each call must answer a player
     * with no screen open.
     */
    @Nonnull
    public static List<String> check(@Nonnull ScreenRenderer renderer, @Nonnull ScreenWorld world, @Nonnull Supplier<FPlayer> viewers) {
        List<String> failures = new ArrayList<>();
        Optional<TypeDescriptor<ScreenRenderer>> registered = Screens.registry().find(renderer.getId());
        ECPluginData owner = registered.flatMap(Screens.registry()::getOwner).orElse(null);
        if (owner == null) {
            failures.add("setup: the renderer [" + renderer.getId() + "] is not registered in this world. Register it with "
                    + "Screens.registerRenderer(owner, renderer) (or world.withRenderer) before checking it.");
            return failures;
        }
        Probe probe = new Probe(renderer);
        Screens.registry().unregister(owner, renderer.getId());
        Screens.registerRenderer(owner, probe);
        try {
            run(failures, "open-registers-view", () -> openRegistersView(world, probe, viewers));
            run(failures, "open-posts-event", () -> openPostsEvent(world, viewers));
            run(failures, "click-routes-by-id-and-gesture", () -> clickRoutes(world, viewers));
            run(failures, "patch-batches-per-tick", () -> patchBatches(world, probe, viewers));
            run(failures, "patch-touches-only-dirty-elements", () -> patchTouchesOnlyDirty(world, probe, viewers));
            run(failures, "close-runs-onclose-once", () -> closeRunsOnCloseOnce(world, viewers));
            run(failures, "close-posts-event-with-reason", () -> closePostsEvent(world, viewers));
            run(failures, "prompt-sets-aside-and-restores", () -> promptSetsAside(world, viewers));
            run(failures, "nav-push-pop-hands-value", () -> navPushPop(world, viewers));
            run(failures, "capability-refusal-teaches", () -> capabilityRefusal(world, renderer, viewers));
            run(failures, "title-change-when-declared", () -> titleChange(world, probe, renderer, viewers));
            run(failures, "label-draws-its-text", () -> labelDrawsItsText(world, probe, viewers));
            run(failures, "choice-disabled-carries-reason", () -> choiceDisabledCarriesReason(world, probe, viewers));
            run(failures, "progress-reports-ratio", () -> progressReportsRatio(world, probe, viewers));
            run(failures, "input-asks-through-a-prompt", () -> inputAsksThroughAPrompt(world, viewers));
            run(failures, "list-pages-with-arrows", () -> listPagesWithArrows(world, probe, viewers));
        } finally {
            Screens.closeAll();
            Screens.registry().unregister(owner, renderer.getId());
            Screens.registerRenderer(owner, renderer);
        }
        return failures;
    }

    private interface Scenario {
        @Nullable
        String run() throws Exception;
    }

    private static void run(List<String> failures, String name, Scenario scenario) {
        try {
            String failure = scenario.run();
            if (failure != null) {
                failures.add(name + ": " + failure);
            }
        } catch (Throwable broken) {
            failures.add(name + ": threw " + broken);
        } finally {
            Screens.closeAll();
        }
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Scenarios
    // -----------------------------------------------------------------------------------------------------------------

    private static String openRegistersView(ScreenWorld world, Probe probe, Supplier<FPlayer> viewers) {
        FPlayer viewer = viewers.get();
        ScreenView view = openNow(world, Screen.of(1), viewer);
        if (view == null) {
            return "the open did not complete with a view";
        }
        if (Screens.getOpenView(viewer) != view) {
            return "the open view is not registered for the viewer";
        }
        return view.getRenderer() == probe ? null : "the view was drawn by [" + view.getRenderer().getId() + "]";
    }

    private static String openPostsEvent(ScreenWorld world, Supplier<FPlayer> viewers) {
        List<ScreenOpenEvent> events = world.recorded(ScreenOpenEvent.class);
        int before = events.size();
        ScreenView view = openNow(world, Screen.of(1), viewers.get());
        if (events.size() != before + 1) {
            return "expected one ScreenOpenEvent, saw " + (events.size() - before);
        }
        return events.get(events.size() - 1).getView() == view ? null : "the event names another view";
    }

    private static String clickRoutes(ScreenWorld world, Supplier<FPlayer> viewers) {
        List<String> heard = new ArrayList<>();
        Screen<?> screen = Screen.of(1);
        screen.icon(0, icon("minecraft:stone")).id("probe")
                .onClick(context -> heard.add(context.getElementId() + "/" + context.getGesture()));
        screen.icon(1, icon("minecraft:dirt")).id("other");
        ScreenView view = openNow(world, screen, viewers.get());
        view.click("probe", ClickGesture.SECONDARY);
        view.click("other", ClickGesture.PRIMARY);
        view.click("nobody", ClickGesture.PRIMARY);
        return heard.size() == 1 && heard.get(0).equals("probe/SECONDARY") ? null : "the handlers heard " + heard;
    }

    private static String patchBatches(ScreenWorld world, Probe probe, Supplier<FPlayer> viewers) {
        MutableState<Integer> left = new MutableState<>(1);
        MutableState<Integer> right = new MutableState<>(1);
        Screen<?> screen = Screen.of(1);
        screen.icon(0, icon("minecraft:stone")).id("left").dependsOn(left).render(i -> i.amount(left.get()));
        screen.icon(1, icon("minecraft:stone")).id("right").dependsOn(right).render(i -> i.amount(right.get()));
        ScreenView view = openNow(world, screen, viewers.get());
        Counting surface = probe.surfaceOf(view);
        surface.applies.clear();
        left.set(2);
        right.set(3);
        world.advanceTicks(1);
        if (surface.applies.size() != 1) {
            return "two invalidations in one tick made " + surface.applies.size() + " apply calls, not one";
        }
        Set<String> ids = idsOf(surface.applies.get(0));
        if (!ids.contains("left") || !ids.contains("right")) {
            return "the one apply carried " + ids + ", not both changed elements";
        }
        if (surface.delegate instanceof GridSurface) {
            RenderedIcon shown = ((GridSurface) surface.delegate).read(1);
            if (shown == null || shown.getItem().getAmount() != 3) {
                return "the patch reached the renderer but slot 1 shows " + shown + ": the renderer did not apply it";
            }
        }
        return null;
    }

    private static String patchTouchesOnlyDirty(ScreenWorld world, Probe probe, Supplier<FPlayer> viewers) {
        MutableState<Integer> left = new MutableState<>(1);
        Screen<?> screen = Screen.of(1);
        screen.icon(0, icon("minecraft:stone")).id("left").dependsOn(left).render(i -> i.amount(left.get()));
        screen.icon(1, icon("minecraft:dirt")).id("still");
        ScreenView view = openNow(world, screen, viewers.get());
        Counting surface = probe.surfaceOf(view);
        surface.applies.clear();
        left.set(5);
        world.advanceTicks(1);
        if (surface.applies.size() != 1) {
            return "one invalidation made " + surface.applies.size() + " apply calls";
        }
        Set<String> ids = idsOf(surface.applies.get(0));
        return ids.size() == 1 && ids.contains("left") ? null : "the apply touched " + ids + ", not just [left]";
    }

    private static String closeRunsOnCloseOnce(ScreenWorld world, Supplier<FPlayer> viewers) {
        AtomicInteger closes = new AtomicInteger();
        Screen<?> screen = Screen.of(1).onClose(context -> closes.incrementAndGet());
        ScreenView view = openNow(world, screen, viewers.get());
        view.close();
        world.advanceTicks(2);
        view.close();
        world.advanceTicks(2);
        if (!view.isClosed()) {
            return "the view is still open after close() and two ticks: the renderer never reported the window gone";
        }
        return closes.get() == 1 ? null : "onClose ran " + closes.get() + " times";
    }

    private static String closePostsEvent(ScreenWorld world, Supplier<FPlayer> viewers) {
        List<ScreenCloseEvent> events = world.recorded(ScreenCloseEvent.class);
        int before = events.size();
        ScreenView view = openNow(world, Screen.of(1), viewers.get());
        view.close();
        world.advanceTicks(2);
        if (events.size() != before + 1) {
            return "expected one ScreenCloseEvent, saw " + (events.size() - before);
        }
        ScreenCloseEvent event = events.get(events.size() - 1);
        return event.getView() == view && event.getReason() != null ? null : "the event names another view or no reason";
    }

    private static String promptSetsAside(ScreenWorld world, Supplier<FPlayer> viewers) {
        FPlayer viewer = viewers.get();
        ScreenView view = openNow(world, Screen.of(1), viewer);
        CompletableFuture<String> answer = view.ask(PromptSpec.of("Name?"));
        world.advanceTicks(1);
        if (!view.isSuspended()) {
            return "the screen was not set aside while the prompt asked";
        }
        world.answerPrompt("Steve");
        world.advanceTicks(2);
        if (!"Steve".equals(answer.getNow(null))) {
            return "the answer did not come back: " + answer;
        }
        return !view.isSuspended() && Screens.getOpenView(viewer) == view ? null : "the screen did not come back after the answer";
    }

    private static String navPushPop(ScreenWorld world, Supplier<FPlayer> viewers) {
        FPlayer viewer = viewers.get();
        ScreenView first = openNow(world, Screen.of(1), viewer);
        CompletableFuture<Object> handed = ScreenNavigation.open(first, Screen.of(2));
        world.advanceTicks(2);
        ScreenView second = Screens.getOpenView(viewer);
        if (second == null || second == first || !first.isSuspended()) {
            return "the pushed screen did not take the window, or the first was not set aside";
        }
        ScreenNavigation.back(second, "picked");
        world.advanceTicks(2);
        if (Screens.getOpenView(viewer) != first || first.isSuspended()) {
            return "back() did not give the first screen its window again";
        }
        return "picked".equals(handed.getNow(null)) ? null : "the opener got " + handed + ", not the handed value";
    }

    private static String capabilityRefusal(ScreenWorld world, ScreenRenderer renderer, Supplier<FPlayer> viewers) {
        if (renderer.getCapabilities().contains(RendererCapability.STORAGE_SLOTS)) {
            return null; //skipped: nothing to refuse to a renderer that has STORAGE_SLOTS
        }
        Screen<?> screen = Screen.of(1);
        screen.storage(Slots.of(0));
        CompletableFuture<ScreenView> opening = world.tryOpen(screen, viewers.get());
        world.advanceTicks(1);
        if (!opening.isCompletedExceptionally()) {
            return "a screen needing STORAGE_SLOTS opened on a renderer without it";
        }
        String message;
        try {
            opening.join();
            return "the refusal completed normally";
        } catch (Throwable refused) {
            message = String.valueOf(refused.getCause() != null ? refused.getCause().getMessage() : refused.getMessage());
        }
        return message.contains("STORAGE_SLOTS") && message.contains(renderer.getId()) ? null
                : "the refusal does not name the capability and the renderer: " + message;
    }

    private static String titleChange(ScreenWorld world, Probe probe, ScreenRenderer renderer, Supplier<FPlayer> viewers) {
        if (!renderer.getCapabilities().contains(RendererCapability.TITLE_CHANGE)) {
            return null; //skipped: the renderer does not declare TITLE_CHANGE
        }
        MutableState<String> title = new MutableState<>("First");
        Screen<?> screen = Screen.of(1).title(title::get);
        ScreenView view = openNow(world, screen, viewers.get());
        Counting surface = probe.surfaceOf(view);
        title.set("Second");
        view.refresh();
        world.advanceTicks(1);
        return surface.titles.contains("Second") ? null : "the title change never reached the surface: " + surface.titles;
    }

    private static String labelDrawsItsText(ScreenWorld world, Probe probe, Supplier<FPlayer> viewers) {
        Screen<?> screen = Screen.of(1);
        screen.label(Slots.of(0), icon("minecraft:paper").displayName("Conformance label")).id("label");
        ScreenView view = openNow(world, screen, viewers.get());
        ElementPatch patch = lastPatchOf(probe.surfaceOf(view), 0);
        if (patch == null || patch.getKind() != ElementKind.LABEL) {
            return "slot 0 was not sent as a LABEL: " + patch;
        }
        String name = patch.getIcon() == null ? null : patch.getIcon().getItem().getDisplayName();
        return name != null && name.contains("Conformance label") ? null : "the label patch carries the text " + name;
    }

    private static String choiceDisabledCarriesReason(ScreenWorld world, Probe probe, Supplier<FPlayer> viewers) {
        Gate gate = Gate.builder().require(ctx -> ConditionResult.fail(FancyText.of("needs the conformance key"))).build();
        Screen<?> screen = Screen.of(1);
        screen.choice(Slots.of(0), icon("minecraft:paper"), FancyText.of("Open the door"), gate, Collections.emptyList())
                .id("choice");
        ScreenView view = openNow(world, screen, viewers.get());
        ElementPatch patch = lastPatchOf(probe.surfaceOf(view), 0);
        if (patch == null || patch.isEnabled()) {
            return "a choice whose gate fails was sent enabled: " + patch;
        }
        String reason = patch.getReason();
        return reason != null && reason.contains("needs the conformance key") ? null
                : "the disabled choice does not carry the failure as its reason: " + reason;
    }

    private static String progressReportsRatio(ScreenWorld world, Probe probe, Supplier<FPlayer> viewers) {
        MutableState<Double> ratio = new MutableState<>(0.25D);
        Screen<?> screen = Screen.of(1);
        screen.progress(Slots.of(0), icon("minecraft:experience_bottle"), ratio).id("bar");
        ScreenView view = openNow(world, screen, viewers.get());
        Counting surface = probe.surfaceOf(view);
        ElementPatch first = lastPatchOf(surface, 0);
        if (first == null || first.getKind() != ElementKind.PROGRESS || Math.abs(first.getProgress() - 0.25D) > 1e-9) {
            return "the bar opened as " + first + ", not a PROGRESS patch at 0.25";
        }
        ratio.set(0.5D);
        world.advanceTicks(1);
        ElementPatch moved = lastPatchOf(surface, 0);
        return moved != null && Math.abs(moved.getProgress() - 0.5D) < 1e-9 ? null
                : "the ratio moved to 0.5 and the renderer last heard " + (moved == null ? null : moved.getProgress());
    }

    private static String inputAsksThroughAPrompt(ScreenWorld world, Supplier<FPlayer> viewers) {
        List<String> typed = new ArrayList<>();
        FPlayer viewer = viewers.get();
        Screen<?> screen = Screen.of(1).debounce(0);
        screen.input(Slots.of(0), icon("minecraft:name_tag"))
                .onInput(PromptSpec.of("Name?"), (answered, value) -> typed.add(value))
                .id("field");
        ScreenView view = openNow(world, screen, viewer);
        view.click("field", ClickGesture.PRIMARY);
        world.advanceTicks(1);
        if (!view.isSuspended()) {
            return "a click on the input did not set the screen aside to ask";
        }
        world.answerPrompt("Steve");
        world.advanceTicks(2);
        if (!typed.equals(Collections.singletonList("Steve"))) {
            return "the input handler got " + typed + ", not the typed answer";
        }
        return Screens.getOpenView(viewer) == view && !view.isSuspended() ? null : "the screen did not come back after the answer";
    }

    private static String listPagesWithArrows(ScreenWorld world, Probe probe, Supplier<FPlayer> viewers) {
        Screen<?> screen = Screen.of(1).debounce(0);
        screen.<Integer>list(() -> Arrays.asList(1, 2, 3)).id("pages")
                .into(Slots.of(0, 1))
                .render((entry, icon) -> icon.setItem(ItemSpec.of("minecraft:paper", entry)))
                .pagedBy(Slots.of(7), Slots.of(8));
        ScreenView view = openNow(world, screen, viewers.get());
        Counting surface = probe.surfaceOf(view);
        ElementPatch next = lastPatchOf(surface, 8);
        if (next == null || !"pages#next".equals(next.getElementId())) {
            return "three entries in two slots drew no [pages#next] on slot 8: " + next;
        }
        view.click("pages#next", ClickGesture.PRIMARY);
        world.advanceTicks(1);
        ElementPatch turned = lastPatchOf(surface, 0);
        int amount = turned == null || turned.getIcon() == null ? -1 : turned.getIcon().getItem().getAmount();
        return amount == 3 ? null : "after [pages#next] slot 0 shows " + (turned == null ? null : turned.getIcon())
                + ", not the third entry";
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Plumbing
    // -----------------------------------------------------------------------------------------------------------------

    private static Icon icon(String id) {
        return Icon.of(ItemSpec.of(id, 1));
    }

    @Nullable
    private static ScreenView openNow(ScreenWorld world, Screen<?> screen, FPlayer viewer) {
        CompletableFuture<ScreenView> opening = world.tryOpen(screen, viewer);
        if (!opening.isDone()) {
            world.advanceTicks(1);
        }
        return opening.join();
    }

    /** The last patch {@code surface} was sent for {@code slot}, or {@code null}. */
    @Nullable
    private static ElementPatch lastPatchOf(Counting surface, int slot) {
        for (int i = surface.applies.size() - 1; i >= 0; i--) {
            for (ElementPatch each : surface.applies.get(i)) {
                if (each.getSlot() == slot) {
                    return each;
                }
            }
        }
        return null;
    }

    private static Set<String> idsOf(List<ElementPatch> patch) {
        Set<String> ids = new LinkedHashSet<>();
        for (ElementPatch each : patch) {
            ids.add(each.getElementId());
        }
        return ids;
    }

    /** The renderer under test, with every window it opens wrapped in a {@link Counting} surface. */
    private static final class Probe implements ScreenRenderer {

        private final ScreenRenderer delegate;
        private final List<Counting> surfaces = new ArrayList<>();

        private Probe(ScreenRenderer delegate) {
            this.delegate = delegate;
        }

        private Counting surfaceOf(ScreenView view) {
            for (int i = surfaces.size() - 1; i >= 0; i--) {
                if (surfaces.get(i).view == view) {
                    return surfaces.get(i);
                }
            }
            throw new IllegalStateException("The renderer opened no window for " + view);
        }

        @Nonnull
        @Override
        public String getId() {
            return delegate.getId();
        }

        @Override
        public int getPriority() {
            return delegate.getPriority();
        }

        @Nonnull
        @Override
        public Set<RendererCapability> getCapabilities() {
            return delegate.getCapabilities();
        }

        @Override
        public boolean supports(@Nonnull FPlayer viewer, @Nonnull Screen<?> screen) {
            return delegate.supports(viewer, screen);
        }

        @Nullable
        @Override
        public ScreenSurface open(@Nonnull ScreenView view) {
            ScreenSurface opened = delegate.open(view);
            if (opened == null) {
                return null;
            }
            Counting counting = new Counting(view, opened);
            surfaces.add(counting);
            return counting;
        }

        @Nonnull
        @Override
        public ScreenScheduler getScheduler() {
            return delegate.getScheduler();
        }

    }

    /** A surface that remembers every apply and title before handing them on. */
    private static final class Counting implements ScreenSurface {

        private final ScreenView view;
        private final ScreenSurface delegate;
        private final List<List<ElementPatch>> applies = new ArrayList<>();
        private final List<String> titles = new ArrayList<>();

        private Counting(ScreenView view, ScreenSurface delegate) {
            this.view = view;
            this.delegate = delegate;
        }

        @Override
        public void apply(@Nonnull List<ElementPatch> patch) {
            applies.add(new ArrayList<>(patch));
            delegate.apply(patch);
        }

        @Override
        public void setTitle(@Nonnull String title) {
            titles.add(title);
            delegate.setTitle(title);
        }

        @Override
        public void close(@Nonnull CloseReason reason) {
            delegate.close(reason);
        }

        @Override
        public boolean isBackedBy(@Nullable Object nativeWindow) {
            return nativeWindow == this || delegate.isBackedBy(nativeWindow);
        }

        @Override
        public void suspended() {
            delegate.suspended();
        }

        @Override
        public void released(@Nonnull CloseReason reason) {
            delegate.released(reason);
        }

    }

}
