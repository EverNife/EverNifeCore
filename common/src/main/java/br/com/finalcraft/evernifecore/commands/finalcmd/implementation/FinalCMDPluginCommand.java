package br.com.finalcraft.evernifecore.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.api.common.player.FPlayer;
import br.com.finalcraft.evernifecore.argumento.FlagedArgumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.FCDefaultExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import br.com.finalcraft.evernifecore.config.ConfigFactory;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import br.com.finalcraft.everyconfig.config.Config;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Data
public class FinalCMDPluginCommand {

    protected final ECPluginData owningPlugin;
    protected final FinalCMDData finalCMD;
    protected final FCDefaultExecutor executor;
    protected final @Nullable CMDMethodInterpreter mainInterpreter;
    protected final List<CMDMethodInterpreter> subCommands = new ArrayList<>();
    //
    //KEY: 0
    //VALUES: 0
    //   SUB_CMD  ? /sharelag add <Player>
    //   SUB_CMD  ? /sharelag remove <Player>
    //   SUB_CMD  ? /sharelag list
    //

    protected HelpContext helpContext;// Immutable Context from all HelpLines from all SubCmds (come from list 'helpLineList' bellow)
    protected transient IPlatformCMD platformCommand; //The actual command.class inside the platform

    public static final String DEFAULT_USAGE = "§3§l ▶ §a/§e%label% ";

    public FinalCMDPluginCommand(@Nonnull ECPluginData owningPlugin, @Nonnull FinalCMDData finalCMD, @Nullable CMDMethodInterpreter mainInterpreter) {
        Validate.notNull(owningPlugin, "OwningPlugin is null!");
        Validate.notNull(finalCMD, "FinalCMD is null!");
        Validate.isTrue(!finalCMD.getLabels()[0].isEmpty(), "Name is empty!");

        this.owningPlugin = owningPlugin;
        this.finalCMD = finalCMD;
        this.executor = new FCDefaultExecutor(this);
        this.mainInterpreter = mainInterpreter;
    }

    public String getPrimaryLabel(){
        return finalCMD.getLabels()[0];
    }

    public String[] getExtraLabels(){
        return Arrays.copyOfRange(finalCMD.getLabels(), 1, finalCMD.getLabels().length);
    }

    /**
     * Unregister all commands that are registered on the server with the name of this command, apply
     * the central {@code commands.yml} overrides ({@code enabled}/{@code aliases}), then register with
     * the platform (skipped entirely when disabled). On success, tracks this command on its owning
     * plugin's {@link ECPluginData#getRegisteredCommands() registered-commands list}, replacing any
     * previous entry with the same primary label - a reload never leaves a duplicate/stale entry.
     *
     * @return true if the command has been successfully registered
     */
    public boolean registerCommand() {
        boolean enabled = applyCommandsYamlOverrides();

        unregisterFromPlatform();

        if (!enabled){
            return false;
        }

        //Sort all Methods based on the First Label's Name
        Collections.sort(subCommands, Comparator.comparing(cmdMethodInterpreter -> cmdMethodInterpreter.getLabels()[0]));

        this.helpContext = new HelpContext(finalCMD.getHelpHeader(), this);

        boolean registered = EverNifeCore.getPlatform().registerCommand(this);
        if (registered){
            owningPlugin.trackRegisteredCommand(this);
        }
        return registered;
    }

    /**
     * Unregisters this command from the platform (primary label + aliases) and removes it from its
     * owner plugin's registered-commands list. Safe to call twice.
     */
    public void unregister() {
        unregisterFromPlatform();
        owningPlugin.untrackRegisteredCommand(this);
    }

    private void unregisterFromPlatform() {
        FinalCMDManager.unregisterCommand(this.getPrimaryLabel(), this.getOwningPlugin());
        for (String alias : this.getExtraLabels()) {
            FinalCMDManager.unregisterCommand(alias, this.getOwningPlugin());
        }
    }

    /**
     * Reads (and, on first sight, seeds) this command's entry in the central {@code commands.yml}: an
     * {@code enabled} flag and an {@code aliases} override for the extra labels. A non-default aliases
     * override REPLACES the annotation's extra labels (the primary label is never touched) before this
     * command reaches the platform, so registration/unregistration/help/tab all see the effective ones.
     * Applied on every registration call (boot/reload of the owning plugin) - there is no hot rebind.
     *
     * @return whether the command is enabled (false means the caller must skip registration entirely)
     */
    private boolean applyCommandsYamlOverrides() {
        String pluginName = owningPlugin.getMetaInfo().getName();
        String primaryLabel = getPrimaryLabel();
        String path = "Commands." + pluginName + "." + primaryLabel;

        Config commandsConfig = ConfigFactory.open(EverNifeCore.getEcPluginData(), "commands.yml");
        commandsConfig.setComment("Commands", COMMANDS_YAML_HEADER);

        boolean enabled = commandsConfig.getOrSetValueIfAbsent(path + ".enabled", true,
                "Set to 'false' to stop this command from being registered.");
        List<String> aliasesOverride = commandsConfig.getOrSetValueIfAbsent(path + ".aliases",
                Arrays.asList(getExtraLabels()),
                "Extra labels for this command - the primary label ('" + primaryLabel + "') cannot be changed here.");

        if (!Arrays.asList(getExtraLabels()).equals(aliasesOverride)){
            String[] overriddenLabels = new String[1 + aliasesOverride.size()];
            overriddenLabels[0] = primaryLabel;
            for (int i = 0; i < aliasesOverride.size(); i++) {
                overriddenLabels[i + 1] = aliasesOverride.get(i);
            }
            finalCMD.setLabels(overriddenLabels);
        }

        if (commandsConfig.hasNewSeededDefaults()){
            commandsConfig.save();
            commandsConfig.clearNewSeededDefaults();
        }

        if (!enabled){
            owningPlugin.getLog().info("Command '" + primaryLabel + "' of plugin '" + pluginName + "' disabled by commands.yml");
        }

        return enabled;
    }

