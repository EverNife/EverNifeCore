package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.fancytext.MessagePlaceholders;
import br.com.finalcraft.evernifecore.fancytext.MessageScope;
import br.com.finalcraft.evernifecore.fancytext.RenderContext;
import br.com.finalcraft.evernifecore.locale.ChainPiece;
import br.com.finalcraft.evernifecore.locale.ILocaleMessageBase;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.pageviewer.nav.PageNavigation;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageContext;
import br.com.finalcraft.evernifecore.pageviewer.theme.PageTheme;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.ranking.RankedEntry;
import br.com.finalcraft.evernifecore.ranking.RankedList;
import br.com.finalcraft.evernifecore.ranking.RankedLists;
import br.com.finalcraft.evernifecore.ranking.RankedSnapshot;
import br.com.finalcraft.evernifecore.time.ECScheduler;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The chat renderer of a {@link RankedList}: header, one line per entry, footer, theme and navigation, built for each
 * reader. The list holds the read and the values every reader shares ({@code ${number}}, {@code ${value}},
 * {@code ${player}} and every {@code addValue}); {@link Builder#addViewerPlaceholder} is the level resolved per entry
 * AND per reader, which is why it is a method of its own.
 *
 * <pre>{@code
 * PageViewer.of(topMiner).setFormatLine("§7#${number} §e${player} §2LvL §a${value}").build().send(sender);
 * }</pre>
 *
 * <p>A list with a plain source is delivered inline, on the sender's thread. A list with an async source is
 * delivered when its read lands, on the main thread - the command scope ({@code ${label}}) is captured at the
 * {@code send}.</p>
 */
public final class PageViewer<T> {

    // The renderer each named list was last built with, by list id. A list the registry replaced or dropped is
    // answered null and pruned on the next ask, so at most one stale renderer per id outlives its list.
    private static final Map<String, PageViewer<?>> ATTACHED = new ConcurrentHashMap<>();

    // What a session handle is derived from, so re-sending the same page finds the reader's own session instead of
    // opening a second one beside it.
    private final UUID instanceId = UUID.randomUUID();

    private final RankedList<T> list;
    private final List<ChainPiece> formatHeader;
    private final RowTemplate<T> formatLine;
    private final List<ChainPiece> formatFooter;
    private final PageTheme theme;
    private final PageNavigation navigation;
    private final int pageSize;
    private final Map<String, BiFunction<T, FCommandSender, Object>> viewerKeys;
    private final RegexReplacer<RankedEntry<T>> rowReplacer;

    private PageViewer(BuilderImp<T> builder) {
        this.list = builder.list;
        this.formatHeader = builder.formatHeader;
        this.formatLine = builder.formatLine;
        this.formatFooter = builder.formatFooter;
        this.theme = builder.theme;
        this.navigation = builder.navigation != null ? builder.navigation
                : list.getId() != null ? PageNavigation.registered(list.getId()) : PageNavigation.session();
        this.pageSize = builder.pageSize;
        this.viewerKeys = new LinkedHashMap<>(builder.viewerKeys);
        this.rowReplacer = new RegexReplacer<>(Closures.DOLLAR_CURLY);
        for (String key : list.getValueKeys()) {
            //a key the line never cites is never asked, so a declared value nobody writes is never computed
            rowReplacer.addParser(key, entry -> String.valueOf(list.valueOf(entry, key)));
        }
    }

    public static <T> Builder<T> of(RankedList<T> list) {
        return new BuilderImp<>(list);
    }

    /**
     * The chat renderer the last {@code PageViewer.of(list).build()} attached to {@code list} - what
     * {@code /ecpage <id>} sends; {@code null} for an anonymous list, one never paged, or one the registry no longer
     * holds under its id.
     */
    public static @Nullable PageViewer<?> attachedTo(@Nullable RankedList<?> list) {
        if (list == null || list.getId() == null) {
            return null;
        }
        PageViewer<?> viewer = ATTACHED.get(list.getId());
        if (viewer == null || viewer.list != list) {
            return null;
        }
        if (RankedLists.find(list.getId()) != list) {
            ATTACHED.remove(list.getId(), viewer);
            return null;
        }
        return viewer;
    }

    public RankedList<T> getList() {
        return list;
    }

    /** This page's identity for as long as it lives, which is what a session handle is derived from. */
    public UUID getInstanceId() {
        return instanceId;
    }

    // ------------------------------------------------------------------
    //  Sending
    // ------------------------------------------------------------------

    public void send(@Nonnull FCommandSender... recipients) {
        send(1, recipients);
    }

    public void send(@Nullable Integer page, @Nonnull FCommandSender... recipients) {
        send((RenderContext) null, page == null ? 1 : page, recipients);
    }

    /**
     * Sends carrying an explicit {@link RenderContext}, whose command scope wins over the one open on this thread. This
     * is how a page reached through {@code /ecpage} still names the command that produced it wherever a line cites
     * {@code ${label}}.
     */
    public void send(@Nullable RenderContext context, int page, @Nonnull FCommandSender... recipients) {
        RenderContext captured = captured(context);
        whenRead(snapshot -> {
            int current = boundPage(page, snapshot);
            deliver(snapshot, captured, navigation, current, (current - 1) * pageSize, current * pageSize, recipients);
        });
    }

    public void send(@Nullable PageVisualization pageVisualization, @Nonnull FCommandSender... recipients) {
        if (pageVisualization == null) {
            send(1, recipients);
            return;
        }
        RenderContext captured = captured(null);
        whenRead(snapshot -> {
            if (pageVisualization.isShowAll()) {
                //everything the list kept, with no bar under it: every entry is already above it, so an arrow to
                //"page 2" would offer a subset of what was just read
                deliver(snapshot, captured, PageNavigation.none(), 1, 0, snapshot.getShownCount(), recipients);
                return;
            }
            int firstPage = boundPage(pageVisualization.getPageStart(), snapshot);
            int lastPage = boundPage(pageVisualization.getPageEnd(), snapshot);
            deliver(snapshot, captured, navigation, firstPage, (firstPage - 1) * pageSize, lastPage * pageSize, recipients);
        });
    }

    // Captured at the send, before any wait: an async list is delivered later, when the scope of this thread is gone.
    private static RenderContext captured(@Nullable RenderContext context) {
        return context != null ? context : RenderContext.of(null, MessageScope.currentOrEmpty());
    }

    private void whenRead(Consumer<RankedSnapshot<T>> delivery) {
        CompletableFuture<RankedSnapshot<T>> read = list.snapshot();
        if (read.isDone()) {
            delivery.accept(readNow(read));
            return;
        }
        read.whenComplete((snapshot, failure) -> {
            RankedSnapshot<T> shown = snapshot;
            if (failure != null) {
                shown = list.snapshotIfPresent();
                EverNifeCore.getLog().warning("The read of the ranked list " + describeList() + " failed"
                        + (shown == null ? ", and there is no previous read to show: " : "; showing the previous read: ")
                        + failure);
            }
            if (shown != null) {
                RankedSnapshot<T> delivered = shown;
                //the chat is never sent from the storage thread that completed the read
                ECScheduler.onMainThread(() -> delivery.accept(delivered));
            }
        });
    }

    private RankedSnapshot<T> readNow(CompletableFuture<RankedSnapshot<T>> read) {
        try {
            return read.join();
        } catch (CompletionException failed) {
            RankedSnapshot<T> previous = list.snapshotIfPresent();
            if (previous != null) {
                return previous;
            }
            //a plain source that throws reaches the command as it always did
            throw failed.getCause() instanceof RuntimeException ? (RuntimeException) failed.getCause() : failed;
        }
    }

    private String describeList() {
        return list.getId() != null ? list.getId() : "<anonymous " + list.getTarget().getSimpleName() + ">";
    }

    private void deliver(RankedSnapshot<T> snapshot,
                         RenderContext context,
                         PageNavigation bar,
                         int page,
                         int rowStart,
                         int rowEnd,
                         FCommandSender... recipients) {

        List<RankedEntry<T>> entries = snapshot.getEntries();
        int from = Math.max(0, rowStart);
        int to = Math.min(entries.size(), rowEnd);
        int lastPage = lastPageOf(snapshot);

        //One page per recipient. The loop variable is named for what it is - a single recipient - so that handing the
        //whole array to a send() inside the loop reads as the mistake it would be.
        for (FCommandSender recipient : recipients) {
            PageContext pageContext = new PageContext(this, snapshot, page, lastPage, list.isAboutPlayers(),
                    recipient, context.getMessageContext());

            for (FancyText line : theme.header(pageContext)) {
                line.send(context, recipient);
            }
            for (ChainPiece piece : formatHeader) {
                piece.renderFor(recipient).send(context, recipient);
            }

            if (entries.isEmpty()) {
                FancyText empty = theme.empty(pageContext);
                if (empty != null) {
                    empty.send(context, recipient);
                }
            } else {
                for (int index = from; index < to; index++) {
                    renderRow(entries.get(index), recipient).send(context, recipient);
                }
                FancyText renderedBar = bar.render(pageContext);
                if (renderedBar != null) {
                    renderedBar.send(context, recipient);
                }
            }

            for (ChainPiece piece : formatFooter) {
                piece.renderFor(recipient).send(context, recipient);
            }
            for (FancyText line : theme.footer(pageContext)) {
                line.send(context, recipient);
            }
        }
    }

    /**
     * One line's text for one reader: the template in their language, the list's values baked in, and the
     * {@code viewer} keys declared so the render resolves the ones it cites.
     */
    private FancyText renderRow(RankedEntry<T> entry, @Nullable FCommandSender reader) {
        FancyText text = formatLine.textFor(entry.getObject(), reader);

        //the list's values: memoized on the entry, so N readers of this line still cost one call
        text.bake(payload -> rowReplacer.apply(payload, entry));

        //'viewer' keys: declared, not computed - the engine resolves the ones the line cites, once per render
        for (Map.Entry<String, BiFunction<T, FCommandSender, Object>> declaration : viewerKeys.entrySet()) {
            BiFunction<T, FCommandSender, Object> function = declaration.getValue();
            text.addParser(declaration.getKey(), context -> function.apply(entry.getObject(), context.getSender()));
        }
        return text;
    }

    private int lastPageOf(RankedSnapshot<T> snapshot) {
        return Math.max(1, (int) Math.ceil(snapshot.getShownCount() / (double) pageSize));
    }

    private int boundPage(int page, RankedSnapshot<T> snapshot) {
        return Math.min(Math.max(page, 1), lastPageOf(snapshot));
    }

    // ------------------------------------------------------------------
    //  Builder
    // ------------------------------------------------------------------

    public interface Builder<T> {

        Builder<T> setFormatHeader(LocaleMessage... header);

        /**
         * A header out of anything sendable, which is what {@code custom()} hands back: a message with a hover, a
         * click, or two locale messages appended into one line.
         */
        Builder<T> setFormatHeader(ILocaleMessageBase... header);

        Builder<T> setFormatHeader(FancyText... header);

        Builder<T> setFormatHeader(String... header);

        Builder<T> setFormatLine(LocaleMessage line);

        /** The line out of anything sendable - see {@link #setFormatHeader(ILocaleMessageBase...)}. */
        Builder<T> setFormatLine(ILocaleMessageBase line);

        Builder<T> setFormatLine(FancyText line);

        Builder<T> setFormatLine(String line);

        /**
         * The line built per entry. What the function hands back is copied before it is used, so a function that
         * memoizes - one text per rank, one per type - is safe: the copy is what gets this entry's values baked in.
         */
        Builder<T> setFormatLine(Function<T, FancyText> line);

        Builder<T> setFormatFooter(LocaleMessage... footer);

        /** A footer out of anything sendable - see {@link #setFormatHeader(ILocaleMessageBase...)}. */
        Builder<T> setFormatFooter(ILocaleMessageBase... footer);

        Builder<T> setFormatFooter(FancyText... footer);

        Builder<T> setFormatFooter(String... footer);

        /** Entries per page. Defaults to 10. */
        Builder<T> setPageSize(int pageSize);

        /** The chrome around the entries - see {@code PageTheme.classic()}, the default. */
        Builder<T> theme(PageTheme theme);

        /** How a reader reaches the other pages. Defaults to {@code /ecpage <list id>} when the list has one, else a session. */
        Builder<T> navigation(PageNavigation navigation);

        /**
         * Declares {@code ${key}} (case-insensitive) resolved against the entry AND the reader, in the render of each
         * of them - one call per line per recipient. A value every reader shares belongs on the list
         * ({@code RankedList.Builder.addValue}), which costs one call per entry.
         */
        Builder<T> addViewerPlaceholder(String key, BiFunction<T, FCommandSender, Object> function);

        /** Builds and, when the list has an id, becomes that list's chat renderer: what {@code /ecpage <id> <page>} sends. */
        PageViewer<T> build();
    }

    private static final class BuilderImp<T> implements Builder<T> {

        private final RankedList<T> list;

        //ChainPiece is how a header line answers for the reader in front of it, and it stays an implementation
        //detail: nothing public here is spelled in terms of it.
        private List<ChainPiece> formatHeader = Collections.emptyList();
        private RowTemplate<T> formatLine = null;
        private List<ChainPiece> formatFooter = Collections.emptyList();

        private PageTheme theme = PageTheme.classic();
        private PageNavigation navigation = null;
        private int pageSize = 10;
        private final Map<String, BiFunction<T, FCommandSender, Object>> viewerKeys = new LinkedHashMap<>();

        private BuilderImp(RankedList<T> list) {
            this.list = list;
        }

        @Override
        public Builder<T> setFormatHeader(LocaleMessage... header) {
            this.formatHeader = piecesOf(header);
            return this;
        }

        @Override
        public Builder<T> setFormatHeader(ILocaleMessageBase... header) {
            this.formatHeader = piecesOf(header);
            return this;
        }

        @Override
        public Builder<T> setFormatHeader(FancyText... header) {
            this.formatHeader = piecesOf(header);
            return this;
        }

        @Override
        public Builder<T> setFormatHeader(String... header) {
            this.formatHeader = piecesOf(header);
            return this;
        }

        @Override
        public Builder<T> setFormatLine(LocaleMessage line) {
            this.formatLine = (object, reader) -> line.getFancyText(reader).copy();
            return this;
        }

        @Override
        public Builder<T> setFormatLine(ILocaleMessageBase line) {
            this.formatLine = (object, reader) -> line.getFancyText(reader).copy();
            return this;
        }

        @Override
        public Builder<T> setFormatLine(FancyText line) {
            this.formatLine = (object, reader) -> line.copy();//new instance for every call
            return this;
        }

        @Override
        public Builder<T> setFormatLine(String line) {
            this.formatLine = (object, reader) -> new FancySegment(line);//new instance for every call
            return this;
        }

        @Override
        public Builder<T> setFormatLine(Function<T, FancyText> line) {
            //copied here, not left to the caller: a line baked with this entry's values must not be a text somebody kept
            this.formatLine = (object, reader) -> line.apply(object).copy();
            return this;
        }

        @Override
        public Builder<T> setFormatFooter(LocaleMessage... footer) {
            this.formatFooter = piecesOf(footer);
            return this;
        }

        @Override
        public Builder<T> setFormatFooter(ILocaleMessageBase... footer) {
            this.formatFooter = piecesOf(footer);
            return this;
        }

        @Override
        public Builder<T> setFormatFooter(FancyText... footer) {
            this.formatFooter = piecesOf(footer);
            return this;
        }

        @Override
        public Builder<T> setFormatFooter(String... footer) {
            this.formatFooter = piecesOf(footer);
            return this;
        }

        @Override
        public Builder<T> setPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        @Override
        public Builder<T> theme(PageTheme theme) {
            this.theme = theme;
            return this;
        }

        @Override
        public Builder<T> navigation(PageNavigation navigation) {
            this.navigation = navigation;
            return this;
        }

        @Override
        public Builder<T> addViewerPlaceholder(String key, BiFunction<T, FCommandSender, Object> function) {
            MessagePlaceholders.warnOnceIfDelimited(key);
            viewerKeys.put(key, function);
            return this;
        }

        @Override
        public PageViewer<T> build() {
            if (formatLine == null) {
                formatLine = defaultLine();
            }
            PageViewer<T> pageViewer = new PageViewer<>(this);
            if (list.getId() != null) {
                ATTACHED.put(list.getId(), pageViewer);
            }
            return pageViewer;
        }

        /**
         * The line for a page that never said what a line looks like: the number, then whoever the entry is about,
         * then what the list was ordered by - built from what the list actually answers, so a reader never looks at a
         * raw token instead of their data.
         */
        private RowTemplate<T> defaultLine() {
            List<String> written = new ArrayList<>(2);
            if (list.isAboutPlayers()) {
                written.add("§e${player}");
            }
            if (list.isOrdered() || !list.isAboutPlayers()) {
                written.add("§a${value}");
            }
            String text = "§7#  ${number}:   " + String.join("§f - ", written);
            return (object, reader) -> new FancySegment(text);
        }

        private static List<ChainPiece> piecesOf(LocaleMessage... messages) {
            List<ChainPiece> pieces = new ArrayList<>(messages.length);
            for (LocaleMessage message : messages) {
                pieces.add(ChainPiece.of(message));
            }
            return pieces;
        }

        private static List<ChainPiece> piecesOf(ILocaleMessageBase... messages) {
            List<ChainPiece> pieces = new ArrayList<>(messages.length);
            for (ILocaleMessageBase message : messages) {
                pieces.add(ChainPiece.of(message));
            }
            return pieces;
        }

        private static List<ChainPiece> piecesOf(FancyText... texts) {
            List<ChainPiece> pieces = new ArrayList<>(texts.length);
            for (FancyText text : texts) {
                pieces.add(ChainPiece.of(text));
            }
            return pieces;
        }

        private static List<ChainPiece> piecesOf(String... lines) {
            return piecesOf(Arrays.stream(lines).<FancyText>map(FancySegment::new).toArray(FancyText[]::new));
        }
    }
}
