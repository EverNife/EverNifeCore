package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
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
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A paginated list sent to chat. What it caches is one ordered read of the source - the expensive
 * half, and the half nobody's language changes - while the text is built for each reader, which is
 * what lets a page be translated, highlight the reader's own line and still cost one source read.
 *
 * <p>Two levels of placeholder answer for a line: {@code addRowPlaceholder} is computed once per
 * entry and shared by every reader of the page, {@code addViewerPlaceholder} once per entry per
 * reader. The cost is in the name of the method, so nobody pays it without writing it.</p>
 */
public class PageViewer<OBJ> {

    //Keys every page answers for on its own, unless the caller declared one of them itself.
    private static final String NUMBER_KEY = "number";
    private static final String VALUE_KEY = "value";
    private static final String PLAYER_KEY = "player";

    private static final CachePolicy DEFAULT_CACHE_POLICY = CachePolicy.ttl(Duration.ofSeconds(5));

    private final @Nullable String id;

    // What a session handle is derived from, so re-sending the same page finds the reader's own
    // session instead of opening a second one beside it.
    private final UUID instanceId = UUID.randomUUID();

    private final Class<OBJ> target;
    private final boolean aboutPlayers;
    private final Supplier<List<OBJ>> source;
    private final Function<List<OBJ>, Integer> maxEntries;
    private final @Nullable PageOrder<OBJ> order;
    private final List<ChainPiece> formatHeader;
    private final RowTemplate<OBJ> formatLine;
    private final List<ChainPiece> formatFooter;
    private final PageTheme theme;
    private final PageNavigation navigation;
    private final CachePolicy cachePolicy;
    private final int pageSize;
    private final Map<String, BiFunction<OBJ, FCommandSender, Object>> viewerKeys;
    private final RegexReplacer<Row<OBJ>> rowReplacer;

    // Published by reference and never mutated afterwards, so a send that has one in hand is
    // reading a whole page: header, lines and count all come from the same read.
    private volatile PageSnapshot<OBJ> cachedSnapshot;

    PageViewer(BuilderImp<OBJ> builder) {
        this.id = builder.id;
        this.target = builder.target;
        this.aboutPlayers = FPlayer.class.isAssignableFrom(builder.target)
                || IPlayerData.class.isAssignableFrom(builder.target);
        this.source = builder.source;
        this.maxEntries = builder.maxEntries;
        this.order = builder.order;
        this.formatHeader = builder.formatHeader;
        this.formatLine = builder.formatLine;
        this.formatFooter = builder.formatFooter;
        this.theme = builder.theme;
        this.navigation = builder.navigation != null ? builder.navigation
                : builder.id != null ? PageNavigation.registered(builder.id) : PageNavigation.session();
        this.cachePolicy = builder.cachePolicy;
        this.pageSize = builder.pageSize;
        this.viewerKeys = new LinkedHashMap<>(builder.viewerKeys);
        this.rowReplacer = buildRowReplacer(builder.rowKeys);
    }

    public @Nullable String getId() {
        return id;
    }

    /** This page's identity for as long as it lives, which is what a session handle is derived from. */
    public UUID getInstanceId() {
        return instanceId;
    }

    public Class<OBJ> getTarget() {
        return target;
    }

    // ------------------------------------------------------------------
    //  The cached read
    // ------------------------------------------------------------------

    /** The current read of the source, taken again if the cache policy no longer vouches for it. */
    public PageSnapshot<OBJ> snapshot() {
        PageSnapshot<OBJ> current = cachedSnapshot;
        if (cachePolicy.isValid(current)) {
            return current;
        }
        synchronized (this) {
            if (cachePolicy.isValid(cachedSnapshot)) {
                return cachedSnapshot;
            }
            PageSnapshot<OBJ> built = buildSnapshot();
            //held only if the policy would vouch for it again: a policy that never revalidates - or
            //a lifetime already spent - would otherwise keep a read nobody may serve, and a page of
            //ten thousand rows is ten thousand rows of memory nobody will ever look at
            cachedSnapshot = cachePolicy.isValid(built) ? built : null;
            return built;
        }
    }

