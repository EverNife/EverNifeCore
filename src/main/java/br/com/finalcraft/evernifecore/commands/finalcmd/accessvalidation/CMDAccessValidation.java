package br.com.finalcraft.evernifecore.commands.finalcmd.accessvalidation;

import br.com.finalcraft.evernifecore.commands.finalcmd.custom.ICustomFinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.executor.CMDMethodInterpreter;
import br.com.finalcraft.evernifecore.config.playerdata.PDSection;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerController;
import br.com.finalcraft.evernifecore.config.playerdata.PlayerData;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

    public static class Allowed extends CMDAccessValidation{

        @Override
        public boolean onPreCommandValidation(AccessContext accessContext) {
            return true;
        }

        @Override
        public boolean onPreTabValidation(AccessContext accessContext) {
            return true;
        }

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

        private final CMDMethodInterpreter interpreter;
        private final CommandSender sender;

        public AccessContext(CMDMethodInterpreter interpreter, CommandSender sender) {
            this.interpreter = interpreter;
            this.sender = sender;
        }

        public CMDMethodInterpreter getInterpreter() {
            return interpreter;
        }

        public CommandSender getSender() {
            return sender;
        }

        public boolean isPlayer(){
            return sender instanceof Player;
        }

        public PlayerData getPlayerData(){
            if (!isPlayer()) return null;

            return PlayerController.getPlayerData(((Player) sender).getUniqueId());
        }

        public <P extends PDSection> P getPDSection(Class<P> pdSectionClass){
            if (!isPlayer()) return null;

            return PlayerController.getPDSection(((Player) sender).getUniqueId(), pdSectionClass);
        }

        public boolean hasProperPermission(){
            return sender.hasPermission(this.interpreter.getCmdData().getPermission());
        }
    }

}
