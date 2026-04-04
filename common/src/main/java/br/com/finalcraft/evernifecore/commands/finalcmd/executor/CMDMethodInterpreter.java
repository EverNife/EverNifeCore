package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgContextualData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.SubCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.*;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.FancyFormatter;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.locale.scanner.FCLocaleScanner;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCReflectionUtil;
import br.com.finalcraft.evernifecore.util.commons.Tuple;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class CMDMethodInterpreter {

    private final ECPluginData owningPlugin;
    private final Method method;
    private final Object executor;
    private final CMDData<?> cmdData;
    private final String[] labels; //Alias of the command or name of the subCMD
    private final boolean isSubCommand;
    private final boolean playerOnly;
    private final Map<Integer, ArgParser> arguments = new LinkedHashMap<>(); // Args with @Arg annotation
    private final Map<Integer, ArgParserContextual> contextualArguments = new LinkedHashMap(); //Args without any annotation or with @ContextualArg
    private final Map<Integer, ITabParser> tabParsers = new LinkedHashMap<>();

    private transient HelpLine helpLine;

    public CMDMethodInterpreter(ECPluginData owningPlugin, MethodData<?> methodData, Object executor) {
        this.owningPlugin = owningPlugin;
        this.method = methodData.getMethod();
        this.executor = executor;
        this.cmdData = methodData.getData();
        this.labels = cmdData.getLabels();
        this.isSubCommand = cmdData instanceof SubCMDData;

        if (!method.isAccessible()){
            method.setAccessible(true);
        }

        boolean playerOnly = false;

        List<Tuple<Class, Annotation[]>> argsAndAnnotations = FCReflectionUtil.getArgsAndAnnotationsDeeply(method);

        int flagArgIndex = isSubCommand ? 1 : 0;
        for (Map.Entry<Integer, Tuple<ArgData, Class>> entry : methodData.getArgDataMap().entrySet()) {
            Integer index = entry.getKey();
            ArgData argData = entry.getValue().getLeft();
            Class parameterClazz = entry.getValue().getRight();

            if (ArgParser.class == argData.getParser()){
                //This means the DEFAULT parser, so, we look over the ArgParserManager
                Class<? extends ArgParser> parserClass = ArgParserManager.getParser(owningPlugin, parameterClazz);
                if (parserClass == null){
                    throw new IllegalStateException("Failed to found the proper ArgParser on the FinalCMD (" + executor.getClass().getName() +")[" + method.getName() +"] parameter {index='" + index + "', name='" + argData.getName() + "'}. The dev should set it manually or register it on the ArgParserManager!");
                }
                argData.setParser(parserClass);
            }

            ArgRequirementType argRequirementType = ArgRequirementType.getArgumentType(argData.getName());
            if (argRequirementType == null){
                String possibleReqTypes = Arrays.stream(ArgRequirementType.values())
                        .map(reqType -> reqType.getStart() + "" + reqType.getEnd())
                        .collect(Collectors.joining(" or "));

                throw new ArgMountException ("Failed to load ArgRequirementType from ArgData [" + argData.getName() + "], usually this means the " +
                        "ArgData.name() is not Quoted within \'" + possibleReqTypes + "\'");
            }

            ArgInfo argInfo = new ArgInfo(parameterClazz, argData, flagArgIndex, argRequirementType); //If subcommand, move arg to the RIGHT 1 slot
            ArgParser parserInstance;
            try {
                Constructor<? extends ArgParser> constructor = argData.getParser().getDeclaredConstructor(ArgInfo.class);
                constructor.setAccessible(true);
                parserInstance = constructor.newInstance(argInfo);
            }catch (Exception e){
                e.printStackTrace();
                throw new IllegalStateException("Failed to instantiate the ArgParser on the FinalCMD (" + executor.getClass().getName() +")[" + method.getName() +"] parameter [index=" + index + ", name=" + argData.getName() + "]");
            }

            arguments.put(index, parserInstance); //Index of the methodOrder eco_give(Player, arg1, PlayerData, arg3, etc...)
            tabParsers.put(flagArgIndex, parserInstance); //Index of the final TabParser (/eco give arg1 arg2)
            flagArgIndex++;
        }

        for (Map.Entry<Integer, Tuple<ArgContextualData, Class>> entry : methodData.getContextualArgDataMap().entrySet()) {
            Integer index = entry.getKey();
            ArgContextualData argContextualData = entry.getValue().getLeft();
            Class parameterClazz = entry.getValue().getRight();

            if (ArgParserContextual.class == argContextualData.getParser()){
                //This means the DEFAULT parser, so, we look over the ArgParserManager
                Class<? extends ArgParserContextual> contextualParserClass = ArgParserManager.getContextualParser(owningPlugin, parameterClazz);
                if (contextualParserClass == null){
                    throw new IllegalStateException("Failed to found the proper ArgParserContextual on the FinalCMD (" + executor.getClass().getName() +")[" + method.getName() +"] parameter {index='" + index + "', class='" + parameterClazz.getSimpleName() + "'}. The dev should set it manually or register it on the ArgParserManager!");
                }
                argContextualData.setParser(contextualParserClass);
            }

            ArgContextualInfo argContextualInfo = new ArgContextualInfo(parameterClazz, argContextualData);
            ArgParserContextual parserInstance;
            try {
                Constructor<? extends ArgParserContextual> constructor = argContextualData.getParser().getDeclaredConstructor(ArgContextualInfo.class);
                constructor.setAccessible(true);
                parserInstance = constructor.newInstance(argContextualInfo);

                if (parserInstance.requiresToBeAPlayer()){
                    playerOnly = true;
                }
            }catch (Exception e){
                e.printStackTrace();
                throw new IllegalStateException("Failed to instantiate the ArgParserContextual on the FinalCMD (" + executor.getClass().getName() +")[" + method.getName() +"] parameter [index=" + index + ", class=" + parameterClazz.getSimpleName() + "]");
            }

            contextualArguments.put(index, parserInstance); //Index of the methodOrder eco_give(Player, arg1, PlayerData, arg3, etc...)
        }

        this.playerOnly = playerOnly;

        if (contextualArguments.size() == 0) {
            throw new IllegalStateException("You tried to create a FinalCMD with a method that has no contextual args at all! You must add parameters like Player, FPlayer, FCommandSender, etc.");
        }

        this.helpLine = buildHelpLine();
    }

    public CMDData<?> getCmdData() {
        return cmdData;
    }

    public HelpLine getHelpLine() {
        return helpLine;
    }

    private HelpLine buildHelpLine(){
        String localeMessageKey = method.getDeclaringClass().getSimpleName() + "." + method.getName().toUpperCase();
        FCLocaleData[] locales = cmdData.getLocales();
        LocaleMessageImp localeMessage;

        if (locales.length > 0){
            localeMessage = FCLocaleScanner.scanForLocale(owningPlugin, localeMessageKey, true, locales);
        }else {
            //If no FCLocale is present, use the cmdData desc() to build it, it will be a static locale, will not be reloaded
            ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(owningPlugin);
            localeMessage = new LocaleMessageImp(owningPlugin, localeMessageKey, false);
            FancyText fancyText = new FancyText(null, cmdData.getDesc());
            //Add to the default locale only
            localeMessage.addLocale(ecPluginData.getPluginLanguage(), fancyText);
        }

        HashMap<ArgParser<?>, LocaleMessageImp> argParserToLocale = new HashMap<>(); //This will hold every single @Arg locale message
        for (ArgParser argParser : arguments.values()) {
            if (argParser.getArgInfo().getArgData().getLocales().length > 0){
                LocaleMessageImp localesForThisArg = FCLocaleScanner.scanForLocale(owningPlugin, localeMessageKey + "_Argumento." + argParser.getArgInfo().getArgData().getName(), false, argParser.getArgInfo().getArgData().getLocales());
                argParserToLocale.put(argParser, localesForThisArg);
            }
        }

        for (Map.Entry<String, FancyText> entry : new ArrayList<>(localeMessage.getFancyTextMap().entrySet())) {
            String locale = entry.getKey();
            FancyText fancyText = entry.getValue();

            //By Default, any Method FCLocale for both FinalCMD and SubCMD should be in the 'hover' not on the 'text'
            //So, we will check boths in here and priorize the hover and remove the text, as the 'text' of these
            //help lines are the USAGE and the hover is the DESCRIPTION
            String textOrHover = fancyText.getHoverText() != null && !fancyText.getHoverText().isEmpty() ? fancyText.getHoverText() : fancyText.getText();
            String description = textOrHover != null && !FCColorUtil.stripColor(textOrHover).trim().isEmpty() ? "§b" + textOrHover : null;

            //For the USAGE we have two scenarios
            // Or we have a declared usage over here, like a full text like '%name% <give|take> <Player>'
            // or we have annotated @Args, in this case, we have a priority on the construction of the usage using these args

            Consumer<FancyText> applyDefaultFormatting = fancyTextOrFormatter -> {
                fancyTextOrFormatter.setHoverText(description);
                fancyTextOrFormatter.setSuggestCommandAction("/%label% %subcmd%");
            };

            FancyFormatter fancyFormatter = FancyFormatter.of("§3§l ▶ §a/§e%label%" + (isSubCommand ? " %subcmd%" : ""));
            applyDefaultFormatting.accept(fancyFormatter);

            AtomicBoolean anyLocalizedArg = new AtomicBoolean(false);
            if (arguments.size() == 0){
                //TODO Remove Legacy Support on Next Major Release, tecnically this is not needed anymore
                //For legacy support we need to remove the placeholders '%name%' and '%label%', on modern ECPLugins we do not use it, maybe one day I might remove this
                fancyFormatter.append(
                        " " + cmdData.getUsage().replace("%name%", "").replace("%label%", "").trim()
                );
                applyDefaultFormatting.accept(fancyFormatter);
            }else {
                //So, if we have customArgs we need to build the proper usage using these args.
                //Put all args one after the other
                arguments.entrySet().stream()
                        .sorted(Comparator.comparingInt(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .forEach(argParser -> {

                            LocaleMessage localesForThisArg = argParserToLocale.getOrDefault(argParser, null);

                            String extraDescription = null;
                            if (localesForThisArg != null){
                                //This means there is a description for this arg.
                                //For example, lets say this arg is "<Player>", there is a FCLocale for this arg saying for exaple "The player to give the money"
                                //This extra info should come at the bottom of the base description!
                                FancyText argFancyText = localesForThisArg.getFancyText(locale);
                                if (argFancyText == null){
                                    argFancyText = localesForThisArg.getDefaultFancyText();
                                }
                                if (argFancyText != null){//I think this will never be null, but whatever
                                    extraDescription = argFancyText.getHoverText() != null && !argFancyText.getHoverText().isEmpty() ? argFancyText.getHoverText() : argFancyText.getText();
                                }
                            }

                            fancyFormatter.append(" " + argParser.getArgInfo().getArgData().getName());
                            applyDefaultFormatting.accept(fancyFormatter);
                            if (extraDescription != null){
                                anyLocalizedArg.set(true);
                                fancyFormatter.setHoverText(description + "" +
                                        "\n" +
                                        "\n §d ✯ §7§l[§e" + argParser.getArgInfo().getArgData().getName() + "§7§l]§r" +
                                        "\n §7● §6" + extraDescription);
                            }
                        });
            }

            if (anyLocalizedArg.get()){
                localeMessage.getFancyTextMap().put(locale, fancyFormatter);
            }else {
                fancyText.setText(fancyFormatter.getFancyTextList().stream().map(FancyText::getText).collect(Collectors.joining()));
                applyDefaultFormatting.accept(fancyText);
            }
        }

        return new HelpLine(localeMessage, cmdData.getPermission());
    }

    public Method getMethod() {
        return method;
    }

    public Object getExecutor() {
        return executor;
    }

    public boolean isPlayerOnly() {
        return playerOnly;
    }

    public Map<Integer, ArgParser> getCustomArguments() {
        return arguments;
    }

    public String[] getLabels(){
        return labels;
    }

    public boolean hasTabComplete(){
        return tabParsers.size() > 0;
    }

    public ITabParser getTabParser(int index) {
        return tabParsers.get(index);
    }

    public void invoke(FCommandSender sender, String label, MultiArgumentos argumentos, HelpContext helpContext, HelpLine helpLine) throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {

        helpContext.setLastLabel(label);

        Object[] theArgs = new Object[contextualArguments.size() + arguments.size()];
        LinkedHashMap<Class, Object> parsedArgs = new LinkedHashMap<>();
        LinkedHashMap<Class, Object> parsedContext = new LinkedHashMap<>();

        int backwardNiddle = 0;//This is used to go backwards on the possibleArgs array when necessary

        for (Map.Entry<Integer, ArgParser> entry : arguments.entrySet()) {
            Integer index = entry.getKey();
            ArgParser parser = entry.getValue();

            Argumento argumento = argumentos.get(parser.getArgInfo().getIndex() - backwardNiddle);
            if (argumento.isEmpty() && parser.getArgInfo().isRequired() == true && parser.getArgInfo().isProvidedByContext() == false){
                helpLine.sendTo(sender);
                return;
            }
            try {
                ArgParserCommandContext argContext = new ArgParserCommandContext(helpContext, helpLine, label, argumentos, parsedArgs, parsedContext);
                Object parsedArgument = parser.parserArgument(argContext, sender, argumento);
                theArgs[index] = parsedArgument;
                if (parsedArgument != null){
                    parsedArgs.put(parsedArgument.getClass(), parsedArgument);
                }
                if (!argContext.shouldMoveArgIndex()){
                    backwardNiddle++;//If we can't move to next argumento, lets look backward on next iteration
                }
            }catch (ArgParseException argParseException){
                //If we fail to parse this arg, for example, "ArgParserPlayer" 'the player is not online', we can leave now
                return;
            }
        }

        for (Map.Entry<Integer, ArgParserContextual> entry : contextualArguments.entrySet()) {
            Integer index = entry.getKey();
            ArgParserContextual parserContextual = entry.getValue();
            try {
                ArgParserCommandContext argContext = new ArgParserCommandContext(helpContext, helpLine, label, argumentos, parsedArgs, parsedContext);
                Object parsedContextual = parserContextual.parserArgument(argContext, sender);
                theArgs[index] = parsedContextual;
                if (parsedContextual != null){
                    parsedContext.put(parsedContextual.getClass(), parsedContextual);
                }
            }catch (ArgParseException argParseException){
                //If we fail to parse this arg, for example, "ArgParserContextualItemStack" 'the player is not holding an itemstack', we can leave now
                return;
            }
        }

        try {
            method.invoke(executor, theArgs);
        }catch (IllegalArgumentException e){
            System.err.println("[CMDMethodInterpreter] IllegalArgumentException on method: " + method.getName());
            System.err.println("Expected args: " + Arrays.toString(method.getParameterTypes()));
            System.err.println("Received args: " + Arrays.toString(Arrays.stream(theArgs).map(arg -> arg == null ? "null" : arg.getClass().getName()).toArray()));
            throw e;
        }
    }
}
