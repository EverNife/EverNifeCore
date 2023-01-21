package br.com.finalcraft.evernifecore.argumento;

import br.com.finalcraft.evernifecore.time.FCTimeFrame;
import br.com.finalcraft.evernifecore.util.FCTimeUtil;
import org.apache.commons.lang.Validate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class MultiArgumentos {

    private final List<String> stringArgs = new ArrayList<String>();
    private final List<FlagedArgumento> flags = new ArrayList<FlagedArgumento>();
    private final List<Argumento> argumentos = new ArrayList<Argumento>();

    private boolean flagfied;

    public MultiArgumentos(String[] args) {
        this(args,false);
    }

    public MultiArgumentos(String[] args, boolean flagedArgs) {
        flagfied = flagedArgs;
        for (int i = 0; i < args.length; i++) {
            String theArg = args[i];
            if (flagedArgs && theArg.charAt(0) == '-' && !(theArg.length() > 1 &&  Character.isDigit(theArg.charAt(1)))){

                FlagedArgumento flagedArgumento = new FlagedArgumento(theArg);

                if (flagedArgumento.getFlagValue().length() > 0 && flagedArgumento.getFlagValue().charAt(0) == '\''){
                    //If the first char is ' then we need to get the next args until we find the last '
                    StringBuilder stringBuilder = new StringBuilder();
                    boolean foundLastQuote = false;
                    int j = i;
                    for (; j < args.length; j++) {
                        String arg = args[j];
                        stringBuilder.append(arg);
                        if (arg.charAt(arg.length() - 1) == '\''){
                            foundLastQuote = true;
                            break;
                        }
                    }
                    if (foundLastQuote){
                        flagedArgumento = new FlagedArgumento(stringBuilder.toString());
                        i = j;
                    }
                }

                flags.add(flagedArgumento);
                continue;
            }
            stringArgs.add(theArg);
            argumentos.add(new Argumento(theArg));
        }
    }

    public void flagify(){
        if (!flagfied){
            flagfied = true;
            for (int i = stringArgs.size() - 1; i >= 0; i--) {
                String theArg = stringArgs.get(i);
                if (theArg.charAt(0) == '-' && !(theArg.length() > 1 && Character.isDigit(theArg.charAt(1)))){
                    stringArgs.remove(i);
                    argumentos.remove(i);
                    flags.add(0, new FlagedArgumento(theArg));
                }
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
