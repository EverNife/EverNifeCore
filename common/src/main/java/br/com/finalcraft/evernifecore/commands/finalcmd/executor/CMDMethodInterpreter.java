package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.FlagedArgumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CaptureData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.SubCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.*;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CapturedBinding;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.DispatchContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgMountException;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLineTemplate;
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
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CMDMethodInterpreter {

    /**
     * What turns the last {@code @Arg} into the tail that takes every token left. An
     * {@code [optional]} tail nobody typed is EMPTY rather than null - "the rest of the line" always
     * exists - unless the argument declares a {@code def()}.
     */
    public static final String GREEDY_SUFFIX = "...";

    /**
     * The shapes a variadic tail can be handed to a method in. The two collections carry the raw
     * tokens, so their element type is always {@link String}: {@code List} keeps the line as typed,
     * {@code Set} keeps the typing order and drops repeats.
     */
    private static final Set<Class<?>> GREEDY_TYPES = new HashSet<Class<?>>(Arrays.asList(
            String.class, String[].class, Argumento.class, MultiArgumentos.class, List.class, Set.class));

    private final ECPluginData owningPlugin;
    private final Method method;
    private final Object executor;
    private final CMDData<?> cmdData;
    private final CommandNode ownerNode;
    private final String[] labels; //Alias of the command or name of the subCMD
    private final boolean rendersPath; //everything but the root's own method is reached through a path, and shows it
    private final boolean playerOnly;
    private final Map<Integer, ArgParser> arguments = new LinkedHashMap<>(); // Args with @Arg annotation
    //Args without any annotation or with @Arg.Contextual, split by the phase they resolve in so a
    //dispatch iterates the group of the turn whole instead of filtering one out of the other
    private final Map<Integer, ArgParserContextual> contextualsBeforeArguments = new LinkedHashMap<>();
    private final Map<Integer, ArgParserContextual> contextualsAfterArguments = new LinkedHashMap<>();
    private final Map<Integer, ITabParser> tabParsers = new LinkedHashMap<>();
    private final Map<Integer, FlagBinding> flagBindings = new LinkedHashMap<>(); // Args with @Arg.Flag annotation, keyed by parameter index
    private final Map<String, MultiArgumentos.FlagBinding> flagExtractionBindings = new LinkedHashMap<>(); //every declared name/alias (normalized) -> its extraction rule, fed to MultiArgumentos#extractDeclaredFlags
    private final Map<Integer, CapturedBinding> capturedArguments = new LinkedHashMap<>(); // Args with @Arg.NodeCaptured annotation, keyed by parameter index
    private int greedyTailIndex = -1; //local window index of the variadic tail, -1 when there is none

    /** Where the whole parse policy lives - swappable so a test can watch what it decided. */
    private ParseEngine engine = ParseEngine.DEFAULT;

    private transient HelpLineTemplate helpLineTemplate;

    void setEngine(@Nonnull ParseEngine engine) {
        this.engine = engine;
    }

    /**
     * @param ownerNode the node this method answers for, so every {@code @Arg.NodeCaptured} can be
     * bound to a concrete ancestor here, at registration, and so the help line knows its own path
     */
    public CMDMethodInterpreter(ECPluginData owningPlugin, MethodData<?> methodData, Object executor, @Nonnull CommandNode ownerNode) {
        this.owningPlugin = owningPlugin;
        this.method = methodData.getMethod();
        this.executor = executor;
        this.cmdData = methodData.getData();
        this.ownerNode = ownerNode;
        this.labels = cmdData.getLabels();
        //Only the root's own method sits at the label itself; a subcommand, a node executable and a
        //node capture are all reached through a path, and their usage line has to spell it out
        this.rendersPath = !(cmdData instanceof FinalCMDData);

        if (!method.isAccessible()){
            method.setAccessible(true);
        }

        boolean playerOnly = false;

        if (!cmdData.getUsage().isEmpty() && (!methodData.getArgDataMap().isEmpty() || !methodData.getFlagArgDataMap().isEmpty())){
            throw new ArgMountException("The usage [" + cmdData.getUsage() + "] declared on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                    "is dead: the help line of a method that declares @Arg/@Arg.Flag is always built from those. Delete the usage(), or delete the annotated parameters.");
        }

        String whereMethod = "on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "]";
        refuseMixedAnnotationFamilies(methodData, whereMethod);
        refuseRepeatedDeclaredNames(methodData, whereMethod);

        //Local to this method's own window: the path (labels and captured tokens) is sliced off before
        //the tokens ever reach here, so the first positional of ANY executable sits at 0
        int flagArgIndex = 0;
        for (Map.Entry<Integer, Tuple<ArgData, Class>> entry : methodData.getArgDataMap().entrySet()) {
            Integer index = entry.getKey();
            ArgData argData = entry.getValue().getLeft();
            Class parameterClazz = entry.getValue().getRight();

            String where = "on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "]";

            ArgRequirementType argRequirementType = ArgRequirementType.getArgumentType(argData.getName());
            if (argRequirementType == null){
                String possibleReqTypes = Arrays.stream(ArgRequirementType.values())
                        .map(reqType -> reqType.getStart() + "" + reqType.getEnd())
                        .collect(Collectors.joining(" or "));

                throw new ArgMountException ("Failed to load ArgRequirementType from ArgData [" + argData.getName() + "], usually this means the " +
                        "ArgData.name() is not Quoted within \'" + possibleReqTypes + "\'");
            }

            boolean greedy = ArgRequirementType.stripBrackets(argData.getName()).endsWith(GREEDY_SUFFIX);
            if (greedy){
                if (greedyTailIndex >= 0){
                    throw new ArgMountException("The @Arg [" + argData.getName() + "] " + where + " is a second variadic tail. " +
                            "A method takes at most one - only the LAST @Arg may end with '" + GREEDY_SUFFIX + "'.");
                }
                if (!GREEDY_TYPES.contains(parameterClazz)){
                    throw new ArgMountException("The @Arg [" + argData.getName() + "] " + where + " is variadic but its type is [" + parameterClazz.getSimpleName() + "]. " +
                            "A variadic tail is one of String, String[] (or String...), Argumento, MultiArgumentos, List<String> or Set<String>.");
                }
                refuseNonStringElement(index, argData, parameterClazz, where);
                if (argData.isFromSender()){
                    throw new ArgMountException("The @Arg [" + argData.getName() + "] " + where + " is variadic AND declares fromSender. " +
                            "A tail that takes every token left has nothing to infer - drop one of the two.");
                }
                greedyTailIndex = flagArgIndex;
            }else if (greedyTailIndex >= 0){
                throw new ArgMountException("The @Arg [" + argData.getName() + "] " + where + " comes after a variadic tail. " +
                        "The tail takes every token left, so nothing can follow it - move it to the end of the parameter list.");
            }

            //Mirrors the refusal every @Arg.Flag already gets: an argument that may resolve to nothing
            //hands the method a null, and a primitive has nowhere to put one
            if (parameterClazz.isPrimitive() && (argRequirementType != ArgRequirementType.REQUIRED || argData.isFromSender())){
                throw new ArgMountException("The @Arg [" + argData.getName() + "] " + where + " is a primitive (" + parameterClazz.getName() + ") " +
                        "but can resolve to nothing - an [optional] argument nobody typed, or a fromSender that answers with no value, is null. " +
                        "Use the wrapper type (e.g. Integer, Boolean), or make the argument required.");
            }

            if (ArgParser.class == argData.getParser()){
                //This means the DEFAULT parser, so, we look over the ArgParserManager
                //A variadic tail is read straight off the window, so its parser only has to answer tab
                //and usage - which is String's job, whatever shape the tail is handed to the method in.
                Class<? extends ArgParser> parserClass = ArgParserManager.getParser(owningPlugin, greedy ? String.class : parameterClazz);
                if (parserClass == null){
                    throw new ArgMountException("No ArgParser answers for [" + parameterClazz.getSimpleName() + "] " + where + " parameter [index=" + index + ", name=" + argData.getName() + "]. " +
                            "Name one with @Arg(parser = ...), or register it once with ArgParserManager.");
                }
                argData.setParser(parserClass);
            }

            if (!argData.getDef().isEmpty() && argRequirementType != ArgRequirementType.OPTIONAL){
                throw new ArgMountException("The @Arg [" + argData.getName() + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                        "declares def() but is not [optional]; def() is only legal on [optional] arguments.");
            }

            //An argument the sender answers for never runs out of value, so its def() is text nobody
            //could ever reach - the same reasoning that already refuses fromSender on a tail
            if (!argData.getDef().isEmpty() && argData.isFromSender()){
                throw new ArgMountException("The @Arg [" + argData.getName() + "] " + where + " declares both fromSender and def(). " +
                        "An argument answered from the sender is never absent, so the def() would never be read - drop one of the two.");
            }

            ArgInfo argInfo = ArgInfo.positional(parameterClazz, argData, flagArgIndex, argRequirementType, greedy);
            String whereParameter = "on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] parameter [index=" + index + ", name=" + argData.getName() + "]";
            ArgParser parserInstance;
            try {
                Constructor<? extends AbstractArgParser> constructor = argData.getParser().getDeclaredConstructor(ArgInfo.class);
                constructor.setAccessible(true);
                parserInstance = (ArgParser) constructor.newInstance(argInfo);
            }catch (Exception e){
                throw instantiationFailure(e, "ArgParser", whereParameter);
            }

            if (argData.isFromSender() && !resolvesFromSender(parserInstance.getClass())){
                throw new ArgMountException("The @Arg [" + argData.getName() + "] " + where + " declares fromSender, but its parser " +
                        "[" + argData.getParser().getSimpleName() + "] cannot answer without a token - it would silently read the NEXT argument instead. " +
                        "Override fromSender(ParseCall) on the parser, or drop fromSender.");
            }

            // Load the ArgParser's own @FCLocale static fields
            ECPluginData parserPlugin = ECPluginManager.getProvidingPlugin(argData.getParser());
            FCLocaleManager.loadLocale(parserPlugin, true, argData.getParser());

            arguments.put(index, parserInstance); //Index of the methodOrder eco_give(Player, arg1, PlayerData, arg3, etc...)
            tabParsers.put(flagArgIndex, parserInstance); //Index of the final TabParser (/eco give arg1 arg2)
            flagArgIndex++;
        }

        Set<String> claimedFlagSpellings = new HashSet<>(); //normalized (dashes stripped, lowercase) name/alias, unique across every @Arg.Flag on this method

        for (Map.Entry<Integer, Tuple<ArgData, Class>> entry : methodData.getFlagArgDataMap().entrySet()) {
            Integer index = entry.getKey();
            ArgData argData = entry.getValue().getLeft();
            Class parameterClazz = entry.getValue().getRight();

            if (parameterClazz.isPrimitive()){
                throw new ArgMountException("The @Arg.Flag [" + argData.getName() + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                        "is a primitive (" + parameterClazz.getName() + "); flags are never primitive - use the wrapper type (e.g. Boolean, Integer) so an absent flag can be null.");
            }

            String rawName = argData.getName();
            if (!rawName.startsWith("--") || rawName.contains(" ") || rawName.substring(2).trim().isEmpty()){
                throw new ArgMountException("The @Arg.Flag name [" + rawName + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                        "must be in the long form '--name', with no spaces and a non-empty name after the dashes.");
            }
            String canonicalName = rawName.substring(2).toLowerCase();

            List<String> spellings = new ArrayList<>();
            spellings.add(rawName);
            for (String alias : argData.getAliases()) {
                //The line is tokenized before anything looks at the declarations, so an alias that the
                //marker rule never recognizes is a binding nobody can ever reach
                if (containsWhitespace(alias) || !MultiArgumentos.isFlagMarker(alias)){
                    throw new ArgMountException("The @Arg.Flag alias [" + alias + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                            "parameter [" + rawName + "] is not a token that can ever be typed as a flag. " +
                            "An alias is one or more '-' followed by a name that does not start with a digit and holds no whitespace - " +
                            "dashes alone name nothing, and a leading digit is a negative number.");
                }
                spellings.add(alias);
            }

            String usageName = argData.getUsageName();
            if (!usageName.isEmpty() && !spellings.contains(usageName)){
                throw new ArgMountException("The @Arg.Flag [" + rawName + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                        "declares usageName [" + usageName + "], which is neither its name nor one of its aliases. " +
                        "The usage line can only show a spelling the sender may actually type - use one of " + String.join(", ", spellings) + ".");
            }

            for (String spelling : spellings) {
                String normalizedSpelling = spelling.replaceFirst("^-+", "").toLowerCase();
                if (!claimedFlagSpellings.add(normalizedSpelling)){
                    throw new ArgMountException("The @Arg.Flag spelling [" + spelling + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                            "is already claimed by a name or alias declared earlier on the same method.");
                }
                flagExtractionBindings.put(normalizedSpelling, new MultiArgumentos.FlagBinding(canonicalName, parameterClazz == Boolean.class ? 0 : 1));
            }

            if (ArgParser.class == argData.getParser()){
                //This means the DEFAULT parser, so, we look over the ArgParserManager
                Class<? extends ArgParser> parserClass = ArgParserManager.getParser(owningPlugin, parameterClazz);
                if (parserClass == null){
                    throw new ArgMountException("No ArgParser answers for [" + parameterClazz.getSimpleName() + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                            "flag [index=" + index + ", name=" + rawName + "]. Name one with @Arg.Flag(parser = ...), or register it once with ArgParserManager.");
                }
                argData.setParser(parserClass);
            }

            ArgInfo flagArgInfo = ArgInfo.flag(parameterClazz, argData);
            String whereFlag = "on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] flag [index=" + index + ", name=" + rawName + "]";
            ArgParser flagParserInstance;
            try {
                Constructor<? extends AbstractArgParser> constructor = argData.getParser().getDeclaredConstructor(ArgInfo.class);
                constructor.setAccessible(true);
                flagParserInstance = (ArgParser) constructor.newInstance(flagArgInfo);
            }catch (Exception e){
                throw instantiationFailure(e, "ArgParser", whereFlag);
            }

            // Load the ArgParser's own @FCLocale static fields
            ECPluginData flagParserPlugin = ECPluginManager.getProvidingPlugin(argData.getParser());
            FCLocaleManager.loadLocale(flagParserPlugin, true, argData.getParser());

            flagBindings.put(index, new FlagBinding(index, canonicalName, rawName, parameterClazz == Boolean.class, argData, flagParserInstance));
        }

        for (Map.Entry<Integer, Tuple<ArgData, Class>> entry : methodData.getContextualArgDataMap().entrySet()) {
            Integer index = entry.getKey();
            ArgData argContextualData = entry.getValue().getLeft();
            Class parameterClazz = entry.getValue().getRight();

            if (ArgParserContextual.class == argContextualData.getParser()){
                //This means the DEFAULT parser, so, we look over the ArgParserManager
                Class<? extends ArgParserContextual> contextualParserClass = ArgParserManager.getContextualParser(owningPlugin, parameterClazz);
                if (contextualParserClass == null){
                    throw new ArgMountException("No ArgParserContextual answers for [" + parameterClazz.getSimpleName() + "] on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                            "parameter [index=" + index + "]. A parameter with no @Arg is read off the invocation, so its type needs a contextual parser - " +
                            "name one with @Arg.Contextual(parser = ...), register it once with ArgParserManager, or annotate the parameter as an @Arg if it was meant to be a token.");
                }
                argContextualData.setParser(contextualParserClass);
            }

            ArgInfo argContextualInfo = ArgInfo.contextual(parameterClazz, argContextualData);
            String whereContextual = "on the FinalCMD (" + executor.getClass().getName() + ")[" + method.getName() + "] " +
                    "parameter [index=" + index + ", class=" + parameterClazz.getSimpleName() + "]";
            ArgParserContextual parserInstance;
            try {
                Constructor<? extends AbstractArgParser> constructor = argContextualData.getParser().getDeclaredConstructor(ArgInfo.class);
                constructor.setAccessible(true);
                parserInstance = (ArgParserContextual) constructor.newInstance(argContextualInfo);

                if (parserInstance.requiresToBeAPlayer()){
                    playerOnly = true;
                }
            }catch (Exception e){
                throw instantiationFailure(e, "ArgParserContextual", whereContextual);
            }

            // Load the ArgParserContextual's own @FCLocale static fields
            ECPluginData contextualParserPlugin = ECPluginManager.getProvidingPlugin(argContextualData.getParser());
            FCLocaleManager.loadLocale(contextualParserPlugin, true, argContextualData.getParser());

            //Index of the methodOrder eco_give(Player, arg1, PlayerData, arg3, etc...)
            groupOf(effectivePhase(argContextualData, parserInstance, whereContextual)).put(index, parserInstance);
        }

        for (Map.Entry<Integer, Tuple<Arg.NodeCaptured, Class>> entry : methodData.getCapturedArgDataMap().entrySet()) {
            Integer index = entry.getKey();
            Arg.NodeCaptured captured = entry.getValue().getLeft();
            Class parameterClazz = entry.getValue().getRight();

            capturedArguments.put(index, resolveCaptured(ownerNode, index, captured.value().trim(), parameterClazz));
        }

        this.playerOnly = playerOnly;

        //A @FinalCMD.Capture is exempt: it is never reached by a sender directly, it only turns the
        //node's own tokens into the node's context, and a capture that needs the sender just asks for it
        if (contextualArgCount() == 0 && capturedArguments.size() == 0 && !(cmdData instanceof CaptureData)) {
            throw new ArgMountException("The FinalCMD method " + whereMethod + " reads nothing off the invocation. " +
                    "Take at least one contextual parameter - FCommandSender, FPlayer, or any type a contextual parser is registered for - " +
                    "or an @Arg.NodeCaptured, so the method knows who is running it.");
        }

        this.helpLineTemplate = buildHelpLineTemplate();
    }

    /**
     * What a parser blowing up while being built means. A parser that refuses the declaration it was
     * handed - a {@code context()} its type cannot read - is stating a shape error like any other, so it
     * has to arrive as one instead of being buried inside the reflection failure that carried it. Only
     * this side knows WHICH parameter it was about, so the location is added here.
     * <p>
     * Whatever the refusal was written as, its own sentence travels: a parser that says
     * "[GOLDEN] is not a constant of Material" through a plain {@code IllegalArgumentException} has
     * written the only line that helps, and answering "failed to instantiate" over it threw the
     * diagnosis away and left the developer with the location alone.
     */
    private static RuntimeException instantiationFailure(Exception failure, String parserFamily, String where) {
        Throwable cause = failure instanceof InvocationTargetException && failure.getCause() != null ? failure.getCause() : failure;

        if (cause instanceof ArgMountException){
            return new ArgMountException(cause.getMessage() + " (" + where + ")");
        }

        String explanation = cause.getMessage() == null || cause.getMessage().trim().isEmpty()
                ? cause.getClass().getSimpleName()
                : cause.getClass().getSimpleName() + ": " + cause.getMessage();
        return new ArgMountException("Failed to instantiate the " + parserFamily + " " + where + " - " + explanation, cause);
    }

    /**
     * A parameter has exactly ONE source. The four families - {@code @Arg}, {@code @Arg.Flag},
     * {@code @Arg.Contextual} and {@code @Arg.NodeCaptured} - each fill the same slot from a different
     * place, so two of them on one parameter are not a richer declaration but two answers to one
     * question: the invocation would size its argument array by adding the families up, hand the method
     * more arguments than it has parameters, and die on every single dispatch.
     */
    private static void refuseMixedAnnotationFamilies(MethodData<?> methodData, String where) {
        Map<Integer, List<String>> declaredFamilies = new TreeMap<>();
        collectFamily(declaredFamilies, methodData.getArgDataMap().keySet(), "@Arg");
        collectFamily(declaredFamilies, methodData.getFlagArgDataMap().keySet(), "@Arg.Flag");
        collectFamily(declaredFamilies, methodData.getCapturedArgDataMap().keySet(), "@Arg.NodeCaptured");
        //An unannotated parameter is filed as contextual too, but it carries no annotation to clash with
        collectFamily(declaredFamilies, methodData.getContextualArgDataMap().keySet(), "@Arg.Contextual");

        for (Map.Entry<Integer, List<String>> entry : declaredFamilies.entrySet()) {
            List<String> families = entry.getValue();
            if (families.size() > 1){
                throw new ArgMountException("The parameter [index=" + entry.getKey() + "] " + where + " declares " +
                        String.join(" and ", families) + ". A parameter takes exactly one of @Arg, @Arg.Flag, " +
                        "@Arg.Contextual and @Arg.NodeCaptured - each names a different place the value comes from, " +
                        "so keep the one you meant and delete the other.");
            }
        }
    }

    private static void collectFamily(Map<Integer, List<String>> declaredFamilies, Set<Integer> indexes, String family) {
        for (Integer index : indexes) {
            List<String> families = declaredFamilies.get(index);
            if (families == null){
                families = new ArrayList<>();
                declaredFamilies.put(index, families);
            }
            families.add(family);
        }
    }

    /**
     * A {@code List}/{@code Set} tail is handed the raw tokens, so anything but {@code String} inside it
     * is a conversion nobody performs: the method would be handed strings under a type that promises
     * something else, and the {@code ClassCastException} would surface at the first read, far from the
     * declaration. Erasure hides that from the type system, so it is checked here, at registration.
     */
    private void refuseNonStringElement(int paramIndex, ArgData argData, Class<?> parameterClazz, String where) {
        if (parameterClazz != List.class && parameterClazz != Set.class){
            return;
        }

        Type declared = method.getGenericParameterTypes()[paramIndex];
        Type element = declared instanceof ParameterizedType
                ? ((ParameterizedType) declared).getActualTypeArguments()[0]
                : null;

        if (element != String.class){
            throw new ArgMountException("The @Arg [" + argData.getName() + "] " + where + " is a variadic tail of " +
                    parameterClazz.getSimpleName() + "<" + (element == null ? "?" : element.getTypeName()) + ">. " +
                    "A tail hands over the tokens exactly as typed, so the only element type it can have is String - " +
                    "declare it as " + parameterClazz.getSimpleName() + "<String>.");
        }
    }

    /** Whether {@code text} holds any whitespace at all - a flag spelling never survives tokenization with one. */
    private static boolean containsWhitespace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))){
                return true;
            }
        }
        return false;
    }

    /**
     * A declared name is how a value is addressed after it is resolved, so two parameters of the same
     * method cannot answer to one. The three families share the namespace and are compared exactly as
     * written, which is why {@code --amount} and {@code <amount>} never collide - nobody would ever type
     * one meaning the other.
     */
    private static void refuseRepeatedDeclaredNames(MethodData<?> methodData, String where) {
        Map<String, Integer> claimedBy = new HashMap<>();

        List<Map<Integer, Tuple<ArgData, Class>>> families = Arrays.asList(
                methodData.getArgDataMap(), methodData.getFlagArgDataMap(), methodData.getContextualArgDataMap());

        for (Map<Integer, Tuple<ArgData, Class>> family : families) {
            for (Map.Entry<Integer, Tuple<ArgData, Class>> entry : family.entrySet()) {
                String declaredName = entry.getValue().getLeft().getName();
                if (declaredName.isEmpty()){
                    continue; //an unannotated parameter declares no name, so it claims nothing
                }

                Integer claimant = claimedBy.get(declaredName);
                if (claimant == null){
                    claimedBy.put(declaredName, entry.getKey());
                    continue;
                }
                //The same parameter appearing in two families is a different shape error, already
                //refused above with a message that says so
                if (claimant.equals(entry.getKey())){
                    continue;
                }

                int first = Math.min(claimant, entry.getKey());
                int second = Math.max(claimant, entry.getKey());
                throw new ArgMountException("The name [" + declaredName + "] " + where + " is declared twice, " +
                        "on parameters [index=" + first + "] and [index=" + second + "]. " +
                        "A name is how a value is addressed once it is resolved - rename them apart.");
            }
        }
    }

    /**
     * When a contextual parameter resolves: what the parameter itself declared, or, when it left the
     * choice open, what its parser answers. A parser that hands the choice back is refused here instead
     * of being silently placed somewhere - {@code PARSER_DEFAULT} is the question, not an answer to it.
     */
    private static ResolutionPhase effectivePhase(ArgData argData, ArgParserContextual parser, String where) {
        if (argData.getPhase() != ResolutionPhase.PARSER_DEFAULT){
            return argData.getPhase();
        }

        ResolutionPhase parserPhase = parser.defaultPhase();
        if (parserPhase == null || parserPhase == ResolutionPhase.PARSER_DEFAULT){
            throw new ArgMountException("The parser [" + parser.getClass().getSimpleName() + "] " + where + " " +
                    "answered " + parserPhase + " to defaultPhase(), which is what an annotation says to ask the parser. " +
                    "Return " + ResolutionPhase.BEFORE_ARGUMENTS + " or " + ResolutionPhase.AFTER_ARGUMENTS + ".");
        }
        return parserPhase;
    }

    private Map<Integer, ArgParserContextual> groupOf(ResolutionPhase phase) {
        return phase == ResolutionPhase.AFTER_ARGUMENTS ? contextualsAfterArguments : contextualsBeforeArguments;
    }

    /** How many parameters this method reads off the invocation, in both phases together. */
    private int contextualArgCount() {
        return contextualsBeforeArguments.size() + contextualsAfterArguments.size();
    }

    /**
     * Whether {@code parserClass} answers without a token at all - that is, whether anything below
     * {@link ArgParser} overrides {@link ArgParser#fromSender}. The base implementation refuses, so an
     * {@code @Arg(fromSender = true)} pointed at a parser that never overrode it is a shape error, not
     * something a player discovers by watching their argument eat the next token.
     */
    private static boolean resolvesFromSender(Class<?> parserClass) {
        try {
            return parserClass.getMethod("fromSender", ParseCall.class).getDeclaringClass() != ArgParser.class;
        }catch (NoSuchMethodException impossible){
            return false;
        }
    }

    /**
     * Binds one {@code @Arg.NodeCaptured} parameter to the ancestor node that feeds it - either to the
     * whole context object that capture returned, or, when the value is written {@code "path:<arg>"}, to
     * the single token of that capture. The only genuinely ambiguous case - an unnamed parameter that
     * more than one ancestor capture fits - is refused here, with the node paths to choose from, instead
     * of silently picking one at dispatch.
     */
    private CapturedBinding resolveCaptured(@Nullable CommandNode ownerNode, int paramIndex, String wanted, Class<?> parameterClazz) {
        String where = "(" + executor.getClass().getName() + ")[" + method.getName() + "] parameter [index=" + paramIndex + ", type=" + parameterClazz.getSimpleName() + "]";

        int separator = wanted.indexOf(CapturedBinding.ARG_SEPARATOR);
        String wantedNodePath = (separator < 0 ? wanted : wanted.substring(0, separator)).trim();
        String wantedArgName = separator < 0 ? null : wanted.substring(separator + CapturedBinding.ARG_SEPARATOR.length()).trim();

        List<CommandNode> capturingAncestry = ownerNode == null ? Collections.<CommandNode>emptyList() : ownerNode.getCapturingAncestry();
        List<String> offered = new ArrayList<>();
        List<CommandNode> compatible = new ArrayList<>();
        for (CommandNode ancestor : capturingAncestry) {
            offered.add(describeOffer(ancestor));
            Class<?> contextType = ancestor.getCapture().getContextType();
            //A flags-only capture produces no value to hand out, but its @Arg tokens are still reachable
            if (contextType != null && parameterClazz.isAssignableFrom(contextType)){
                compatible.add(ancestor);
            }
        }
        String offeredList = offered.isEmpty() ? "<none - no ancestor of this method captures anything>" : String.join(", ", offered);

        if (wantedArgName != null){
            return resolveCapturedArg(capturingAncestry, paramIndex, wanted, wantedNodePath, wantedArgName, parameterClazz, where, offeredList);
        }

        if (!wantedNodePath.isEmpty()){
            for (CommandNode ancestor : compatible) {
                if (ancestor.getNodePath().equals(wantedNodePath)){
                    return new CapturedBinding(paramIndex, wantedNodePath, null, parameterClazz);
                }
            }
            //The path may exist and simply hand out something else. Saying "no such capture" would send
            //the dev hunting for a node that was there all along, when the type is what has to change.
            for (CommandNode ancestor : capturingAncestry) {
                if (ancestor.getNodePath().equals(wantedNodePath)){
                    Class<?> contextType = ancestor.getCapture().getContextType();
                    throw new ArgMountException("The @Arg.NodeCaptured(\"" + wantedNodePath + "\") on the FinalCMD " + where + " " +
                            "names a capture that hands out " + (contextType == null ? "nothing: it returns void" : "[" + contextType.getSimpleName() + "]") +
                            ", which is not assignable to this parameter. " + (contextType == null
                            ? "Read one of its tokens instead, with \"" + wantedNodePath + CapturedBinding.ARG_SEPARATOR + "<arg>\"."
                            : "Declare the parameter as " + contextType.getSimpleName() + "."));
                }
            }
            throw new ArgMountException("The @Arg.NodeCaptured(\"" + wantedNodePath + "\") on the FinalCMD " + where + " " +
                    "names no capture this path offers. Use one of " + offeredList + ".");
        }

        if (compatible.isEmpty()){
            throw new ArgMountException("The @Arg.NodeCaptured on the FinalCMD " + where + " " +
                    "matches no capture of this path. Use one of " + offeredList + ", " +
                    "or drop the annotation if the parameter is contextual.");
        }

        if (compatible.size() > 1){
            List<String> names = new ArrayList<>();
            for (CommandNode ancestor : compatible) {
                names.add("@Arg.NodeCaptured(\"" + ancestor.getNodePath() + "\")");
            }
            throw new ArgMountException("The @Arg.NodeCaptured on the FinalCMD " + where + " " +
                    "is ambiguous: " + compatible.size() + " captures of this path fit it. Name the one you want - " + String.join(" or ", names) + ".");
        }

        return new CapturedBinding(paramIndex, compatible.get(0).getNodePath(), null, parameterClazz);
    }

    /**
     * Binds the {@code "path:<arg>"} form. The argument name of a capture is a public contract of every
     * leaf below it, so renaming one has to break here, at boot, with the names that do exist - never at
     * the far leaf that quietly stops receiving its token.
     */
    private CapturedBinding resolveCapturedArg(List<CommandNode> capturingAncestry, int paramIndex, String wanted, String wantedNodePath,
                                               String wantedArgName, Class<?> parameterClazz, String where, String offeredList) {
        String annotation = "The @Arg.NodeCaptured(\"" + wanted + "\") on the FinalCMD " + where + " ";

        if (wantedNodePath.isEmpty()){
            throw new ArgMountException(annotation + "names an argument without a node. " +
                    "Write the ancestor's node path before the '" + CapturedBinding.ARG_SEPARATOR + "' - one of " + offeredList + ".");
        }

        CommandNode owner = null;
        for (CommandNode ancestor : capturingAncestry) {
            if (ancestor.getNodePath().equals(wantedNodePath)){
                owner = ancestor;
                break;
            }
        }
        if (owner == null){
            throw new ArgMountException(annotation + "names no capture this path offers. Use one of " + offeredList + ".");
        }

        List<ArgParser> named = new ArrayList<>();
        for (ArgParser parser : owner.getCapture().getArgParsers()) {
            if (parser.getArgInfo().getArgData().getName().equals(wantedArgName)){
                named.add(parser);
            }
        }
        String declaredArgs = owner.getCapture().getArgNames().isEmpty()
                ? "<none - that capture declares no @Arg>"
                : String.join(", ", owner.getCapture().getArgNames());

        if (named.isEmpty()){
            throw new ArgMountException(annotation + "names no @Arg of the capture at \"" + wantedNodePath + "\". " +
                    "Use one of " + declaredArgs + " (spelled exactly as declared), or drop the '" + CapturedBinding.ARG_SEPARATOR + "' to take what the capture returns.");
        }
        if (named.size() > 1){
            throw new ArgMountException(annotation + "is ambiguous: the capture at \"" + wantedNodePath + "\" declares " + named.size() + " arguments named [" + wantedArgName + "]. " +
                    "Rename them apart - an argument name is how a leaf addresses the token.");
        }

        Class<?> argType = named.get(0).getArgInfo().getArgumentType();
        if (!parameterClazz.isAssignableFrom(argType)){
            throw new ArgMountException(annotation + "reads [" + wantedArgName + "], which the capture at \"" + wantedNodePath + "\" declares as " +
                    "[" + argType.getSimpleName() + "] - not assignable to this parameter. Declare the parameter as " + argType.getSimpleName() + ".");
        }

        return new CapturedBinding(paramIndex, wantedNodePath, wantedArgName, parameterClazz);
    }

    /** One entry of "what this path offers": the node, what its capture returns, and the tokens it eats. */
    private static String describeOffer(CommandNode ancestor) {
        Class<?> contextType = ancestor.getCapture().getContextType();
        List<String> argNames = ancestor.getCapture().getArgNames();
        return "\"" + ancestor.getNodePath() + "\" (" + (contextType == null ? "void" : contextType.getSimpleName()) + ")"
                + (argNames.isEmpty() ? "" : " with [" + String.join(", ", argNames) + "]");
    }

    public CMDData<?> getCmdData() {
        return cmdData;
    }

    public HelpLineTemplate getHelpLineTemplate() {
        return helpLineTemplate;
    }

    private HelpLineTemplate buildHelpLineTemplate(){
        //A @SubCMD is a method OF the node above it - the leaf node is only where it is mounted - while
        //a node's own @Execute/@Capture belongs to the node itself. Either way the key names whoever
        //holds the method, then the method: two same-named inner classes no longer share an entry.
        CommandNode keyNode = cmdData instanceof SubCMDData ? ownerNode.getParent() : ownerNode;
        String localeMessageKey = keyNode.getLocaleKeyPrefix() + "." + method.getName().toUpperCase(Locale.ROOT);
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

        HashMap<FlagBinding, LocaleMessageImp> flagBindingToLocale = new HashMap<>(); //This will hold every single @Arg.Flag locale message
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
            // Or we have a declared usage over here, like a full text like '<give|take> <Player>'
            // or we have annotated @Args, in this case, we have a priority on the construction of the usage using these args

            Consumer<FancyText> applyDefaultFormatting = fancyTextOrFormatter -> {
                fancyTextOrFormatter.setHover(description);
                fancyTextOrFormatter.setClickSuggest("/${label} ${path}");
            };

            FancyFormatter fancyFormatter = FancyFormatter.of("§3§l ▶ §a/§e${label}" + (rendersPath ? " ${path}" : ""));
            applyDefaultFormatting.accept(fancyFormatter);

            AtomicBoolean anyLocalizedArg = new AtomicBoolean(false);
            if (arguments.size() == 0){
                //A usage() is only legal on a method with no @Arg/@Arg.Flag at all, so it is the whole
                //argument part of the line and it is rendered as written - the framework already put
                //"/${label} ${path}" in front of it, and nothing is stripped out of what the dev typed.
                fancyFormatter.append(" " + cmdData.getUsage().trim());
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
                                extraDescription = argFancyText.getHoverText() != null && !argFancyText.getHoverText().isEmpty() ? argFancyText.getHoverText() : argFancyText.getText();
                            }

                            fancyFormatter.append(" " + argParser.getArgInfo().getArgData().getName());
                            applyDefaultFormatting.accept(fancyFormatter);
                            if (extraDescription != null){
                                anyLocalizedArg.set(true);
                                fancyFormatter.setHover((description != null ? description : "") +
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

                fancyFormatter.append(" [" + binding.getUsageSpelling() + "]");
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
                    extraDescription = flagFancyText.getHoverText() != null && !flagFancyText.getHoverText().isEmpty() ? flagFancyText.getHoverText() : flagFancyText.getText();
                }

                anyLocalizedFlag.set(true);
                String flagBlock = (description != null ? description : "") +
                        "\n" +
                        "\n §d ✯ §7§l[§e" + flagTitle + "§7§l]§r";
                if (extraDescription != null){
                    flagBlock += "\n §7● §6" + extraDescription;
                }
                fancyFormatter.setHover(flagBlock);
            }

            if (anyLocalizedArg.get() || anyLocalizedFlag.get()){
                localeMessage.getFancyTextMap().put(locale, fancyFormatter);
            }else {
                fancyText.setText(fancyFormatter.getFancyTextList().stream().map(FancyText::getText).collect(Collectors.joining()));
                applyDefaultFormatting.accept(fancyText);
            }
        }

        return new HelpLineTemplate(localeMessage, cmdData.getPermission());
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

    /** Every {@code @Arg.NodeCaptured} parameter of this method, bound to its ancestor at registration. */
    public Map<Integer, CapturedBinding> getCapturedArguments() {
        return capturedArguments;
    }

    /** How many tokens of the line this method consumes positionally - zero for a node executable. */
    public int getPositionalArgCount() {
        return arguments.size();
    }

    public String[] getLabels(){
        return labels;
    }

    public boolean hasTabComplete(){
        return tabParsers.size() > 0;
    }

    /**
     * The parser that answers tab at a given local index. A variadic tail is STICKY: it answers for
     * every index from its own on, because there is no position after it - without that, tab would
     * simply die at the end of the line.
     */
    public ITabParser getTabParser(int index) {
        ITabParser tabParser = tabParsers.get(index);
        if (tabParser == null && greedyTailIndex >= 0 && index > greedyTailIndex){
            return tabParsers.get(greedyTailIndex);
        }
        return tabParser;
    }

    /** Whether this method declares any {@code @Arg.Flag} parameter - gates the flag-aware tab-complete branch. */
    public boolean hasFlags() {
        return !flagBindings.isEmpty();
    }

    /** Every declared {@code @Arg.Flag} binding, in declaration order - read by help rendering and tab-complete. */
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
     * The local window index at which this method's variadic tail begins, or -1 when it has none.
     * <p>
     * It is a POSITIONAL index, counted over the tokens the flags left behind - which is exactly what
     * the flag scan needs to know where to stop, because a tail is a stretch of line somebody reads as
     * typed and a marker inside it is the sender's own text.
     */
    public int getGreedyTailIndex() {
        return greedyTailIndex;
    }

    /**
     * Every declared spelling (name/alias, normalized) mapped to its fixed extraction rule - the same
     * map fed to {@link MultiArgumentos#extractDeclaredFlags}, exposed so tab-complete can run its
     * own read-only scan over {@code args[0..index-1]} through the identical {@code scanFlagMarkers}
     * core, instead of re-deriving the tokenizer rules.
     */
    public Map<String, MultiArgumentos.FlagBinding> getFlagExtractionBindings() {
        return flagExtractionBindings;
    }

    /**
     * Parses this method's parameters and calls it.
     * <p>
     * The order is part of the contract, because each step can read what the previous ones resolved:
     * <b>captures -&gt; contextuals BEFORE_ARGUMENTS -&gt; flags -&gt; positionals -&gt; contextuals
     * AFTER_ARGUMENTS</b>. Captures come first because they were already resolved while the path was
     * being walked - nothing of this method's own is involved in producing them, so making anything
     * wait for them would only hide them from whoever runs first.
     *
     * @param window the tokens of the line that belong to THIS method - the path is already gone from
     * it, so {@code get(0)} is its own first argument
     * @return what the method returned, or {@link Invocation#isAborted() aborted} when a parser,
     * a permission or a missing required argument stopped it (the sender was already told why)
     */
    public Invocation invoke(FCommandSender sender, DispatchContext dispatch, MultiArgumentos window, HelpContext helpContext, HelpLine helpLine) throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {

        Object[] theArgs = new Object[contextualArgCount() + arguments.size() + flagBindings.size() + capturedArguments.size()];
        //One bag for the whole invocation: a flag, a capture, a token and a contextual parameter all
        //land in it, and both kinds of call read it through the same two lookups
        ResolvedArguments resolved = new ResolvedArguments();
        LinkedHashMap<String, Object> parsedByArgName = new LinkedHashMap<>();

        //One call shape for every contextual parameter of this invocation, whichever phase it runs in
        Function<ArgInfo, ContextualParseCall> contextualCall = info ->
                new ContextualParseCall(sender, info, dispatch, resolved, window, helpContext, helpLine);

        //An ancestor's capture was resolved by the walk, before this method existed as far as the
        //dispatch is concerned, so it goes into the bag first and every step below can read it
        for (Map.Entry<Integer, CapturedBinding> entry : capturedArguments.entrySet()) {
            CapturedBinding binding = entry.getValue();
            Object capturedValue = binding.getArgName() == null
                    ? dispatch.captured(binding.getNodePath(), binding.getType())
                    : dispatch.capturedArg(binding.getNodePath(), binding.getArgName(), binding.getType());
            theArgs[entry.getKey()] = capturedValue;
            //A @Arg.NodeCaptured declares no name of its own - it is addressed by the ancestor's node
            //path, which is what captured() already answers
            resolved.resolved(null, capturedValue);
        }

        //What is read off the invocation comes next, so the parser of a token can ask for it. A parser
        //whose answer depends on a token instead declares AFTER_ARGUMENTS and runs at the very end.
        if (!resolveContextuals(contextualsBeforeArguments, theArgs, contextualCall, resolved, sender, helpLine)){
            return Invocation.ABORTED;
        }

        //Flags were pulled out of the tail once, for the whole path, before any capture ran - so a
        //node's flag and a leaf's flag are read from the same place no matter who declared them.
        MultiArgumentos flagSource = dispatch.getFlagSource();

        for (FlagBinding binding : flagBindings.values()) {
            FlagedArgumento matchedFlag = flagSource.getFlag(binding.canonicalName);

            Object parsedValue;
            if (matchedFlag.isSet()){
                String permission = binding.argData.getPermission();
                if (!permission.isEmpty() && !FCMessageUtil.hasThePermission(sender, permission)){
                    return Invocation.ABORTED;
                }

                if (binding.booleanFlag){
                    //A Boolean flag never reaches its parser when present: arity 0 means it never
                    //consumed a value token, so there is nothing meaningful to parse - presence IS true.
                    parsedValue = Boolean.TRUE;
                }else {
                    //A plain Argumento, not the FlagedArgumento itself: FlagedArgumento overrides
                    //toString() for display ("--name value"), and several builtin ArgParsers call
                    //argumento.toString() expecting just the raw value.
                    ParseOutcome<?> outcome = engine.run(binding.parser,
                            flagCall(binding, sender, dispatch, resolved, new Argumento(matchedFlag.getFlagValue())));
                    if (!isUsable(outcome, sender, helpLine)){
                        return Invocation.ABORTED;
                    }
                    parsedValue = outcome.getValueOrNull();
                }
            }else if (!binding.argData.getDef().isEmpty()){
                //An absent flag is indistinguishable from one typed with an empty value once it is a
                //token, so the caller states it instead: the def() runs as a def, and a default the
                //parser cannot read comes back as the command's bug rather than as the sender's
                ParseOutcome<?> outcome = engine.runDefault(binding.parser,
                        flagCall(binding, sender, dispatch, resolved, Argumento.EMPTY_ARG));
                if (!isUsable(outcome, sender, helpLine)){
                    return Invocation.ABORTED;
                }
                parsedValue = outcome.getValueOrNull();
            }else {
                parsedValue = null; //absent, no def(): a flag is never required, this is a normal outcome
            }

            theArgs[binding.paramIndex] = parsedValue;
            resolved.resolved(binding.rawName, parsedValue);
        }

        for (Map.Entry<Integer, ArgParser> entry : arguments.entrySet()) {
            Integer index = entry.getKey();
            ArgParser parser = entry.getValue();
            ArgInfo info = parser.getArgInfo();

            Object parsedArgument;
            if (info.isGreedy()){
                List<String> tail = remainingTokens(window, info.getIndex());
                if (tail.isEmpty() && info.isRequired()){
                    helpLine.sendTo(sender);
                    return Invocation.ABORTED;
                }

                if (tail.isEmpty() && !info.getArgData().getDef().isEmpty()){
                    //An empty tail is a tail nobody typed, which is exactly what a declared default is
                    //for - without this, def() would be dead on the one argument that is never "absent"
                    //in the token sense, because an empty tail always had a value of its own
                    tail = defaultTailTokens(info.getArgData().getDef());
                }
                parsedArgument = greedyValue(info.getArgumentType(), tail);
            }else {
                Argumento argumento = window.get(info.getIndex());
                ParseCall call = new ParseCall(sender, argumento, info, dispatch, resolved, false);

                ParseOutcome<?> outcome = engine.run(parser, call);

                if (!isUsable(outcome, sender, helpLine)){
                    return Invocation.ABORTED;
                }

                parsedArgument = outcome.getValueOrNull();
            }

            theArgs[index] = parsedArgument;
            resolved.resolved(info.getArgData().getName(), parsedArgument);
            if (parsedArgument != null){
                parsedByArgName.put(info.getArgData().getName(), parsedArgument);
            }
        }

        if (!resolveContextuals(contextualsAfterArguments, theArgs, contextualCall, resolved, sender, helpLine)){
            return Invocation.ABORTED;
        }

        try {
            return new Invocation(false, method.invoke(executor, theArgs), parsedByArgName);
        }catch (IllegalArgumentException e){
            //Registration refuses every shape that could produce this, so reaching it means the
            //framework itself handed the method something it cannot take: the two lists side by side
            //are the only way to see which slot disagrees
            owningPlugin.getLog().severe("The FinalCMD method [" + method.getName() + "] was handed arguments it cannot take."
                    + System.lineSeparator() + "  Expected: " + Arrays.toString(method.getParameterTypes())
                    + System.lineSeparator() + "  Received: " + Arrays.toString(Arrays.stream(theArgs).map(arg -> arg == null ? "null" : arg.getClass().getName()).toArray()), e);
            throw e;
        }
    }

    /**
     * Resolves one phase's worth of contextual parameters into {@code theArgs}.
     *
     * @return false when the invocation is over: the sender has already been told why, either by the
     * engine or - when the parser answered {@code missing()} - by the command's own usage line, which
     * is the one thing no parser could have written
     */
    private boolean resolveContextuals(Map<Integer, ArgParserContextual> group, Object[] theArgs,
                                       Function<ArgInfo, ContextualParseCall> callFor, ResolvedArguments resolved,
                                       FCommandSender sender, HelpLine helpLine) {
        for (Map.Entry<Integer, ArgParserContextual> entry : group.entrySet()) {
            ArgParserContextual parserContextual = entry.getValue();
            ArgInfo contextualInfo = parserContextual.getArgInfo();

            ParseOutcome<?> outcome = engine.run(parserContextual, callFor.apply(contextualInfo));

            if (!isUsable(outcome, sender, helpLine)){
                return false;
            }

            Object parsedContextual = outcome.getValueOrNull();
            theArgs[entry.getKey()] = parsedContextual;
            resolved.resolved(contextualInfo.getArgData().getName(), parsedContextual);
        }
        return true;
    }

    /**
     * Whether the invocation can go on with what the engine answered. Everything fatal has already
     * been explained to the sender by the engine, with one exception it could not have written:
     * {@code MISSING} says nothing was typed where something had to be, and the only answer to that is
     * the command's own usage line. Every family gets that same answer - a positional, a flag's value
     * and a contextual alike.
     */
    private static boolean isUsable(ParseOutcome<?> outcome, FCommandSender sender, HelpLine helpLine) {
        if (outcome.getResult().getKind() == ParseResult.Kind.MISSING){
            helpLine.sendTo(sender);
            return false;
        }
        return !outcome.isFatal();
    }

    /**
     * A flag's value takes the same "token present" row of the routing table a positional does: its
     * {@link ArgInfo} is REQUIRED, so a value the parser does not recognize gets the parser's own
     * message instead of silently turning into null.
     */
    private static ParseCall flagCall(FlagBinding binding, FCommandSender sender, DispatchContext dispatch,
                                      ResolvedArguments resolved, Argumento value) {
        return new ParseCall(sender, value, binding.parser.getArgInfo(), dispatch, resolved, true);
    }

    private static List<String> remainingTokens(MultiArgumentos window, int from) {
        List<String> tokens = window.getStringArgs();
        return from >= tokens.size() ? Collections.<String>emptyList() : new ArrayList<>(tokens.subList(from, tokens.size()));
    }

    /**
     * The {@code def()} of a variadic tail, as tokens. Unlike every other {@code def()}, which is one
     * token because a position holds one, a tail IS a stretch of the line - so its default is read the
     * way the line would have been, split on whitespace.
     */
    private static List<String> defaultTailTokens(String def) {
        return Arrays.asList(def.trim().split("\\s+"));
    }

    /**
     * A variadic tail never goes through a parser: the accepted types are exactly the ways of handing
     * the same tokens over, and picking between them is the framework's job, not a conversion the dev
     * could get wrong.
     */
    private static Object greedyValue(Class<?> type, List<String> tail) {
        if (type == String[].class){
            return tail.toArray(new String[0]);
        }
        if (type == MultiArgumentos.class){
            return new MultiArgumentos(tail.toArray(new String[0]));
        }
        if (type == List.class){
            return new ArrayList<String>(tail);
        }
        if (type == Set.class){
            //Insertion-ordered: the tail is a line somebody typed, and dropping repeats is no reason
            //to also drop the order they typed them in
            return new LinkedHashSet<String>(tail);
        }
        String joined = String.join(" ", tail);
        return type == Argumento.class ? new Argumento(joined) : joined;
    }

    /**
     * The outcome of one {@link #invoke}: either the method ran (and this carries what it returned,
     * which a {@code @FinalCMD.Capture} needs) or it never did, because something upstream already
     * refused and told the sender why.
     */
    public static final class Invocation {

        public static final Invocation ABORTED = new Invocation(true, null, Collections.<String, Object>emptyMap());

        private final boolean aborted;
        private final @Nullable Object returnedValue;
        private final Map<String, Object> parsedArguments;

        private Invocation(boolean aborted, @Nullable Object returnedValue, Map<String, Object> parsedArguments) {
            this.aborted = aborted;
            this.returnedValue = returnedValue;
            this.parsedArguments = parsedArguments;
        }

        public boolean isAborted() {
            return aborted;
        }

        public @Nullable Object getReturnedValue() {
            return returnedValue;
        }

        /**
         * Every positional this invocation parsed, keyed by its declared name ({@code "<server>"}) - what
         * a {@code @FinalCMD.Capture} hands to the subtree that addresses one of its tokens directly.
         */
        public Map<String, Object> getParsedArguments() {
            return parsedArguments;
        }
    }

    /**
     * A single {@code @Arg.Flag} parameter, bound at registration time (fail-fast) and resolved on
     * every invoke. Also read by the help renderer ({@code buildHelpLineTemplate}) and by
     * {@code FinalCMDPluginCommand}'s tab-complete, which is why it is a public nested type
     * with getters instead of staying invoke()-only.
     */
    public static final class FlagBinding {
        private final int paramIndex;
        private final String canonicalName; //dashes stripped, lowercase - matches MultiArgumentos.FlagBinding's canonical name
        private final String rawName; //as declared, e.g. "--force" - used for error/help/tab display
        private final boolean booleanFlag; //true when the parameter type is Boolean: arity 0, presence == TRUE
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

        /**
         * The spelling this flag takes on a usage line: the short one the dev picked, or the long name.
         * Only the LINE shortens - the hover keeps listing every spelling, because that is the only
         * place a player ever discovers the short form.
         */
        public String getUsageSpelling() {
            return argData.getUsageName().isEmpty() ? rawName : argData.getUsageName();
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
