package br.com.finalcraft.evernifecore.argumento;

import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCTimeUtil;
import org.apache.commons.lang.Validate;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
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
        this(args,false);
    }

    public MultiArgumentos(String[] args, boolean flagedArgs) {
        for (String arg : args) {
            stringArgs.add(arg);
            argumentos.add(new Argumento(arg));
        }

        if (flagfied == true){ //We must flagify this than!
            flagify();
        }
    }

    public void flagify(){
        if (!flagfied){
            flagfied = true;
            List<Integer> allArgsToBeRemoved = new ArrayList<Integer>();

            for (int i = 0; i < stringArgs.size(); i++) {

                String theArg = stringArgs.get(i);

                if (theArg.charAt(0) == '-' && !(theArg.length() > 1 && Character.isDigit(theArg.charAt(1)))){

                    List<Integer> flagIndexes = new ArrayList<>(Arrays.asList(i)); //All flags this FlagArgument takes, like 'args 3, 4 and 5'
                    FlagedArgumento flagedArgumento = new FlagedArgumento(theArg);

                    Character QUOTE_AT_START = null;
                    String flagValue = flagedArgumento.getFlagValue();
                    if (flagValue.length() > 0){
                        char firstChar = flagedArgumento.getFlagValue().charAt(0);
                        if (firstChar == '\'' || firstChar == '"'){
                            QUOTE_AT_START = firstChar;
                        }
                    }

                    //If the first char is ' then we need to get the next args until we find the last '
                    boolean foundLastQuote = false;
                    if (QUOTE_AT_START != null){

                        if (flagValue.charAt(flagValue.length() - 1) == QUOTE_AT_START){
                            //In this case, is probably something like  /command blabla -player:'EverNife'
                            //So we only need to remove the quote from the flag value
                            flagedArgumento = new FlagedArgumento(StringUtils.substring(theArg, 1, -1));
                        }else {
                            //This case is something like               /command blabla -message:'The night is Dark!'
                            //Or a similar case like                    /command blabla -message:'The night is Dark     without the ending quote

                            //Remove prefix   '   from the flag value
                            StringBuilder stringBuilder = new StringBuilder(flagedArgumento.getFlagName() + ":" + StringUtils.substring(flagedArgumento.getFlagValue(), 1));

                            //Then serach for the existance of an ending quote
                            int j = i + 1;
                            for (; j < stringArgs.size(); j++) {
                                flagIndexes.add(j);
                                String arg = stringArgs.get(j);
                                stringBuilder.append(" ");
                                if (arg.charAt(arg.length() - 1) == QUOTE_AT_START){
                                    //Remove suffix   '   from the flag value
                                    stringBuilder.append(StringUtils.substring(arg, 0, -1));
                                    foundLastQuote = true;
                                    break;
                                }else {
                                    stringBuilder.append(arg);
                                }
                            }
                            if (foundLastQuote){
                                flagedArgumento = new FlagedArgumento(stringBuilder.toString());
                                i = j;
                            }
                        }
                    }

                    if (!foundLastQuote){
                        allArgsToBeRemoved.add(i);
                    }else {
                        allArgsToBeRemoved.addAll(flagIndexes);
                    }
                    flags.add(flagedArgumento);
                }
            }
            Collections.reverse(allArgsToBeRemoved);//Remove from back to front
            for (Integer flagIndex : allArgsToBeRemoved) {
                stringArgs.remove(flagIndex.intValue());
                argumentos.remove(flagIndex.intValue());
            }
        }
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
            if (!flagName.startsWith("-")){
                flagName = "-" + flagName; //Enforce AT LEAST ONE LEADING SLASH when getting a flag
            }

            for (FlagedArgumento flag : flags) {
                if (flag.getFlagName().equalsIgnoreCase(flagName)){
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
