package br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation;

import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.custom.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.data.CMDData;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.commands.finalcmd.tree.CommandNode;
import br.com.finalcraft.evernifecore.playerdata.PDSection;
import br.com.finalcraft.evernifecore.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.playerdata.PlayerData;
import br.com.finalcraft.evernifecore.util.FCMessageUtil;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Used to validate command access and HelpContext display based on customizable contexts
 *
 * Lets say for example there is a FinalCMD that implements {@link ICustomFinalCMD}
 * and it wants to only show some specific subCommands on the help line, and only allow the user to access these subCommands
 * when the player matches a specific requirement, lets say, be a CLAN_LEADER.
 *
 * So, to achieve this customization, the {@link ICustomFinalCMD} must customize the
 * cmdDatas using this context that is called before those actions
 */
public abstract class CMDAccessValidation {

    /**
     * The access contract, written once and read by every surface.
     * <p>
     * A command is reachable when the WHOLE chain lets the sender through: the root, every node walked
     * to get there, and - when the target is a node made executable by {@code @FinalCMD.Execute} - the
     * declaration on the method itself. Skipping a link is what lets tab and dispatch disagree.
     * <p>
     * The only thing that changes between surfaces is whether a refusal SPEAKS, which is what
     * {@link AccessMode} names.
     * <p>
     * {@code playerOnly} is not decided here: it comes from the parsers a method declares, so each
     * surface reads it off the node.
     */
    public enum AccessMode {
        /** About to run it: a missing permission says so, and a validation that refuses is the one that warns. */
        RUN,
        /** About to list it (tab, help): the same rules, evaluated without a word to the sender. */
        LIST
    }

    /** Whether ONE segment's own declaration lets {@code sender} through - its permission and its validations. */
    public static boolean allows(@Nonnull FCommandSender sender, @Nonnull CommandNode node, @Nonnull AccessMode mode) {
        return allows(sender, node.getCmdData(), new AccessContext(node, sender), mode);
    }

    /** Whether every segment from the root down to {@code target} lets {@code sender} through. */
    public static boolean allowsPath(@Nonnull FCommandSender sender, @Nonnull CommandNode target, @Nonnull AccessMode mode) {
        List<CommandNode> rootDownToTarget = new ArrayList<>();
        for (CommandNode node = target; node != null; node = node.getParent()) {
            rootDownToTarget.add(node);
        }
        Collections.reverse(rootDownToTarget); //the shallowest refusal is the one worth reporting

        for (CommandNode node : rootDownToTarget) {
            if (!allows(sender, node, mode)){
                return false;
            }
        }
        return true;
    }

    /**
     * Whether {@code node} is somewhere this sender could go at all - what every LISTING of nodes asks,
     * be it a tab completion or the "available subcommands" of a word that named none. On top of the
     * node's own declaration it answers for {@code playerOnly} and for the subtree: a branch whose every
     * leaf refuses this sender is not a place to go, so naming it in a list would only send them one hop
     * further into the same refusal.
     * <p>
     * The node's own {@code @FinalCMD.Execute} may deny while the branch still counts, because a denied
     * method says nothing about the children hanging under it.
     */
    public static boolean listsFor(@Nonnull FCommandSender sender, @Nonnull CommandNode node) {
        if (node.isPlayerOnly() && !sender.isPlayer()){
            return false;
        }
        if (!allows(sender, node, AccessMode.LIST)){
            return false;
        }
        if (node.getExecutable() != null && allowsExecutableOf(sender, node, AccessMode.LIST)){
            return true;
        }
        for (CommandNode child : node.getChildren()) {
            if (listsFor(sender, child)){
                return true;
            }
        }
        return false;
    }

    /**
     * Whether the executable {@code node} holds lets {@code sender} through on its OWN terms. Only a
     * {@code @FinalCMD.Execute} has any: it declares a permission and validations the node it hangs on
     * knows nothing about. A leaf's method and its node are one declaration, so there is nothing extra
     * to ask - and asking would run the same validation twice.
     */
    public static boolean allowsExecutableOf(@Nonnull FCommandSender sender, @Nonnull CommandNode node, @Nonnull AccessMode mode) {
        CMDMethodInterpreter executable = node.getExecutable();
        if (executable == null || executable.getCmdData() == node.getCmdData()){
            return true;
        }
        return allows(sender, executable.getCmdData(), new AccessContext(executable, sender), mode);
    }

    private static boolean allows(FCommandSender sender, CMDData<?> cmdData, AccessContext accessContext, AccessMode mode) {
        String permission = cmdData.getPermission();
        if (!permission.isEmpty()){
            boolean granted = mode == AccessMode.RUN
                    ? FCMessageUtil.hasThePermission(sender, permission)
                    : sender.hasPermission(permission);
            if (!granted){
                return false;
            }
        }

        for (CMDAccessValidation validation : cmdData.getCmdAccessValidations()) {
            boolean passed = mode == AccessMode.RUN
                    ? validation.onPreCommandValidation(accessContext)
                    : validation.onPreTabValidation(accessContext);
            if (!passed){
                return false;
            }
        }
        return true;
    }

    /**
     * Called before invoking and parsing all the arguments of the FinalCMD
     *
     * Here its usefull to warn the player if he has done something wrong!
     *
     * Returning false will prevent the player from invoking this command!
     */
    public abstract boolean onPreCommandValidation(AccessContext accessContext);

    /**
     * Called before adding a cmdData to a tab completion
     *
     * You should not warn the player here!
     *
     * Returning false will hide this cmdData from table completion
     */
    public abstract boolean onPreTabValidation(AccessContext accessContext);

    public static class AccessContext {

        private final CMDData<?> cmdData;
        private final @Nullable CMDMethodInterpreter interpreter;
        private final FCommandSender sender;

        public AccessContext(CMDMethodInterpreter interpreter, FCommandSender sender) {
            this.cmdData = interpreter.getCmdData();
            this.interpreter = interpreter;
            this.sender = sender;
        }

        /**
         * A validation being asked about a whole node instead of one method. A branch that only holds
         * children has no method to run, so {@link #getInterpreter()} is null there - read
         * {@link #getCmdData()} instead, which every segment has.
         */
        public AccessContext(CommandNode node, FCommandSender sender) {
            this.cmdData = node.getCmdData();
            this.interpreter = node.getExecutable();
            this.sender = sender;
        }

        /** The method being validated, or null when the segment is a branch with no executable of its own. */
        public @Nullable CMDMethodInterpreter getInterpreter() {
            return interpreter;
        }

        /** The labels, permission and validations of whatever is being validated - method or node. */
        public CMDData<?> getCmdData() {
            return cmdData;
        }

        public FCommandSender getSender() {
            return sender;
        }

        public boolean isPlayer(){
            return sender.isPlayer();
        }

        public PlayerData getPlayerData(){
            if (!isPlayer()) return null;

            return PlayerController.getPlayerData(sender.getUniqueId()).join();
        }

        public <P extends PDSection> P getPDSection(Class<P> pdSectionClass){
            if (!isPlayer()) return null;

            return PlayerController.getPDSection(sender.getUniqueId(), pdSectionClass).join();
        }

        public boolean hasProperPermission(){
            return sender.hasPermission(this.cmdData.getPermission());
        }
    }

}
