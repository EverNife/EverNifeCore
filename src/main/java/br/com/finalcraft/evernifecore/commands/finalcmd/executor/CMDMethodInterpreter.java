package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.argumento.Argumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.ArgData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.SubCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgInfo;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParserManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.exception.ArgParseException;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.parameter.CMDParameterType;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpLine;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginManager;
import br.com.finalcraft.evernifecore.fancytext.FancyText;
import br.com.finalcraft.evernifecore.locale.LocaleMessageImp;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.locale.data.FCLocaleData;
import br.com.finalcraft.evernifecore.locale.scanner.FCLocaleScanner;
import br.com.finalcraft.evernifecore.util.FCBukkitUtil;
import br.com.finalcraft.evernifecore.util.FCColorUtil;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import br.com.finalcraft.evernifecore.util.ReflectionUtil;
import br.com.finalcraft.evernifecore.util.commons.Tuple;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class CMDMethodInterpreter {

    private final JavaPlugin owningPlugin;
    private final Method method;
    private final Object executor;
    private final CMDData cmdData;
    private final String[] labels; //Alias of the command or name of the subCMD
    private final boolean isSubCommand;
    private final boolean playerOnly;
    private final Map<Integer, Tuple<CMDParameterType,Class>> simpleArguments = new HashMap();
    private final Map<Integer, ArgParser> customArguments = new HashMap();
    private final Map<Integer, ITabParser> tabParsers = new HashMap<>();

    private transient HelpLine helpLine;

    public CMDMethodInterpreter(JavaPlugin owningPlugin, Method method, Object executor, CMDData cmdData) {
        this.owningPlugin = owningPlugin;
        this.method = method;
        this.executor = executor;
        this.cmdData = cmdData;
        this.labels = cmdData.labels();
        this.isSubCommand = cmdData instanceof SubCMDData;

        if (!method.isAccessible()) method.setAccessible(true);

        boolean playerOnly = false;

        List<Tuple<Class, Annotation[]>> argsAndAnnotations = ReflectionUtil.getArgsAndAnnotations(method);

        int flagArgIndex = isSubCommand ? 1 : 0;

        for (int index = 0; index < argsAndAnnotations.size(); index++) {

            Tuple<Class, Annotation[]> tuple = argsAndAnnotations.get(index);
            Class parameterClazz = tuple.getAlfa();

            Arg arg = (Arg) Arrays.stream(tuple.getBeta()).filter(annotation -> annotation.annotationType() == Arg.class).findFirst().orElse(null);

            if (arg != null){
                ArgData argData = new ArgData(arg);
                if (cmdData.argCustomizer() != null){//Customize this ArgData if needed
                    cmdData.argCustomizer().accept(argData, parameterClazz);
                }
                if (ArgParser.class == argData.parser()){
                    //This means the DEFAULT parser, so, we look over the ArgParserManager
                    Class<? extends ArgParser> parserClass = ArgParserManager.getParser(owningPlugin, parameterClazz);
                    if (parserClass == null){
                        throw new IllegalStateException("Failed to found the proper ArgParser on the FinalCMD (" + executor.getClass().getName() +")[" + method.getName() +"] parameter {index='" + index + "', name='" + argData.name() + "'}. The dev should set it manually or register it on the ArgParserManager!");
                    }
                    argData.parser(parserClass);
                }

                ArgInfo argInfo = new ArgInfo(parameterClazz, argData, flagArgIndex); //If subcommand, move arg to the RIGHT 1 slot
                ArgParser parserInstance;
                try {
                    Constructor<? extends ArgParser> constructor = argData.parser().getDeclaredConstructor(ArgInfo.class);
                    constructor.setAccessible(true);
                    parserInstance = constructor.newInstance(argInfo);
                }catch (Exception e){
                    e.printStackTrace();
                    throw new IllegalStateException("Failed to instantiate the ArgParser on the FinalCMD (" + executor.getClass().getName() +")[" + method.getName() +"] parameter [index=" + index + ", name=" + argData.name() + "]");
                }

                customArguments.put(index, parserInstance); //Index of the methodOrder eco_give(Player, arg1, PlayerData, arg3, etc...)
                tabParsers.put(flagArgIndex, parserInstance); //Index of the final TabParser (/eco give arg1 arg2)
                flagArgIndex++;
            }else { //IF it's not an Annotated @Arg we look upon allowed classes
                for (CMDParameterType parameterType : CMDParameterType.ALLOWED_CLASSES) {
                    if (parameterClazz == parameterType.getClazz() || (parameterType.isCheckExtends() && parameterType.getClazz().isAssignableFrom(parameterClazz))){
                        Tuple<CMDParameterType, Class> nonArgParameter = Tuple.of(parameterType, parameterClazz);
                        if (parameterType.isPlayerOnly()){
                            playerOnly = true;
                        }
                        simpleArguments.put(index, nonArgParameter);
                    }
                }
            }

        }

        this.playerOnly = playerOnly;

        if (simpleArguments.size() == 0) {
            throw new IllegalStateException("You tried to create a FinalCMD with a method that has no args at all!");
        }

        this.helpLine = buildHelpLine();
    }

    public CMDData getCmdData() {
        return cmdData;
    }

    public HelpLine getHelpLine() {
        return helpLine;
    }

    private HelpLine buildHelpLine(){
        String localeMessageKey = method.getDeclaringClass().getSimpleName() + "." + method.getName().toUpperCase();
        FCLocaleData[] locales = cmdData.locales();
        LocaleMessageImp localeMessage;

        if (locales.length > 0){
            localeMessage = FCLocaleScanner.scanForLocale(owningPlugin, localeMessageKey, true, locales);
        }else {
            //If no FCLocale is present, use the cmdData desc() to build it
            localeMessage = new LocaleMessageImp(owningPlugin,localeMessageKey);
            FancyText fancyText = new FancyText(null, cmdData.desc());
            for (LocaleType lang : LocaleType.values()) {
                localeMessage.addLocale(
                        lang.name(),
                        fancyText
                );
            }
            //Add the default lang as well
            localeMessage.addLocale(ECPluginManager.getOrCreateECorePlugin(owningPlugin).getPluginLanguage(), fancyText);
        }

        Set<FancyText> fancyTexts = new HashSet<>(localeMessage.getFancyTextMap().values());
        //The HashMap values() might have several repeated values, we need to filter it to prevent reprocess the same FancyText more than once

        for (FancyText fancyText : fancyTexts) {
            //By Default, any Method FCLocale for both FinalCMD and SubCMD should be in the 'hover' not on the  'text'
            //So, we will check boths in here and priorize the hover and remove the text, as the 'text' of these
            //help lines are the USAGE and the hover is the DESCRIPTION
            final String description = fancyText.getHoverText() != null && !fancyText.getHoverText().isEmpty() ? fancyText.getHoverText() : fancyText.getText();

            //For the USAGE we have two scenarios
            // Or we have a declared usage over here, like a full text like '%name% <give|take> <Player>'
            // or we have annotated @Args, in this case, we have a priority on the construction of the usage using these args

            final String usage;
            String commandPrefix = "§3§l ▶ §a/§e%label% " + (isSubCommand ? "%subcmd% " : "");
            if (customArguments.size() == 0){
                //For legacy support we need to remove the placeholders '%name%' and '%label%', on modern ECPLugins we do not use it, maybe one day i might remove this
                usage = commandPrefix + cmdData.usage().replace("%name%", "").replace("%label%", "").trim();
            }else {

                //So, if we have customArgs we need to build the proper usage using these args.
                StringBuilder stringBuilder = new StringBuilder();

                //Put all args one after the other
                customArguments.entrySet().stream()
                        .sorted(Comparator.comparingInt(Map.Entry::getKey))
                        .map(Map.Entry::getValue)
                        .forEach(argParser -> stringBuilder.append(" " + argParser.getArgInfo().getArgData().name()));

                usage = commandPrefix + stringBuilder.toString().trim();
            }

            fancyText.setText(usage);
            fancyText.setSuggestCommandAction("/%label% %subcmd%");
            fancyText.setHoverText(description != null && !FCColorUtil.stripColor(description).trim().isEmpty() ? "§b" + description : null); //set HoverText with a 'LIGHT_BLUE' prefix
        }

        return new HelpLine(localeMessage, cmdData.permission());
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
        return customArguments;
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

    public void invoke(CommandSender sender, String label, MultiArgumentos argumentos, HelpContext helpContext, HelpLine helpLine) throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {

        helpContext.setLastLabel(label);

        Object[] possibleArgs = new Object[]{label, argumentos, helpContext, helpLine};
        Object[] theArgs = new Object[simpleArguments.size() + customArguments.size()];

        for (int index = 0; index < theArgs.length; index++) {

            ArgParser parser = customArguments.get(index);

            if (parser != null){
                Argumento argumento = argumentos.get(parser.getArgInfo().getIndex());
                if (parser.getArgInfo().isRequired() && argumento.isEmpty()){
                    helpLine.sendTo(sender);
                    return;
                }
                try {
                    theArgs[index] = parser.parserArgument(sender, argumento);
                }catch (ArgParseException argParseException){
                    //If we fail to parse this arg, for example, "ArgParserPlayer" 'the player is not online', we can leave now
                    return;
                }
                continue;
            }

            Tuple<CMDParameterType, Class> tuple = simpleArguments.get(index);

            CMDParameterType parameterType = tuple.getAlfa();
            Class parameterClass = tuple.getBeta();

            if (parameterType.getClazz() == CommandSender.class) { theArgs[index] = sender; continue; }
            if (parameterType.getClazz() == Player.class) { theArgs[index] = sender; continue; }
            if (parameterType.getClazz() == PlayerData.class) { theArgs[index] = PlayerController.getPlayerData((Player) sender); continue; }
            if (parameterType.getClazz() == PDSection.class) { theArgs[index] = PlayerController.getPDSection((Player) sender, parameterClass); continue; }
            if (parameterType.getClazz() == ItemStack.class) {
                theArgs[index] = FCBukkitUtil.getPlayersHeldItem((Player) sender);
                if (theArgs[index] == null){
                    FCMessageUtil.needsToBeHoldingItem(sender);
                    return;
                }
            }

            for (Object possibleArg : possibleArgs) {
                if (parameterType.isCheckExtends()){
                    if (parameterClass.isInstance(possibleArg)){
                        theArgs[index] = possibleArg;
                        break;
                    }
                }else if (parameterClass == possibleArg.getClass()){
                    theArgs[index] = possibleArg;
                    break;
                }
            }
        }

        method.invoke(executor, theArgs);
    }
}
