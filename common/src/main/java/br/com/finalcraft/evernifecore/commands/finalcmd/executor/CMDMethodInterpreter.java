package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.FlagedArgumento;
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
import br.com.finalcraft.evernifecore.fancytext.FancySegment;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.FCLocaleManager;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.locale.scanner.FCLocaleScanner;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.everylibs.commons.Tuple;
import jakarta.annotation.Nullable;

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
    private final Map<Integer, FlagBinding> flagBindings = new LinkedHashMap<>(); // Args with @FlagArg annotation, keyed by parameter index
    private final Map<String, MultiArgumentos.FlagBinding> flagExtractionBindings = new LinkedHashMap<>(); //every declared name/alias (normalized) -> its extraction rule, fed to MultiArgumentos#extractDeclaredFlags

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

        List<Tuple<Class, Annotation[]>> argsAndAnnotations = MethodArgScanner.getArgsAndAnnotationsDeeply(method);

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

            if (!argData.getDef().isEmpty() && argRequirementType != ArgRequirementType.OPTIONAL){
                throw new ArgMountException("The @Arg [" + argData.getName() + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                        "declares def() but is not [optional]; def() is only legal on [optional] arguments.");
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

            // Load the ArgParser's own @FCLocale static fields
            ECPluginData parserPlugin = ECPluginManager.getProvidingPlugin(argData.getParser());
            FCLocaleManager.loadLocale(parserPlugin, true, argData.getParser());

            arguments.put(index, parserInstance); //Index of the methodOrder eco_give(Player, arg1, PlayerData, arg3, etc...)
            tabParsers.put(flagArgIndex, parserInstance); //Index of the final TabParser (/eco give arg1 arg2)
            flagArgIndex++;
        }

        Set<String> claimedFlagSpellings = new HashSet<>(); //normalized (dashes stripped, lowercase) name/alias, unique across every @FlagArg on this method

        for (Map.Entry<Integer, Tuple<ArgData, Class>> entry : methodData.getFlagArgDataMap().entrySet()) {
            Integer index = entry.getKey();
            ArgData argData = entry.getValue().getLeft();
            Class parameterClazz = entry.getValue().getRight();

            if (methodData.getArgDataMap().containsKey(index)){
                throw new ArgMountException("The @FlagArg [" + argData.getName() + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                        "parameter [index=" + index + "] declares both @Arg and @FlagArg; a parameter can only be one or the other.");
            }

            if (parameterClazz.isPrimitive()){
                throw new ArgMountException("The @FlagArg [" + argData.getName() + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                        "is a primitive (" + parameterClazz.getName() + "); flags are never primitive - use the wrapper type (e.g. Boolean, Integer) so an absent flag can be null.");
            }

            String rawName = argData.getName();
            if (!rawName.startsWith("--") || rawName.contains(" ") || rawName.substring(2).trim().isEmpty()){
                throw new ArgMountException("The @FlagArg name [" + rawName + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                        "must be in the long form '--name', with no spaces and a non-empty name after the dashes.");
            }
            String canonicalName = rawName.substring(2).toLowerCase();

            List<String> spellings = new ArrayList<>();
            spellings.add(rawName);
            for (String alias : argData.getAliases()) {
                if (!alias.startsWith("-")){
                    throw new ArgMountException("The @FlagArg alias [" + alias + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                            "parameter [" + rawName + "] must start with at least one '-'.");
                }
                spellings.add(alias);
            }

            for (String spelling : spellings) {
                String normalizedSpelling = spelling.replaceFirst("^-+", "").toLowerCase();
                if (!claimedFlagSpellings.add(normalizedSpelling)){
                    throw new ArgMountException("The @FlagArg spelling [" + spelling + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                            "is already claimed by a name or alias declared earlier on the same method.");
                }
                flagExtractionBindings.put(normalizedSpelling, new MultiArgumentos.FlagBinding(canonicalName, parameterClazz == Boolean.class ? 0 : 1));
            }

            if (ArgParser.class == argData.getParser()){
                //This means the DEFAULT parser, so, we look over the ArgParserManager
                Class<? extends ArgParser> parserClass = ArgParserManager.getParser(owningPlugin, parameterClazz);
                if (parserClass == null){
                    throw new IllegalStateException("Failed to found the proper ArgParser on the FinalCMD (" + executor.getClass().getName() +")[" + method.getName() +"] flag {index='" + index + "', name='" + rawName + "'}. The dev should set it manually or register it on the ArgParserManager!");
                }
                argData.setParser(parserClass);
            }

            //Flags never pass through ArgRequirementType.getArgumentType (that needs bracket-quoted names like an @Arg has);
            //REQUIRED here only makes the shared builtin ArgParsers throw on an unparseable value/def instead of silently
            //returning null (the behavior they already reserve for a REQUIRED positional) - the flag's own PRESENCE is
            //still never required, see the absence handling in invoke().
            ArgInfo flagArgInfo = new ArgInfo(parameterClazz, argData, -1, ArgRequirementType.REQUIRED);
            ArgParser flagParserInstance;
            try {
                Constructor<? extends ArgParser> constructor = argData.getParser().getDeclaredConstructor(ArgInfo.class);
                constructor.setAccessible(true);
                flagParserInstance = constructor.newInstance(flagArgInfo);
            }catch (Exception e){
                e.printStackTrace();
                throw new IllegalStateException("Failed to instantiate the ArgParser on the FinalCMD (" + executor.getClass().getName() +")[" + method.getName() +"] flag [index=" + index + ", name=" + rawName + "]");
            }

            // Load the ArgParser's own @FCLocale static fields
            ECPluginData flagParserPlugin = ECPluginManager.getProvidingPlugin(argData.getParser());
            FCLocaleManager.loadLocale(flagParserPlugin, true, argData.getParser());

            flagBindings.put(index, new FlagBinding(index, canonicalName, rawName, parameterClazz == Boolean.class, argData, flagParserInstance));
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
        }else if (cmdData.getDescriptionOverride() != null){
            //Runtime-only, per-instance description (e.g. a command alias): already a fully-formed,
            //unregistered LocaleMessageImp (see LocaleMessageImp#derivePlaceholderResolved), so every
            //locale it carries renders - this is what makes a dynamic command's hover multi-language.
            localeMessage = cmdData.getDescriptionOverride();
        }else {
            //Neither a declarative locales() nor a runtime override: a single, empty, per-interpreter,
            //unregistered LocaleMessageImp so the usage line still renders (hover stays absent).
            ECPluginData ecPluginData = ECPluginManager.getOrCreateECorePluginData(owningPlugin);
            localeMessage = new LocaleMessageImp(owningPlugin, localeMessageKey, false);
            localeMessage.addLocale(ecPluginData.getPluginLanguage(), new FancySegment(null, null));
        }

        HashMap<ArgParser<?>, LocaleMessageImp> argParserToLocale = new HashMap<>(); //This will hold every single @Arg locale message
        for (ArgParser argParser : arguments.values()) {
            if (argParser.getArgInfo().getArgData().getLocales().length > 0){
                LocaleMessageImp localesForThisArg = FCLocaleScanner.scanForLocale(owningPlugin, localeMessageKey + "_Argumento." + argParser.getArgInfo().getArgData().getName(), false, argParser.getArgInfo().getArgData().getLocales());
                argParserToLocale.put(argParser, localesForThisArg);
            }
        }

        HashMap<FlagBinding, LocaleMessageImp> flagBindingToLocale = new HashMap<>(); //This will hold every single @FlagArg locale message
        for (FlagBinding binding : flagBindings.values()) {
            if (binding.argData.getLocales().length > 0){
                LocaleMessageImp localesForThisFlag = FCLocaleScanner.scanForLocale(owningPlugin, localeMessageKey + "_Flag." + binding.canonicalName, false, binding.argData.getLocales());
                flagBindingToLocale.put(binding, localesForThisFlag);
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
                fancyTextOrFormatter.hover(description);
                fancyTextOrFormatter.clickSuggest("/%label% %subcmd%");
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
                                fancyFormatter.hover(description + "" +
                                        "\n" +
                                        "\n §d ✯ §7§l[§e" + argParser.getArgInfo().getArgData().getName() + "§7§l]§r" +
                                        "\n §7● §6" + extraDescription);
                            }
                        });
            }

            //Flags always render as a compact "[--name]" token after the positionals (in declaration
            //order), regardless of arity - the value's own shape stays in the hover, not the usage line.
            //Unlike a plain @Arg, a visible flag ALWAYS gets its own hover block (title + aliases), even
            //without a locale - it is the only place a player can discover a flag's short alias at all.
            AtomicBoolean anyLocalizedFlag = new AtomicBoolean(false);
            for (FlagBinding binding : flagBindings.values()) {
                if (!binding.argData.isShowOnUsage()){
                    continue; //showOnUsage=false: invisible on usage AND hover, still tab-completable/functional
                }

                fancyFormatter.append(" [" + binding.rawName + "]");
                applyDefaultFormatting.accept(fancyFormatter);

                String flagTitle = binding.rawName;
                if (binding.argData.getAliases().length > 0){
                    flagTitle += " | " + String.join(" | ", binding.argData.getAliases());
                }

                LocaleMessage localesForThisFlag = flagBindingToLocale.getOrDefault(binding, null);
                String extraDescription = null;
                if (localesForThisFlag != null){
                    FancyText flagFancyText = localesForThisFlag.getFancyText(locale);
                    if (flagFancyText == null){
                        flagFancyText = localesForThisFlag.getDefaultFancyText();
                    }
                    if (flagFancyText != null){
                        extraDescription = flagFancyText.getHoverText() != null && !flagFancyText.getHoverText().isEmpty() ? flagFancyText.getHoverText() : flagFancyText.getText();
                    }
                }

                anyLocalizedFlag.set(true);
                String flagBlock = (description != null ? description : "") +
                        "\n" +
                        "\n §d ✯ §7§l[§e" + flagTitle + "§7§l]§r";
                if (extraDescription != null){
                    flagBlock += "\n §7● §6" + extraDescription;
                }
                fancyFormatter.hover(flagBlock);
            }

            if (anyLocalizedArg.get() || anyLocalizedFlag.get()){
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

    /** Whether this method declares any {@code @FlagArg} parameter - gates the flag-aware tab-complete branch (F6). */
    public boolean hasFlags() {
        return !flagBindings.isEmpty();
    }

    /** Every declared {@code @FlagArg} binding, in declaration order - read by help rendering and tab-complete (F6). */
    public Collection<FlagBinding> getFlagBindings() {
        return flagBindings.values();
    }

    public @Nullable FlagBinding getFlagBindingByCanonicalName(String canonicalName) {
        for (FlagBinding binding : flagBindings.values()) {
            if (binding.canonicalName.equals(canonicalName)){
                return binding;
            }
        }
        return null;
    }

    /**
     * Every declared spelling (name/alias, normalized) mapped to its fixed extraction rule - the same
     * map fed to {@link MultiArgumentos#extractDeclaredFlags}, exposed so tab-complete (F6) can run its
     * own read-only scan over {@code args[0..index-1]} through the identical {@code scanFlagMarkers}
     * core, instead of re-deriving the tokenizer rules.
     */
    public Map<String, MultiArgumentos.FlagBinding> getFlagExtractionBindings() {
        return flagExtractionBindings;
    }

    public void invoke(FCommandSender sender, String label, MultiArgumentos argumentos, HelpContext helpContext, HelpLine helpLine) throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {

        helpContext.setLastLabel(label);

        Object[] theArgs = new Object[contextualArguments.size() + arguments.size() + flagBindings.size()];
        LinkedHashMap<Class, Object> parsedArgs = new LinkedHashMap<>();
        LinkedHashMap<Class, Object> parsedContext = new LinkedHashMap<>();

        if (!flagBindings.isEmpty()){
            //Extraction happens BEFORE any positional parse below, so the positional loop only ever sees the
            //already-stripped list - a flag can live anywhere on the command line (before/between/after positionals).
            List<String> unknownFlags = argumentos.extractDeclaredFlags(flagExtractionBindings);

            if (!unknownFlags.isEmpty()){
                sendUnknownFlagMessage(sender, unknownFlags.get(0));
                return;
            }

            for (FlagBinding binding : flagBindings.values()) {
                FlagedArgumento matchedFlag = argumentos.getFlag(binding.canonicalName);

                Object parsedValue;
                if (matchedFlag.isSet()){
                    String permission = binding.argData.getPermission();
                    if (!permission.isEmpty() && !FCMessageUtil.hasThePermission(sender, permission)){
                        return;
                    }

                    if (binding.booleanFlag){
                        //A Boolean flag never reaches its parser when present: aridade 0 means it never
                        //consumed a value token, so there is nothing meaningful to parse - presence IS true.
                        parsedValue = Boolean.TRUE;
                    }else {
                        try {
                            ArgParserCommandContext argContext = new ArgParserCommandContext(helpContext, helpLine, label, argumentos, parsedArgs, parsedContext, true);
                            //A plain Argumento, not the FlagedArgumento itself: FlagedArgumento overrides
                            //toString() for display ("--name value"), and several builtin ArgParsers call
                            //argumento.toString() expecting just the raw value.
                            parsedValue = binding.parser.parserArgument(argContext, sender, new Argumento(matchedFlag.getFlagValue()));
                        }catch (ArgParseException argParseException){
                            //Same contract as a positional: the parser already messaged the sender
                            return;
                        }
                    }
                }else if (!binding.argData.getDef().isEmpty()){
                    try {
                        ArgParserCommandContext argContext = new ArgParserCommandContext(helpContext, helpLine, label, argumentos, parsedArgs, parsedContext, true);
                        parsedValue = binding.parser.parserArgument(argContext, sender, new Argumento(binding.argData.getDef()));
                    }catch (ArgParseException argParseException){
                        return;
                    }
                }else {
                    parsedValue = null; //absent, no def(): a flag is never required, this is a normal outcome
                }

                theArgs[binding.paramIndex] = parsedValue;
                if (parsedValue != null){
                    parsedArgs.put(parsedValue.getClass(), parsedValue);
                }
            }
        }

        int backwardNiddle = 0;//This is used to go backwards on the possibleArgs array when necessary

        for (Map.Entry<Integer, ArgParser> entry : arguments.entrySet()) {
            Integer index = entry.getKey();
            ArgParser parser = entry.getValue();

            Argumento argumento = argumentos.get(parser.getArgInfo().getIndex() - backwardNiddle);
            if (argumento.isEmpty() && parser.getArgInfo().isRequired() == true && parser.getArgInfo().isProvidedByContext() == false){
                helpLine.sendTo(sender);
                return;
            }
            String def = parser.getArgInfo().getArgData().getDef();
            if (argumento.isEmpty() && !def.isEmpty()){
                //Optional argument omitted (or given as an explicit empty string) and a def() is declared:
                //feed the def() text through the same parser, as if the player had typed it themselves.
                argumento = new Argumento(def);
            }
            try {
                ArgParserCommandContext argContext = new ArgParserCommandContext(helpContext, helpLine, label, argumentos, parsedArgs, parsedContext, false);
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
                ArgParserCommandContext argContext = new ArgParserCommandContext(helpContext, helpLine, label, argumentos, parsedArgs, parsedContext, false);
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

    private void sendUnknownFlagMessage(FCommandSender sender, String unknownRawToken){
        String availableFlags = flagBindings.values().stream()
                .filter(binding -> binding.argData.getPermission().isEmpty() || sender.hasPermission(binding.argData.getPermission()))
                .map(binding -> binding.rawName)
                .collect(Collectors.joining(", "));

        FCDefaultExecutor.UNKNOWN_FLAG
                .addPlaceholder("%flag%", unknownRawToken)
                .addPlaceholder("%available_flags%", availableFlags)
                .send(sender);
    }

    /**
     * A single {@code @FlagArg} parameter, bound at registration time (fail-fast) and resolved on
     * every invoke. Also read by the help renderer ({@code buildHelpLine}) and by
     * {@code FinalCMDPluginCommand}'s tab-complete (F6), which is why it is a public nested type
     * with getters instead of staying invoke()-only.
     */
    public static final class FlagBinding {
        private final int paramIndex;
        private final String canonicalName; //dashes stripped, lowercase - matches MultiArgumentos.FlagBinding's canonical name
        private final String rawName; //as declared, e.g. "--force" - used for error/help/tab display
        private final boolean booleanFlag; //true when the parameter type is Boolean: aridade 0, presence == TRUE
        private final ArgData argData; //carries def/permission/aliases/locales/showOnUsage
        private final ArgParser parser;

        private FlagBinding(int paramIndex, String canonicalName, String rawName, boolean booleanFlag, ArgData argData, ArgParser parser) {
            this.paramIndex = paramIndex;
            this.canonicalName = canonicalName;
            this.rawName = rawName;
            this.booleanFlag = booleanFlag;
            this.argData = argData;
            this.parser = parser;
        }

        public String getCanonicalName() {
            return canonicalName;
        }

        public String getRawName() {
            return rawName;
        }

        public boolean isBooleanFlag() {
            return booleanFlag;
        }

        public ArgData getArgData() {
            return argData;
        }

        public ArgParser getParser() {
            return parser;
        }
    }
}
