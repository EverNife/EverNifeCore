package br.com.finalcraft.evernifecore.minecraft.gui.component;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.minecraft.gui.Gui;
import br.com.finalcraft.evernifecore.minecraft.gui.icons.DefaultIcons;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.Icon;
import br.com.finalcraft.evernifecore.minecraft.gui.layout.LayoutBase;
import br.com.finalcraft.evernifecore.minecraft.gui.model.SlotSet;
import br.com.finalcraft.evernifecore.minecraft.gui.state.State;
import br.com.finalcraft.evernifecore.minecraft.gui.view.ClickContext;
import br.com.finalcraft.evernifecore.minecraft.gui.view.GuiView;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A list of data poured into a region of the screen, one entry per slot.
 *
 * <p>The page size is the size of the region and nothing else, so a list of forty entries in a region
 * of seven slots paginates on its own - and an admin who widens the region in the yml widens the page
 * with it, without a line of Java. The page itself lives in the view, so two players browsing the same
 * screen never move each other.</p>
 *
 * <p>What makes the list draw again is a state it {@link #dependsOn(State...)}, a page turn, or a
 * refresh; the source is never polled. Drawing again is cheap either way: the commit at the end of the
 * tick writes only the slots whose rendered item actually changed, so a list that answered the same
 * entries costs zero writes.</p>
 *
 * <p>An entry whose render function throws costs that one entry: it is logged with its index and the
 * rest of the page is drawn. A single bad row in a config-driven list never takes the screen down.</p>
 */
public final class ListComponent<T, L extends LayoutBase> {

    /** A source that answers one page at a time, for a catalogue too big to materialise. */
    public interface PageSource<T> {

        /** The entries of {@code page}, counting from 1, with at most {@code pageSize} of them. */
        List<T> get(int page, int pageSize);

    }

    private final Gui<L> gui;
    private final Supplier<List<T>> materialized;
    private final PageSource<T> paged;
    private Supplier<Integer> total;

    private SlotSet region;
    private Icon template;
    private BiConsumer<T, Icon> renderer;

    private SlotSet previousSlots;
    private SlotSet nextSlots;
    private Icon previousIcon;
    private Icon nextIcon;

    private final List<State<?>> dependencies = new ArrayList<>();
    private Pager shared;

    public ListComponent(@Nonnull Gui<L> gui, @Nullable Supplier<List<T>> materialized,
                         @Nullable PageSource<T> paged) {
        this.gui = gui;
        this.materialized = materialized;
        this.paged = paged;
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Declaration
    // -----------------------------------------------------------------------------------------------------------------

    /** Where the entries go, as raw slots or as a shape - see {@code Slots}. */
    @Nonnull
    public ListComponent<T, L> into(@Nonnull SlotSet region) {
        this.region = region;
        return this;
    }

    /**
     * Where the entries go, named by a layout icon: that icon is the TEMPLATE every entry starts from,
     * and the slots the file gave it are the region they are poured into.
     */
    @Nonnull
    public ListComponent<T, L> into(@Nonnull Function<L, Icon> template) {
        LayoutBase.PlacedIcon placed = gui.takeOver(template);
        this.region = placed.getSlots();
        this.template = placed.getIcon();
        return this;
    }

    /** What each entry looks like and does. The icon handed over is that entry's own copy of the template. */
    @Nonnull
    public ListComponent<T, L> render(@Nonnull BiConsumer<T, Icon> renderer) {
        this.renderer = renderer;
        return this;
    }

    /** How many entries the source has, which is the only thing a page-at-a-time source cannot answer. */
    @Nonnull
    public ListComponent<T, L> total(@Nonnull Supplier<Integer> total) {
        this.total = total;
        return this;
    }

    /** The page buttons, on raw slots, drawn with the framework's own arrows. */
    @Nonnull
    public ListComponent<T, L> pagedBy(@Nullable SlotSet previous, @Nullable SlotSet next) {
        this.previousSlots = previous;
        this.nextSlots = next;
        return this;
    }

    /** The page buttons, named by the layout icons that carry their look and their slots. */
    @Nonnull
    public ListComponent<T, L> pagedBy(@Nonnull Function<L, Icon> previous, @Nonnull Function<L, Icon> next) {
        LayoutBase.PlacedIcon back = gui.takeOver(previous);
        LayoutBase.PlacedIcon forward = gui.takeOver(next);
        this.previousSlots = back.getSlots();
        this.previousIcon = back.getIcon();
        this.nextSlots = forward.getSlots();
        this.nextIcon = forward.getIcon();
        return this;
    }

    /** States that make this list draw again - a filter, a tab, a sort order. */
    @Nonnull
    public ListComponent<T, L> dependsOn(@Nonnull State<?>... states) {
        Collections.addAll(dependencies, states);
        return this;
    }

    /**
     * Browses through {@code pager} instead of one of this list's own, which is what lets the title
     * read the page - and what makes several lists turn together.
     *
     * <p>A pager handed in here is shared by every viewer of this screen, exactly like a
     * {@code State.of(...)} declared outside. A screen built once and opened for many players wants
     * the default instead.</p>
     */
    @Nonnull
    public ListComponent<T, L> pager(@Nonnull Pager pager) {
        this.shared = pager;
        return this;
    }

    @Nonnull
    public Gui<L> getGui() {
        return gui;
    }

    /** Opens the screen this list belongs to - the chain's way out. */
    @Nonnull
    public CompletableFuture<GuiView> open(@Nonnull Player player) {
        return gui.open(player);
    }

    // -----------------------------------------------------------------------------------------------------------------
    //  Rendering
    // -----------------------------------------------------------------------------------------------------------------

    /** Builds this viewer's own page cursor and draws the list into their view. */
    public void bind(@Nonnull GuiComponent component) {
        if (region == null) {
            throw new IllegalStateException("A gui list has no region to pour into. Say where it goes with "
                    + "into(Slots.box(...)) or into(l -> l.TEMPLATE_ICON).");
        }
        if (renderer == null) {
            throw new IllegalStateException("A gui list has no render function, so its entries have no "
                    + "appearance. Add render((entry, icon) -> ...).");
        }
        if (paged != null && total == null) {
            throw new IllegalStateException("A page-at-a-time gui list needs total(...): one page cannot "
                    + "say how many pages there are, so nothing knows when to stop.");
        }

        final Pager pager = shared != null ? shared : new Pager();
        component.remember(pager);
        for (State<?> dependency : dependencies) {
            component.remember(dependency);
        }
        component.render(writer -> renderPage(writer, pager));
    }

    private void renderPage(SlotWriter writer, Pager pager) {
        SlotSet slots = region.resolve(writer.getGeometry());
        int pageSize = slots.size();
        if (pageSize == 0) {
            pager.measure(0, 0);
            return;
        }

        List<T> entries;
        if (paged != null) {
            Integer counted = total.get();
            pager.measure(pageSize, counted == null ? 0 : counted);
            entries = paged.get(pager.getPage(), pageSize);
        } else {
            List<T> all = materialized.get();
            pager.measure(pageSize, all == null ? 0 : all.size());
            int from = (pager.getPage() - 1) * pageSize;
            entries = all == null ? Collections.<T>emptyList()
                    : all.subList(Math.min(from, all.size()), Math.min(from + pageSize, all.size()));
        }

        int[] target = slots.toArray();
        int offset = (pager.getPage() - 1) * pageSize;
        for (int index = 0; index < entries.size() && index < target.length; index++) {
            Icon icon = template == null ? Icon.empty() : template.copy();
            try {
                renderer.accept(entries.get(index), icon);
            } catch (Throwable failure) {
                EverNifeCore.getLog().warning("Entry " + (offset + index) + " of a gui list was left out: "
                        + failure + ". The other entries were drawn.");
                continue;
            }
            writer.icon(target[index], icon);
        }

        renderPageButtons(writer, pager);
    }

    /** The arrows, drawn only while there is somewhere to go: one page needs no navigation. */
    private void renderPageButtons(SlotWriter writer, Pager pager) {
        if (pager.getTotalPages() <= 1) {
            return;
        }
        if (previousSlots != null) {
            writer.icon(previousSlots, button(previousIcon, true, context -> pager.previous()));
        }
        if (nextSlots != null) {
            writer.icon(nextSlots, button(nextIcon, false, context -> pager.next()));
        }
    }

    private Icon button(Icon declared, boolean backwards, Consumer<ClickContext> onClick) {
        Icon icon = declared != null ? declared.copy()
                : backwards ? DefaultIcons.previousPage() : DefaultIcons.nextPage();
        return icon.onClick(onClick);
    }

}
