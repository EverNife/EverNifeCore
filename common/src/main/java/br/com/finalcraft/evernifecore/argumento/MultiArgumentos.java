package br.com.finalcraft.evernifecore.argumento;

import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everylibs.util.FCTimeUtil;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class MultiArgumentos {

    private final List<String> stringArgs = new ArrayList<String>();
    private final List<FlagedArgumento> flags = new ArrayList<FlagedArgumento>();
    private final List<Argumento> argumentos = new ArrayList<Argumento>();

    private boolean flagfied = false;

    public MultiArgumentos(String[] args) {
        for (String arg : args) {
            stringArgs.add(arg);
            argumentos.add(new Argumento(arg));
        }
        //Flagification is always lazy (getFlags()/getFlag() call the idempotent flagify()).
    }

    /**
     * Sniffed-mode flag extraction: scans the raw tokens for {@code --name value} markers and strips
     * them out of the positional lists, closing the remaining indices up. Lazy and idempotent, so
     * positional-only usages never pay for it.
     * <p>
     * Grammar in {@link #scanFlagMarkers}. Multi-word values need quotes: {@code --title 'Two Words'}
     * is one value, while {@code --title Two Words} is {@code "Two"} plus a positional.
     */
    public void flagify(){
        if (flagfied){
            return;
        }
        flagfied = true;

        //Manual/sniffed mode: every marker is "recognized" with arity -1 (auto-sniff), so
        //scanFlagMarkers never reports an unknown flag here.
        ScanOutcome outcome = scanFlagMarkers(rawName -> new Recognition(rawName, -1), -1);
        this.flags.addAll(outcome.matched);
    }

    /**
     * Declarative counterpart of {@link #flagify()}: extracts only the markers whose normalized name
     * (dashes stripped, case-insensitive) is a key of {@code bindingsByNormalizedNameOrAlias}, taking
     * the FIXED arity that binding declares instead of sniffing. Matches are stored under the
     * binding's canonical name, so an alias like {@code -f} resolves the same
     * {@link #getFlag(String)} entry as the long name.
     * <p>
     * Declared and manual extraction are mutually exclusive on one instance: this marks it flagified.
     *
     * @param literalTailAt the positional index at which the line stops being scanned at all: from that
     * token on everything is text, marker-shaped or not. Pass -1 to scan the whole window.
     * @return what the scan could not turn into a flag - unknown markers, markers whose spelling and
     * declaration disagree about the value, and markers written twice; {@link FlagExtraction#isClean()}
     * when there was none
     * @throws IllegalStateException if this instance was already flagified (declared or manual)
     */
    public FlagExtraction extractDeclaredFlags(Map<String, FlagBinding> bindingsByNormalizedNameOrAlias, int literalTailAt){
        if (flagfied){
            throw new IllegalStateException("MultiArgumentos was already flagified; extractDeclaredFlags must run exactly once, before any getFlags()/getFlag() call.");
        }
        flagfied = true;

        ScanOutcome outcome = scanFlagMarkers(rawName -> {
            FlagBinding binding = bindingsByNormalizedNameOrAlias.get(rawName.toLowerCase());
            return binding == null ? null : new Recognition(binding.getCanonicalName(), binding.getArity());
        }, literalTailAt);
        this.flags.addAll(outcome.matched);
        return new FlagExtraction(outcome.unknownMarkers, outcome.markersMissingValue, outcome.markersRefusingValue, outcome.repeatedMarkers);
    }

    /**
     * Shared core of {@link #flagify()} (sniffed) and {@link #extractDeclaredFlags} (fixed arity):
     * walks the raw tokens once, recognizing markers and consuming their value, honoring the
     * {@code --} end-of-flags escape and quoted multi-word groups identically in both modes.
     * <p>
     * The modes differ only in what {@code recognizer} answers for a marker name - arity 0 (presence),
     * arity 1 (value), negative (sniff the next token) or null (unrecognized). The scan also ends on
     * its own at {@code literalTailAt}, which is why the {@code --} escape is only ever needed BEFORE
     * the tail.
     */
    private ScanOutcome scanFlagMarkers(FlagRecognizer recognizer, int literalTailAt){
        List<FlagedArgumento> matched = new ArrayList<FlagedArgumento>();
        List<String> unknownMarkers = new ArrayList<String>();
        List<String> markersMissingValue = new ArrayList<String>();
        List<String> markersRefusingValue = new ArrayList<String>();
        List<String> repeatedMarkers = new ArrayList<String>();
        List<Integer> indexesToRemove = new ArrayList<Integer>();
        List<String> claimedNames = new ArrayList<String>();
        boolean endOfFlags = false;
        int positionalsSeen = 0;

        int i = 0;
        while (i < stringArgs.size()) {
            String token = stringArgs.get(i);

            if (!endOfFlags && token.equals("--")){
                //End-of-flags marker: drop it, everything after stays positional even if it looks like a flag
                indexesToRemove.add(i);
                endOfFlags = true;
                i++;
                continue;
            }

            if (!endOfFlags && !isFlagMarker(token)){
                if (positionalsSeen == literalTailAt){
                    endOfFlags = true; //this token opens the tail: it and everything after it is text
                    continue;
                }
                positionalsSeen++;
            }

            if (endOfFlags || !isFlagMarker(token)){
                i++;
                continue;
            }

            String spelling = token.substring(leadingDashCount(token));
            //Only "=" opens an inline value, so anything else glued onto the marker - a quote in
            //"--title'X'" included - is part of the name
            int equalsAt = spelling.indexOf('=');
            String inlineValue = equalsAt < 0 ? null : spelling.substring(equalsAt + 1);
            String rawName = equalsAt < 0 ? spelling : spelling.substring(0, equalsAt);
            //How the message should name this flag: what was typed, minus the inline value, which is
            //the part the flag itself is not about
            String marker = equalsAt < 0 ? token : token.substring(0, token.length() - spelling.length() + equalsAt);

            Recognition recognition = recognizer.recognize(rawName);
            if (recognition == null){
                unknownMarkers.add(token); //declared mode only: left untouched, the caller decides how to react
                i++;
                continue;
            }

            boolean sniffed = recognition.arity < 0;
            if (!sniffed && claimedNames.contains(recognition.canonicalName)){
                //Two answers to one question: taking either would be a guess about which the sender
                //meant, and the one thing the framework knows is that they did not say
                repeatedMarkers.add(marker);
                i++;
                continue;
            }

            if (recognition.arity == 0 && inlineValue != null){
                markersRefusingValue.add(marker); //presence IS the value; leave the token in place
                i++;
                continue;
            }

            List<Integer> consumed = new ArrayList<Integer>();
            consumed.add(i);

            String value;
            int nextIndex = i + 1;
            String nextToken = nextIndex < stringArgs.size() ? stringArgs.get(nextIndex) : null;

            if (recognition.arity == 0){
                value = "true"; //fixed presence flag: never looks at what follows
            }else if (inlineValue != null){
                if (inlineValue.isEmpty()){
                    if (!sniffed){
                        markersMissingValue.add(marker); //"--name=" is a value nobody typed, not presence
                        i++;
                        continue;
                    }
                    value = "";
                }else {
                    //"--name=value": the value came glued to the marker, so the tokens after it are
                    //only read when the value opens a quoted group
                    value = isQuoteChar(inlineValue.charAt(0))
                            ? consumeQuotedValue(inlineValue, nextIndex, consumed)
                            : inlineValue;
                }
            }else if (nextToken == null || nextToken.equals("--") || isFlagMarker(nextToken)){
                //"--" is never swallowed as a value in any mode, and neither is another flag marker
                if (sniffed){
                    value = "true"; //manual mode has no declaration to hold the spelling to
                }else {
                    markersMissingValue.add(marker);
                    i++;
                    continue;
                }
            }else if (!nextToken.isEmpty() && isQuoteChar(nextToken.charAt(0))){
                consumed.add(nextIndex);
                value = consumeQuotedValue(nextToken, nextIndex + 1, consumed);
            }else {
                value = nextToken;
                consumed.add(nextIndex);
            }

            matched.add(new FlagedArgumento(recognition.canonicalName, value));
            claimedNames.add(recognition.canonicalName);
            indexesToRemove.addAll(consumed);
            i = consumed.get(consumed.size() - 1) + 1;
        }

        Collections.sort(indexesToRemove, Collections.reverseOrder());//Remove from back to front
        for (Integer index : indexesToRemove) {
            stringArgs.remove(index.intValue());
            argumentos.remove(index.intValue());
        }

        return new ScanOutcome(matched, unknownMarkers, markersMissingValue, markersRefusingValue, repeatedMarkers);
    }

    /**
     * Consumes a quoted value group opening at {@code opening} and continuing at
     * {@code continueFromIndex}: tokens are joined with a single space until one ENDS with the same
     * quote character (both quotes stripped), or every remaining token is swallowed if the quote never
     * closes. Indices read from {@code continueFromIndex} on are appended to {@code consumed}.
     * <p>
     * The opening fragment comes in as text because it may be a token of its own or the tail of a
     * {@code --name='...} marker.
     */
    private String consumeQuotedValue(String opening, int continueFromIndex, List<Integer> consumed){
        char quote = opening.charAt(0);
        String first = opening.substring(1); //strip the opening quote
        if (first.length() > 0 && first.charAt(first.length() - 1) == quote){
            return first.substring(0, first.length() - 1); //closed inside the very same fragment
        }

        StringBuilder valueBuilder = new StringBuilder(first);
        for (int j = continueFromIndex; j < stringArgs.size(); j++) {
            String part = stringArgs.get(j);
            consumed.add(j);
            valueBuilder.append(" ");
            if (part.length() > 0 && part.charAt(part.length() - 1) == quote){
                valueBuilder.append(part, 0, part.length() - 1); //strip the closing quote
                break;
            }else {
                valueBuilder.append(part);
            }
        }
        return valueBuilder.toString();
    }

    private interface FlagRecognizer {
        /** @return the recognized binding for this marker's normalized name, or null if unrecognized (declared mode only). */
        Recognition recognize(String normalizedName);
    }

    private static final class Recognition {
        private final String canonicalName;
        private final int arity; //0 = presence, 1 = fixed single value, negative = sniff (manual mode)

        private Recognition(String canonicalName, int arity) {
            this.canonicalName = canonicalName;
            this.arity = arity;
        }
    }

    private static final class ScanOutcome {
        private final List<FlagedArgumento> matched;
        private final List<String> unknownMarkers;
        private final List<String> markersMissingValue;
        private final List<String> markersRefusingValue;
        private final List<String> repeatedMarkers;

        private ScanOutcome(List<FlagedArgumento> matched, List<String> unknownMarkers,
                            List<String> markersMissingValue, List<String> markersRefusingValue,
                            List<String> repeatedMarkers) {
            this.matched = matched;
            this.unknownMarkers = unknownMarkers;
            this.markersMissingValue = markersMissingValue;
            this.markersRefusingValue = markersRefusingValue;
            this.repeatedMarkers = repeatedMarkers;
        }
    }

    /**
     * What {@link #extractDeclaredFlags} could not turn into a flag. Three different sentences, so
     * they stay three lists: a name nobody declared is a typo in the NAME, while the other two are
     * about a declared flag whose spelling and whose declaration disagree over the value - and telling
     * a player "unknown flag" when the flag is right and only the value is missing sends them looking
     * in the wrong place.
     * <p>
     * Every marker listed here was left in the positional tokens: the scan reports, it does not guess.
     */
    public static final class FlagExtraction {

        private final List<String> unknownMarkers;
        private final List<String> markersMissingValue;
        private final List<String> markersRefusingValue;
        private final List<String> repeatedMarkers;

        private FlagExtraction(List<String> unknownMarkers, List<String> markersMissingValue,
                               List<String> markersRefusingValue, List<String> repeatedMarkers) {
            this.unknownMarkers = unknownMarkers;
            this.markersMissingValue = markersMissingValue;
            this.markersRefusingValue = markersRefusingValue;
            this.repeatedMarkers = repeatedMarkers;
        }

        /** Whether every marker on the line became a flag - nothing to report. */
        public boolean isClean() {
            return unknownMarkers.isEmpty() && markersMissingValue.isEmpty()
                    && markersRefusingValue.isEmpty() && repeatedMarkers.isEmpty();
        }

        /** Markers no declaration knows, verbatim as typed (e.g. {@code "--froce"}). */
        public List<String> getUnknownMarkers() {
            return unknownMarkers;
        }

        /**
         * Declared value flags left with nothing to take: end of line, another flag, the bare
         * {@code --}, or an {@code =} with nothing after it. Named without the {@code =}, because the
         * flag is right and only its value is missing.
         */
        public List<String> getMarkersMissingValue() {
            return markersMissingValue;
        }

        /** Declared presence flags given an inline value ({@code --force=false}), named without it. */
        public List<String> getMarkersRefusingValue() {
            return markersRefusingValue;
        }

        /**
         * Flags written more than once on the same line, named once each by the SECOND spelling that
         * claimed them - which is the one the sender can delete. A flag holds one value, so a second
         * one is not a correction and not a list: it is two answers, and picking either silently is
         * how {@code --page 1 --page 2} used to quietly mean page one.
         */
        public List<String> getRepeatedMarkers() {
            return repeatedMarkers;
        }
    }

    /**
     * A declared flag's fixed extraction rule, used by {@link #extractDeclaredFlags}: the
     * canonical name every recognized spelling (long name and aliases alike) is normalized to, and
     * how many following tokens a marker for it consumes (0 = presence-only, e.g. a Boolean flag;
     * 1 = always consumes the next token or quoted group).
     */
    public static final class FlagBinding {
        private final String canonicalName;
        private final int arity;

        public FlagBinding(String canonicalName, int arity) {
            this.canonicalName = canonicalName;
            this.arity = arity;
        }

        public String getCanonicalName() {
            return canonicalName;
        }

        public int getArity() {
            return arity;
        }
    }

    /**
     * Whether {@code token} is a flag marker by the same rule {@link #flagify()}/{@link #extractDeclaredFlags}
     * use internally: one or more leading {@code -} followed by a non-digit (so negative numbers like
     * {@code -5} stay positional), and at least one character after the dashes. Exposed for
     * {@code FinalCMDPluginCommand}'s tab-complete, which needs the same recognition rule outside
     * a real scan (e.g. to tell whether the word currently being typed is itself a flag name).
     */
    public static boolean isFlagMarker(String token){
        int dashCount = leadingDashCount(token);
        if (dashCount == 0 || dashCount == token.length()){
            return false; //no leading dash at all, or the token is dashes only (no name)
        }
        return !Character.isDigit(token.charAt(dashCount)); //negative-number guard
    }

    /**
     * The name a marker token looks a declaration up by: leading dashes stripped, inline value
     * ({@code --page=3}) dropped, lowercased. Exposed so the traversal and tab-complete ask the same
     * question the scan does, instead of each deriving its own answer.
     */
    public static String flagLookupName(String markerToken){
        String spelling = markerToken.substring(leadingDashCount(markerToken));
        int equalsAt = spelling.indexOf('=');
        return (equalsAt < 0 ? spelling : spelling.substring(0, equalsAt)).toLowerCase();
    }

    private static int leadingDashCount(String token){
        int count = 0;
        while (count < token.length() && token.charAt(count) == '-'){
            count++;
        }
        return count;
    }

    private static boolean isQuoteChar(char c){
        return c == '\'' || c == '"';
    }

    public void forEach(Consumer<Argumento> action){
        argumentos.forEach(action);
    }

    public Stream<Argumento> stream(){
        return argumentos.stream();
    }

    public List<Argumento> getArgs(){
        return this.argumentos;
    }

    public List<FlagedArgumento> getFlags(){
        flagify();
        return this.flags;
    }

    /**
     * A new instance holding the tokens from {@code offset} on, so an executable at the end of a
     * command path reads {@code get(0)} as its own first argument instead of counting the path.
     * <p>
     * Only the tokens travel: the new instance is not flagified, and any flag already extracted from
     * this one stays behind.
     */
    public MultiArgumentos sliceFrom(int offset){
        Validate.isTrue(offset >= 0, "The offset cannot be negative");
        if (offset >= stringArgs.size()){
            return new MultiArgumentos(new String[0]);
        }
        List<String> tail = stringArgs.subList(offset, stringArgs.size());
        return new MultiArgumentos(tail.toArray(new String[0]));
    }

    public Argumento get(int index){
        return index < this.argumentos.size() ? this.argumentos.get(index) : Argumento.EMPTY_ARG;
    }

    public FlagedArgumento getFlag(String flagName){
        flagify();
        if (flags.size() > 0){
            Validate.isTrue(!flagName.isEmpty(), "The flagName cannot be empty");
            String normalizedName = flagName.substring(leadingDashCount(flagName)); //dash-count-agnostic lookup: "x"/"-x"/"--x" all resolve the same flag

            for (FlagedArgumento flag : flags) {
                if (flag.getFlagName().equalsIgnoreCase(normalizedName)){
                    return flag;
                }
            }
        }
        return FlagedArgumento.EMPTY_ARG;
    }

    public boolean emptyArgs(int... numbers){
        if (numbers != null && numbers.length > 0){
            for (int argNumber : numbers) {
                if (get(argNumber).isEmpty()){
                    return true;
                }
            }
        }
        return false;
    }

    public FCTimeFrame getTimeFrame(int indexStart){
        return getTimeFrame(indexStart, stringArgs.size());
    }

    public FCTimeFrame getTimeFrame(int indexStart, int indexEndExclusive){
        String joinString = String.join(" ", stringArgs.subList(indexStart, indexEndExclusive));
        if (joinString.isEmpty()) return null;
        try {
            Long textToMillis = FCTimeUtil.toMillis(joinString);
            if (textToMillis != null) return FCTimeFrame.of(textToMillis);
        }catch (Exception ignored){

        }
        return null;
    }

    // -----------------------------------------------------------------------------------------------------------------------------//
    // Plain String Part
    // -----------------------------------------------------------------------------------------------------------------------------//

    public List<String> getStringArgs(){
        return this.stringArgs;
    }

    public String getStringArg(int index){
        return index < this.stringArgs.size() ? this.stringArgs.get(index) : "";
    }

    public String joinStringArgs(){
        return String.join(" ", this.stringArgs);
    }

    public String joinStringArgs(int indexStart, int indexEndExclusive){
        return String.join(" ", stringArgs.subList(indexStart, indexEndExclusive));
    }

    public String joinStringArgs(int indexStart){
        return String.join(" ", stringArgs.subList(indexStart,stringArgs.size()));
    }

}
