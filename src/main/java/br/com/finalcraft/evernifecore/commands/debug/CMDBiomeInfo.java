package br.com.finalcraft.evernifecore.commands.debug;


import br.com.finalcraft.evernifecore.PermissionNodes;
import br.com.finalcraft.evernifecore.argumento.MultiArgumentos;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import org.bukkit.Location;
import org.bukkit.block.Biome;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class CMDBiomeInfo {

    @FinalCMD(
            aliases = {"biomeinfo"},
            permission = PermissionNodes.EVERNIFECORE_COMMAND_BIOMEINFO
    )
    public void onCommand(CommandSender sender, String label, MultiArgumentos argumentos) {

        if (sender instanceof Player == false || argumentos.get(0).equalsIgnoreCase("all")){
            for (Biome value : Biome.values()) {
                sender.sendMessage("§b§l > §e" + value.name());
            }
            return;
        }

        Player player = (Player) sender;
        Location location = player.getLocation();
        player.sendMessage("§7§o(" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ() + ") §e" + location.getBlock().getBiome().name());
    }



}
