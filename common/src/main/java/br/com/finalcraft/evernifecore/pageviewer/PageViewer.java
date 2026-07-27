package br.com.finalcraft.evernifecore.pageviewer;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.dynamiccommand.DynamicCommand;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.fancytext.MessagePlaceholders;
import br.com.finalcraft.evernifecore.placeholder.replacer.Closures;
import br.com.finalcraft.evernifecore.placeholder.replacer.RegexReplacer;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.time.ECTimeFormat;
import br.com.finalcraft.everylibs.util.numberwrapper.NumberWrapper;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PageViewer<OBJ, COMPARED_VALUE> {

    //Keys every page answers for on its own, unless the caller declared one of them itself.
    private static final String NUMBER_KEY = "number";
    private static final String VALUE_KEY = "value";
    private static final String PLAYER_KEY = "player";

    @FCLocale(lang = LocaleType.PT_BR, text = "§7Data de hoje: ${date_of_today}")
    @FCLocale(lang = LocaleType.EN_US, text = "§7Date of today: ${date_of_today}")
    private static LocaleMessage DATE_OF_TODAY_IS;

    @FCLocale(lang = LocaleType.PT_BR, text = "§7De um total de ${total_players} jogadores...")
    @FCLocale(lang = LocaleType.EN_US, text = "§7From a total of ${total_players} players...")
    private static LocaleMessage OF_A_TOTAL_OF_X_PLAYERS;

    @FCLocale(lang = LocaleType.PT_BR, text = "§7De um total de ${total_entries}...")
    @FCLocale(lang = LocaleType.EN_US, text = "§7From a total of ${total_entries}...")
    private static LocaleMessage OF_A_TOTAL_OF_X_ENTRIES;

    protected final @Nullable Class<OBJ> target; //Compared Class, can be null
    protected final Supplier<List<OBJ>> supplier;
    protected final @Nullable Function<OBJ, COMPARED_VALUE> valueExtrator;
    protected final @Nullable Comparator<SortedItem<OBJ, COMPARED_VALUE>> comparator;
    protected final List<FancyText> formatHeader;
    protected final Function<OBJ, FancyText> formatLine;
    protected final List<FancyText> formatFooter;
    protected final long cooldown;
    protected final int lineStart;
    protected final int lineEnd;
    protected final int pageSize;
    protected final boolean includeDate;
    protected final boolean includeTotalCount;
    protected final boolean nextAndPreviousPageButton;
    protected final HashMap<String, Function<OBJ,Object>> placeholders;

    //Weak on purpose: setLineEnd(-1) makes a page unbounded, and a page nobody is reading must be
    //collectable. It only ever holds a COMPLETED list - see validateCachedLines.
    protected transient WeakReference<List<FancyText>> pageLinesCache = new WeakReference<>(null);
    protected transient List<FancyText> pageHeaderCache = null;
    protected transient List<FancyText> pageFooterCache = null;
    protected transient long lastBuild = 0L;

    public PageViewer(Class<OBJ> target, Supplier<List<OBJ>> supplier, @Nullable Function<OBJ, COMPARED_VALUE> valueExtrator, @Nullable Comparator<SortedItem<OBJ, COMPARED_VALUE>> comparator, List<FancyText> formatHeader, Function<OBJ, FancyText> formatLine, List<FancyText> formatFooter, long cooldown, int lineStart, int lineEnd, int pageSize, boolean includeDate, boolean includeTotalCount, boolean nextAndPreviousPageButton) {
        this.target = target;
        this.supplier = supplier;
        this.valueExtrator = valueExtrator;
        this.comparator = comparator;
        this.formatHeader = formatHeader;
        this.formatLine = formatLine;
        this.formatFooter = formatFooter;
        this.cooldown = cooldown;
        this.lineStart = lineStart;
        this.lineEnd = lineEnd;
        this.pageSize = pageSize;
        this.includeDate = includeDate;
        this.includeTotalCount = includeTotalCount;
        this.nextAndPreviousPageButton = nextAndPreviousPageButton;
        this.placeholders = new LinkedHashMap<>();
    }

    public int getLineStart() {
        return lineStart;
    }

    public int getLineEnd() {
        return lineEnd;
    }

    /**
     * The page lines, rebuilt if the cache expired or was collected. The list travels out as the
     * return value and is only published into the {@link WeakReference} once it is complete: while it
     * is being built nothing but a strong local can reach it, so a collection at any safepoint cannot
     * leave the build - or the send that follows - holding a null.
     */
    private List<FancyText> validateCachedLines(){

        List<FancyText> lines = pageLinesCache.get();

        if (lines == null || System.currentTimeMillis() - lastBuild >= cooldown){

            pageHeaderCache = new ArrayList<>();
            lines = new ArrayList<>();
            pageFooterCache = new ArrayList<>();

            List<SortedItem<OBJ, COMPARED_VALUE>> sortedList = new ArrayList<>();

            for (OBJ item : supplier.get()) {
                COMPARED_VALUE comparedValue = valueExtrator != null ? valueExtrator.apply(item) : null;
                sortedList.add(new SortedItem(item, comparedValue));
            }

            if (comparator != null){
                Collections.sort(sortedList, comparator);
                Collections.reverse(sortedList);
            }


            final RegexReplacer<LineEntry<OBJ>> lineReplacer = buildLineReplacer(sortedList);

            for (FancyText formatHeaderText : formatHeader) {
                final FancyText fancyText = formatHeaderText.copy();
                pageHeaderCache.add(fancyText);
            }

            if (includeDate){
                pageHeaderCache.add(DATE_OF_TODAY_IS
                        .addPlaceholder("date_of_today", ECTimeFormat.getFormattedNoHours(System.currentTimeMillis()))
                        .getFancyText(null)
                );
            }

            for (int number = lineStart; number < sortedList.size() && number < lineEnd; number++) {
                final SortedItem<OBJ, COMPARED_VALUE> sortedItem = sortedList.get(number);
                OBJ comparedObject = sortedItem.getObject();

                final FancyText fancyText = formatLine.apply(comparedObject);

                //Baked into the cached copy, once, against the LINE - so the page a hundred players
                //are looking at was resolved a hundred times less, and they all read the same values.
                final LineEntry<OBJ> lineEntry = new LineEntry<>(comparedObject, number + 1);
                fancyText.bake(payload -> lineReplacer.apply(payload, lineEntry));

                lines.add(fancyText);
            }

            for (FancyText formatFooterText : formatFooter) {
                final FancyText fancyText = formatFooterText.copy();
                pageFooterCache.add(fancyText);
            }

            if (includeTotalCount){
                if (target != null && (FPlayer.class.isAssignableFrom(target) || IPlayerData.class.isAssignableFrom(target))){
                    pageHeaderCache.add(OF_A_TOTAL_OF_X_PLAYERS
                            .addPlaceholder("total_players", sortedList.size())
                            .getFancyText(null)
                    );
                }else {
                    pageHeaderCache.add(OF_A_TOTAL_OF_X_ENTRIES
                            .addPlaceholder("total_entries", sortedList.size())
                            .getFancyText(null)
                    );
                }
            }

            pageLinesCache = new WeakReference<>(lines);
            lastBuild = System.currentTimeMillis();
        }

        return lines;
    }

    /**
     * The replacer this page's lines are baked with: the caller's own declarations first, then the
     * keys the framework answers for, added only where the caller left them free.
     *
     * <p>Nothing here is match-driven by hand - the replacer walks the tokens the text actually
     * cites, so a key that is declared but never written is never computed.</p>
     */
    private RegexReplacer<LineEntry<OBJ>> buildLineReplacer(List<SortedItem<OBJ, COMPARED_VALUE>> sortedList){
        RegexReplacer<LineEntry<OBJ>> replacer = new RegexReplacer<>(Closures.DOLLAR_CURLY);

        for (Map.Entry<String, Function<OBJ, Object>> declaration : placeholders.entrySet()) {
            Function<OBJ, Object> function = declaration.getValue();
            declare(replacer, declaration.getKey(), entry -> function.apply(entry.object));
        }

        declareIfAbsent(replacer, NUMBER_KEY, entry -> entry.number);

        if (sortedList.size() > 0){
            Object firstObject = sortedList.get(0).getObject();
            if (firstObject instanceof FPlayer) {
                declareIfAbsent(replacer, PLAYER_KEY, entry -> ((FPlayer) entry.object).getName());
            } else if (firstObject instanceof IPlayerData) {
                declareIfAbsent(replacer, PLAYER_KEY, entry -> ((IPlayerData) entry.object).getName());
            }
        }

        return replacer;
    }

    private static <OBJ> void declare(RegexReplacer<LineEntry<OBJ>> replacer, String key, Function<LineEntry<OBJ>, Object> function){
        //The memo token is this declaration's own identity, so two pages declaring the same key never
        //share an answer. String.valueOf keeps a null value visible as text instead of leaving the
        //token raw, which in a click value would be a broken command rather than an ugly line.
        final Object declaration = new Object();
        replacer.addParser(key, entry -> entry.resolveOnce(declaration, () -> String.valueOf(function.apply(entry))));
    }

    /**
     * Declares one of the framework's own keys only where the caller left it free: a page that
     * declared {@code value} (or {@code player}, or {@code number}) meant it, and the automatic answer
     * is the default, not an override of a more specific one.
     */
    private static <OBJ> void declareIfAbsent(RegexReplacer<LineEntry<OBJ>> replacer, String key, Function<LineEntry<OBJ>, Object> function){
        //The provider indexes its keys lower-cased and these names are already lower case, so this
        //also catches a caller who declared the same key spelled with another case.
        if (replacer.getProvider().getParserMap().containsKey(key)){
            return;
        }
        declare(replacer, key, function);
    }

    /**
     * What a line's placeholders resolve against: the object on that line and its 1-based position in
     * the page, never the recipient. That is what keeps the substitution eager - it is baked into the
     * cached line once, and every recipient of that page then reads the same values.
     */
    protected static final class LineEntry<OBJ> {

        final OBJ object;
        final int number;
        // One answer per declaration per line, so a key cited in the text AND in the hover of the
        // same line still costs a single call into the caller's Function.
        private final Map<Object, Optional<Object>> resolvedOnce = new HashMap<>();

        LineEntry(OBJ object, int number) {
            this.object = object;
            this.number = number;
        }

        @Nullable Object resolveOnce(Object declaration, Supplier<?> compute) {
            return resolvedOnce.computeIfAbsent(declaration, ignored -> Optional.ofNullable(compute.get()))
                    .orElse(null);
        }
    }

    public void send(@Nonnull FCommandSender... sender){
        send(1, sender);
    }

    public void send(@Nullable Integer page, @Nonnull FCommandSender... sender){
        page = NumberWrapper.of(page == null ? 1 : page).boundLower(1).intValue();
        int start = NumberWrapper.of((page - 1) * pageSize).boundUpper(lineEnd - pageSize).intValue();
        int end = NumberWrapper.of(page * pageSize).boundUpper(lineEnd).intValue();
        send(page, start, end, sender);
    }

    public void send(@Nullable PageVizualization pageVizualization, @Nonnull FCommandSender... sender){
        if (pageVizualization == null){
            send(1, sender);
            return;
        }

        if (pageVizualization.isShowAll()){
            send(1, 0, Integer.MAX_VALUE, sender);
            return;
        }

        int page = pageVizualization.getPageStart();
        int pageEnd = pageVizualization.getPageEnd();
        int diff = pageEnd - page;

        int start = NumberWrapper.of((page - 1) * pageSize).boundUpper(lineEnd - pageSize).intValue();
        int end = NumberWrapper.of((page * pageSize) + (pageSize * diff)).boundUpper(lineEnd).intValue();
        send(page, start, end, sender);
    }

    public void send(int page, int lineStart, int lineEnd, FCommandSender... sender){
        //The strong reference comes back from the build itself; reading the weak field again here
        //would reintroduce the very window the build closes.
        List<FancyText> lines = validateCachedLines();

        //Bound lineEnd to lastLine
        lineEnd = NumberWrapper.of(lineEnd).boundUpper(lines.size()).intValue();

        if (lineStart > lineEnd){
            //Rebound, one page backwards
            int lastPossiblePage = lines.size() / pageSize;
            lineStart = NumberWrapper.of(lineStart).boundUpper(lastPossiblePage * pageSize).intValue();
        }

        lineStart = NumberWrapper.of(lineStart).boundLower(0).intValue();

        FancyFormatter nextAndPreviousPage = null;
        if (nextAndPreviousPageButton){
            int lastPage = (int) Math.ceil(lines.size() / (double) pageSize);
            int currentPage = NumberWrapper.of(page).boundUpper(lastPage).boundLower(1).intValue();

            Function<Integer, String> moveToPage = integer -> {
                if (integer == 0) return "";//No Previous page
                if (integer > lastPage) return "";//No Next page
                return DynamicCommand.builder()
                        .setRunOnlyOnce(false)
                        .setAction(context -> {
                            send(integer, context.getSender());
                        })
                        .createDynamicCommand()
                        .scheduleAndReturnCommandString();
            };

            if (ECSettings.PAGEVIEWERS_FULL_LOCALIZATION){
                //This will load the messages from the localization
                nextAndPreviousPage = PVExtraMessages.generatePreviousAndNextPage(
                        currentPage,
                        lastPage,
                        moveToPage
                );
            }else {
                //This will attempt to generate the messages from the code with automatic spacing
                String previousButton = "§a§l<§2<§a§l<";
                String centerSpace = "          ";
                String center = "§ePage [" + currentPage + "/" + lastPage + "]";
                String nextButton = "§a§l>§2>§a§l>";

                //Build the space borders: center the whole line around the middle text, then split it on
                //the original text to recover the left and right padding as the two borders.
                String holeLine = previousButton + centerSpace + center + centerSpace + nextButton;
                String[] borders = EverNifeCore.getPlatform().getChatAdapter().alignCenter(holeLine).split(Pattern.quote(holeLine), -1);

                //Replace colors on buttons based on possibility of next or previous page
                if (page <= 1) previousButton = previousButton.replace("§a","§7").replace("§2","§7");
                if (page >= lastPage) nextButton = nextButton.replace("§a","§7").replace("§2","§7");


                nextAndPreviousPage =
                        FancyFormatter.of("\n" + borders[0]) //First Border
                                .append(previousButton).setHover("\n" + previousButton + "\n").setClickCommand(moveToPage.apply(currentPage - 1)) //First Arrow
                                .append(centerSpace)
                                .append(center).setHover("\n§a Refresh Page [" + currentPage + "] \n").setClickCommand(moveToPage.apply(currentPage))
                                .append(centerSpace)
                                .append(nextButton).setHover("\n" + nextButton + "\n").setClickCommand(moveToPage.apply(currentPage + 1)) //Second Arrow
                                .append(borders[1]); //Second Border
            }
        }

        //One page per recipient. The loop variable is named for what it is - a single recipient - so
        //that handing the whole array to a send() inside the loop reads as the mistake it would be.
        for (FCommandSender recipient : sender) {
            for (FancyText headerLine : pageHeaderCache) {
                headerLine.send(recipient);
            }
            for (int i = lineStart; i < lines.size() && i < lineEnd; i++) {
                lines.get(i).send(recipient);
            }
            if (nextAndPreviousPage != null){
                nextAndPreviousPage.send(recipient);
            }
            for (FancyText footerLine : pageFooterCache) {
                footerLine.send(recipient);
            }
        }
    }

    public static <O> IStepWithSuplier<O> targeting(Class<O> comparedClass){
        return new BuilderImp<>(comparedClass, null, null);
    }

    public static interface IStepWithSuplier<O>{

        public IStepExtracting<O> withSuplier(Supplier<List<O>> supplier);

    }

    public static interface IStepExtracting<O>{

        //Null Extraction means keep the supplier order
        public <C> IBuilder<O,C> extracting(@Nullable Function<O, C> valueExtractor);

    }

    public static interface IBuilder<O, C>{

        //Null Comparator means keep the supplier order
        public IBuilder<O, C> setComparator(@Nullable Comparator<SortedItem<O, C>> comparator);

        public IBuilder<O, C> setFormatHeader(List<FancyText> formatHeader);

        public IBuilder<O, C> setFormatHeader(FancyText... formatHeader);

        public IBuilder<O, C> setFormatHeader(String... formatHeader);

        public IBuilder<O, C> setFormatLine(String formatLine);

        public IBuilder<O, C> setFormatLine(FancyText formatLine);

        public IBuilder<O, C> setFormatLine(Function<O, FancyText> formatLineFunction);

        public IBuilder<O, C> setFormatFooter(List<FancyText> formatFooter);

        public IBuilder<O, C> setFormatFooter(FancyText... formatFooter);

        public IBuilder<O, C> setFormatFooter(String... formatFooter);

        public IBuilder<O, C> setCooldown(int cooldownSeconds);

        public IBuilder<O, C> setLineStart(int lineStart);

        public IBuilder<O, C> setLineEnd(int lineEnd);

        public IBuilder<O, C> setIncludeDate(boolean includeDate);

        public IBuilder<O, C> setIncludeTotalCount(boolean includeTotalEntries);

        public IBuilder<O, C> setPageSize(int pageSize);

        /**
         * Declares the value of {@code ${key}} (case-insensitive) on every line of this page. The key
         * is taken exactly as written, so it must be the bare name - {@code "version"}, never
         * {@code "%version%"} or {@code "${version}"}; a delimited one is registered like that, never
         * matches, and says so once in the console.
         *
         * <p>Resolved against the line's own object, once per line, while the page is being cached -
         * so every recipient of that page reads the same values. A key the line never writes is never
         * computed. {@code number}, {@code value} and {@code player} are answered for automatically,
         * unless declared here, in which case this wins.</p>
         */
        public IBuilder<O, C> addPlaceholder(String key, Function<O, Object> function);

        public IBuilder<O, C> setNextAndPreviousPageButton(boolean nextAndPreviousPageButton);

        public PageViewer<O, C> build();
    }

    public static class BuilderImp<O, C> implements IBuilder<O, C>, IStepWithSuplier<O>, IStepExtracting<O>{
        protected final Class<O> target;
        protected Supplier<List<O>> supplier;
        protected Function<O, C> valueExtractor;

        protected Comparator<SortedItem<O, C>> comparator = (o1, o2) -> {
            Object value1 = o1.getValue();
            Object value2 = o2.getValue();
            if (value1 instanceof Number){
                return Double.compare(((Number)value1).doubleValue(), ((Number)value2).doubleValue());
            }
            return String.CASE_INSENSITIVE_ORDER.compare(String.valueOf(value2), String.valueOf(value1));//The order is reversed, to keep the highest value on top
        };

        protected List<FancyText> formatHeader = Arrays.asList(new FancySegment("§a§m" + EverNifeCore.getPlatform().getChatAdapter().straightLineOf("-")));
        protected Function<O, FancyText> formatLine = o -> new FancySegment("§7#  ${number}:   §e${player}§f - §a${value}");
        protected List<FancyText> formatFooter = Collections.emptyList();
        protected long cooldown = ECSettings.PAGEVIEWERS_REFRESH_TIME * 1000; //def 5 seconds
        protected int lineStart = 0;
        protected int lineEnd = 50;
        protected int pageSize = 10;
        protected boolean includeDate = false;
        protected boolean includeTotalCount = false;
        protected boolean nextAndPreviousPageButton = true;

        protected final HashMap<String, Function<O,Object>> placeholders = new LinkedHashMap<>();

        protected BuilderImp(Class<O> target, Supplier<List<O>> supplier, Function<O, C> valueExtractor) {
            this.target = target;
            this.supplier = supplier;
            this.valueExtractor = valueExtractor;
        }

        @Override
        public IStepExtracting<O> withSuplier(Supplier<List<O>> supplier) {
            this.supplier = supplier;
            return this;
        }

        @Override
        public <C2> IBuilder<O, C2> extracting(Function<O, C2> valueExtractor) {
            this.valueExtractor = (Function<O, C>) valueExtractor;
            return (IBuilder<O, C2>) this;
        }

        //Null Comparator means keep the supplier order
        @Override
        public BuilderImp<O, C> setComparator(@Nullable Comparator<SortedItem<O, C>> comparator) {
            this.comparator = comparator;
            return this;
        }

        @Override
        public BuilderImp<O, C> setFormatHeader(List<FancyText> formatHeader) {
            this.formatHeader = formatHeader;
            return this;
        }

        @Override
        public BuilderImp<O, C> setFormatHeader(FancyText... formatHeader) {
            this.formatHeader = Arrays.asList(formatHeader);
            return this;
        }

        @Override
        public BuilderImp<O, C> setFormatHeader(String... formatHeader) {
            this.formatHeader = Arrays.asList(formatHeader).stream().<FancyText>map(FancySegment::new).collect(Collectors.toList());
            return this;
        }

        @Override
        public BuilderImp<O, C> setFormatLine(String formatLine) {
            return setFormatLine((o -> new FancySegment(formatLine)));//new instance for every call
        }

        @Override
        public BuilderImp<O, C> setFormatLine(FancyText formatLine) {
            return setFormatLine((o -> formatLine.copy()));//new instance for every call
        }

        @Override
        public BuilderImp<O, C> setFormatLine(Function<O, FancyText> formatLine) {
            this.formatLine = formatLine;
            return this;
        }

        @Override
        public BuilderImp<O, C> setFormatFooter(List<FancyText> formatFooter) {
            this.formatFooter = formatFooter;
            return this;
        }

        @Override
        public BuilderImp<O, C> setFormatFooter(FancyText... formatFooter) {
            this.formatFooter = Arrays.asList(formatFooter);
            return this;
        }

        @Override
        public BuilderImp<O, C> setFormatFooter(String... formatFooter) {
            this.formatFooter = Arrays.asList(formatFooter).stream().<FancyText>map(FancySegment::new).collect(Collectors.toList());
            return this;
        }

        @Override
        public BuilderImp<O, C> setCooldown(int cooldownSeconds) {
            this.cooldown = cooldownSeconds * 1000;
            return this;
        }

        @Override
        public BuilderImp<O, C> setLineStart(int lineStart) {
            this.lineStart = lineStart;
            return this;
        }

        @Override
        public BuilderImp<O, C> setLineEnd(int lineEnd) {
            this.lineEnd = lineEnd <= 0 ? Integer.MAX_VALUE : lineEnd;
            return this;
        }

        @Override
        public BuilderImp<O, C> setIncludeDate(boolean includeDate) {
            this.includeDate = includeDate;
            return this;
        }

        @Override
        public BuilderImp<O, C> setIncludeTotalCount(boolean includeTotalCount) {
            this.includeTotalCount = includeTotalCount;
            return this;
        }

        @Override
        public BuilderImp<O, C> setPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        @Override
        public BuilderImp<O, C> addPlaceholder(String key, Function<O, Object> function){
            MessagePlaceholders.warnOnceIfDelimited(key);
            placeholders.put(key, function);
            return this;
        }

        //Whether the caller already speaks for this key, whatever case it spelled it in - the
        //provider indexes keys lower-cased, so two spellings of one name are the same key to it.
        private boolean declares(String key){
            for (String declared : placeholders.keySet()) {
                if (declared.equalsIgnoreCase(key)) return true;
            }
            return false;
        }

        @Override
        public BuilderImp<O, C> setNextAndPreviousPageButton(boolean nextAndPreviousPageButton) {
            this.nextAndPreviousPageButton = nextAndPreviousPageButton;
            return this;
        }

        @Override
        public PageViewer<O, C> build(){

            //Conditional for the same reason declareIfAbsent is: the extractor is this key's default
            //answer, not an override of a caller that meant something else by it.
            if (this.valueExtractor != null && !declares(VALUE_KEY)){
                addPlaceholder(VALUE_KEY, (Function<O, Object>) valueExtractor);
            }

            PageViewer<O, C> pageViewer = new PageViewer<>(
                    target,
                    supplier,
                    valueExtractor,
                    comparator,
                    formatHeader,
                    formatLine,
                    formatFooter,
                    cooldown,
                    lineStart,
                    lineEnd,
                    pageSize,
                    includeDate,
                    includeTotalCount,
                    nextAndPreviousPageButton
            );

            pageViewer.placeholders.putAll(this.placeholders);

            return pageViewer;
        }
    }

    @Data
    public static class SortedItem<OBJ, VALUE>{
        final OBJ object;
        final VALUE value;
    }

    public static class PVExtraMessages {

        @FCLocale(lang = LocaleType.EN_US, children = {
                @FCLocale.Child(text = "                 &r"),
                @FCLocale.Child(text = "§a§l<§2<§a§l<&r", hover = "\n§a§l<§2<§a§l<\n", click = "%on_previous_page_click%"),
                @FCLocale.Child(text = "           &r")
        })
        protected static LocaleMessage PREVIOUS_PAGE_WHEN_AVAILABLE;

        @FCLocale(lang = LocaleType.EN_US, children = {
                @FCLocale.Child(text = "                 &r"),
                @FCLocale.Child(text = "§7§l<§7<§7§l<&r", hover = "\n§7§l<§7<§7§l<\n"),
                @FCLocale.Child(text = "           &r")
        })
        protected static LocaleMessage PREVIOUS_PAGE_WHEN_UNAVAILABLE;

        @FCLocale(lang = LocaleType.EN_US, text = "§ePage [%current_page%/%last_page%]§r", hover = "\n§a Refresh Page [%current_page%] \n", click = "%on_refresh_page_click%")
        protected static LocaleMessage CENTER_PAGE_BUTTON;

        @FCLocale(lang = LocaleType.EN_US, children = {
                @FCLocale.Child(text = "            &r"),
                @FCLocale.Child(text = "§a§l>§2>§a§l>", hover = "\n§a§l>§2>§a§l>\n", click = "%on_next_page_click%"),
        })
        protected static LocaleMessage NEXT_PAGE_WHEN_AVAILABLE;

        @FCLocale(lang = LocaleType.EN_US, children = {
                @FCLocale.Child(text = "            &r"),
                @FCLocale.Child(text = "§7§l>§7>§7§l>&r", hover = "\n§7§l>§7>§7§l>\n"),
        })
        protected static LocaleMessage NEXT_PAGE_WHEN_UNAVAILABLE;

        public static FancyFormatter generatePreviousAndNextPage(int currentPage, int lastPage, Function<Integer, String> moveToPage){

            FancyText previousPageButton = currentPage > 1
                    ? PVExtraMessages.PREVIOUS_PAGE_WHEN_AVAILABLE.getDefaultFancyText()
                    : PVExtraMessages.PREVIOUS_PAGE_WHEN_UNAVAILABLE.getDefaultFancyText();

            FancyText centerPageButton = PVExtraMessages.CENTER_PAGE_BUTTON.getDefaultFancyText().copy()
                    .replace("%current_page%", String.valueOf(currentPage))
                    .replace("%last_page%", String.valueOf(lastPage));

            FancyText nextPageButton = lastPage > currentPage
                    ? PVExtraMessages.NEXT_PAGE_WHEN_AVAILABLE.getDefaultFancyText()
                    : PVExtraMessages.NEXT_PAGE_WHEN_UNAVAILABLE.getDefaultFancyText();

            //append copies what it takes in, so the locale's own default text cannot be reached by the
            //replace() calls below; centerPageButton is copied above because it is replaced directly.
            return FancyFormatter.of()
                            .append(previousPageButton)
                            .append(centerPageButton)
                            .append(nextPageButton)
                            .replace("%on_previous_page_click%", moveToPage.apply(currentPage - 1))
                            .replace("%on_next_page_click%", moveToPage.apply(currentPage + 1))
                            .replace("%on_refresh_page_click%", moveToPage.apply(currentPage));
        }
    }

}
