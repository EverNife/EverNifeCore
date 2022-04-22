package br.com.finalcraft.evernifecore.util.pageviwer;

import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCTextUtil;
import br.com.finalcraft.evernifecore.util.numberwrapper.NumberWrapper;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class PageViewer<OBJ, VALUE> {

    protected final Supplier<List<OBJ>> supplier;
    protected final Function<OBJ, VALUE> getValue;
    protected final Comparator<SortedItem<OBJ, VALUE>> comparator;
    protected final List<FancyText> formatHeader;
    protected final FancyText formatLine;
    protected final List<FancyText> formatFooter;
    protected final long cooldown;
    protected final int lineStart;
    protected final int lineEnd;
    protected final int pageSize;
    protected final boolean includeDate;
    protected final boolean includeTotalPlayers;
    protected final HashMap<String, Function<OBJ,Object>> placeholders;

    protected transient WeakReference<List<FancyText>> pageLinesCache = new WeakReference<>(new ArrayList<>());
    protected transient List<FancyText> pageHeaderCache = null;
    protected transient List<FancyText> pageFooterCache = null;
    protected transient long lastBuild = 0L;

    public PageViewer(Supplier<List<OBJ>> supplier, Function<OBJ, VALUE> getValue, Comparator<SortedItem<OBJ, VALUE>> comparator, List<FancyText> formatHeader, FancyText formatLine, List<FancyText> formatFooter, long cooldown, int lineStart, int lineEnd, int pageSize, boolean includeDate, boolean includeTotalPlayers) {
        this.supplier = supplier;
        this.getValue = getValue;
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

            List<SortedItem<OBJ, VALUE>> sortedList = new ArrayList<>();

            for (OBJ item : supplier.get()) {
                VALUE value = getValue.apply(item);
                sortedList.add(new SortedItem(item, value));
            }

            Collections.sort(sortedList, comparator);
            Collections.reverse(sortedList);

            if (sortedList.size() > 0){ //Add more default placeholders here, like "%player%" name
                SortedItem sortedItem = sortedList.get(0);
                if (sortedItem.object instanceof Player) placeholders.put("%player%", obj -> ((Player) obj).getName());
                else if (sortedItem.object instanceof PlayerData) placeholders.put("%player%", obj -> ((PlayerData) obj).getPlayerName());
                else if (sortedItem.object instanceof PDSection) placeholders.put("%player%", obj -> ((PDSection) obj).getPlayerName());
            }

            for (FancyText formatHeaderText : formatHeader) {
                final FancyText fancyText = formatHeaderText.clone();
                pageHeaderCache.add(fancyText);
            }

            if (includeDate){
                pageHeaderCache.add(new FancyText("§7Data de hoje: " + new FCTimeFrame(System.currentTimeMillis()).getFormatedNoHours()));
            }

            for (int number = lineStart; number < sortedList.size() && number < lineEnd; number++) {
                final FancyText fancyText = formatLine.clone();
                final SortedItem<OBJ, VALUE> sortedItem = sortedList.get(number);

                placeholders.entrySet().forEach(entry -> fancyText.replace(entry.getKey(), String.valueOf(entry.getValue().apply(sortedItem.getObject()))));
                fancyText.replace("%number%", String.valueOf(number + 1));

                pageLinesCache.get().add(fancyText);
            }

            for (FancyText formatFooterText : formatFooter) {
                final FancyText fancyText = formatFooterText.clone();
                pageFooterCache.add(fancyText);
            }

            if (includeTotalPlayers){
                pageHeaderCache.add(new FancyText("§7De um total de " + sortedList.size() + " jogadores..."));
            }

            lastBuild = System.currentTimeMillis();
        }
    }

    public void send(CommandSender... sender){
        send(0, pageSize, sender);
    }

    public void send(int page, CommandSender... sender){
        int start = NumberWrapper.of((page - 1) * pageSize).boundUpper(lineEnd - pageSize).intValue();
        int end = NumberWrapper.of(page * pageSize).boundUpper(lineEnd).intValue();
        send(start, end, sender);
    }

    public void send(int lineStart, int lineEnd, CommandSender... sender){
        validateCachedLines();

        //Bound lineEnd to lastLine
        lineEnd = NumberWrapper.of(lineEnd).boundUpper(pageLinesCache.get().size()).intValue();

        if (lineStart > lineEnd){
            //Rebound, one page backwards
            int lastPossiblePage = pageLinesCache.get().size() / pageSize;
            lineStart = NumberWrapper.of(lineStart).boundUpper(lastPossiblePage * pageSize).intValue();
        }

        lineStart = NumberWrapper.of(lineStart).boundLower(0).intValue();

        for (CommandSender commandSender : sender) {
            for (FancyText headerLine : pageHeaderCache) {
                headerLine.send(commandSender);
            }
            for (int i = lineStart; i < pageLinesCache.get().size() && i < lineEnd; i++) {
                pageLinesCache.get().get(i).send(sender);
            }
            for (FancyText headerLine : pageFooterCache) {
                headerLine.send(commandSender);
            }
        }
    }

    public static <OBJ, VALUE> Builder<OBJ, VALUE> builder(Supplier<List<OBJ>> supplier, Function<OBJ, VALUE> getValue){
        return new Builder<>(supplier, getValue);
    }

    public static class Builder<OBJ, VALUE>{
        protected Supplier<List<OBJ>> supplier;
        protected Function<OBJ, VALUE> getValue;

        private final Comparator<Number> doubleComparator = Comparator.comparingDouble(Number::doubleValue);
        private final Comparator<Object> stringComparator = Comparator.comparing(Object::toString);
        protected Comparator<SortedItem<OBJ, VALUE>> comparator = (o1, o2) -> {
            Object value1 = o1.getValue();
            Object value2 = o2.getValue();
            if (value1 instanceof Number){
                return doubleComparator.compare((Number)value1,(Number)value2);
            }
            return stringComparator.compare(String.valueOf(value1),String.valueOf(value2));
        };
        protected List<FancyText> formatHeader = Arrays.asList(new FancyText("§a§m" + FCTextUtil.straightLineOf("-")));
        protected FancyText formatLine = new FancyText("§7#  %number%:   §e%player%§f - §a%value%");
        protected List<FancyText> formatFooter = Arrays.asList(new FancyText(""));
        protected long cooldown = 15000; //15 seconds
        protected int lineStart = 0;
        protected int lineEnd = 50;
        protected int pageSize = 10;
        protected boolean includeDate = false;
        protected boolean includeTotalPlayers = false;

        protected final HashMap<String, Function<OBJ,Object>> placeholders = new HashMap<>();

        protected Builder(Supplier<List<OBJ>> supplier, Function<OBJ, VALUE> getValue) {
            this.supplier = supplier;
            this.getValue = getValue;

            addPlaceholder("%value%", (Function<OBJ, Object>) getValue);
        }

        public Builder<OBJ, VALUE> setComparator(Comparator<SortedItem<OBJ, VALUE>> comparator) {
            this.comparator = comparator;
            return this;
        }

        public Builder<OBJ, VALUE> setFormatHeader(List<FancyText> formatHeader) {
            this.formatHeader = formatHeader;
            return this;
        }

        public Builder<OBJ, VALUE> setFormatHeader(FancyText... formatHeader) {
            this.formatHeader = Arrays.asList(formatHeader);
            return this;
        }

        public Builder<OBJ, VALUE> setFormatHeader(String... formatHeader) {
            this.formatHeader = Arrays.asList(formatHeader).stream().map(FancyText::new).collect(Collectors.toList());
            return this;
        }

        public Builder<OBJ, VALUE> setFormatLine(String formatLine) {
            this.formatLine = new FancyText(formatLine);
            return this;
        }

        public Builder<OBJ, VALUE> setFormatLine(FancyText formatLine) {
            this.formatLine = formatLine;
            return this;
        }

        public Builder<OBJ, VALUE> setFormatFooter(List<FancyText> formatFooter) {
            this.formatFooter = formatFooter;
            return this;
        }

        public Builder<OBJ, VALUE> setFormatFooter(FancyText... formatFooter) {
            this.formatFooter = Arrays.asList(formatFooter);
            return this;
        }

        public Builder<OBJ, VALUE> setFormatFooter(String... formatFooter) {
            this.formatFooter = Arrays.asList(formatFooter).stream().map(FancyText::new).collect(Collectors.toList());
            return this;
        }

        public Builder<OBJ, VALUE> setCooldown(int cooldown) {
            this.cooldown = cooldown * 1000;
            return this;
        }

        public Builder<OBJ, VALUE> setLineStart(int lineStart) {
            this.lineStart = lineStart;
            return this;
        }

        public Builder<OBJ, VALUE> setLineEnd(int lineEnd) {
            this.lineEnd = lineEnd < 0 ? Integer.MAX_VALUE : lineEnd;
            return this;
        }

        public Builder<OBJ, VALUE> setIncludeDate(boolean includeDate) {
            this.includeDate = includeDate;
            return this;
        }

        public Builder<OBJ, VALUE> setIncludeTotalPlayers(boolean includeTotalPlayers) {
            this.includeTotalPlayers = includeTotalPlayers;
            return this;
        }

        public Builder<OBJ, VALUE> setPageSize(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        public Builder<OBJ, VALUE> addPlaceholder(String placeholder, Function<OBJ, Object> function){
            placeholders.put(placeholder, function);
            return this;
        }

        public PageViewer<OBJ, VALUE> build(){
            PageViewer<OBJ, VALUE> pageViewer = new PageViewer<>(
                    supplier,
                    getValue,
                    comparator,
                    formatHeader,
                    formatLine,
                    formatFooter,
                    cooldown,
                    lineStart,
                    lineEnd,
                    pageSize,
                    includeDate,
                    includeTotalPlayers);

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
