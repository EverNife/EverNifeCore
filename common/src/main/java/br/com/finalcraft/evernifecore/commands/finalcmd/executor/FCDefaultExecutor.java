package br.com.finalcraft.evernifecore.commands.finalcmd.executor;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation.CMDAccessValidation;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.CMDHelpType;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.FinalCMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContextTemplate;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpWords;
import br.com.finalcraft.evernifecore.commands.finalcmd.implementation.FinalCMDPluginCommand;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CaptureBinding;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandPath;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandWalker;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.DispatchContext;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.WalkResult;
import br.com.finalcraft.evernifecore.fancytext.MessageScope;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleMessage;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class FCDefaultExecutor {

    @FCLocale(lang = LocaleType.EN_US, text = "§cParameters error, please use /${label} help")
    @FCLocale(lang = LocaleType.PT_BR, text = "§cErro de parâmetros, por favor use /${label} help")
    public static LocaleMessage PARAMETER_ERROR;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cUnknown flag §6[§e${flags}§6]§c! Available flags: §b${available_flags}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cFlag desconhecida §6[§e${flags}§6]§c! Flags disponíveis: §b${available_flags}")
    public static LocaleMessage UNKNOWN_FLAG;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThe flag §6[§e${flags}§6]§c means nothing here - write §b--§c before it to keep it as plain text")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cA flag §6[§e${flags}§6]§c não significa nada aqui - escreva §b--§c antes para mantê-la como texto")
    public static LocaleMessage UNKNOWN_FLAG_NO_LIST;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThe flag §6[§e${flag}§6]§c needs a value: §b${flag} <value>")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cA flag §6[§e${flag}§6]§c precisa de um valor: §b${flag} <valor>")
    public static LocaleMessage FLAG_NEEDS_VALUE;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThe flag §6[§e${flag}§6]§c takes no value - write just §b${flag}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cA flag §6[§e${flag}§6]§c não recebe valor - escreva apenas §b${flag}")
    public static LocaleMessage FLAG_TAKES_NO_VALUE;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cThe flag §6[§e${flag}§6]§c was written twice - it holds one value, so keep the one you meant")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cA flag §6[§e${flag}§6]§c foi escrita duas vezes - ela guarda um valor só, então mantenha a que você quis")
    public static LocaleMessage FLAG_REPEATED;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cNothing here reads the flag §6[§e${flag}§6]§c - it comes after the subcommand that declares it: §b${path} <subcommand> ${flag} ...")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cNada aqui lê a flag §6[§e${flag}§6]§c - ela vem depois do subcomando que a declara: §b${path} <subcomando> ${flag} ...")
    public static LocaleMessage FLAG_BEFORE_PATH;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cUnknown subcommand §6[§e${subcmd}§6]§c! Available: §b${available_subcmds}")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cSubcomando desconhecido §6[§e${subcmd}§6]§c! Disponíveis: §b${available_subcmds}")
    public static LocaleMessage UNKNOWN_SUBCOMMAND;

    @FCLocale(lang = LocaleType.EN_US, text = "§4§l ▶ §cSomething went wrong running this command - it has been reported to the server log.")
    @FCLocale(lang = LocaleType.PT_BR, text = "§4§l ▶ §cAlgo deu errado ao executar este comando - o erro foi registrado no log do servidor.")
    public static LocaleMessage INTERNAL_COMMAND_ERROR;

    private final @Nonnull FinalCMDPluginCommand finalCommand;
    private final FinalCMDData finalCMD;

    public FCDefaultExecutor(@Nonnull FinalCMDPluginCommand finalCommand) {
        this.finalCommand = finalCommand;
        this.finalCMD = finalCommand.getFinalCMD();
    }

    /**
     * The five phases, in this order and no other: WALK resolves the node by counting tokens, EXTRACT
     * pulls every declared flag out of the tail, CAPTURE runs each {@code @Capture} from the root
     * down, PARSE reads the executable's own positionals, INVOKE calls it.
     * <p>
     * The order exists to break a circle: finding the leaf would need the arity of the flags, and the
     * arity of the flags needs the leaf. Counting first, with flags refused before the path ends,
     * makes the traversal a single pass.
     */
    public void onCommand(FCommandSender sender, String label, String[] args) {

        CommandNode root = finalCommand.getRoot();

        if (root.getExecutable() != null && root.getExecutable().isPlayerOnly() && !sender.isPlayer()){
            FCMessageUtil.needsToBeAPlayer(sender);
            return;
        }

        //The command's own permission gates the help below as well, so it cannot wait for the full
        //access check further down - a sender who may not use /cmd may not read its help either
        if (!finalCMD.getPermission().isEmpty() && !FCMessageUtil.hasThePermission(sender, finalCMD.getPermission())){
            return;
        }

        if (finalCMD.getHelpType() != CMDHelpType.NONE && root.hasChildren() && opensRootHelp(root, args)){
            root.getHelpContext().render(CommandPath.ofRoot(label)).sendTo(sender, pageAfter(args, 1));
            return;
        }

        WalkResult walk = CommandWalker.walk(root, args, label);
        CommandNode target = walk.getNode();

        switch (walk.getOutcome()){
            case FLAG_TOO_EARLY:
                FLAG_BEFORE_PATH
                        .addPlaceholder("flag", walk.getOffendingToken())
                        .addPlaceholder("path", walk.getPath().full())
                        .send(sender);
                return;
            case NO_MATCH:
            case NODE_HELP:
            case RESERVED_HELP:
                sendHelpOrError(sender, label, walk, args);
                return;
            default:
                break;
        }

        CMDMethodInterpreter executable = target.getExecutable();

        for (CommandNode pathNode : walk.getPathNodes()) {
            if (pathNode.isPlayerOnly() && !sender.isPlayer()){
                FCMessageUtil.needsToBeAPlayer(sender);
                return;
            }
        }

        try {
            // The scope dies with the invocation, exception or not, so nothing of this execution
            // can be observed by the next one - not even on another thread.
            try (MessageScope scope = MessageScope.open(walk.getPath())) {
                //One access contract for the whole chain - the root included, every hop, and the
                //@FinalCMD.Execute declaration of the node that runs, which nothing else answers for.
                //Whoever refuses has already told the sender why.
                if (!CMDAccessValidation.allowsPath(sender, target, CMDAccessValidation.AccessMode.RUN)
                        || !CMDAccessValidation.allowsExecutableOf(sender, target, CMDAccessValidation.AccessMode.RUN)){
                    return;
                }

                MultiArgumentos window = new MultiArgumentos(args).sliceFrom(walk.getConsumed());

                //Always, even where nothing declares a flag: the bare "--" escape and the shape of a
                //flag marker are rules of the line itself, and a line whose meaning depended on what the
                //target happened to declare had two tokenizers wearing one syntax. It stops where the
                //variadic tail starts, because from there on the line is somebody's sentence.
                MultiArgumentos.FlagExtraction extraction = window.extractDeclaredFlags(
                        target.getAccumulatedFlagExtractionBindings(), executable.getGreedyTailIndex());
                if (!extraction.isClean()){
                    sendFlagExtractionError(sender, target, extraction);
                    return;
                }

                DispatchContext dispatch = new DispatchContext(label, walk.getPath(), window);
                HelpContextTemplate reached = reachedHelpContext(target);
                HelpContext helpContext = reached == null ? null : reached.render(walk.getPath());

                if (!runCaptures(sender, walk, dispatch, helpContext)){
                    return;
                }

                executable.invoke(sender, dispatch, window, helpContext, executable.getHelpLineTemplate().render(walk.getPath()));
            }
        } catch (IllegalAccessException | InvocationTargetException e) {
            //The last surface the framework owns. Rethrowing handed the sender whatever the platform
            //says about an unhandled command error - a different sentence on each one, in no language -
            //while the cause was logged here anyway. So it is answered here, once, and stops here.
            Throwable cause = e instanceof InvocationTargetException && e.getCause() != null ? e.getCause() : e;
            String commandInfo = getCommandInfo(executable, label, walk.getPath().joined(), args);
            finalCommand.getOwningPlugin().getLog().severe("Failed to execute the FinalCMD: {}", commandInfo, cause);
            INTERNAL_COMMAND_ERROR.send(sender);
        }
    }

    /**
     * Whether the first word of the line is asking for the root's help. An empty line is the
     * {@link CMDHelpType} question and nothing else; a help word is an interceptor, so it loses to any
     * declaration that would have taken that very token - a child answering it, or a capture standing
     * where it was typed. The word only means help where nothing claims it.
     */
    private boolean opensRootHelp(CommandNode root, String[] args) {
        String firstArg = args.length >= 1 ? args[0] : "";
        if (firstArg.isEmpty()){
            return finalCMD.getHelpType() != CMDHelpType.EXCEPT_EMPTY;
        }
        return HelpWords.isHelpWord(firstArg) && root.getCapture() == null && root.getChild(firstArg) == null;
    }

    /**
     * The help a method that asks for a {@link HelpContext} gets: the one of the node it was reached
     * through, not the root's. A leaf holds no help of its own - its siblings are its parent's children -
     * so the nearest ancestor that does is the honest answer, and the root always has one.
     */
    private static HelpContextTemplate reachedHelpContext(CommandNode target) {
        for (CommandNode node = target; node != null; node = node.getParent()) {
            if (node.getHelpContext() != null){
                return node.getHelpContext();
            }
        }
        return null;
    }

    /**
     * Runs every {@code @Capture} of the path, root first, each one reading only the tokens the
     * traversal already counted for it. A capture that refuses - by failing to parse or by answering
     * {@code null} - stops the whole dispatch: whoever refuses is whoever warns.
     *
     * @return false when the dispatch must stop
     */
    private boolean runCaptures(FCommandSender sender, WalkResult walk, DispatchContext dispatch, HelpContext helpContext) throws IllegalAccessException, InvocationTargetException {
        List<CommandNode> capturingNodes = walk.getNode().getCapturingAncestry();
        if (capturingNodes.isEmpty()){
            return true;
        }

        List<String> captureTokens = walk.getCaptureTokens();
        int cursor = 0;

        for (CommandNode capturingNode : capturingNodes) {
            CaptureBinding capture = capturingNode.getCapture();
            String[] tokens = captureTokens.subList(cursor, cursor + capture.tokenWidth()).toArray(new String[0]);
            cursor += capture.tokenWidth();

            CMDMethodInterpreter interpreter = capture.getInterpreter();
            //Without its own capture: the capture method's line appends its @Arg names itself, and
            //printing them twice is exactly what the sender is being asked to type once
            CommandPath capturePath = capturingNode.toUsagePath(dispatch.getLabel(), false);
            CMDMethodInterpreter.Invocation invocation = interpreter.invoke(sender, dispatch, new MultiArgumentos(tokens), helpContext, interpreter.getHelpLineTemplate().render(capturePath));

            if (invocation.isAborted()){
                return false;
            }

            //Published even when the capture returns nothing: a flags-only capture still eats tokens,
            //and a leaf may address one of them directly instead of taking the context object
            for (Map.Entry<String, Object> parsedArgument : invocation.getParsedArguments().entrySet()) {
                dispatch.getCaptures().putArg(capturingNode.getNodePath(), parsedArgument.getKey(), parsedArgument.getValue());
            }

            if (capture.getContextType() != null){
                if (invocation.getReturnedValue() == null){
                    return false; //a null context aborts silently: the capture already refused on its own terms
                }
                dispatch.getCaptures().put(capturingNode.getNodePath(), invocation.getReturnedValue());
            }
        }

        return true;
    }

    /**
     * What a path that reached no executable answers. A word that named no child is answered the same
     * way at every depth, root included - naming the children that do exist beats telling the sender
     * their parameters are wrong when the word itself was the problem. Running out of tokens is the
     * one thing the root still answers its own way: whether an empty line means help there is the
     * {@link CMDHelpType} gate above, and it already said no.
     */
    private void sendHelpOrError(FCommandSender sender, String label, WalkResult walk, String[] args) {
        CommandNode target = walk.getNode();

        if (walk.getOutcome() == WalkResult.Outcome.NO_MATCH){
            //The same predicate the tab and the help ask, so a typo cannot reveal what neither lists
            String available = target.getChildren().stream()
                    .filter(child -> CMDAccessValidation.listsFor(sender, child))
                    .map(CommandNode::getPrimaryLabel)
                    .collect(Collectors.joining(", "));

            UNKNOWN_SUBCOMMAND
                    .addPlaceholder("subcmd", walk.getOffendingToken())
                    .addPlaceholder("available_subcmds", available)
                    .send(sender);
            return;
        }

        if (target == finalCommand.getRoot()){
            PARAMETER_ERROR.addPlaceholder("label", label).send(sender);
            return;
        }

        target.getHelpContext().render(walk.getPath()).sendTo(sender, pageAfter(args, walk.getConsumed()));
    }

    /**
     * The page a help word asks for: the token right after it, when it reads as a number. Anything
     * else - no token, a word, a negative - is page one, because a help nobody could ask a page of is
     * still a help.
     */
    private static int pageAfter(String[] args, int index) {
        if (index < 0 || index >= args.length){
            return 1;
        }
        try {
            return Integer.parseInt(args[index].trim());
        }catch (NumberFormatException notAPage){
            return 1;
        }
    }

    /**
     * Says everything that went wrong with the flags at once, name problems before value problems.
     * Fixing one typo only to be told about the next one is the same trip twice, so all the markers
     * nobody declared travel in one sentence, and each declared flag whose value is missing or unwanted
     * gets its own - those two teach a form, and a form is per flag.
     * <p>
     * A target that declares no flag at all still reaches here, because the marker syntax is the line's
     * and not the declaration's: there the answer is not a list of alternatives but the escape.
     */
    private static void sendFlagExtractionError(FCommandSender sender, CommandNode target, MultiArgumentos.FlagExtraction extraction){
        if (!extraction.getUnknownMarkers().isEmpty()){
            //Hidden means hidden here too: a flag kept off the usage line is not revealed by a typo
            String availableFlags = target.getAccumulatedFlagBindings().stream()
                    .filter(binding -> binding.getArgData().isShowOnUsage())
                    .filter(binding -> binding.getArgData().getPermission().isEmpty() || sender.hasPermission(binding.getArgData().getPermission()))
                    .map(CMDMethodInterpreter.FlagBinding::getRawName)
                    .collect(Collectors.joining(", "));

            String unknown = String.join(", ", extraction.getUnknownMarkers());
            if (availableFlags.isEmpty()){
                UNKNOWN_FLAG_NO_LIST.addPlaceholder("flags", unknown).send(sender);
            }else {
                UNKNOWN_FLAG
                        .addPlaceholder("flags", unknown)
                        .addPlaceholder("available_flags", availableFlags)
                        .send(sender);
            }
        }

        for (String marker : extraction.getMarkersMissingValue()) {
            FLAG_NEEDS_VALUE.addPlaceholder("flag", marker).send(sender);
        }

        for (String marker : extraction.getMarkersRefusingValue()) {
            FLAG_TAKES_NO_VALUE.addPlaceholder("flag", marker).send(sender);
        }

        for (String marker : extraction.getRepeatedMarkers()) {
            FLAG_REPEATED.addPlaceholder("flag", marker).send(sender);
        }
    }

    private String getCommandInfo(CMDMethodInterpreter interpreter, String label, String subCommandName, String[] args) {

        if (!subCommandName.isEmpty()){
            label = String.format("%s %s", label, subCommandName);
        }

        if (interpreter == null) {
            return String.format("</%s> [%s] \n  Args: %s", label, finalCommand.getOwningPlugin().getMetaInfo().getName(), Arrays.toString(args));
        }

        String className = interpreter.getMethod().getDeclaringClass().getSimpleName();
        String methodName = interpreter.getMethod().getName();

        return String.format("</%s> [%s] #%s%%%s \n  Args: %s",
            label,
            finalCommand.getOwningPlugin().getMetaInfo().getName(),
            className,
            methodName,
            Arrays.toString(args));
    }
}
