package br.com.finalcraft.evernifecore.util.pageviwer;

import br.com.finalcraft.evernifecore.config.playerdata.IPlayerData;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.dynamiccommand.DynamicCommand;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.time.DayOfToday;
import br.com.finalcraft.evernifecore.util.FCTextUtil;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PageViewer<OBJ, COMPARED_VALUE> {

    @FCLocale(lang = LocaleType.PT_BR, text = "§7Data de hoje: %date_of_today%")
    @FCLocale(lang = LocaleType.EN_US, text = "§7Date of today: %date_of_today%")
    private static LocaleMessage DATE_OF_TODAY_IS;

    @FCLocale(lang = LocaleType.PT_BR, text = "§7De um total de %total_players% jogadores...")
    @FCLocale(lang = LocaleType.EN_US, text = "§7From a total of %total_players% players...")
    private static LocaleMessage OF_A_TOTAL_OF_X_PLAYERS;

    protected final Supplier<List<OBJ>> supplier;
    protected final @Nullable Function<OBJ, COMPARED_VALUE> valueExtrator;
    protected final @Nullable Comparator<SortedItem<OBJ, COMPARED_VALUE>> comparator;
    protected final List<FancyText> formatHeader;
    protected final FancyText formatLine;
    protected final List<FancyText> formatFooter;
    protected final long cooldown;
    protected final int lineStart;
    protected final int lineEnd;
    protected final int pageSize;
    protected final boolean includeDate;
    protected final boolean includeTotalPlayers;
    protected final boolean nextAndPreviousPageButton;
    protected final HashMap<String, Function<OBJ,Object>> placeholders;

    protected transient WeakReference<List<FancyText>> pageLinesCache = new WeakReference<>(new ArrayList<>());
    protected transient List<FancyText> pageHeaderCache = null;
    protected transient List<FancyText> pageFooterCache = null;
    protected transient long lastBuild = 0L;

    public PageViewer(Supplier<List<OBJ>> supplier, @Nullable Function<OBJ, COMPARED_VALUE> valueExtrator, @Nullable Comparator<SortedItem<OBJ, COMPARED_VALUE>> comparator, List<FancyText> formatHeader, FancyText formatLine, List<FancyText> formatFooter, long cooldown, int lineStart, int lineEnd, int pageSize, boolean includeDate, boolean includeTotalPlayers, boolean nextAndPreviousPageButton) {
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
        this.includeTotalPlayers = includeTotalPlayers;
        this.nextAndPreviousPageButton = nextAndPreviousPageButton;
        this.placeholders = new HashMap<>();
    }

    public int getLineStart() {
        return lineStart;
    }

    public int getLineEnd() {
        return lineEnd;
    }

    private void validateCachedLines(){

        if (pageLinesCache.get() == null || System.currentTimeMillis() - lastBuild >= cooldown){

            pageHeaderCache = new ArrayList<>();
            pageLinesCache = new WeakReference<>(new ArrayList<>());
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


            if (sortedList.size() > 0){ //Add more default placeholders here, like "%player%" name
                SortedItem sortedItem = sortedList.get(0);
                if (sortedItem.object instanceof Player) {
                    placeholders.put("%player%", obj -> ((Player) obj).getName());
                } else if (sortedItem.object instanceof IPlayerData) {
                    placeholders.put("%player%", obj -> ((IPlayerData) obj).getPlayerName());
                }
            }

            for (FancyText formatHeaderText : formatHeader) {
                final FancyText fancyText = formatHeaderText.clone();
                pageHeaderCache.add(fancyText);
            }

            if (includeDate){
                pageHeaderCache.add(DATE_OF_TODAY_IS
                        .addPlaceholder("%date_of_today%", DayOfToday.getInstance().getTimeOfToday().getFormattedNoHours())
                        .getFancyText(null)
                );
            }

            for (int number = lineStart; number < sortedList.size() && number < lineEnd; number++) {
                final FancyText fancyText = formatLine.clone();
                final SortedItem<OBJ, COMPARED_VALUE> sortedItem = sortedList.get(number);

                placeholders.entrySet().forEach(entry -> fancyText.replace(entry.getKey(), String.valueOf(entry.getValue().apply(sortedItem.getObject()))));
                fancyText.replace("%number%", String.valueOf(number + 1));

                pageLinesCache.get().add(fancyText);
            }

            for (FancyText formatFooterText : formatFooter) {
                final FancyText fancyText = formatFooterText.clone();
                pageFooterCache.add(fancyText);
            }

            if (includeTotalPlayers){
                pageHeaderCache.add(OF_A_TOTAL_OF_X_PLAYERS
                        .addPlaceholder("%total_players%", sortedList.size())
                        .getFancyText(null)
                );
            }

            lastBuild = System.currentTimeMillis();
        }
    }

    public void send(@NotNull CommandSender... sender){
        send(1, sender);
    }

    public void send(@Nullable Integer page, @NotNull CommandSender... sender){
        page = NumberWrapper.of(page == null ? 1 : page).boundLower(1).intValue();
        int start = NumberWrapper.of((page - 1) * pageSize).boundUpper(lineEnd - pageSize).intValue();
        int end = NumberWrapper.of(page * pageSize).boundUpper(lineEnd).intValue();
        send(page, start, end, sender);
    }

    public void send(int page, int lineStart, int lineEnd, CommandSender... sender){
        validateCachedLines();

        //Bound lineEnd to lastLine
        lineEnd = NumberWrapper.of(lineEnd).boundUpper(pageLinesCache.get().size()).intValue();

        if (lineStart > lineEnd){
            //Rebound, one page backwards
            int lastPossiblePage = pageLinesCache.get().size() / pageSize;
            lineStart = NumberWrapper.of(lineStart).boundUpper(lastPossiblePage * pageSize).intValue();
        }

        lineStart = NumberWrapper.of(lineStart).boundLower(0).intValue();

        FancyFormatter nextAndPreviousPage = null;
        if (nextAndPreviousPageButton){
            int lastPage = (pageLinesCache.get().size() / pageSize);
            int currentPage = NumberWrapper.of(page).boundUpper(lastPage).boundLower(1).intValue();

            String previousButton = "§a§l<§2<§a§l<";
            String centerSpace = "          ";
            String center = "§ePage [" + currentPage + "/" + lastPage + "]";
            String nextButton = "§a§l>§2>§a§l>";

            //Gerenete the SpaceBorders, by generenating it arround the center and spliting it afterwards
            String holeLine = previousButton + centerSpace + center + centerSpace + nextButton;
            String[] borders = FCTextUtil.alignCenter(holeLine).split(Pattern.quote(holeLine), -1);

            //Replace colors on buttons based on possibility of next or previous page
            if (page <= 1) previousButton = previousButton.replace("§a","§7").replace("§2","§7");
            if (page >= lastPage) nextButton = nextButton.replace("§a","§7").replace("§2","§7");

            Function<Integer, String> moveToPage = integer -> {
                if (integer == 0) return null;//No Previous page
                if (integer > lastPage) return null;//No Next page
                return DynamicCommand.builder()
                        .setRunOnlyOnce(false)
                        .setAction(context -> {
                            send(integer, context.getSender());
                        })
                        .createDynamicCommand()
                        .scheduleAndReturnCommandString();
            };

            nextAndPreviousPage =
                    FancyFormatter.of("\n" + borders[0]) //First Border
                            .append(previousButton).setHoverText("\n" + previousButton + "\n").setRunCommandAction(moveToPage.apply(currentPage - 1)) //First Arrow
                            .append(centerSpace)
                            .append(center).setHoverText("\n§a Refresh Page [" + currentPage + "] \n").setRunCommandAction(moveToPage.apply(currentPage))
                            .append(centerSpace)
                            .append(nextButton).setHoverText("\n" + nextButton + "\n").setRunCommandAction(moveToPage.apply(currentPage + 1)) //Second Arrow
                            .append(borders[1]); //Second Border
        }

        for (CommandSender commandSender : sender) {
            for (FancyText headerLine : pageHeaderCache) {
                headerLine.send(commandSender);
            }
            for (int i = lineStart; i < pageLinesCache.get().size() && i < lineEnd; i++) {
                pageLinesCache.get().get(i).send(sender);
            }
            if (nextAndPreviousPage != null){
                nextAndPreviousPage.send(sender);
            }
            for (FancyText headerLine : pageFooterCache) {
                headerLine.send(commandSender);
            }
        }
    }

    public static <O> IStepWithSuplier<O> targeting(Class<O> comparedClass){
        return new BuilderImp<>(null, null);
    }

    public static interface IStepWithSuplier<O>{

        public IStepExtracting<O> withSuplier(Supplier<List<O>> supplier);

    }

    public static interface IStepExtracting<O>{

        public <C> IBuilder<O,C> extracting(Function<O, C> valueExtractor);

    }

    public static interface IBuilder<O, C>{

        //Null Comparator means keep the supplier order
        public IBuilder<O, C> setComparator(@Nullable Comparator<SortedItem<O, C>> comparator);

        public IBuilder<O, C> setFormatHeader(List<FancyText> formatHeader);

        public IBuilder<O, C> setFormatHeader(FancyText... formatHeader);

        public IBuilder<O, C> setFormatHeader(String... formatHeader);

        public IBuilder<O, C> setFormatLine(String formatLine);

        public IBuilder<O, C> setFormatLine(FancyText formatLine);

        public IBuilder<O, C> setFormatFooter(List<FancyText> formatFooter);

        public IBuilder<O, C> setFormatFooter(FancyText... formatFooter);

        public IBuilder<O, C> setFormatFooter(String... formatFooter);

        public IBuilder<O, C> setCooldown(int cooldown);

        public IBuilder<O, C> setLineStart(int lineStart);

        public IBuilder<O, C> setLineEnd(int lineEnd);

        public IBuilder<O, C> setIncludeDate(boolean includeDate);

        public IBuilder<O, C> setIncludeTotalPlayers(boolean includeTotalPlayers);

        public IBuilder<O, C> setPageSize(int pageSize);

        public IBuilder<O, C> addPlaceholder(String placeholder, Function<O, Object> function);

        public IBuilder<O, C> setNextAndPreviousPageButton(boolean nextAndPreviousPageButton);

        public PageViewer<O, C> build();
    }

    public static class BuilderImp<O, C> implements IBuilder<O, C>, IStepWithSuplier<O>, IStepExtracting<O>{
        protected Supplier<List<O>> supplier;
        protected Function<O, C> valueExtrator;

        private final Comparator<Number> doubleComparator = Comparator.comparingDouble(Number::doubleValue);
        private final Comparator<Object> stringComparator = Comparator.comparing(Object::toString);
        protected Comparator<SortedItem<O, C>> comparator = (o1, o2) -> {
            Object value1 = o1.getValue();
            Object value2 = o2.getValue();
            if (value1 instanceof Number){
                return doubleComparator.compare((Number)value1,(Number)value2);
            }
            return stringComparator.compare(String.valueOf(value1),String.valueOf(value2));
        };
        protected List<FancyText> formatHeader = Arrays.asList(new FancyText("§a§m" + FCTextUtil.straightLineOf("-")));
        protected FancyText formatLine = new FancyText("§7#  %number%:   §e%player%§f - §a%value%");
        protected List<FancyText> formatFooter = Collections.emptyList();
        protected long cooldown = ECSettings.PAGEVIEWERS_REFRESH_TIME * 1000; //15 seconds
        protected int lineStart = 0;
        protected int lineEnd = 50;
        protected int pageSize = 10;
        protected boolean includeDate = false;
        protected boolean includeTotalPlayers = false;
        protected boolean nextAndPreviousPageButton = true;

        protected final HashMap<String, Function<O,Object>> placeholders = new HashMap<>();

        protected BuilderImp(Supplier<List<O>> supplier, Function<O, C> valueExtrator) {
            this.supplier = supplier;
            this.valueExtrator = valueExtrator;
        }

        @Override
        public IStepExtracting<O> withSuplier(Supplier<List<O>> supplier) {
            this.supplier = supplier;
            return this;
        }

        @Override
        public <C> IBuilder<O, C> extracting(Function<O, C> valueExtractor) {
            this.valueExtrator = valueExtrator;
            return (IBuilder<O, C>) this;
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
            this.formatHeader = Arrays.asList(formatHeader).stream().map(FancyText::new).collect(Collectors.toList());
            return this;
        }

        @Override
        public BuilderImp<O, C> setFormatLine(String formatLine) {
            this.formatLine = new FancyText(formatLine);
            return this;
        }

        @Override
        public BuilderImp<O, C> setFormatLine(FancyText formatLine) {
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
            this.formatFooter = Arrays.asList(formatFooter).stream().map(FancyText::new).collect(Collectors.toList());
            return this;
        }

        @Override
        public BuilderImp<O, C> setCooldown(int cooldown) {
            this.cooldown = cooldown * 1000;
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
        public BuilderImp<O, C> setIncludeTotalPlayers(boolean includeTotalPlayers) {
            this.includeTotalPlayers = includeTotalPlayers;
            return this;
        }

        @Override
        public BuilderImp<O, C> setPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        @Override
        public BuilderImp<O, C> addPlaceholder(String placeholder, Function<O, Object> function){
            placeholders.put(placeholder, function);
            return this;
        }

        @Override
        public BuilderImp<O, C> setNextAndPreviousPageButton(boolean nextAndPreviousPageButton) {
            this.nextAndPreviousPageButton = nextAndPreviousPageButton;
            return this;
        }

        @Override
        public PageViewer<O, C> build(){

            if (this.valueExtrator != null){
                addPlaceholder("%value%", (Function<O, Object>) valueExtrator);
            }

            PageViewer<O, C> pageViewer = new PageViewer<>(
                    supplier,
                    valueExtrator,
                    comparator,
                    formatHeader,
                    formatLine,
                    formatFooter,
                    cooldown,
                    lineStart,
                    lineEnd,
                    pageSize,
                    includeDate,
                    includeTotalPlayers,
                    nextAndPreviousPageButton
            );

            pageViewer.placeholders.putAll(this.placeholders);

            return pageViewer;
        }
    }

    public static class SortedItem<OBJ, VALUE>{
        final OBJ object;
        final VALUE value;

        public SortedItem(OBJ object, VALUE value) {
            this.object = object;
            this.value = value;
        }

        public OBJ getObject() {
            return object;
        }

        public VALUE getValue() {
            return value;
        }
    }

}