    private static final String COMMANDS_YAML_HEADER = String.join("\n",
            "============================================================",
            " EverNifeCore - Central command registry",
            "",
            " One entry per registered command, from EVERY ECPlugin using this",
            " framework (not just EverNifeCore's own), keyed by",
            " <PluginName>.<primaryLabel>. Entries are generated automatically",
            " on the first registration of each command.",
            "",
            " enabled: false stops the command from being registered.",
            " aliases: overrides the EXTRA labels only - the primary label is",
            "          the entry's identity and cannot be changed here.",
            "",
            " Changes only apply on the owning plugin's next boot/reload -",
            " there is no hot rebind.",
            "============================================================");

    public CMDMethodInterpreter getSubCommand(String firstArg) {

        for (CMDMethodInterpreter methodInterpreter : subCommands) {
            for (String label : methodInterpreter.getLabels()) {
                if (label.equalsIgnoreCase(firstArg)){
                    return methodInterpreter;
                }
            }
        }

        return null;
    }

    public void addSubCommand(CMDMethodInterpreter executor) {
        subCommands.add(executor);
    }

    public ECPluginData getOwningPlugin() {
        return owningPlugin;
    }

    /**
     * @param sender sender
     * @param alias  alias used
     * @param args   argument of the command
     *
     * @return a list of possible values
     */
    public List<String> tabComplete(FCommandSender sender, String alias, String[] args) {
        return tabComplete(sender, alias, args, Collections::emptyList);
    }

    /**
     * Same as {@link #tabComplete(FCommandSender, String, String[])}, but lets the caller supply what
     * to return when no sub-command/main-interpreter matches at all (or the one that matched has no
     * tab-complete or fails its permission check) - the ONE divergence between platforms:
     * {@code McFinalCMDPluginCommand} falls back to Bukkit's own player-name completion there, this
     * class's own zero-arg overload just returns an empty list.
     *
     * @param sender sender
     * @param alias  alias used
     * @param args   argument of the command
     * @param noInterpreterFallback supplies the result for the "nothing matched" case (F6)
     *
     * @return a list of possible values
     */
    public List<String> tabComplete(FCommandSender sender, String alias, String[] args, Supplier<List<String>> noInterpreterFallback) {
        int index = args.length - 1;

        boolean isPlayer = sender instanceof FPlayer;

        //The TabComplete is based on the FirstArg.
        CMDMethodInterpreter interpreter = (args.length == 0 || args[0].isEmpty()) ? null : getSubCommand(args[0]);
        if (interpreter == null && mainInterpreter != null && ((FinalCMDData)mainInterpreter.getCmdData()).getHelpType() == CMDHelpType.FULL){
            interpreter = mainInterpreter;
        }

        if (interpreter == null && subCommands.size() > 0){
            return subCommands.stream()
                    .filter(subCommand -> subCommand.getCmdData().getPermission().isEmpty() || sender.hasPermission(subCommand.getCmdData().getPermission())) //For the first arg of all sub commands we need ot check each permission
                    .filter(subCommand -> !subCommand.isPlayerOnly() ? true : isPlayer) //If is the console calling this tab completion, ignore the subCommand if it's a 'playerOnly' subCMD
                    .filter(subCommand -> {
                        if (subCommand.getCmdData().getCmdAccessValidations().length == 0){
                            return true;
                        }
                        CMDAccessValidation.AccessContext accessContext = new CMDAccessValidation.AccessContext(subCommand, sender);
                        for (CMDAccessValidation cmdAccessValidation : subCommand.getCmdData().getCmdAccessValidations()) {
                            if (!cmdAccessValidation.onPreTabValidation(accessContext)){
                                return false;
                            }
                        }
                        return true;
                    }) //Apply a final custom filtering, in case this cmd has a custom cmdAccessValidation
                    .map(subCommand -> subCommand.getLabels()[0])
                    .filter(s -> StringUtils.startsWithIgnoreCase(s, args[index]))
                    .collect(Collectors.toList());
        }

        if (interpreter == null || !interpreter.hasTabComplete() || (!interpreter.getCmdData().getPermission().isEmpty() && !sender.hasPermission(interpreter.getCmdData().getPermission()))){
            return noInterpreterFallback.get();
        }

        int effectiveIndex = index;
        if (interpreter.hasFlags()){
            List<String> flagSuggestions = tabCompleteFlags(interpreter, sender, alias, args, index);
            if (flagSuggestions != null){
                return flagSuggestions;
            }
            effectiveIndex = effectivePositionalIndex(interpreter, args, index);
        }

        ITabParser tabParser = interpreter.getTabParser(effectiveIndex);

        if (tabParser == null){
            return ImmutableList.of();
        }

        //The word being completed is always args[index] (the real last token) - effectiveIndex only
        //selects WHICH parser to delegate to, it must never replace index inside the TabContext itself
        //(TabContext.getLastWord() reads args[index] directly), or a corrected lookup would silently
        //hand the parser the wrong word to filter against.
        ITabParser.TabContext tabContext = new ITabParser.TabContext(sender, alias, args, index);

        return tabParser.tabComplete(tabContext);
    }

