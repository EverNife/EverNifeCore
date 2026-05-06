package br.com.finalcraft.evernifecore.minecraft.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.commands.finalcmd.help.HelpContext;
import br.com.finalcraft.evernifecore.math.game.vector.blockpos.BlockPos;
import br.com.finalcraft.evernifecore.math.vector.Vec3i;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

public class CMDBiomeInfo {

    @FinalCMD(
            aliases = {"biomeinfo"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_BIOMEINFO
    )
    public void onCommand(FCommandSender sender, MultiArgumentos argumentos, HelpContext helpContext) {

        if (!sender.isPlayer() && argumentos.emptyArgs(0)) {
            helpContext.sendTo(sender);
            return;
        }

        if (argumentos.get(0).equalsIgnoreCase("all")){
            for (Biome value : Biome.values()) {
                sender.sendMessage("§b§l > §e" + value.name());
            }
            return;
        }

        BlockPos blockPos = sender.asFPlayer().getLocation().getBlockPos();

        String biomeName = sender.asFPlayer().getDelegate(Player.class)
                .getLocation().getBlock().getBiome().name();

        sender.sendMessage("§7§o(" + blockPos.getX() + "," + blockPos.getY() + "," + blockPos.getZ() + ") §e" + biomeName);
    }



}
