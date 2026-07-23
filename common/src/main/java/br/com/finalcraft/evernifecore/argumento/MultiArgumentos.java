package br.com.finalcraft.evernifecore.argumento;

import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.everylibs.util.FCTimeUtil;
import org.apache.commons.lang3.Validate;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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

            List<Integer> consumed = new ArrayList<Integer>();
            consumed.add(i);

            String flagName = token.substring(leadingDashCount(token));
            String value;

            int nextIndex = i + 1;
            if (nextIndex >= stringArgs.size()){
                value = "true"; //Nothing follows: presence flag
            }else {
                String nextToken = stringArgs.get(nextIndex);
                if (nextToken.equals("--") || isFlagMarker(nextToken)){
                    value = "true"; //Next token belongs to something else: presence flag, don't consume it
                }else if (!nextToken.isEmpty() && isQuoteChar(nextToken.charAt(0))){
                    char quote = nextToken.charAt(0);
                    StringBuilder valueBuilder = new StringBuilder();
                    boolean firstValueToken = true;
                    int j = nextIndex;
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
                    value = valueBuilder.toString();
                }else {
                    value = nextToken;
                    consumed.add(nextIndex);
                }
            }

            flags.add(new FlagedArgumento(flagName, value));
            indexesToRemove.addAll(consumed);
            i = consumed.get(consumed.size() - 1) + 1;
        }

        Collections.sort(indexesToRemove, Collections.reverseOrder());//Remove from back to front
        for (Integer index : indexesToRemove) {
            stringArgs.remove(index.intValue());
            argumentos.remove(index.intValue());
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