    /**
     * The flag-aware half of tab-complete (F6): either the word being typed is itself a flag name
     * (suggest the declared long names, filtered by permission/prefix/already-used), or the PREVIOUS
     * token is a declared value-flag (delegate to that flag's own value parser) - both scenarios return
     * their result directly. Everything else (including anything typed after a bare {@code --}, which
     * always stays positional) returns {@code null} so the caller falls through to the positional flow.
     */
    private @Nullable List<String> tabCompleteFlags(CMDMethodInterpreter interpreter, FCommandSender sender, String alias, String[] args, int index){
        boolean endOfFlagsReached = index > 0 && Arrays.asList(args).subList(0, index).contains("--");
        if (endOfFlagsReached){
            return null;
        }

        String lastWord = args[index];
        if (looksLikeAFlagBeingTyped(lastWord)){
            Set<String> alreadyUsed = new HashSet<>();
            if (index > 0){
                MultiArgumentos scan = new MultiArgumentos(Arrays.copyOfRange(args, 0, index));
                scan.extractDeclaredFlags(interpreter.getFlagExtractionBindings());
                for (FlagedArgumento flag : scan.getFlags()) {
                    alreadyUsed.add(flag.getFlagName());
                }
            }

            return interpreter.getFlagBindings().stream()
                    .filter(binding -> binding.getArgData().getPermission().isEmpty() || sender.hasPermission(binding.getArgData().getPermission()))
                    .filter(binding -> !alreadyUsed.contains(binding.getCanonicalName()))
                    .map(CMDMethodInterpreter.FlagBinding::getRawName)
                    .filter(rawName -> StringUtils.startsWithIgnoreCase(rawName, lastWord))
                    .collect(Collectors.toList());
        }

        if (index > 0){
            String previousToken = args[index - 1];
            if (MultiArgumentos.isFlagMarker(previousToken)){
                String normalized = previousToken.replaceFirst("^-+", "").toLowerCase();
                MultiArgumentos.FlagBinding extractionBinding = interpreter.getFlagExtractionBindings().get(normalized);
                if (extractionBinding != null && extractionBinding.getArity() == 1){
                    CMDMethodInterpreter.FlagBinding flagBinding = interpreter.getFlagBindingByCanonicalName(extractionBinding.getCanonicalName());
                    if (flagBinding != null){
                        ITabParser.TabContext tabContext = new ITabParser.TabContext(sender, alias, args, index);
                        return flagBinding.getParser().tabComplete(tabContext);
                    }
                }
            }
        }

        return null;
    }

    /**
     * Whether {@code word} looks like a flag name in progress: unlike {@link MultiArgumentos#isFlagMarker},
     * used for a COMPLETE token, this treats a dashes-only word ("-", "--", "---"...) as still-being-typed
     * rather than the committed end-of-flags escape - a player who just typed "--" and hit tab is asking
     * for flag names, not declaring "no more flags" (that reading only applies to a token the player has
     * moved past). The negative-number guard still applies once a digit follows the dashes.
     */
    private static boolean looksLikeAFlagBeingTyped(String word){
        int dashCount = 0;
        while (dashCount < word.length() && word.charAt(dashCount) == '-'){
            dashCount++;
        }
        if (dashCount == 0){
            return false; //no leading dash at all
        }
        if (dashCount == word.length()){
            return true; //dashes-only so far - an open prefix, flags win the tab race over a negative number
        }
        return !Character.isDigit(word.charAt(dashCount));
    }

    /**
     * Translates a raw {@code args} index into its effective positional index by scanning
     * {@code args[0..index-1]} through the same declared-flag extraction the real dispatch uses
     * (F6/TA(f)-(g)): every flag marker and the value/quoted-group it consumes, plus a bare {@code --},
     * is stripped before counting what remains - the count IS the index the current (still being typed)
     * token would land on among the positionals.
     */
    private int effectivePositionalIndex(CMDMethodInterpreter interpreter, String[] args, int index){
        MultiArgumentos scan = new MultiArgumentos(Arrays.copyOfRange(args, 0, index));
        scan.extractDeclaredFlags(interpreter.getFlagExtractionBindings());
        return scan.getStringArgs().size();
    }

}