    /**
     * Discards the cached read, so the next send consults the source again.
     *
     * <p>Under the same lock {@link #snapshot()} takes: without it, a read already inside the lock
     * can publish what it built after this call returned, and with {@code CachePolicy.manual()} that
     * stale read is then served forever.</p>
     */
    public synchronized void invalidate() {
        this.cachedSnapshot = null;
    }

    private PageSnapshot<OBJ> buildSnapshot() {
        List<OBJ> entries = source.get();

        //The ceiling is decided against the RAW list, so a page whose ceiling comes from its own
        //config - or from how much came back - reads what it actually asked about.
        int ceiling = Math.max(0, maxEntries.apply(entries));

        List<Row<OBJ>> ordered = new ArrayList<>(entries.size());
        for (OBJ entry : entries) {
            ordered.add(new Row<>(entry, order == null ? null : order.valueOf(entry), 0));
        }
        if (order != null) {
            order.sort(ordered);
        }

        int kept = Math.min(ceiling, ordered.size());
        List<Row<OBJ>> rows = new ArrayList<>(kept);
        for (int index = 0; index < kept; index++) {
            rows.add(ordered.get(index).at(index + 1));
        }

        return new PageSnapshot<>(rows, ordered.size(), System.currentTimeMillis());
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
     * Sends carrying an explicit {@link RenderContext}, whose command scope wins over the one open on
     * this thread. This is how a page reached through {@code /ecpage} still names the command that
     * produced it wherever a line cites {@code ${label}}.
     */
    public void send(@Nullable RenderContext context, int page, @Nonnull FCommandSender... recipients) {
        PageSnapshot<OBJ> snapshot = snapshot();
        int current = boundPage(page, snapshot);
        deliver(snapshot, context, navigation, current,
                (current - 1) * pageSize, current * pageSize, recipients);
    }

    public void send(@Nullable PageVisualization pageVisualization, @Nonnull FCommandSender... recipients) {
        if (pageVisualization == null) {
            send(1, recipients);
            return;
        }

        PageSnapshot<OBJ> snapshot = snapshot();

        if (pageVisualization.isShowAll()) {
            //Everything the page holds, which is everything maxEntries let in - the count in the
            //header still says how many the source returned. No bar under it: every row is already
            //above it, so an arrow to "page 2" would offer a subset of what was just read.
            deliver(snapshot, null, PageNavigation.none(), 1, 0, snapshot.getShownCount(), recipients);
            return;
        }

        int firstPage = boundPage(pageVisualization.getPageStart(), snapshot);
        int lastPage = boundPage(pageVisualization.getPageEnd(), snapshot);
        deliver(snapshot, null, navigation, firstPage,
                (firstPage - 1) * pageSize, lastPage * pageSize, recipients);
    }

    private void deliver(PageSnapshot<OBJ> snapshot,
                         @Nullable RenderContext explicitContext,
                         PageNavigation bar,
                         int page,
                         int rowStart,
                         int rowEnd,
                         FCommandSender... recipients) {

        RenderContext context = explicitContext != null
                ? explicitContext
                : RenderContext.of(null, MessageScope.currentOrEmpty());

        List<Row<OBJ>> rows = snapshot.getRows();
        int from = Math.max(0, rowStart);
        int to = Math.min(rows.size(), rowEnd);
        int lastPage = lastPageOf(snapshot);

        //One page per recipient. The loop variable is named for what it is - a single recipient - so
        //that handing the whole array to a send() inside the loop reads as the mistake it would be.
        for (FCommandSender recipient : recipients) {
            PageContext pageContext = new PageContext(
                    this,
                    page,
                    lastPage,
                    snapshot.getShownCount(),
                    snapshot.getTotalCount(),
                    aboutPlayers,
                    recipient,
                    context.getMessageContext(),
                    () -> rankOf(snapshot, recipient)
            );

            for (FancyText line : theme.header(pageContext)) {
                line.send(context, recipient);
            }
            for (ChainPiece piece : formatHeader) {
                piece.renderFor(recipient).send(context, recipient);
            }

            if (rows.isEmpty()) {
                FancyText empty = theme.empty(pageContext);
                if (empty != null) {
                    empty.send(context, recipient);
                }
            } else {
                for (int index = from; index < to; index++) {
                    renderRow(rows.get(index), recipient).send(context, recipient);
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
     * One line's text for one reader: the template in their language, this row's {@code row} keys
     * baked in, and the {@code viewer} keys declared so the render resolves the ones it cites.
     */
    private FancyText renderRow(Row<OBJ> row, @Nullable FCommandSender reader) {
        FancyText text = formatLine.textFor(row.getObject(), reader);

        //'row' keys: memoized on the row itself, so N readers of this line still cost one call
        text.bake(payload -> rowReplacer.apply(payload, row));

        //'viewer' keys: declared, not computed - a key the line never cites is never resolved, and
        //the engine resolves it once per render (RenderContext.resolveOnce)
        for (Map.Entry<String, BiFunction<OBJ, FCommandSender, Object>> declaration : viewerKeys.entrySet()) {
            BiFunction<OBJ, FCommandSender, Object> function = declaration.getValue();
            text.addParser(declaration.getKey(), context -> function.apply(row.getObject(), context.getSender()));
        }

        return text;
    }

    private int lastPageOf(PageSnapshot<OBJ> snapshot) {
        return Math.max(1, (int) Math.ceil(snapshot.getShownCount() / (double) pageSize));
    }

    private int boundPage(int page, PageSnapshot<OBJ> snapshot) {
        return Math.min(Math.max(page, 1), lastPageOf(snapshot));
    }

    /**
     * Where {@code reader} stands on this page, 1-based, or {@code null} when they are not on it.
     * A whole scan of the snapshot, which is why the key that answers it is only ever resolved where
     * the text cites it.
     */
    private @Nullable Integer rankOf(PageSnapshot<OBJ> snapshot, @Nullable FCommandSender reader) {
        if (!aboutPlayers || reader == null || reader.getUniqueId() == null) {
            return null;
        }
        UUID readerId = reader.getUniqueId();
        for (Row<OBJ> row : snapshot.getRows()) {
            if (readerId.equals(uniqueIdOf(row.getObject()))) {
                return row.getPosition();
            }
        }
        return null;
    }

    private static @Nullable UUID uniqueIdOf(Object object) {
        if (object instanceof FPlayer) return ((FPlayer) object).getUniqueId();
        if (object instanceof IPlayerData) return ((IPlayerData) object).getUniqueId();
        return null;
    }

    private static String nameOf(Object object) {
        if (object instanceof FPlayer) return ((FPlayer) object).getName();
        if (object instanceof IPlayerData) return ((IPlayerData) object).getName();
        return String.valueOf(object);
    }

    // ------------------------------------------------------------------
    //  The 'row' level replacer
    // ------------------------------------------------------------------

    /**
     * The replacer this page's lines are baked with: the caller's own declarations first, then the
     * keys the framework answers for, added only where the caller left them free.
     *
     * <p>Nothing here is match-driven by hand - the replacer walks the tokens the text actually
     * cites, so a key that is declared but never written is never computed.</p>
     */
    private RegexReplacer<Row<OBJ>> buildRowReplacer(Map<String, Function<OBJ, Object>> rowKeys) {
        RegexReplacer<Row<OBJ>> replacer = new RegexReplacer<>(Closures.DOLLAR_CURLY);

        for (Map.Entry<String, Function<OBJ, Object>> declaration : rowKeys.entrySet()) {
            Function<OBJ, Object> function = declaration.getValue();
            declare(replacer, declaration.getKey(), row -> function.apply(row.getObject()));
        }

        declareIfAbsent(replacer, NUMBER_KEY, Row::getPosition);

        if (order != null) {
            //Read off the row rather than extracted again: the ordering already asked this question.
            declareIfAbsent(replacer, VALUE_KEY, Row::getOrderValue);
        } else {
            //no ordering was declared, so there is no extracted value to read: what the entry says
            //about itself is the closest thing to one, and it beats handing the reader a raw token
            declareIfAbsent(replacer, VALUE_KEY, Row::getObject);
        }

        //One question, one answer: what the page targets decides this, and it goes on deciding it
        //when the list comes back empty.
        if (aboutPlayers) {
            declareIfAbsent(replacer, PLAYER_KEY, row -> nameOf(row.getObject()));
        }

        return replacer;
    }

    private static <OBJ> void declare(RegexReplacer<Row<OBJ>> replacer, String key, Function<Row<OBJ>, Object> function) {
        //The memo token is this declaration's own identity, so two pages declaring the same key never
        //share an answer. String.valueOf keeps a null value visible as text instead of leaving the
        //token raw, which in a click value would be a broken command rather than an ugly line.
        final Object declaration = new Object();
        replacer.addParser(key, row -> row.resolveOnce(declaration, () -> String.valueOf(function.apply(row))));
    }

    /**
     * Declares one of the framework's own keys only where the caller left it free: a page that
     * declared {@code value} (or {@code player}, or {@code number}) meant it, and the automatic answer
     * is the default, not an override of a more specific one.
     */
    private static <OBJ> void declareIfAbsent(RegexReplacer<Row<OBJ>> replacer, String key, Function<Row<OBJ>, Object> function) {
        //The provider indexes its keys lower-cased and these names are already lower case, so this
        //also catches a caller who declared the same key spelled with another case.
        if (replacer.getProvider().getParserMap().containsKey(key)) {
            return;
        }
        declare(replacer, key, function);
    }

    // ------------------------------------------------------------------
    //  Builder
    // ------------------------------------------------------------------

    public static <O> IStepSource<O> of(Class<O> target) {
        return new BuilderImp<>(target);
    }

    public interface IStepSource<O> {

        /**
         * Names this page, as {@code plugin:name}, so a link can point at it: {@code build()}
         * registers it and the navigation becomes {@code /ecpage <id> <page>}. A page whose content
         * depends on a command argument cannot be named, and falls back to an in-memory session.
         */
        IStepSource<O> id(String pageId);

        IStepLimit<O> source(Supplier<List<O>> source);
    }

    public interface IStepLimit<O> {

        /** Keeps at most {@code maxEntries}; what the source returned beyond it is counted, not shown. */
        IBuilder<O> maxEntries(int maxEntries);

        /**
         * The cap decided when the source is read, from the list it returned - for a page whose ceiling
         * lives in the plugin's own config, or depends on how much came back.
         */
        IBuilder<O> maxEntries(Function<List<O>, Integer> maxEntries);

        /** Every entry the source returned, however many. Say it out loud, because it is a real risk. */
        IBuilder<O> unlimitedEntries();
    }

    public interface IStepOrder<O> {

        IBuilder<O> ascending();

        IBuilder<O> descending();
    }

    public interface IBuilder<O> {

        /**
         * Orders the entries by the value extracted here, which is also what {@code ${value}} answers.
         * The direction is the one that is asked for, and a page that declares no order shows what its
         * source returned.
         */
        IStepOrder<O> orderBy(Function<O, ?> extractor);

        IBuilder<O> setFormatHeader(LocaleMessage... header);

        /**
         * A header out of anything sendable, which is what {@code custom()} hands back: a message
         * with a hover, a click, or two locale messages appended into one line.
         */
        IBuilder<O> setFormatHeader(ILocaleMessageBase... header);

        IBuilder<O> setFormatHeader(FancyText... header);

        IBuilder<O> setFormatHeader(String... header);

        IBuilder<O> setFormatLine(LocaleMessage line);

        /** The line out of anything sendable - see {@link #setFormatHeader(ILocaleMessageBase...)}. */
        IBuilder<O> setFormatLine(ILocaleMessageBase line);

        IBuilder<O> setFormatLine(FancyText line);

        IBuilder<O> setFormatLine(String line);

        /**
         * The line built per entry. What the function hands back is copied before it is used, so a
         * function that memoizes - one text per rank, one per type - is safe: the copy is what gets
         * this row's values baked into it, never the instance the caller kept.
         */
        IBuilder<O> setFormatLine(Function<O, FancyText> line);

        IBuilder<O> setFormatFooter(LocaleMessage... footer);

        /** A footer out of anything sendable - see {@link #setFormatHeader(ILocaleMessageBase...)}. */
        IBuilder<O> setFormatFooter(ILocaleMessageBase... footer);

        IBuilder<O> setFormatFooter(FancyText... footer);

        IBuilder<O> setFormatFooter(String... footer);

        IBuilder<O> setPageSize(int pageSize);

        /** The chrome around the entries - see {@code PageTheme.classic()}. */
        IBuilder<O> theme(PageTheme theme);

        /** How a reader reaches the other pages. Defaults to the id's link, or to a session. */
        IBuilder<O> navigation(PageNavigation navigation);

        /** How long one read of the source stays good for. Defaults to five seconds. */
        IBuilder<O> cache(CachePolicy cachePolicy);

        /**
         * Declares the value of {@code ${key}} (case-insensitive) on every line of this page. The key
         * is taken exactly as written, so it must be the bare name - {@code "version"}, never
         * {@code "%version%"} or {@code "${version}"}; a delimited one is registered like that, never
         * matches, and says so once in the console.
         *
         * <p>Resolved against the line's own object, once per line, and shared by every reader of that
         * line. A key the line never writes is never computed. {@code number}, {@code value} and
         * {@code player} are answered for automatically, unless declared here, in which case this
         * wins.</p>
         */
        IBuilder<O> addRowPlaceholder(String key, Function<O, Object> function);

        /**
         * The same, resolved against the line's object AND the reader, in the render of each of them.
         * This is the level that costs one call per line per recipient, which is why it is a method of
         * its own - {@code addRowPlaceholder} is the one to reach for by default.
         */
        IBuilder<O> addViewerPlaceholder(String key, BiFunction<O, FCommandSender, Object> function);

        PageViewer<O> build();
    }

    public static class BuilderImp<O> implements IStepSource<O>, IStepLimit<O>, IBuilder<O> {

        protected final Class<O> target;
        protected String id = null;
        protected Supplier<List<O>> source;
        protected Function<List<O>, Integer> maxEntries;
        protected PageOrder<O> order = null;

        //ChainPiece is how a header line answers for the reader in front of it, and it stays an
        //implementation detail: nothing public here is spelled in terms of it.
        private List<ChainPiece> formatHeader = Collections.emptyList();
        private RowTemplate<O> formatLine = null;
        private List<ChainPiece> formatFooter = Collections.emptyList();

        protected PageTheme theme = PageTheme.classic();
        protected PageNavigation navigation = null;
        protected CachePolicy cachePolicy = DEFAULT_CACHE_POLICY;
        protected int pageSize = 10;

        protected final Map<String, Function<O, Object>> rowKeys = new LinkedHashMap<>();
        protected final Map<String, BiFunction<O, FCommandSender, Object>> viewerKeys = new LinkedHashMap<>();

        protected BuilderImp(Class<O> target) {
            this.target = target;
        }

        @Override
        public BuilderImp<O> id(String pageId) {
            this.id = pageId;
            return this;
        }

        @Override
        public BuilderImp<O> source(Supplier<List<O>> source) {
            this.source = source;
            return this;
        }

        @Override
        public BuilderImp<O> maxEntries(int maxEntries) {
            this.maxEntries = entries -> maxEntries;
            return this;
        }

        @Override
        public BuilderImp<O> maxEntries(Function<List<O>, Integer> maxEntries) {
            this.maxEntries = maxEntries;
            return this;
        }

        @Override
        public BuilderImp<O> unlimitedEntries() {
            this.maxEntries = entries -> Integer.MAX_VALUE;
            return this;
        }

        @Override
        public IStepOrder<O> orderBy(Function<O, ?> extractor) {
            return new IStepOrder<O>() {
                @Override
                public IBuilder<O> ascending() {
                    order = new PageOrder<>(extractor, false);
                    return BuilderImp.this;
                }

                @Override
                public IBuilder<O> descending() {
                    order = new PageOrder<>(extractor, true);
                    return BuilderImp.this;
                }
            };
        }

        @Override
        public BuilderImp<O> setFormatHeader(LocaleMessage... header) {
            this.formatHeader = piecesOf(header);
            return this;
        }

        @Override
        public BuilderImp<O> setFormatHeader(ILocaleMessageBase... header) {
            this.formatHeader = piecesOf(header);
            return this;
        }

        @Override
        public BuilderImp<O> setFormatHeader(FancyText... header) {
            this.formatHeader = piecesOf(header);
            return this;
        }

        @Override
        public BuilderImp<O> setFormatHeader(String... header) {
            this.formatHeader = piecesOf(header);
            return this;
        }

        @Override
        public BuilderImp<O> setFormatLine(LocaleMessage line) {
            this.formatLine = (object, reader) -> line.getFancyText(reader).copy();
            return this;
        }

        @Override
        public BuilderImp<O> setFormatLine(ILocaleMessageBase line) {
            this.formatLine = (object, reader) -> line.getFancyText(reader).copy();
            return this;
        }

        @Override
        public BuilderImp<O> setFormatLine(FancyText line) {
            this.formatLine = (object, reader) -> line.copy();//new instance for every call
            return this;
        }

        @Override
        public BuilderImp<O> setFormatLine(String line) {
            this.formatLine = (object, reader) -> new FancySegment(line);//new instance for every call
            return this;
        }

        @Override
        public BuilderImp<O> setFormatLine(Function<O, FancyText> line) {
            //copied here, not left to the caller: every other overload hands back a fresh instance,
            //and a line that is baked with this row's values must not be a text somebody kept
            this.formatLine = (object, reader) -> line.apply(object).copy();
            return this;
        }

        @Override
        public BuilderImp<O> setFormatFooter(LocaleMessage... footer) {
            this.formatFooter = piecesOf(footer);
            return this;
        }

        @Override
        public BuilderImp<O> setFormatFooter(ILocaleMessageBase... footer) {
            this.formatFooter = piecesOf(footer);
            return this;
        }

        @Override
        public BuilderImp<O> setFormatFooter(FancyText... footer) {
            this.formatFooter = piecesOf(footer);
            return this;
        }

        @Override
        public BuilderImp<O> setFormatFooter(String... footer) {
            this.formatFooter = piecesOf(footer);
            return this;
        }

        @Override
        public BuilderImp<O> setPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        @Override
        public BuilderImp<O> theme(PageTheme theme) {
            this.theme = theme;
            return this;
        }

        @Override
        public BuilderImp<O> navigation(PageNavigation navigation) {
            this.navigation = navigation;
            return this;
        }

        @Override
        public BuilderImp<O> cache(CachePolicy cachePolicy) {
            this.cachePolicy = cachePolicy;
            return this;
        }

        @Override
        public BuilderImp<O> addRowPlaceholder(String key, Function<O, Object> function) {
            MessagePlaceholders.warnOnceIfDelimited(key);
            rowKeys.put(key, function);
            return this;
        }

        @Override
        public BuilderImp<O> addViewerPlaceholder(String key, BiFunction<O, FCommandSender, Object> function) {
            MessagePlaceholders.warnOnceIfDelimited(key);
            viewerKeys.put(key, function);
            return this;
        }

        @Override
        public PageViewer<O> build() {
            if (formatLine == null) {
                formatLine = defaultLine();
            }
            PageViewer<O> pageViewer = new PageViewer<>(this);
            if (id != null) {
                PageRegistry.register(id, pageViewer);
            }
            return pageViewer;
        }

        /**
         * The line for a page that never said what a line looks like: the number, then whoever the
         * entry is about, then what the page was ordered by.
         *
         * <p>Built from what this page actually declares. A page of strings with no order has no
         * {@code ${player}} and no {@code ${value}} to answer with, and writing them anyway is how a
         * reader ends up looking at the token instead of at their data.</p>
         */
        private RowTemplate<O> defaultLine() {
            boolean aboutPlayers = FPlayer.class.isAssignableFrom(target)
                    || IPlayerData.class.isAssignableFrom(target);

            List<String> written = new ArrayList<>(2);
            if (aboutPlayers) {
                written.add("§e${player}");
            }
            if (order != null || !aboutPlayers) {
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
