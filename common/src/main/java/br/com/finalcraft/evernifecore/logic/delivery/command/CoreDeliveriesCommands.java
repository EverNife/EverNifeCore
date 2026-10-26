package br.com.finalcraft.evernifecore.logic.delivery.command;

import br.com.finalcraft.evernifecore.EverNifeCore;
import br.com.finalcraft.evernifecore.api.common.commandsender.FCommandSender;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.Arg;
import br.com.finalcraft.evernifecore.commands.finalcmd.annotations.FinalCMD;
import br.com.finalcraft.evernifecore.config.uuids.UUIDsController;
import br.com.finalcraft.evernifecore.locale.FCLocale;
import br.com.finalcraft.evernifecore.locale.LocaleType;
import br.com.finalcraft.evernifecore.logic.Caller;
import br.com.finalcraft.evernifecore.logic.delivery.Deliveries;
import br.com.finalcraft.evernifecore.logic.delivery.Delivery;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@code /ecore deliveries list|purge <plugin>}: what an admin sees and drops of the pending deliveries a plugin left. */
public class CoreDeliveriesCommands {

    @FinalCMD.SubCMD(subcmd = "list", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "How many pending deliveries a plugin left, per player"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Quantas entregas pendentes um plugin deixou, por jogador")
    })
    public void list(FCommandSender sender, @Arg("<plugin>") String plugin) {
        Deliveries.get().listFrom(plugin).whenComplete((found, failure) -> {
            if (failure != null) {
                sender.sendMessage("§cThe deliveries could not be read: " + failure.getMessage());
                return;
            }
            if (found.isEmpty()) {
                sender.sendMessage("§7No player has a pending delivery from §e" + plugin + "§7.");
                return;
            }
            sender.sendMessage("§6Pending deliveries from §e" + plugin + "§6, " + found.size() + " players:");
            for (Map.Entry<UUID, List<Delivery>> entry : found.entrySet()) {
                String name = UUIDsController.getNameFromUUID(entry.getKey());
                sender.sendMessage("§7- §f" + (name == null ? entry.getKey() : name) + "§7: §e" + entry.getValue().size());
            }
        });
    }

    @FinalCMD.SubCMD(subcmd = "purge", locales = {
            @FCLocale(lang = LocaleType.EN_US, text = "Drop every pending delivery a plugin left, of every player"),
            @FCLocale(lang = LocaleType.PT_BR, text = "Descarta toda entrega pendente que um plugin deixou, de todo jogador")
    })
    public void purge(FCommandSender sender, @Arg("<plugin>") String plugin) {
        Caller by = Caller.of(EverNifeCore.getEcPluginData(), "command:" + sender.getName());
        Deliveries.get().purge(plugin, by).whenComplete((count, failure) -> {
            if (failure != null) {
                sender.sendMessage("§cThe purge did not finish: " + failure.getMessage());
                return;
            }
            sender.sendMessage("§aPurged §e" + count + "§a pending deliveries from §e" + plugin + "§a.");
        });
    }
}
