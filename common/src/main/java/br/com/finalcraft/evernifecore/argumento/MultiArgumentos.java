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
     * Scans the raw tokens for {@code --name value} flags (the industry-standard syntax) and strips
     * them out of the positional lists, closing the remaining indices up. Lazy and idempotent: only
     * the first call does any work, so plain positional {@link MultiArgumentos} usages that never
     * touch {@link #getFlags()}/{@link #getFlag(String)} never pay this cost.
     * <p>
     * Tokenization rules:
     * <ul>
     *     <li>A token is a FLAG MARKER when it starts with one or more {@code -} and the first char
     *     after the dashes is not a digit, so negative numbers like {@code -5}/{@code --5} stay
     *     positional. A token made of dashes only (no name after them) is not a marker either.</li>
     *     <li>The flag's name is the marker minus its leading dashes, taken verbatim - a quote glued
     *     directly onto the name (no separating space, e.g. {@code --title'X'}) is part of the name,
     *     not the start of a quoted value: it becomes a flag literally called {@code title'X'}.</li>
     *     <li>The value is the next token, unless that token is itself a flag marker or {@code --},
     *     in which case the flag is a presence flag with value {@code "true"}.</li>
     *     <li>A value token starting with {@code '} or {@code "} opens a quoted group: tokens are
     *     joined with a single space until one ENDS with that same quote character (both quotes are
     *     stripped from the result); an unclosed quote swallows every remaining token verbatim.</li>
     *     <li>A bare {@code --} token ends flag scanning: it is removed and every token after it stays
     *     positional literally, even if it looks like a flag.</li>
     * </ul>
     * Examples: {@code /dbroad Teste My Friend --title 'Title Message'} -> flag {@code title} =
     * {@code "Title Message"}. Without quotes, {@code /dbroad Teste My Friend --title Title Message}
     * -> flag {@code title} = {@code "Title"} and {@code Message} remains a positional argument
     * (multi-word values need quotes).
     */
    public void flagify(){
        if (flagfied){
            return;
        }
        flagfied = true;

        //Manual/sniffed mode: every marker is "recognized" with arity -1 (auto-sniff), so
        //scanFlagMarkers never reports an unknown flag here.
        ScanOutcome outcome = scanFlagMarkers(rawName -> new Recognition(rawName, -1));
        this.flags.addAll(outcome.matched);
    }

    /**
     * Declarative counterpart of {@link #flagify()}: extracts ONLY the flags whose normalized
     * marker name (dashes stripped, case-insensitive) is a key of {@code bindingsByNormalizedNameOrAlias},
     * using the FIXED arity declared for that name instead of sniffing the next token like
     * {@link #flagify()} does. Every recognized marker is stored under its {@link FlagBinding}'s
     * canonical name (so an alias like {@code -f} resolves the same {@link #getFlag(String)} entry
     * as the flag's long name), which is also why a later manual {@link #getFlags()}/
     * {@link #getFlag(String)} call on this same instance sees exactly what was declared here:
     * this method marks the instance as flagified, so {@link #flagify()} becomes a no-op for it -
     * declared and manual extraction are mutually exclusive on the same instance by design.
     * <p>
     * A flag-marker token whose normalized name is NOT a key is left untouched (not stripped) and
     * reported back in the returned list, for the caller to turn into an error.
     *
     * @return every unrecognized flag-marker token, verbatim (e.g. {@code "--froce"}); empty if
     * every marker found matched a declared binding
     * @throws IllegalStateException if this instance was already flagified (declared or manual)
     */
    public List<String> extractDeclaredFlags(Map<String, FlagBinding> bindingsByNormalizedNameOrAlias){
        if (flagfied){
            throw new IllegalStateException("MultiArgumentos was already flagified; extractDeclaredFlags must run exactly once, before any getFlags()/getFlag() call.");
        }
        flagfied = true;

        ScanOutcome outcome = scanFlagMarkers(rawName -> {
            FlagBinding binding = bindingsByNormalizedNameOrAlias.get(rawName.toLowerCase());
            return binding == null ? null : new Recognition(binding.getCanonicalName(), binding.getArity());
        });
        this.flags.addAll(outcome.matched);
        return outcome.unknownMarkers;
    }

    /**
     * Shared core of {@link #flagify()} (manual/sniffed mode) and {@link #extractDeclaredFlags}
     * (fixed-arity mode): walks the raw tokens once, recognizing flag markers and consuming their
     * value (if any), honoring the {@code --} end-of-flags escape and quoted multi-word groups
     * identically in both modes. The two modes differ ONLY in what {@code recognizer} returns for a
     * given marker name:
     * <ul>
     *     <li>{@code arity == 0}: fixed presence flag - never looks at what follows.</li>
     *     <li>{@code arity == 1}: fixed value flag - always consumes the next token or quoted
     *     group, UNLESS that next token is the bare {@code --} (which is never swallowed as a
     *     value, in either mode, so the escape hatch always works); a marker-shaped next token
     *     (e.g. {@code --page --force}) is captured literally, since a declared arity is not
     *     sniffed.</li>
     *     <li>{@code arity < 0} (manual mode only): sniffed - a marker-shaped or {@code --} next
     *     token degrades this marker to a presence flag instead of consuming it.</li>
     *     <li>{@code recognizer} returning {@code null} (declared mode only): the marker is
     *     unrecognized - it is left untouched (not stripped) and reported back instead of being
     *     turned into a matched flag.</li>
     * </ul>
     * A marker with nothing left in the token stream after it always degrades to a presence flag
     * ({@code "true"}), regardless of arity.
     */
    private ScanOutcome scanFlagMarkers(FlagRecognizer recognizer){
        List<FlagedArgumento> matched = new ArrayList<FlagedArgumento>();
        List<String> unknownMarkers = new ArrayList<String>();
        List<Integer> indexesToRemove = new ArrayList<Integer>();
        boolean endOfFlags = false;

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

            if (endOfFlags || !isFlagMarker(token)){
                i++;
                continue;
            }

            String rawName = token.substring(leadingDashCount(token));
            Recognition recognition = recognizer.recognize(rawName);
            if (recognition == null){
                unknownMarkers.add(token); //declared mode only: left untouched, the caller decides how to react
                i++;
                continue;
            }

            List<Integer> consumed = new ArrayList<Integer>();
            consumed.add(i);

            String value;
            int nextIndex = i + 1;

            if (recognition.arity == 0){
                value = "true"; //fixed presence flag: never looks at what follows
            }else if (nextIndex >= stringArgs.size()){
                value = "true"; //nothing follows: degrade to presence
            }else {
                String nextToken = stringArgs.get(nextIndex);
                boolean sniffed = recognition.arity < 0;
                if (nextToken.equals("--") || (sniffed && isFlagMarker(nextToken))){
                    value = "true"; //"--" is never swallowed as a value in any mode; a marker-shaped
                                     //token only degrades a SNIFFED flag (manual mode) - a fixed,
                                     //declared arity of 1 always consumes the next token literally
                }else if (!nextToken.isEmpty() && isQuoteChar(nextToken.charAt(0))){
                    value = consumeQuotedValue(nextIndex, consumed);
                }else {
                    value = nextToken;
                    consumed.add(nextIndex);
                }
            }

            matched.add(new FlagedArgumento(recognition.canonicalName, value));
            indexesToRemove.addAll(consumed);
            i = consumed.get(consumed.size() - 1) + 1;
        }

        Collections.sort(indexesToRemove, Collections.reverseOrder());//Remove from back to front
        for (Integer index : indexesToRemove) {
            stringArgs.remove(index.intValue());
            argumentos.remove(index.intValue());
        }

        return new ScanOutcome(matched, unknownMarkers);
    }

    /**
     * Consumes a quoted value group starting at {@code startIndex} (whose token is already known to
     * start with a quote char): tokens are joined with a single space until one ENDS with that same
     * quote character (both quotes stripped from the result), or every remaining token is swallowed
     * verbatim if the quote is never closed. Every consumed index (including {@code startIndex}) is
     * appended to {@code consumed}.
     */
    private String consumeQuotedValue(int startIndex, List<Integer> consumed){
        char quote = stringArgs.get(startIndex).charAt(0);
        StringBuilder valueBuilder = new StringBuilder();
        boolean firstValueToken = true;
        int j = startIndex;
        for (; j < stringArgs.size(); j++) {
            String part = stringArgs.get(j);
            if (firstValueToken){
                part = part.substring(1); //strip the opening quote
                firstValueToken = false;
            }else {
                valueBuilder.append(" ");
            }
            consumed.add(j);
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

        private ScanOutcome(List<FlagedArgumento> matched, List<String> unknownMarkers) {
            this.matched = matched;
            this.unknownMarkers = unknownMarkers;
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

    private static boolean isFlagMarker(String token){
        int dashCount = leadingDashCount(token);
        if (dashCount == 0 || dashCount == token.length()){
            return false; //no leading dash at all, or the token is dashes only (no name)
        }
        return !Character.isDigit(token.charAt(dashCount)); //negative-number guard
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
