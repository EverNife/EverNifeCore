package br.com.finalcraft.evernifecore.commands.finalcmd.implementation;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.FlagedArgumento;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.FinalCMDManager;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.argument.ArgParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.FCDefaultExecutor;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContextTemplate;
import br.com.finalcraft.evernifecore.commands.finalcmd.tab.ITabParser;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CaptureBinding;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandTreeScanner;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandWalker;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.WalkResult;
import br.com.finalcraft.evernifecore.config.settings.ECSettings;
import br.com.finalcraft.evernifecore.ecplugin.ECPluginData;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Validate;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class FinalCMDPluginCommand {

    protected final ECPluginData owningPlugin;
    protected final FinalCMDData finalCMD;
    protected final FCDefaultExecutor executor;
    protected final CommandNode root;

    protected transient IPlatformCMD platformCommand; //The actual command.class inside the platform

    public static final String DEFAULT_USAGE = "§3§l ▶ §a/§e${label} ";

    public FinalCMDPluginCommand(@Nonnull ECPluginData owningPlugin, @Nonnull FinalCMDData finalCMD, @Nonnull CommandNode root) {
        Validate.notNull(owningPlugin, "OwningPlugin is null!");
        Validate.notNull(finalCMD, "FinalCMD is null!");
        Validate.notNull(root, "Root node is null!");
        Validate.isTrue(!finalCMD.getLabels()[0].isEmpty(), "Name is empty!");

        this.owningPlugin = owningPlugin;
        this.finalCMD = finalCMD;
        this.executor = new FCDefaultExecutor(this);
        this.root = root;
    }

    /** The root of this command's tree; {@code root.getExecutable()} is the {@code @FinalCMD} method, if any. */
    public CommandNode getRoot() {
        return root;
    }

    public @Nullable CMDMethodInterpreter getMainInterpreter() {
        return root.getExecutable();
    }

    /** The root's help as registration knows it - render it with a {@code CommandPath} to send it. */
    public HelpContextTemplate getHelpContextTemplate() {
        return root.getHelpContext();
    }

    public String getPrimaryLabel(){
        return finalCMD.getLabels()[0];
    }

    public String[] getExtraLabels(){
        return Arrays.copyOfRange(finalCMD.getLabels(), 1, finalCMD.getLabels().length);
    }

    /**
     * Unregister all commands that are registered on the server with the name of this command, apply
     * whatever this plugin's command registry file says ({@code enabled}/{@code aliases}), then
     * register with the platform (skipped entirely when disabled). On success, tracks this command on
     * its owning plugin's {@link ECPluginData#getRegisteredCommands() registered-commands list},
     * replacing any previous entry with the same primary label - a reload never leaves a
     * duplicate/stale entry.
     *
     * @return true if the command has been successfully registered
     */
    public boolean registerCommand() {
        boolean enabled = applyRegistryFile();

        unregisterFromPlatform();

        if (!enabled){
            return false;
        }

        CommandTreeScanner.buildHelpContexts(root, finalCMD.getHelpHeader());

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
     * Applies this plugin's command registry file to the tree about to be registered: an {@code enabled}
     * flag and an {@code aliases} override, per node. An aliases override REPLACES the annotation's extra
     * labels (the primary label is never touched) before the command reaches the platform, so
     * registration/unregistration/help/tab all see the effective ones. Applied on every registration
     * call (boot/reload of the owning plugin) - there is no hot rebind.
     * <p>
     * While the feature is off the file is not even opened, and the annotations decide alone.
     *
     * @return whether the command is enabled (false means the caller must skip registration entirely)
     */
    private boolean applyRegistryFile() {
        if (!ECSettings.COMMAND_REGISTRY_FILES_ENABLED){
            return true;
        }
        return CommandRegistryFile.of(owningPlugin).applyTo(root, owningPlugin);
    }

    public ECPluginData getOwningPlugin() {
        return owningPlugin;
    }

    /**
     * Completes the last token of {@code args} by running the same traversal the dispatch runs, over
     * the tokens the sender already committed. Nothing is parsed and no {@code @Capture} is invoked -
     * this runs once per keystroke, so it only ever counts tokens.
     * <p>
     * A line the tree cannot place answers with an EMPTY list, which is the truth: the command does not
     * know what goes there. There is no platform fallback to guess with.
     *
     * @param sender sender
     * @param alias  alias used
     * @param args   argument of the command
     *
     * @return a list of possible values
     */
    public List<String> tabComplete(FCommandSender sender, String alias, String[] args) {
        int index = args.length - 1;
        if (index < 0){
            return ImmutableList.of();
        }

        boolean isPlayer = sender.isPlayer();

        //Only the tokens the sender already committed take part in the traversal - the word being
        //typed is not a path segment yet, it is what we are asked to complete
        WalkResult walk = CommandWalker.walk(root, Arrays.copyOfRange(args, 0, index), alias);
        CommandNode node = walk.getNode();

        if (walk.getOutcome() == WalkResult.Outcome.FLAG_TOO_EARLY || walk.getOutcome() == WalkResult.Outcome.NO_MATCH
                || walk.getOutcome() == WalkResult.Outcome.RESERVED_HELP){
            return ImmutableList.of();
        }

        //The same chain the dispatch evaluates, asked in silence: the root, every hop walked to get
        //here, permission and validations alike. A word the tab offers is a word the dispatch runs.
        if (!CMDAccessValidation.allowsPath(sender, node, CMDAccessValidation.AccessMode.LIST)){
            return ImmutableList.of();
        }

        for (CommandNode pathNode : walk.getPathNodes()) {
            if (pathNode.isPlayerOnly() && !isPlayer){
                return ImmutableList.of();
            }
        }

        int localIndex = index - walk.getConsumed();

        //The traversal ran out of line inside a capture: the word being typed is one of the tokens that
        //capture eats, so its own @Arg parser answers - never the children's labels, which come later
        CaptureBinding pendingCapture = walk.getPendingCapture();
        if (pendingCapture != null){
            List<ArgParser> captureParsers = pendingCapture.getArgParsers();
            return localIndex >= 0 && localIndex < captureParsers.size()
                    ? captureParsers.get(localIndex).tabComplete(tabContextAt(sender, alias, args, index, walk))
                    : ImmutableList.<String>of();
        }

        CMDMethodInterpreter interpreter = node.getExecutable();

        //A node with children expects a literal here, unless it is the root of a command whose own
        //method answers for everything (the historic FULL-help behaviour, where args[0] is an argument)
        boolean rootExecutableTakesOver = node == root && interpreter != null && ((FinalCMDData) interpreter.getCmdData()).getHelpType() == CMDHelpType.FULL;
        if (node.hasChildren() && localIndex == 0 && !rootExecutableTakesOver){
            return node.getChildren().stream()
                    .filter(child -> CMDAccessValidation.listsFor(sender, child))
                    .map(CommandNode::getPrimaryLabel)
                    .filter(s -> StringUtils.startsWithIgnoreCase(s, args[index]))
                    .collect(Collectors.toList());
        }

        if (interpreter == null || !CMDAccessValidation.allowsExecutableOf(sender, node, CMDAccessValidation.AccessMode.LIST)){
            return ImmutableList.of();
        }

        //Every flag the PATH declares, not just the leaf's: a node's flag is written after the path,
        //in the same window, so tab has to know about it exactly where the sender may type it. This
        //comes before the positional check because an executable may have flags and no @Arg at all.
        Map<String, MultiArgumentos.FlagBinding> extractionBindings = node.getAccumulatedFlagExtractionBindings();
        int greedyTailIndex = interpreter.getGreedyTailIndex();

        //The scan runs even where nothing declares a flag, exactly as the dispatch does: a bare "--"
        //disappears off the line there too, and the positional the sender is typing has to be counted
        //against the same tokens the method will see
        int effectiveIndex = effectivePositionalIndex(extractionBindings, args, walk.getConsumed(), index, greedyTailIndex);

        if (!extractionBindings.isEmpty()){
            List<String> flagSuggestions = tabCompleteFlags(node, extractionBindings, sender, alias, args,
                    walk.getConsumed(), index, walk, effectiveIndex, greedyTailIndex);
            if (flagSuggestions != null){
                return flagSuggestions;
            }
        }

        ITabParser tabParser = interpreter.hasTabComplete() ? interpreter.getTabParser(effectiveIndex) : null;

        if (tabParser == null){
            return ImmutableList.of();
        }

        //The word being completed is always args[index] (the real last token) - effectiveIndex only
        //selects WHICH parser to delegate to, it must never replace index inside the TabContext itself
        //(TabContext.getLastWord() reads args[index] directly), or a corrected lookup would silently
        //hand the parser the wrong word to filter against.
        return tabParser.tabComplete(tabContextAt(sender, alias, args, index, walk));
    }

    private static ITabParser.TabContext tabContextAt(FCommandSender sender, String alias, String[] args, int index, WalkResult walk){
        return new ITabParser.TabContext(sender, alias, args, index, index - walk.getConsumed(), walk.getPath(), capturedTokensByNodePath(walk));
    }

    /**
     * Each ancestor's captured token(s) keyed by node path, exactly as typed. A capture the traversal
     * never got to finish contributes nothing - there is no token to hand back yet.
     */
    private static Map<String, String> capturedTokensByNodePath(WalkResult walk){
        List<CommandNode> capturingNodes = walk.getNode().getCapturingAncestry();
        if (capturingNodes.isEmpty()){
            return Collections.emptyMap();
        }

        List<String> tokens = walk.getCaptureTokens();
        Map<String, String> byNodePath = new LinkedHashMap<>();
        int cursor = 0;
        for (CommandNode capturingNode : capturingNodes) {
            int width = capturingNode.getCapture().tokenWidth();
            if (cursor + width > tokens.size()){
                break;
            }
            byNodePath.put(capturingNode.getNodePath(), String.join(" ", tokens.subList(cursor, cursor + width)));
            cursor += width;
        }
        return byNodePath;
    }

    /**
     * The flag-aware half of tab-complete: either the word being typed is itself a flag name
     * (suggest the declared long names, filtered by permission/prefix/already-used), or the PREVIOUS
     * token is a declared value-flag (delegate to that flag's own value parser) - both scenarios return
     * their result directly. Everything else (including anything typed after a bare {@code --}, which
     * always stays positional) returns {@code null} so the caller falls through to the positional flow.
     *
     * @param pathLength how many tokens the command path ate, so only the executable's own window is
     * scanned - a label or a captured token is never a flag
     * @param positionalsTyped how many positionals the committed tokens already fill
     * @param greedyTailIndex where this executable's variadic tail begins, or -1
     */
    private @Nullable List<String> tabCompleteFlags(CommandNode node, Map<String, MultiArgumentos.FlagBinding> extractionBindings,
                                                    FCommandSender sender, String alias, String[] args, int pathLength, int index,
                                                    WalkResult walk, int positionalsTyped, int greedyTailIndex){
        boolean endOfFlagsReached = index > pathLength && Arrays.asList(args).subList(pathLength, index).contains("--");
        //Once the tail has opened, every remaining word is somebody's sentence - offering flag names
        //there would suggest a spelling the dispatch is about to hand over as plain text
        boolean insideTheTail = greedyTailIndex >= 0 && positionalsTyped > greedyTailIndex;
        if (endOfFlagsReached || insideTheTail){
            return null;
        }

        String lastWord = args[index];
        if (looksLikeAFlagBeingTyped(lastWord)){
            int equalsAt = lastWord.indexOf('=');
            if (equalsAt >= 0){
                return completeInlineFlagValue(node, extractionBindings, sender, alias, args, index, walk, equalsAt);
            }

            Set<String> alreadyUsed = new HashSet<>();
            if (index > pathLength){
                MultiArgumentos scan = new MultiArgumentos(Arrays.copyOfRange(args, pathLength, index));
                scan.extractDeclaredFlags(extractionBindings, greedyTailIndex);
                for (FlagedArgumento flag : scan.getFlags()) {
                    alreadyUsed.add(flag.getFlagName());
                }
            }

            return node.getAccumulatedFlagBindings().stream()
                    .filter(binding -> binding.getArgData().getPermission().isEmpty() || sender.hasPermission(binding.getArgData().getPermission()))
                    .filter(binding -> !alreadyUsed.contains(binding.getCanonicalName()))
                    .map(binding -> suggestedSpelling(binding, lastWord))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        if (index > pathLength){
            String previousToken = args[index - 1];
            //A marker that already carries its value ("--page=3") took it: what follows is a positional
            if (MultiArgumentos.isFlagMarker(previousToken) && previousToken.indexOf('=') < 0){
                MultiArgumentos.FlagBinding extractionBinding = extractionBindings.get(MultiArgumentos.flagLookupName(previousToken));
                if (extractionBinding != null && extractionBinding.getArity() == 1){
                    CMDMethodInterpreter.FlagBinding flagBinding = declaredFlagOf(node, extractionBinding);
                    if (flagBinding != null){
                        return flagBinding.getParser().tabComplete(tabContextAt(sender, alias, args, index, walk));
                    }
                }
            }
        }

        return null;
    }

    /**
     * Completes the value half of a marker being typed with its value glued on ({@code --page=3}). The
     * flag's own parser answers, as it would for the spaced form, but it is asked about the value alone
     * and its answers come back with the marker glued in front - a completion replaces the whole word,
     * so handing back a bare value would replace the flag with it.
     *
     * @param equalsAt where the {@code =} sits in the word being typed
     */
    private @Nonnull List<String> completeInlineFlagValue(CommandNode node, Map<String, MultiArgumentos.FlagBinding> extractionBindings,
                                                          FCommandSender sender, String alias, String[] args, int index, WalkResult walk, int equalsAt){
        String lastWord = args[index];
        MultiArgumentos.FlagBinding extractionBinding = extractionBindings.get(MultiArgumentos.flagLookupName(lastWord));
        if (extractionBinding == null || extractionBinding.getArity() != 1){
            return ImmutableList.of();
        }

        CMDMethodInterpreter.FlagBinding flagBinding = declaredFlagOf(node, extractionBinding);
        if (flagBinding == null){
            return ImmutableList.of();
        }

        String marker = lastWord.substring(0, equalsAt + 1);
        String[] valueArgs = args.clone();
        valueArgs[index] = lastWord.substring(equalsAt + 1);

        List<String> values = flagBinding.getParser().tabComplete(tabContextAt(sender, alias, valueArgs, index, walk));
        List<String> inlined = new ArrayList<>(values.size());
        for (String value : values) {
            inlined.add(marker + value);
        }
        return inlined;
    }

    /** The declared flag {@code extractionBinding} normalizes to, or null when this path declares none. */
    private static @Nullable CMDMethodInterpreter.FlagBinding declaredFlagOf(CommandNode node, MultiArgumentos.FlagBinding extractionBinding){
        for (CMDMethodInterpreter.FlagBinding flagBinding : node.getAccumulatedFlagBindings()) {
            if (flagBinding.getCanonicalName().equals(extractionBinding.getCanonicalName())){
                return flagBinding;
            }
        }
        return null;
    }

    /**
     * Which spelling of {@code binding} answers what has been typed so far: the long name when it
     * matches, otherwise the first alias that does.
     * <p>
     * A short alias exists to be typed, so the one moment it has to be discoverable is exactly when
     * what was typed can only be it - {@code -n} used to complete to nothing at all. One flag still
     * answers with at most ONE suggestion, or a single flag with three spellings would fill the list.
     *
     * @return the spelling to suggest, or null when nothing this flag answers to starts with {@code typed}
     */
    private static @Nullable String suggestedSpelling(CMDMethodInterpreter.FlagBinding binding, String typed) {
        if (StringUtils.startsWithIgnoreCase(binding.getRawName(), typed)){
            return binding.getRawName();
        }
        for (String alias : binding.getArgData().getAliases()) {
            if (StringUtils.startsWithIgnoreCase(alias, typed)){
                return alias;
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
     * {@code args[pathLength..index-1]} through the same declared-flag extraction the real dispatch uses:
     * every flag marker and the value/quoted-group it consumes, plus a bare {@code --}, is stripped
     * before counting what remains - the count IS the index the current (still being typed) token would
     * land on among the positionals.
     * <p>
     * A marker the extraction could not take - a name nobody declared, or a value flag left with nothing
     * to consume - stays in the tokens by design, so the caller can report it. It is still a flag the
     * sender typed and never a positional, so it does not shift the count either.
     */
    private static int effectivePositionalIndex(Map<String, MultiArgumentos.FlagBinding> extractionBindings, String[] args,
                                                int pathLength, int index, int greedyTailIndex){
        MultiArgumentos scan = new MultiArgumentos(Arrays.copyOfRange(args, pathLength, index));
        scan.extractDeclaredFlags(extractionBindings, greedyTailIndex);

        int positionals = 0;
        for (String token : scan.getStringArgs()) {
            if (!MultiArgumentos.isFlagMarker(token)){
                positionals++;
            }
        }
        return positionals;
    }

}
